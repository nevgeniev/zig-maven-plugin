
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
import org.codehaus.plexus.components.io.fileselectors.FileSelector;
import org.codehaus.plexus.digest.Digester;
import org.codehaus.plexus.digest.DigesterException;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;

/**
 * Zig maven plugin. Automates downloading and execution of <pre>zig build</pre>
 */
@Mojo(name = "build", defaultPhase = LifecyclePhase.PROCESS_CLASSES)
public class ZigBuildMojo extends AbstractMojo {

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

    @Parameter(defaultValue = "0.16.0")
    private String zigVersion;

    @Parameter(defaultValue = "true", property = "zigDependencyCache")
    private boolean dependencyCache;

    @Parameter
    private List<ZigDependency> dependencies;

    /**
     * Required List of Targets
     *
     * @see Target
     */
    @Parameter(required = true)
    private List<Target> targets;

    @Parameter(defaultValue = "${project.build.directory}", readonly = true)
    private String buildDir;

    @Parameter(defaultValue = "${basedir}", readonly = true)
    private String baseDir;

    @Parameter(defaultValue = "io.github.nevgeniev.zig")
    private String zigRuntimeGroup;


    public void execute() throws MojoExecutionException, MojoFailureException {

        try {
            File runtimeDir = fetchZigRuntime(getZigArtifactString());

            execZigFetch(runtimeDir, fetchDependencies());

            execZigBuild(runtimeDir);
        } catch (ArtifactResolverException | NoSuchArchiverException | DigesterException | IOException | InterruptedException e) {
            throw new MojoExecutionException(e.getMessage(), e);
        }
    }

    private void execZigFetch(File runtimeDir, Map<ZigDependency, Artifact> artifacts) throws IOException, MojoFailureException, InterruptedException {
        if (dependencyCache && !shouldRunZigFetch(artifacts)) {
            getLog().info("Skipping zig fetch as dependencies are up-to-date");
            return;
        }

        for (Map.Entry<ZigDependency, Artifact> entry : artifacts.entrySet()) {
            Artifact a = entry.getValue();
            ZigDependency zd = entry.getKey();

            String depName = a.getArtifactId() + "_" + a.getVersion();

            String path = a.getFile().getAbsolutePath();

            try {
                if (zd.isUnpack()) {
                    path = tryUnpackArtifact(a, false).getAbsolutePath();
                }
            } catch (MojoExecutionException | DigesterException e) {
                throw new MojoFailureException("Failed to process artifact " + a, e);
            } catch (NoSuchArchiverException e) {
                // unsupported archive type try direct installation from file
                getLog().warn("Can't unpack " + path + " trying direct fetch");
            }

            getLog().info("Installing zig dependency: " + depName + " from " + path);
            ProcessBuilder pb = createZigProcessBuilder();
            pb.command(
                    runtimeDir + File.separator + "zig",
                    "fetch",
                    "--save=" + depName,
                    path
            );
            handleExecutionErrors(pb.start());
        }
        updateBuildZigZonDigest(artifacts);
    }

    private Map<ZigDependency, Artifact> fetchDependencies() throws ArtifactResolverException, DigesterException, NoSuchArchiverException {
        if (dependencies == null || dependencies.isEmpty()) {
            return Collections.emptyMap();
        }

        LinkedHashMap<ZigDependency, Artifact> result = new LinkedHashMap<>();

        for (ZigDependency d : dependencies) {

            result.put(d, downloader
                    .getArtifact(session, pomRemoteRepositories, d.toString())
                    .getArtifact()
            );
        }

        return result;
    }

    private File fetchZigRuntime(String artifactName)
            throws ArtifactResolverException, DigesterException, MojoExecutionException, NoSuchArchiverException {

        Artifact artifact = downloader
                .getArtifact(session, pomRemoteRepositories, artifactName)
                .getArtifact();

        return tryUnpackArtifact(artifact, true);
    }

    private File tryUnpackArtifact(Artifact artifact, boolean trimTopFolder) throws DigesterException, MojoExecutionException, NoSuchArchiverException {
        File destination = getDestinationFolder(artifact);
        if (destination.exists()) {
            return destination;
        }

        if (!destination.mkdirs()) {
            throw new MojoExecutionException("Can't create " + destination.getAbsolutePath());
        }

        UnArchiver u = archiverManager.getUnArchiver(artifact.getType());
        if (trimTopFolder) {
            u.setFileMappers(new FileMapper[]{pName -> {
                int idx = pName.indexOf('/');

                if (idx > 0) {
                    return idx + 1 < pName.length() ? pName.substring(idx + 1) : "";
                }
                return pName;
            }});
            u.setFileSelectors(new FileSelector[]{fileInfo -> {
                String name = fileInfo.getName();
                int idx = name.indexOf('/');
                return idx > 0 && idx + 1 < name.length();
            }});
        }
        u.setSourceFile(artifact.getFile());
        u.setDestDirectory(destination);
        u.extract();
        return destination;
    }

    private ArrayList<String> getZigBuildOptions(Target target, Map<String, String> env) {
        ArrayList<String> options = new ArrayList<>();
        options.add("-Dtarget=" + target.getPlatform());

        options.add("-DMAVEN_BUILD_LIB_DIR=" + "classes" + File.separator + target.getPackageName().replaceAll("\\.", File.separator));
        options.add("-DMAVEN_BUILD_JAVA_HOME=" + env.getOrDefault("JAVA_HOME", "_JAVA_HOME_NOT_SET_"));

        if (target.getOptions() == null) {
            return options;
        }

        for (Map.Entry<?, ?> e : target.getOptions().entrySet()) {
            options.add(String.format("-D%s=%s", e.getKey(), e.getValue()));
        }

        return options;
    }


    private ProcessBuilder createZigProcessBuilder() {
        ProcessBuilder pb = new ProcessBuilder();
        pb.directory(new File(baseDir));
        pb.redirectErrorStream(true);

        pb.environment().put("ZIG_GLOBAL_CACHE_DIR", session.getLocalRepository().getBasedir() + File.separator + ".zig-cache"); // .m2/repository/.zig-cache
        pb.environment().put("ZIG_LOCAL_CACHE_DIR", buildDir + File.separator + ".zig-cache"); // target

        return pb;
    }


    private void execZigBuild(File runtimePath) throws IOException, InterruptedException, MojoFailureException {
        for (Target target : targets) {
            getLog().info("Executing zig build for target platform: " + target.getPlatform() + " in " + baseDir);

            ProcessBuilder pb = createZigProcessBuilder();


            ArrayList<String> command = new ArrayList<>();
            command.add(runtimePath + File.separator + "zig");
            command.add("build");
            command.addAll(getZigBuildOptions(target, pb.environment()));
            command.add("--prefix");
            command.add(buildDir);

            pb.command(command.toArray(new String[0]));

            Process process = pb.start();

            handleExecutionErrors(process);
        }
    }

    private void handleExecutionErrors(Process process) throws IOException, InterruptedException, MojoFailureException {
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

        return String.format("%s:zig-%s-%s:%s:%s", zigRuntimeGroup, osName, osArch, zigVersion, packaging);
    }

    private File getDestinationFolder(Artifact artifact) throws DigesterException {
        String path = session.getLocalRepository().getBasedir() + File.separator + ".zig-runtime";

        File sha1file = new File(artifact.getFile().getAbsolutePath() + ".sha1");
        String sha1;
        try {
            if (sha1file.exists()) {
                sha1 = new String(Files.readAllBytes(sha1file.toPath()));
            } else {
                sha1 = digester.calc(artifact.getFile());
            }
        } catch (IOException e) {
            throw new DigesterException(e);
        }

        path += File.separator + sha1;
        return new File(path);
    }

    private String calcDependenciesDigest(Map<ZigDependency, Artifact> deps) throws IOException, DigesterException {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<ZigDependency, Artifact> kv : deps.entrySet()) {
            Artifact a = kv.getValue();
            ZigDependency d = kv.getKey();

            File sha1file = new File(a.getFile().getAbsolutePath() + ".sha1");
            sb.append(d.toDigestString(new String(Files.readAllBytes(sha1file.toPath()))));
        }

        MessageDigest md;
        try {
            md = MessageDigest.getInstance(digester.getAlgorithm());
        } catch (NoSuchAlgorithmException e) {
            throw new DigesterException(e);
        }

        byte[] digest = md.digest(sb.toString().getBytes());

        sb.setLength(0);
        for (byte b : digest) {
            sb.append(String.format("%02x", b));
        }

        return sb.toString();
    }


    private boolean shouldRunZigFetch(Map<ZigDependency, Artifact> deps) {
        File digestCache = new File(baseDir + File.separator + "build.zig.zon.sha1");

        if (!digestCache.exists()) {
            return true;
        }

        try {
            String cachedDigest = new String(Files.readAllBytes(digestCache.toPath()));
            String actualDigest = calcDependenciesDigest(deps);

            return !actualDigest.equals(cachedDigest);
        } catch (IOException | DigesterException e) {
            getLog().warn("Failed to check digest cache: ", e);
            return true;
        }
    }

    private void updateBuildZigZonDigest(Map<ZigDependency, Artifact> artifacts) {
        File digestCache = new File(baseDir + File.separator + "build.zig.zon.sha1");


        try {
            String digest = calcDependenciesDigest(artifacts);
            Files.write(digestCache.toPath(), digest.getBytes());
        } catch (IOException | DigesterException e) {
            getLog().warn("Failed to cache build.zig.zon.sha1 digest: ", e);
        }
    }

}