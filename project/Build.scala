import sbt._
import Keys._
import PlayProject._
import sbtassembly.Plugin._
import AssemblyKeys._
import com.gu.deploy.PlayArtifact._

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
    "org.elasticsearch" % "elasticsearch" % "0.19.4",
    "org.elasticsearch" % "elasticsearch-cloud-aws" % "1.6.0",
    "org.scala-sbt" %% "io" % "0.11.3",
    "com.gu" %% "management-play" % "5.12",
    "org.specs2" %% "specs2" % "1.6.1" % "test"
  )

  val main = PlayProject(appName, appVersion, appDependencies, mainLang = SCALA)
    .settings(playArtifactDistSettings: _*)
    .settings(
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
            <exclude org="org.scala-tools.sbt"/>
        </dependencies>,

      playArtifactResources <<= (baseDirectory, target, name, playArtifactResources) map {
        (base, target, name, defaults) =>
          defaults :+ (base / "bash" / "run.sh" -> "packages/%s/%s".format(name, "run.sh"))
      }
    )
}
