mvn gpg:sign-and-deploy-file ^
  -DpomFile=zig-macos-x86_64.pom ^
  -Dfile=zig-macos-x86_64-0.8.0.tar.xz ^
  -DrepositoryId=ossrh ^
  -Durl=https://s01.oss.sonatype.org/service/local/staging/deploy/maven2

