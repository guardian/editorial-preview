import com.typesafe.sbt.web.pipeline.Pipeline
import scala.sys.process.Process
import Path.relativeTo

name := "viewer"

version := "0.1-SNAPSHOT"

scalacOptions := Seq(
  "-unchecked",
  "-deprecation",
  "-feature",
  "-Xfatal-warnings"
)

lazy val root = (project in file("."))
  .enablePlugins(PlayScala)
  .enablePlugins(JDebPackaging)
  .enablePlugins(SystemdPlugin)
  .settings(
    Universal / javaOptions ++= Seq(
      "-Dpidfile.path=/dev/null"
     )
  )

scalaVersion := "2.13.0"

val awsVersion = "1.12.129"

val testDependencies = Seq(
  "org.scala-lang" %% "toolkit-test" % "0.1.7" % Test,
  "org.scalatest" %% "scalatest" % "3.2.13" % "test",
  "org.scalatestplus" %% "mockito-4-6" % "3.2.13.0" % "test",
  "org.mockito" %% "mockito-scala" % "1.17.12" % Test,
  "com.softwaremill.diffx" %% "diffx-scalatest-should" % "0.9.0" % Test,
)

libraryDependencies ++= Seq(
  "com.amazonaws" % "aws-java-sdk-s3" % awsVersion,
  "com.amazonaws" % "aws-java-sdk-ec2" % awsVersion,
  "com.amazonaws" % "aws-java-sdk-ses" % awsVersion,
  "com.gu" %% "pan-domain-auth-play_3-0" % "4.0.0",
  "net.logstash.logback" % "logstash-logback-encoder" % "7.2",
  ws,
  "com.google.guava" % "guava" % "27.0-jre",
  "de.leanovate.play-mockws" %% "play-mockws-3-0" % "3.0.3" % Test
  )

libraryDependencies ++= testDependencies

 pipelineStages := Seq(digest, gzip)

// Config for packing app for deployment
Universal / packageName := normalizedName.value

debianPackageDependencies := Seq("java11-runtime-headless")
maintainer := "Digital CMS <digitalcms.dev@guardian.co.uk>"
packageSummary := "viewer"
packageDescription := """wrapper over the preview mode to give different platform previews"""

PlayKeys.devSettings += "play.server.pekko.max-header-size" -> "16k"

