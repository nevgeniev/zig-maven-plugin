package ne.zig.demo;

import org.junit.Test;

import java.io.IOException;

import static org.junit.Assert.assertEquals;

public class HelloJNITest {

    @Test
    public void testHelloJni() throws IOException {
        LibraryHelper helper = new LibraryHelper();
        helper.loadLibraryFromResource("ne.zig.demo", "hellojni");
        String result = HelloJNI.sayHello();
        assertEquals("Hello zig!", result);
    }
}