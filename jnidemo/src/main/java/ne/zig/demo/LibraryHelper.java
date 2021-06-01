package ne.zig.demo;

import java.io.*;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Objects;

class LibraryHelper {

    private final Path extractPath;

    public LibraryHelper() {
        try {
            extractPath = Files.createTempDirectory(null);
        } catch (IOException e) {
            throw new IllegalStateException("Can't create temporary directory", e);
        }
    }

    public String getOsName() {
        String os = System.getProperty("os.name").toLowerCase().replaceAll("[^a-z0-9]", "");
        if (os.startsWith("windows"))
            return "windows";
        if (os.startsWith("osx") || os.startsWith("macosx"))
            return "macos";
        if (os.startsWith("linux"))
            return "linux";

        return os;
    }

    public String getArch() {
        String osArch = System.getProperty("os.arch").toLowerCase().replaceAll("[^a-z0-9]", "");


        HashSet<String> amd64 = new HashSet<>(Arrays.asList(
                "x8664", "amd64", "ia32e", "em64t", "x64"
        ));

        HashSet<String> x86 = new HashSet<>(Arrays.asList(
                "x8632", "x86", "i386", "i486", "i586", "i686", "ia32", "x32"
        ));

        HashSet<String> aarch64 = new HashSet<>(Arrays.asList(
                "aarch64"
        ));

        if (amd64.contains(osArch)) {
            return "amd64";
        }

        if (x86.contains(osArch)) {
            return "x86";
        }

        if (aarch64.contains(osArch)) {
            return "aarch64";
        }

        return osArch;

    }

    public void loadLibraryFromResource(String packageName, String library) throws IOException {
        library = System.mapLibraryName(library);
        packageName = packageName.replaceAll("\\.", "/");
        if (!packageName.endsWith("/")) {
            packageName += "/";
        }

        packageName += getArch() + "/" + getOsName() + "/";

        String libFile = extractResource(packageName, library, LibraryHelper.class.getClassLoader(), extractPath.toString());
        System.load(libFile);
    }


    private static String extractResource(String resPackage, String resource, ClassLoader loader, String dstPath) throws IOException {
        File f = new File(dstPath + resource);
        if (f.exists()) {
            return f.getAbsolutePath();
        }

        URL u = loader.getResource(resPackage + resource);
        Objects.requireNonNull(u, "Can't create resource for " + resPackage + resource);

        try (
                InputStream in = u.openStream();
                OutputStream out = new FileOutputStream(f)) {

            Objects.requireNonNull(in, "can't open " + resPackage + resource);
            byte[] buf = new byte[4096];
            int bytes_read;
            while ((bytes_read = in.read(buf)) > 0)
                out.write(buf, 0, bytes_read);

            f.deleteOnExit();
            return f.getAbsolutePath();
        }
    }

}
