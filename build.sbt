name := "cerebro"

maintainer := "Leonardo Menezes <leonardo.menezes@xing.com>"

packageSummary := "Elasticsearch web admin tool"

packageDescription := """cerebro is an open source(MIT License) elasticsearch web admin tool built
  using Scala, Play Framework, AngularJS and Bootstrap."""

version := "0.9.5-rbac"

scalaVersion := "2.13.4"

rpmVendor := "lmenezes"

rpmLicense := Some("MIT")

rpmUrl := Some("http://github.com/lmenezes/cerebro")

libraryDependencies ++= Seq(
  "com.typesafe.play" %% "play"                    % "2.8.7",
  "com.typesafe.play" %% "play-json"               % "2.9.1",
  "com.typesafe.play" %% "play-slick"              % "5.0.0",
  "com.typesafe.play" %% "play-slick-evolutions"   % "5.0.0",
  "org.xerial"        %  "sqlite-jdbc"             % "3.34.0",
  "org.specs2"        %% "specs2-junit"  % "4.10.0" % "test",
  "org.specs2"        %% "specs2-core"   % "4.10.0" % "test",
  "org.specs2"        %% "specs2-mock"   % "4.10.0" % "test",
  "com.unboundid"     %  "unboundid-ldapsdk"       % "6.0.3"  % "test",
  "com.nimbusds"      %  "nimbus-jose-jwt"         % "9.31"
)

libraryDependencies += filters
libraryDependencies += ws
libraryDependencies += guice

lazy val root = (project in file(".")).
  enablePlugins(PlayScala, BuildInfoPlugin, LauncherJarPlugin, JDebPackaging, RpmPlugin).
  settings(
    buildInfoKeys := Seq[BuildInfoKey](name, version, scalaVersion, sbtVersion),
    buildInfoPackage := "models"
  )

sources in (Compile, doc) := Seq.empty

enablePlugins(JavaServerAppPackaging)
enablePlugins(SystemdPlugin)

// Java 17+ module access for Guice reflection in forked test JVM
Test / javaOptions ++= Seq(
  "--add-opens=java.base/java.lang=ALL-UNNAMED",
  "--add-opens=java.base/java.lang.invoke=ALL-UNNAMED",
  "--add-opens=java.base/java.io=ALL-UNNAMED",
  "--add-opens=java.base/java.util=ALL-UNNAMED",
  "--add-opens=java.base/sun.security.ssl=ALL-UNNAMED",
  "--add-opens=java.base/sun.net.www.protocol.file=ALL-UNNAMED"
)
Test / fork := true

pipelineStages := Seq(digest, gzip)

serverLoading := Some(ServerLoader.Systemd)
systemdSuccessExitStatus in Debian += "143"
systemdSuccessExitStatus in Rpm += "143"
linuxPackageMappings += packageTemplateMapping(s"/var/lib/${packageName.value}")() withUser((daemonUser in Linux).value) withGroup((daemonGroup in Linux).value)
