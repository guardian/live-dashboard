import sbt._
import Keys._
import PlayProject._
import sbtassembly.Plugin._
import AssemblyKeys._

object ApplicationBuild extends Build {

    val appName         = "live-dashboard"
    val appVersion      = "1.1"

    val appDependencies = Seq(
      "org.zeromq" %% "zeromq-scala-binding" % "0.0.3",
      "org.scala-tools.time" %% "time" % "0.5",
      "com.gu.openplatform" %% "content-api-client" % "1.13",
      "com.typesafe.akka" % "akka-agent" % "2.0",
      "org.joda" % "joda-convert" % "1.1" % "provided",
      "org.jsoup" % "jsoup" % "1.6.1",
      "net.liftweb" %% "lift-json" % "2.4",
      "net.liftweb" %% "lift-json-ext" % "2.4",
      "com.amazonaws" % "aws-java-sdk" % "1.3.4",
      "org.scala-tools.sbt" %% "io" % "0.11.2",
      "org.specs2" %% "specs2" % "1.6.1" % "test"
    )

    val main = PlayProject(appName, appVersion, appDependencies)
      .settings(defaultScalaSettings ++ assemblySettings:_*).settings(
      resolvers ++= Seq(
        "Typesafe Repository (snapshots)" at "http://repo.typesafe.com/typesafe/snapshots/",
        "Guardian Github Releases" at "http://guardian.github.com/maven/repo-releases"
      )
    ).settings(
      mainClass in assembly := Some("play.core.server.NettyServer"),
      jarName in assembly := "app.jar",

      // aws-java-sdk brings in commons-logging, but we don't want that because
      // we use slf4j's jcl-over-slf4j instead
      ivyXML :=
        <dependencies>
            <exclude org="commons-logging"/>
            <exclude org="org.springframework"/>
        </dependencies>,

      dist <<= myDistTask
    )

  lazy val myDistTask = (baseDirectory, target, name, assembly in assembly) map {
    (root, outDir, projectName, uberjar) =>
      // Build  magenta capable zip
      val distFile = outDir / "artifacts.zip"
      if (distFile exists) {
        distFile delete()
      }

      def inPackage(name: String) = "packages/%s/%s" format (projectName, name)

      val filesToZip = Seq(
        root / "conf" / "deploy.json" -> "deploy.json",
        root / "bash" / "run.sh" -> inPackage("run.sh"),
        uberjar -> inPackage(uberjar.getName)
      )

      IO.zip(filesToZip, distFile)

      distFile
  }
}
