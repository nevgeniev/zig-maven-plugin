

Zig Maven plugin
=====

This plugin allows you to execute `zig build` to build  `JNI` code of your java
project as a part of maven build to cross compile it for different platforms
making `JNI` great again ;).

Check https://ziglang.org to learn about zig. The most exciting things are:
* it can compile C code
* it can cross compile C code for different targets (linux/win/osx/...)


Check `jnidemo` project for quick example how to do it

How it works
===== 

It downloads zig runtime for your platform and caches it in `~/.m2` directory. 
Also, it downloads zig dependencies published as maven artifacts and feeds them to the 
`zig fetch` tool which in turn creates `build.zig.zon` file 

It uses `build.zig` file (must be next to your `pom.xml`) and runs `zig build` according to
parameters configured for the plugin. Check the sample `pom.xml` copied from `jnidemo` project:
 
```xml
<plugin>
    <groupId>io.github.nevgeniev.zig</groupId>
    <artifactId>zig-maven-plugin</artifactId>
    <version>0.3.0</version>
    <configuration>
        <!-- configure version of downloaded zig runtimes  -->
        <!-- one can configure group for zig runtime artifacts using "zigRuntimeGroup" option       -->
        <zigVersion>0.16.0</zigVersion>
        <targets>
            <target>
                <!-- zig -Dtarget= option -->
                <platform>x86_64-linux-gnu</platform>
                <!-- Path where to put library within generated jar -->
                <packageName>ne.zig.demo.amd64.linux</packageName>
                <!-- List of options like -Dcpu=... for zig         -->
                <options>
                    <cpu>x86_64_v3</cpu>
                </options>
            </target>
            <target>
                <platform>x86_64-windows-gnu</platform>
                <packageName>ne.zig.demo.amd64.windows</packageName>
            </target>
            <target>
                <platform>x86_64-macos</platform>
                <packageName>ne.zig.demo.amd64.osx</packageName>
            </target>
            <target>
                <platform>aarch64-macos</platform>
                <packageName>ne.zig.demo.aarch64.osx</packageName>
            </target>
            <target>
                <platform>aarch64-linux-gnu</platform>
                <packageName>ne.zig.demo.aarch64.linux</packageName>
            </target>
        </targets>
<!--
        disables dependency caching (build.zig.zon.sha1)
        not recommended. zig BLADE3 hashing is inconsistent on apple m2
        (m1/m4 - are fine)
-->
<!--        <dependencyCache>false</dependencyCache>-->
        <dependencies>
            <!-- list of cpp/zig dependencies for your project       -->
            <!-- to be installed using "zig fetch"                   -->
            <!-- To cross compile jni you need at least jni-includes -->
            <dependency>
                <groupId>io.github.nevgeniev.zig</groupId>
                <artifactId>jni-includes</artifactId>
                <version>1.0.0</version>
                <type>zip</type>
                <!-- unpack dependency before fetching                     -->
                <!-- zig fetch support for .zip is totally broken in 0.16  -->
                <!-- so you have to unpack it first                        -->
                <unpack>true</unpack>
            </dependency>
            <!-- common dependency for cpp projects (headers only!)       -->
            <dependency>
                <groupId>io.github.nevgeniev.zig</groupId>
                <artifactId>boost-headers</artifactId>
                <version>1.89.0</version>
                <type>tar.xz</type>
                <unpack>false</unpack>
            </dependency>
        </dependencies>
    </configuration>
    <executions>
        <execution>
            <id>zig-build</id>
            <goals>
                <goal>build</goal>
            </goals>
        </execution>
    </executions>
</plugin>
```

One last thing
====

You can code `JNI` in zig! Happy hacking :)
