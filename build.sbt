import scala.util.matching.Regex
import Regex.Groups
import scala.sys.process._
import java.nio.file.Paths

Global / onChangedBuildSource := ReloadOnSourceChanges

ThisBuild / scalaVersion := "3.3.0"

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
    // "-Xlint",
    // "-Ywarn-dead-code", // N.B. doesn't work well with the ??? hole
    // "-Ywarn-numeric-widen",
    // "-Ywarn-value-discard"
  )
)

def generateConstants(base: File): File = {
  base.mkdirs()
  val outputFile = base / "constants.scala"
  val cc = sys.env.getOrElse("CC", "clang")
  val output = scala.io.Source.fromString(s"$cc -E -P jvm/src/main/c/constants.scala.c".!!)
  val definition = "_(S_[^ ]+) = ((?:0[xX])?[0-9]+)".r
  val shift = "[(] *((?:0[xX])?[0-9]+) *>> *((?:0[xX])?[0-9]+) *[)]".r

  def evalShifts(s: String): String = {
    val replaced = shift.replaceAllIn(
      s,
      _ match {
        case Groups(CNumber(lhs), CNumber(rhs)) => (lhs >> rhs).toString
      },
    )
    if (replaced == s) s
    else evalShifts(replaced)
  }

  val constants = for {
    line <- output.getLines()
    Groups(name, CNumber(value)) <- definition.findFirstMatchIn(evalShifts(line))
  } yield f"val $name%s: Int = $value%#x"

  io.IO.write(
    outputFile,
    s"""package de.bley.scalals
       |
       |object UnixConstants {
       |  ${constants.mkString("\n  ")}
       |}
       |""".stripMargin,
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
        "com.github.scopt" %%% "scopt" % "4.1.0",
        "org.scalameta" %%% "munit" % "1.0.0-M8" % Test,
      ),
    )
    // configure JVM settings
    .jvmEnablePlugins(GraalVMNativeImagePlugin)
    .jvmSettings(
      Compile / sourceGenerators += Def.task {
        Seq(generateConstants((Compile / sourceManaged).value / "de" / "bley" / "scalals"))
      }.taskValue,
      Compile / run / fork := true,
      Compile / run / javaOptions += "--add-opens=java.base/sun.nio.fs=ALL-UNNAMED",
      Compile / packageSrc / mappings ~= { mappings =>
        mappings.map { case mapping @ (from, to) =>
          if (from.name == "Core.scala" && from.getPath.contains("/shared/"))
            from -> to.replace("Core.scala", "CoreShared.scala")
          else
            mapping
        }
      },
      graalVMNativeImageOptions ++= Seq(
        "--no-fallback",
        "-H:-CheckToolchain",
        s"-H:ReflectionConfigurationFiles=${baseDirectory.value / "graal-config.json" absolutePath}",
      ),
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
          .withLinkingOptions("-fuse-ld=lld" :: "-lc++abi" +: nixCFlagsLink)
          .withClang(cc.fold(config.clang)(Paths.get(_)))
          .withClangPP(cxx.fold(config.clangPP)(Paths.get(_)))
      },
      nativeCompileOptions += "-Wall",
      nativeLinkStubs := false,
    )
