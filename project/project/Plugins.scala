import sbt._

object Plugins extends Build {
  val playArtifactPluginVersion = "1.5"

	lazy val plugins = Project("plugins", file("."))
      .dependsOn(uri("git://github.com/guardian/sbt-play-artifact.git#" + playArtifactPluginVersion))
      .dependsOn(uri("git://github.com/guardian/sbt-version-info-plugin.git#2.1"))
}