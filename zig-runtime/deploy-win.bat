mvn gpg:sign-and-deploy-file ^
  -DpomFile=zig-windows-x86_64.pom ^
  -Dfile=zig-windows-x86_64-0.8.0.zip ^
  -DrepositoryId=ossrh ^
  -Durl=https://s01.oss.sonatype.org/service/local/staging/deploy/maven2

