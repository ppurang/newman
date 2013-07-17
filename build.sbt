import net.virtualvoid.sbt.graph.Plugin
import org.scalastyle.sbt.ScalastylePlugin
import NewmanReleaseSteps._
import sbtrelease._
import ReleaseStateTransformations._
import ReleasePlugin._
import ReleaseKeys._
import sbt._

name := "newman"

organization := "com.stackmob"

scalaVersion := "2.10.2"

scalacOptions := Seq("-unchecked", "-deprecation", "-feature")

resolvers += "spray repo" at "http://repo.spray.io"

resolvers += "spray nightlies" at "http://nightlies.spray.io"

libraryDependencies ++= {
  val httpCoreVersion = "4.2.1"
  val httpClientVersion = "4.2.1"
  val scalaCheckVersion = "1.10.1"
  val specs2Version = "1.14"
  val mockitoVersion = "1.9.0"
  val scalazVersion = "7.0.0"
  val liftJsonVersion = "2.5"
  Seq(
    "org.scalaz" %% "scalaz-core" % scalazVersion,
    "org.scalaz" %% "scalaz-effect" % scalazVersion,
    "org.scalaz" %% "scalaz-concurrent" % scalazVersion,
    "org.apache.httpcomponents" % "httpcore" % httpCoreVersion,
    "org.apache.httpcomponents" % "httpclient" % httpClientVersion,
    "io.spray" % "spray-client" % "1.2-20130712",
    "com.typesafe.akka" %% "akka-actor" % "2.2.0",
    "com.twitter" %% "finagle-http" % "6.5.0",
    "net.liftweb" %% "lift-json-scalaz7" % liftJsonVersion,
    "org.scalacheck" %% "scalacheck" % scalaCheckVersion % "test",
    "org.specs2" %% "specs2" % specs2Version % "test",
    "org.pegdown" % "pegdown" % "1.0.2" % "test",
    "org.mockito" % "mockito-all" % mockitoVersion % "test"
  )
}

logBuffered := false

ScalastylePlugin.Settings

Plugin.graphSettings

releaseSettings

releaseProcess := Seq[ReleaseStep](
  checkSnapshotDependencies,
  inquireVersions,
  runTest,
  setReleaseVersion,
  commitReleaseVersion,
  setReadmeReleaseVersion,
  tagRelease,
  publishArtifacts,
  setNextVersion,
  commitNextVersion,
  pushChanges
)

publishTo <<= (version) { version: String =>
  val nexus = "https://oss.sonatype.org/"
  if (version.trim.endsWith("SNAPSHOT")) {
    Some("snapshots" at nexus + "content/repositories/snapshots")
   } else {
    Some("releases" at nexus + "service/local/staging/deploy/maven2")
  }
}

publishMavenStyle := true

publishArtifact in Test := true

testOptions in Test += Tests.Argument("html", "console")

pomExtra := (
  <url>https://github.com/stackmob/newman</url>
  <licenses>
    <license>
      <name>Apache 2</name>
      <url>http://www.apache.org/licenses/LICENSE-2.0.txt</url>
      <distribution>repo</distribution>
    </license>
  </licenses>
  <scm>
    <url>git@github.com:stackmob/newman.git</url>
    <connection>scm:git:git@github.com:stackmob/newman.git</connection>
  </scm>
  <developers>
    <developer>
      <id>arschles</id>
      <name>Aaron Schlesinger</name>
      <url>http://www.stackmob.com</url>
    </developer>
    <developer>
      <id>devmage</id>
      <name>Andrew Harris</name>
      <url>http://www.stackmob.com</url>
    </developer>
    <developer>
      <id>taylorleese</id>
      <name>Taylor Leese</name>
      <url>http://www.stackmob.com</url>
    </developer>
    <developer>
      <id>kelseyq</id>
      <name>Kelsey Innis</name>
      <url>http://www.stackmob.com</url>
    </developer>
    <developer>
      <id>wpalmeri</id>
      <name>Will Palmeri</name>
      <url>http://www.stackmob.com</url>
    </developer>
    <developer>
      <id>jrwest</id>
      <name>Jordan West</name>
      <url>http://github.com/jrwest</url>
    </developer>
  </developers>
)
