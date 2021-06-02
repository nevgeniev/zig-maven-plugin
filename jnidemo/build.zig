const std = @import("std");
const builtin = @import("builtin");
const Builder = std.build.Builder;


pub fn build(b: *Builder) void {
    const target = b.standardTargetOptions(.{});
    const mode = b.standardReleaseOptions();
    const lib = b.addSharedLibrary("hellojni", null, .unversioned);

    lib.addCSourceFile("src/main/zig/jni.c", &[_][]const u8 {
        "--std=c99"
    });

    lib.setTarget(target);
    lib.addIncludeDir("target/generated-include");
    lib.strip = true;
    lib.linkLibC();

    const env = b.env_map;
    const java_home = env.get("JAVA_HOME");
    const jni_md = env.get("JNI_INCLUDES") orelse java_home;
    const target_dir = env.get("TARGET_LIB_DIR");
    if (target_dir) |dir| {
        lib.override_dest_dir = std.build.InstallDir{ .Custom = dir };
    }

    const os = target.os_tag orelse builtin.os.tag;
    const target_os = switch (os) {
        .linux => "linux",
        .windows => "win32",
        .macos => "darwin",
        else => "unsupported",
    };

    lib.addIncludeDir(b.fmt("{s}/include", .{ java_home }));
    lib.addIncludeDir(b.fmt("{s}/include/{s}", .{ jni_md, target_os }));
    lib.setBuildMode(mode);
    lib.install();
}
