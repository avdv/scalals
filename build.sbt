
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
      libraryDependencies ++= Seq(
        "org.scala-lang.modules" %% "scala-java8-compat" % "0.9.0",
      ),
      buildInfoKeys := Seq[BuildInfoKey](name, version, scalaVersion, sbtVersion),
      buildInfoPackage := "de.bley.scalals",
      testFrameworks := Seq(new TestFramework("utest.runner.Framework"))
    )
    // configure JVM settings
    .jvmSettings(
      libraryDependencies ++= Seq(
        "com.github.scopt" %% "scopt" % "4.0.1",
        "com.lihaoyi" %% "utest" % "0.7.10" % Test
      ),
      Compile / sourceGenerators += Def.task {
        Seq(generateConstants((Compile / sourceManaged).value / "de" / "bley" / "scalals"))
      }.taskValue
    )
    // configure Scala-Native settings
    .nativeSettings(
      libraryDependencies ++= Seq(
        "com.github.scopt" %% "scopt_native0.4" % "4.0.1" intransitive(),
        "com.lihaoyi" %% "utest_native0.4" % "0.7.10" % Test intransitive()
      ),
      //nativeConfig ~= {
      //  _.withLTO(LTO.thin)
      //    .withMode(Mode.releaseFast)
      //    .withGC(GC.commix)
      //}
      nativeCompileOptions += "-Wall",
      nativeLinkingOptions ++= {
        val cFiles = (baseDirectory.value / "src" / "main" / "c") ** "*.c"
        val outDir = (Compile / sourceManaged).value / "native"
        val clang = nativeClang.value.absolutePath
        val clangOptions = nativeCompileOptions.value

        IO.createDirectory(outDir)

        cFiles.get.map { cfile ⇒
          val objFile = outDir / cfile.base + ".o"
          val _ = Process(clang, clangOptions ++ Seq(cfile.absolutePath, "-c", "-o", objFile)).!!
          objFile
        }
      },
      nativeLinkStubs := true
    )

