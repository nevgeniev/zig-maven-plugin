

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

It downloads zig runtime for your platform and caches it in `~/.zigcache` directory.
Also, it downloads missing `JNI`headers necessary for cross compilation. As of now
headers are provided for `windows`, `linux`, `osx`

It uses `build.zig` file (must be next to your `pom.xml`) and runs `zig build` according to
parameters configured for the plugin. Check the sample `pom.xml` copied from `jnidemo` project:
 
```xml
      <plugin>
        <groupId>io.github.nevgeniev.zig</groupId>
        <artifactId>zig-maven-plugin</artifactId>
        <version>0.2.1</version>
        <configuration>
          <zigVersion>0.8.0</zigVersion>
          <targets>
            <target>
              <platform>x86_64-linux-gnu</platform>
              <packageName>ne.zig.demo.amd64.linux</packageName>
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
              <platform>aarch64-linux-gnu</platform>
              <packageName>ne.zig.demo.aarch64.linux</packageName>
            </target>
          </targets>
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
