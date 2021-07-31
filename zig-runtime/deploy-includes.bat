mvn gpg:sign-and-deploy-file ^
  -DpomFile=jni-includes.pom ^
  -Dfile=jni-includes-1.0.0.zip  ^
  -DrepositoryId=ossrh ^
  -Durl=https://s01.oss.sonatype.org/service/local/staging/deploy/maven2

