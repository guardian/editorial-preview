import com.typesafe.sbt.web.pipeline.Pipeline

name := "viewer"

version := "0.1-SNAPSHOT"

lazy val root = (project in file("."))
  .enablePlugins(PlayScala)
  .enablePlugins(SbtWeb)
  .enablePlugins(RiffRaffArtifact)

scalaVersion := "2.11.6"

resolvers += "scalaz-bintray" at "http://dl.bintray.com/scalaz/releases"

libraryDependencies ++= Seq(
  "com.amazonaws" % "aws-java-sdk" % "1.10.10",
  "net.logstash.logback" % "logstash-logback-encoder" % "4.5.1",
  ws
)

// Play provides two styles of routers, one expects its actions to be injected, the
// other, legacy style, accesses its actions statically.
routesGenerator := InjectedRoutesGenerator


// Front-end assets config
val bundle = taskKey[Pipeline.Stage]("JSPM bundle")

bundle := { mappings =>
  val log = streams.value.log
  val sourceDir = (resourceDirectory in Assets).value
  log.info("Running JSPM bundle")
  val cmd = Process("npm run bundlejs", baseDirectory.value) !< log
  if (cmd != 0) sys.error(s"Non-zero error code for `npm run bundlejs`: $cmd")
  mappings ++ ((sourceDir * "build.js*") pair relativeTo(sourceDir))
}

pipelineStages := Seq(bundle, digest, gzip)


// Config for packing app for deployment
packageName in Universal := normalizedName.value

riffRaffPackageType := (packageZipTarball in config("universal")).value

riffRaffArtifactResources ++= Seq(
  baseDirectory.value / "cloudformation" / "editorial-viewer.json" ->
    "packages/cloudformation/editorial-viewer.json"
)

riffRaffPackageName := s"editorial-tools:${name.value}"

riffRaffBuildIdentifier := Option(System.getenv("CIRCLE_BUILD_NUM")).getOrElse("DEV")

riffRaffUploadArtifactBucket := Option("riffraff-artifact")

riffRaffUploadManifestBucket := Option("riffraff-builds")
