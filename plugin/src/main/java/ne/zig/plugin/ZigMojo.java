
package ne.zig.plugin;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.shared.transfer.artifact.resolve.ArtifactResolverException;
import org.codehaus.plexus.archiver.UnArchiver;
import org.codehaus.plexus.archiver.manager.ArchiverManager;
import org.codehaus.plexus.archiver.manager.NoSuchArchiverException;
import org.codehaus.plexus.components.io.filemappers.FileMapper;
import org.codehaus.plexus.components.io.fileselectors.FileInfo;
import org.codehaus.plexus.components.io.fileselectors.FileSelector;
import org.codehaus.plexus.digest.Digester;
import org.codehaus.plexus.digest.DigesterException;

import javax.annotation.Nonnull;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;

/**
 * Zig maven plugin. Automaates downloading and execution of <pre>zig build</pre>
 */
@Mojo(name = "build", defaultPhase = LifecyclePhase.COMPILE)
public class ZigMojo extends AbstractMojo {

    @Component
    private ArtifactDownloader downloader;

    @Component
    private ArchiverManager archiverManager;

    @Component(hint = "sha1")
    private Digester digester;

    @Parameter(defaultValue = "${session}", required = true, readonly = true)
    private MavenSession session;

    @Parameter(defaultValue = "${project.remoteArtifactRepositories}", readonly = true, required = true)
    private List<ArtifactRepository> pomRemoteRepositories;

    @Parameter(defaultValue = "0.8.0")
    private String zigVersion;

    @Parameter(readonly = true)
    private String cachePath;

    @Parameter(defaultValue = "${user.home}", readonly = true)
    private String userHome;

    @Parameter(defaultValue = "true", readonly = true)
    private boolean useRuntimeCache;

    @Parameter(required = true)
    private List<Target> targets;

    @Parameter(defaultValue = "${project.build.directory}", readonly = true)
    private String buildDir;

    public void execute() throws MojoExecutionException, MojoFailureException {

        try {
            File runtimeDir = fetchArtifact(getZigArtifactString(), true);
            File includesDir = fetchArtifact("io.github.nevgeniev.zig:jni-includes:1.0.0:zip", false);

            execZigBuild(runtimeDir, includesDir);
        } catch (ArtifactResolverException | NoSuchArchiverException | DigesterException | IOException | InterruptedException e) {
            throw new MojoExecutionException(e.getMessage(), e);
        }
    }

    private File fetchArtifact(String artifactName, boolean trimTopFolder)
            throws ArtifactResolverException, DigesterException, MojoExecutionException, NoSuchArchiverException {

        Artifact artifact = downloader
                .getArtifact(session, pomRemoteRepositories, artifactName)
                .getArtifact();

        File destination = getDestinationFolder(artifact);
        if (!destination.exists()) {
            if (!destination.mkdirs()) {
                throw new MojoExecutionException("Can't create " + destination.getAbsolutePath());
            }
            unpackArtifactToFolder(artifact, destination, trimTopFolder);
        }
        return destination;
    }

    private void execZigBuild(File runtimePath, File includePath) throws IOException, InterruptedException, MojoFailureException {
        for (Target target : targets) {
            getLog().info("Executing zig build for target platform: " + target.getPlatform());
            ProcessBuilder pb = new ProcessBuilder();
            pb.environment().put(
                    "TARGET_LIB_DIR",
                    "classes" + "/" + target.getPackageName().replaceAll("\\.", "/"));
            pb.environment().put("JNI_INCLUDES", includePath.getAbsolutePath());
            pb.command(
                    runtimePath + File.separator + "zig",
                    "build",
                    "-Dtarget=" + target.getPlatform(),
                    "--cache-dir", buildDir + "/zig-cache",
                    "--prefix", buildDir
//                    "--verbose-cc"
            );
            pb.redirectErrorStream(true);
            Process process = pb.start();

            String errorMessage = "zig execution failed";
            try (BufferedReader stdInput = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String s;
                while ((s = stdInput.readLine()) != null) {
                    if (s.startsWith("error: ")) {
                        errorMessage = s;
                    }
                    getLog().info(s);
                }
            }

            int errCode = process.waitFor();
            if (errCode != 0) {
                throw new MojoFailureException(errorMessage);
            }
        }
    }

    private String getZigArtifactString() throws MojoFailureException {
        String osName = System.getProperty("os.name").toLowerCase().replaceAll("[^a-z0-9]", "");
        String osArch = System.getProperty("os.arch").toLowerCase().replaceAll("[^a-z0-9]", "");

        if (osName.startsWith("windows")) {
            osName = "windows";
        } else if (osName.startsWith("osx") || osName.startsWith("macos")) {
            osName = "macos";
        } else if (osName.startsWith("linux")) {
            osName = "linux";
        } else {
            throw new MojoFailureException("Failed to detect OS for: " + System.getProperty("os.name"));
        }

        HashSet<String> x86_64 = new HashSet<>(Arrays.asList(
                "x8664", "amd64", "ia32e", "em64t", "x64"
        ));

        HashSet<String> i386 = new HashSet<>(Arrays.asList(
                "x8632", "x86", "i386", "i486", "i586", "i686", "ia32", "x32"
        ));

        HashSet<String> aarch64 = new HashSet<>(Arrays.asList(
                "aarch64"
        ));

        if (x86_64.contains(osArch)) {
            osArch = "x86_64";
        } else if (i386.contains(osArch)) {
            osArch = "i386";
        } else if (aarch64.contains(osArch)) {
            osArch = "aarch64";
        } else {
            throw new MojoFailureException("Failed to detect platform arch: " + System.getProperty("os.arch"));
        }

        String packaging = osName.equals("windows") ? "zip" : "tar.xz";

        return String.format("io.github.nevgeniev.zig:zig-%s-%s:%s:%s", osName, osArch, zigVersion, packaging);
    }

    private File getDestinationFolder(Artifact artifact) throws DigesterException {
        String path = cachePath;
        if (path == null) {
            path = userHome + File.separator + ".zigcache";
        }
        path += File.separator + digester.calc(artifact.getFile());
        return new File(path);
    }

    private void unpackArtifactToFolder(Artifact artifact, File folder, boolean trimTopFolder) throws NoSuchArchiverException {
        UnArchiver u = archiverManager.getUnArchiver(artifact.getType());
        if (trimTopFolder) {
            u.setFileMappers(new FileMapper[]{new FileMapper() {
                @Override
                public String getMappedFileName(String pName) {
                    int idx = pName.indexOf('/');

                    if (idx > 0) {
                        return idx + 1 < pName.length() ? pName.substring(idx + 1) : "";
                    }
                    return pName;
                }
            }});
            u.setFileSelectors(new FileSelector[]{new FileSelector() {
                @Override
                public boolean isSelected(@Nonnull FileInfo fileInfo) {
                    String name = fileInfo.getName();
                    int idx = name.indexOf('/');
                    return idx > 0 && idx + 1 < name.length();
                }
            }});
        }
        u.setSourceFile(artifact.getFile());
        u.setDestDirectory(folder);
        u.extract();
    }
}