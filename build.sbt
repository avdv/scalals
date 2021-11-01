import scala.sys.process._
import java.nio.file.Paths

ThisBuild / scalaVersion := "2.13.7"

val sharedSettings = Seq(
  scalacOptions ++= Seq(
    "-deprecation",
    "-encoding",
    "UTF-8",
    "-feature",
    "-language:existentials",
    "-language:higherKinds",
    "-language:implicitConversions",
    "-unchecked",
    "-Xfatal-warnings",
    "-Xlint",
    "-Ywarn-dead-code", // N.B. doesn't work well with the ??? hole
    "-Ywarn-numeric-widen",
    "-Ywarn-value-discard"
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
      buildInfoPackage := "de.bley.scalals",
      libraryDependencies ++= Seq(
        "com.github.scopt" %%% "scopt" % "4.0.1",
        "org.scalameta" %%% "munit" % "0.7.29" % Test
      )
    )
    // configure JVM settings
    .jvmSettings(
      Compile / sourceGenerators += Def.task {
        Seq(generateConstants((Compile / sourceManaged).value / "de" / "bley" / "scalals"))
      }.taskValue
    )
    // configure Scala-Native settings
    .nativeSettings(
      nativeConfig ~= { config =>
        config
          .withClang(
            sys.env.get("CLANG_PATH").fold(config.clang)(Paths.get(_))
          )
          .withClangPP(
            sys.env.get("CLANGPP_PATH").fold(config.clangPP)(Paths.get(_))
          )
      //  _.withLTO(LTO.thin)
      //    .withMode(Mode.releaseFast)
      //    .withGC(GC.commix)
      },
      nativeCompileOptions += "-Wall",
      nativeLinkStubs := false
    )
