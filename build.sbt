import scala.util.matching.Regex
import Regex.Groups
import scala.sys.process._
import java.nio.file.{ Files, Paths }
import java.io.File
import scala.scalanative.build.NativeConfig

Global / onChangedBuildSource := ReloadOnSourceChanges

ThisBuild / scalaVersion := "3.3.3"

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

lazy val targetTriplet = settingKey[Option[String]]("describes target platform for native compilation")

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
        "org.scalameta" %%% "munit" % "1.0.0-M11" % Test,
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
        "--add-opens=java.base/sun.nio.fs=ALL-UNNAMED",
        s"-H:ReflectionConfigurationFiles=${baseDirectory.value / "graal-config.json" absolutePath}",
      ),
    )
    // configure Scala-Native settings
    .nativeSettings(
      targetTriplet := None,
      nativeConfig := {
        val config = nativeConfig.value
        val nixCFlagsCompile = for {
          flags <- sys.env.get("NIX_CFLAGS_COMPILE").toList
          flag <- flags.split(" +") if flag.nonEmpty
        } yield flag

        val nixCFlagsLink = for {
          flags <- sys.env.get("NIX_CFLAGS_LINK").toList
          flag <- flags.split(" +") if flag.nonEmpty
        } yield flag

        config
          .withCompileOptions(nixCFlagsCompile)
          .withLinkingOptions("-fuse-ld=lld" :: nixCFlagsLink)
          .withTargetTriple(targetTriplet.value)
      },
      Compile / nativeLink / artifactPath := {
        val p = (Compile / nativeLink / artifactPath).value
        val target = targetTriplet.value.fold("") { t =>
          val Array(arch, os, _) = t.split("-", 3)
          s"-$os-$arch"
        }
        p.getParentFile / (p.getName + target)
      },
      nativeCompileOptions += "-Wall",
      nativeLinkStubs := false,
    )
