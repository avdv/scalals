import scala.util.matching.Regex
import scala.sys.process._
import java.nio.file.Paths

Global / onChangedBuildSource := ReloadOnSourceChanges

ThisBuild / scalaVersion := "3.2.0"

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
    "-Xfatal-warnings"
    // "-Xlint",
    // "-Ywarn-dead-code", // N.B. doesn't work well with the ??? hole
    // "-Ywarn-numeric-widen",
    // "-Ywarn-value-discard"
  )
)

def generateConstants(base: File): File = {
  base.mkdirs()
  val outputFile = base / "constants.scala"
  val cc = sys.env.get("CC").getOrElse("clang")
  val output = scala.io.Source.fromString(s"$cc -E -P jvm/src/main/c/constants.scala.c".!!)
  val definition = "_(S_[^ ]+) = ([0-9]+)".r

  def getRadix(s: String) =
    if (s.startsWith("0x") || s.startsWith("0X")) 16
    else if (s.startsWith("0")) 8
    else 10

  // might be an octal, hexadecimal or decimal integer literal
  def readCNumber(s: String): Int = Integer.parseInt(s, getRadix(s))

  val constants = output.getLines().collect { case definition(name, value) =>
    f"val $name%s: Int = ${readCNumber(value)}%#x"
  }
  io.IO.write(
    outputFile,
    s"""package de.bley.scalals
       |
       |object UnixConstants {
       |  ${constants.mkString("\n  ")}
       |}
       |""".stripMargin
  )
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
        "com.github.scopt" %%% "scopt" % "4.1.0"
      )
    )
    // configure JVM settings
    .jvmSettings(
      // TODO: munit is not available for Scala 3 / Scala Native yet
      //       see https://github.com/scalameta/munit/issues/524
      libraryDependencies += "org.scalameta" %% "munit" % "0.7.29" % Test,
      Compile / sourceGenerators += Def.task {
        Seq(generateConstants((Compile / sourceManaged).value / "de" / "bley" / "scalals"))
      }.taskValue
    )
    // configure Scala-Native settings
    .nativeSettings(
      nativeConfig ~= { config =>
        val nixCC = sys.env.get("NIX_CC")

        val cc = for {
          n <- nixCC
          c <- sys.env.get("CC")
        } yield s"$n/bin/$c"

        val cxx = for {
          n <- nixCC
          c <- sys.env.get("CXX")
        } yield s"$n/bin/$c"

        val nixCFlagsLink = for {
          flags <- sys.env.get("NIX_CFLAGS_LINK").toList
          flag <- flags.split(" +") if flag.nonEmpty
        } yield flag

        config
          .withLinkingOptions("-fuse-ld=lld" +: nixCFlagsLink)
          .withClang(cc.fold(config.clang)(Paths.get(_)))
          .withClangPP(cxx.fold(config.clangPP)(Paths.get(_)))
      },
      nativeCompileOptions += "-Wall",
      nativeLinkStubs := false
    )
