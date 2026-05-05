package ne.zig.plugin;

import java.util.Properties;

/**
 * Describes zig build target with extra options
 */
public class Target {
    /**
     * Target platform like `x86_64-linux-gnu`
     * passed as -Dtarget=... argument to zig build
     *
     */
    private String platform;

    /**
     * Java package name where to put a compiled binary
     *
     */
    private String packageName;

    /**
     * Collection of extra options passed to zig build
     * Each option maps to -DoptionName=optionValue
     */
    private Properties options;


    public String getPlatform() {
        return platform;
    }

    public void setPlatform(String platform) {
        this.platform = platform;
    }

    public String getPackageName() {
        return packageName;
    }

    public void setPackageName(String packageName) {
        this.packageName = packageName;
    }

    public Properties getOptions() {
        return options;
    }

    public void setOptions(Properties options) {
        this.options = options;
    }
}
