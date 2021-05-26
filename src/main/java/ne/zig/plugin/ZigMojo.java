
package ne.zig.plugin;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Component;
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
 * Says "Hi" to the user.
 */
@Mojo(name = "build")
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

    @Parameter(defaultValue = "0.7.1")
    private String zigVersion;

    @Parameter
    private String cachePath;

    @Parameter(defaultValue = "${user.home}")
    private String userHome;

    @Parameter(defaultValue = "true")
    private boolean useRuntimeCache;

    public void execute() throws MojoExecutionException, MojoFailureException {

        try {
            Artifact artifact = downloader.getArtifact(
                    session, pomRemoteRepositories, getArtifactString()
            ).getArtifact();

            File destination = getDestinationFolder(artifact);
            if (!destination.exists()) {
                if (!destination.mkdirs()) {
                    throw new MojoExecutionException("Can't create " + destination.getAbsolutePath());
                }
                unpackArtifactToFolder(artifact, destination);
            }

            execZigBuild(destination);

        } catch (ArtifactResolverException | NoSuchArchiverException | DigesterException | IOException | InterruptedException e) {
            throw new MojoExecutionException(e.getMessage(), e);
        }
    }

    private void execZigBuild(File runtimePath) throws IOException, InterruptedException, MojoFailureException {
        ProcessBuilder pb = new ProcessBuilder();
        pb.command(
                runtimePath + File.separator + "zig",
                "build"
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

    private String getArtifactString() {
        String osName = System.getProperty("os.name").toLowerCase();
        String osArch = System.getProperty("os.arch").toLowerCase();

        getLog().info("OS name: " + osName);
        getLog().info("OS Arch: " + osArch);

        if (osName.startsWith("windows")) {
            osName = "windows";
        } else if (osName.startsWith("osx") || osName.startsWith("macos")) {
            osName = "macos";
        } else if (osName.startsWith("linux")) {
            osName = "linux";
        } else {
            osName = "unknown";
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
        }

        String packaging = osName.equals("windows") ? "zip" : "tar.xz";

        return String.format("org.ziglang:zig-%s-%s:%s:%s", osName, osArch, zigVersion, packaging);
    }

    private File getDestinationFolder(Artifact artifact) throws DigesterException {
        String path = cachePath;
        if (path == null) {
            path = userHome + File.separator + ".zigcache";
        }
        path += File.separator + digester.calc(artifact.getFile());
        return new File(path);
    }

    private void unpackArtifactToFolder(Artifact artifact, File folder) throws NoSuchArchiverException {
        UnArchiver u = archiverManager.getUnArchiver(artifact.getType());
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
        u.setSourceFile(artifact.getFile());
        u.setDestDirectory(folder);
        u.extract();
    }
}