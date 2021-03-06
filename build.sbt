
import scala.sys.process._

ThisBuild / scalaVersion := "2.13.6"

val sharedSettings = Seq(
  scalacOptions ++= Seq(
    "-deprecation",
    "-encoding", "UTF-8",
    "-feature",
    "-language:existentials",
    "-language:higherKinds",
    "-language:implicitConversions",
    "-unchecked",
    "-Xfatal-warnings",
    "-Xlint",
    "-Ywarn-dead-code",        // N.B. doesn't work well with the ??? hole
    "-Ywarn-numeric-widen",
    "-Ywarn-value-discard",
  )
)

def generateConstants(base: File): File = {
  base.mkdirs()
  val outputFile = base / "constants.scala"
  val cmd = s"tcc -run jvm/src/main/c/constants.scala.c" #> outputFile
  cmd.!
  outputFile
}

lazy val scalals =
  // select supported platforms
  crossProject(JVMPlatform, NativePlatform)
    .crossType(CrossType.Full)
    .in(file("."))
    .enablePlugins(BuildInfoPlugin)
    .settings(sharedSettings)
    .settings(
      buildInfoKeys := Seq[BuildInfoKey](name, version, scalaVersion, sbtVersion),
      buildInfoPackage := "de.bley.scalals"
    )
    // configure JVM settings
    .jvmSettings(
      libraryDependencies ++= Seq(
        "com.github.scopt" %% "scopt" % "4.0.1",
        "org.scalameta" %% "munit" % "0.7.27" % Test
      ),
      Compile / sourceGenerators += Def.task {
        Seq(generateConstants((Compile / sourceManaged).value / "de" / "bley" / "scalals"))
      }.taskValue
    )
    // configure Scala-Native settings
    .nativeSettings(
      libraryDependencies ++= Seq(
        "com.github.scopt" %% "scopt_native0.4" % "4.0.1" intransitive(),
        "org.scalameta" %% "munit_native0.4" % "0.7.27" % Test intransitive()
      ),
      //nativeConfig ~= {
      //  _.withLTO(LTO.thin)
      //    .withMode(Mode.releaseFast)
      //    .withGC(GC.commix)
      //}
      nativeCompileOptions += "-Wall",
      nativeLinkStubs := false
    )

