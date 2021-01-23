
import scala.sys.process._

// Set to false or remove if you want to show stubs as linking errors

val sharedSettings = Seq(
  scalaVersion := "2.13.4",
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

lazy val scalals =
  // select supported platforms
  crossProject(JVMPlatform, NativePlatform)
    .crossType(CrossType.Full)
    .in(file("."))
    .enablePlugins(BuildInfoPlugin)
    .settings(sharedSettings)
    .settings(
      libraryDependencies ++= Seq(
        "com.github.scopt" %%% "scopt" % "4.0.0",
        "org.scala-lang.modules" %% "scala-java8-compat" % "0.9.0"
      ),
      buildInfoKeys := Seq[BuildInfoKey](name, version, scalaVersion, sbtVersion),
      buildInfoPackage := "de.bley.scalals",
      testFrameworks := Seq(new TestFramework("utest.runner.Framework"))
    )
    // configure JVM settings
    .jvmSettings(
    )
    // configure Scala-Native settings
    .nativeSettings(
      //nativeConfig ~= {
      //  _.withLTO(LTO.thin)
      //    .withMode(Mode.releaseFast)
      //    .withGC(GC.commix)
      //}
      nativeCompileOptions += "-Wall",
      nativeLinkingOptions ++= {
        val cFiles = (baseDirectory.value / "src" / "main" / "c") ** "*.c"
        val outDir = (sourceManaged in Compile).value / "native"
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

