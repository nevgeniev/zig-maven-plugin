const std = @import("std");



pub fn build(b: *std.Build) void {
    const target = b.standardTargetOptions(.{});
    const optimize = b.standardOptimizeOption(.{
        .preferred_optimize_mode = .ReleaseFast
    });

    const module = b.createModule(.{
        .target = target,
        .optimize = optimize,
        .omit_frame_pointer = false,
        .link_libc = true
    });

    const lib = b.addLibrary(.{
        .linkage = .dynamic,
        .name = "hellojni",
        .root_module = module,
    });

    module.addCSourceFiles(.{
        .files = &.{
            "src/main/zig/jni.c"
        },
        .flags = &.{
            "--std=c99"
        }
    });

    configureMavenBuild(b, lib);

    b.installArtifact(lib);
}

fn configureMavenBuild(b: *std.Build, lib: *std.Build.Step.Compile) void {
    const java_home  = b.option([] const u8, "MAVEN_BUILD_JAVA_HOME",
        "JAVA_HOME re-export").?;
    const target_dir = b.option([] const u8, "MAVEN_BUILD_LIB_DIR", "output folder for the library").?;

    const os = lib.rootModuleTarget().os.tag;

    lib.root_module.addIncludePath(b.path("target/generated-include/")); // or whatever was set for javac -h
    lib.root_module.addIncludePath(.{ .cwd_relative = b.fmt("{s}/include", .{ java_home })});

    const jni_md = b.dependency("jni-includes_1.0.0", .{}); // jni_md includes for non-native platforms
    const target_os = switch (os) {
        .linux => "linux",
        .windows => "win32",
        .macos => "darwin",
        else => unreachable,
    };
    lib.root_module.addIncludePath(jni_md.path(b.fmt("include/{s}", .{target_os})));


    // add copy step
    const write_files = b.addUpdateSourceFiles();
    const dst_path = b.fmt("target/{s}/{s}", .{target_dir, lib.out_filename});
    write_files.addCopyFileToSource(lib.getEmittedBin(), dst_path);
    b.getInstallStep().dependOn(&write_files.step);
}