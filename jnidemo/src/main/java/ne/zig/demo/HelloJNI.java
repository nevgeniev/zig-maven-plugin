package ne.zig.demo;

import java.io.*;

public class HelloJNI {

    private static final LibraryHelper helper = new LibraryHelper();

    public static native String sayHello();

    public static void main(String[] args) throws IOException {
        new LibraryHelper().loadLibraryFromResource("ne.zig.demo", "hellojni");
        System.out.println(sayHello());
    }

}
