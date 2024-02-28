/*
 * Drop this into the `project` directory of your scala-native project (needs version 0.4.9+)
 *
 * Now, you can build your project with ninja: `sbt runNinja`
 *
 * Note: this only compiles the .ll and .c(pp) files to .o files and produces a resulting binary,
 *       the heavy lifting of generating .ll files is still done by the sbt scala-native plugin
 *       especially including re-generating the .ll files each time
 */

package scala.scalanative
package build

import scala.scalanative.build._
import scala.scalanative.build.core._
import scala.scalanative.linker
import scala.scalanative.sbtplugin.ScalaNativePlugin.autoImport._
import scala.scalanative.sbtplugin.Utilities._

import sbt._
import Keys._
import java.nio.file.{ Files, Path, Paths }
import scala.scalanative.sbtplugin.ScalaNativePlugin
import scala.sys.process._
import java.nio.file.OpenOption
import java.nio.file.StandardOpenOption
import java.io.OutputStream
import java.io.Writer

/** Internal utilities to interact with Ninja. */
object Ninja extends AutoPlugin {
  private var sharedScope = scala.scalanative.util.Scope.unsafe()

  override def requires = ScalaNativePlugin

  implicit class RichPath(val path: Path) extends AnyVal {
    def abs: String = path.toAbsolutePath.toString
  }

  override def trigger = allRequirements

  object autoImport {
    val ninja = taskKey[Path]("build ninja file")
    val runNinja = taskKey[Unit]("run ninja")
    val ninjaCompileFile = settingKey[Path]("file with ninja build rules")
    val ninjaCompile = taskKey[Unit]("generate ninja compile file")
  }

  import autoImport._

  override lazy val projectSettings = Seq(
    ninja := ninjaTask.value,
    ninjaCompile := ninjaCompileTask.value,
    ninjaCompileFile := (Compile / crossTarget).value.toPath / "compile.ninja",
    runNinja := runNinjaTask.value,
  )

  lazy val runNinjaTask =
    Def.task {
      val ninjaFile = ninja.value

      Process(Seq("ninja", "-f", ninjaFile.abs)) !
    }

  lazy val ninjaCompileTask =
    Def.task {
      val outfile = ninjaCompileFile.value
      val logger = streams.value.log.toLogger

      val config = {
        val mainClass = (Compile / selectMainClass).value.getOrElse {
          throw new MessageOnlyException("No main class detected.")
        }
        val classpath = (Compile / fullClasspath).value.map(_.data.toPath)
        val maincls = mainClass // + "$"
        val cwd = (Compile / scala.scalanative.sbtplugin.ScalaNativePluginInternal.nativeWorkdir).value

        scala.scalanative.build.Config.empty
          .withLogger(logger)
          .withMainClass(maincls)
          .withClassPath(classpath)
          .withWorkdir(cwd.toPath)
          .withCompilerConfig(nativeConfig.value)
      }

      val writer = Files.newBufferedWriter(outfile, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.CREATE)
      logger.info(s"generating $outfile")

      val fclasspath = NativeLib.filterClasspath(config.classPath)
      val fconfig = config.withClassPath(fclasspath)
      val workdir = fconfig.workdir

      // create optimized code and generate ll
      val entries = ScalaNative.entries(fconfig)
      val linked = ScalaNative.link(fconfig, entries)(sharedScope)
      ScalaNative.logLinked(fconfig, linked)
      val optimized = ScalaNative.optimize(fconfig, linked)
      val generated = ScalaNative.codegen(fconfig, optimized)

      // find native libs
      val nativelibs = NativeLib.findNativeLibs(fconfig.classPath, workdir)

      // compile all libs
      val objectPaths = {
        val libObjectPaths = nativelibs
          .map { NativeLib.unpackNativeCode }
          .map { destPath =>
            val paths = NativeLib.findNativePaths(workdir, destPath)
            val (projPaths, projConfig) =
              Filter.filterNativelib(fconfig, linked, destPath, paths)

            addBuildStatements(writer, projConfig, projPaths)
          }
          .flatten

        // compile generated ll
        val llObjectPaths = addBuildStatements(writer, fconfig, generated)

        libObjectPaths ++ llObjectPaths
      }

      writer.write(addExe(config, objectPaths))
      writer.write(addDefaultTarget("$program"))
      writer.close()
    }

  lazy val ninjaTask =
    Def.task {
      val logger = streams.value.log.toLogger
      val outpath = (Compile / nativeLink / artifactPath).value.toPath
      val config = {
        val mainClass = (Compile / selectMainClass).value.getOrElse {
          throw new MessageOnlyException("No main class detected.")
        }
        val classpath = (Compile / fullClasspath).value.map(_.data.toPath)
        val maincls = mainClass // + "$"
        val cwd = (Compile / scala.scalanative.sbtplugin.ScalaNativePluginInternal.nativeWorkdir).value

        scala.scalanative.build.Config.empty
          .withLogger(logger)
          .withMainClass(maincls)
          .withClassPath(classpath)
          .withWorkdir(cwd.toPath)
          .withCompilerConfig(nativeConfig.value)
      }
      val ninjaBuild = config.workdir / "build.ninja"

      val fclasspath = NativeLib.filterClasspath(config.classPath)
      val fconfig = config.withClassPath(fclasspath)
      val workdir = fconfig.workdir
      val incdir = sourceDirectory.value / "main" / "c" / "include"

      val entries = ScalaNative.entries(fconfig)
      val linked = ScalaNative.link(fconfig, entries)(sharedScope)
      ScalaNative.logLinked(fconfig, linked)

      val writer = Files.newBufferedWriter(ninjaBuild, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.CREATE)
      logger.info("generating build.ninja")

      writer.write(addRules(config, linked, outpath, incdir.toPath))

      writer.write(s"include ${ninjaCompileFile.value}\n\n")

      writer.close()

      ninjaBuild
    }

  def addExe(config: Config, objectsPaths: Seq[Path]): String = {
    val paths = for {
      p <- objectsPaths
      if !p.toString.contains("/scala-native/windows/")
    } yield p.abs

    s"build $$program: exe ${paths.mkString(" ")}\n\n"
  }

  def addDefaultTarget(name: String): String = s"default $name\n\n"

  def addRules(config: Config, linkerResult: linker.Result, outpath: Path, incdir: Path): String = {
    val flags = opt(config) ++: flto(config) ++: target(config) :+ "-fvisibility=hidden"

    val links = {
      val srclinks = linkerResult.links.map(_.name)
      val gclinks = config.gc.links
      // We need extra linking dependencies for:
      // * libdl for our vendored libunwind implementation.
      // * libpthread for process APIs and parallel garbage collection.
      "pthread" +: "dl" +: srclinks ++: gclinks
    }
    val linkopts = linkOpts(config) ++ links.map("-l" + _)
    val linkflags = flto(config) ++ Seq("-rdynamic") ++ target(config)
    val ltoName = lto(config).getOrElse("none")

    s"""|clang = ${config.clang.abs}
        |clangpp = ${config.clangPP.abs}
        |
        |cflags = ${flags.mkString(" ")}
        |
        |ldflags = ${linkflags.mkString(" ")}
        |ldopts = ${linkopts.mkString(" ")}
        |
        |program = $outpath
        |
        |rule cc
        |  command = $$clang -std=gnu11 $$cflags $$auxflags -c $$in -o $$out
        |  description = compile object file (${config.gc.name} gc, $ltoName lto)
        |
        |rule ll
        |  command = $$clang $$cflags -c $$in -o $$out
        |  description = compile ll file (${config.gc.name} gc, $ltoName lto)
        |
        |rule cpp
        |  command = $$clangpp -std=c++11 $$cflags $$auxflags -isystem $incdir -c $$in -o $$out
        |  description = compile c++ file (${config.gc.name} gc, $ltoName lto)
        |
        |rule exe
        |  command = $$clangpp $$ldflags $$in -o $$out $$ldopts
        |  description = link exe (${config.gc.name} gc, $ltoName lto)
        |
        |""".stripMargin
  }

  def addBuildStatements(writer: Writer, config: Config, files: Seq[Path]): Seq[Path] = {
    files.map { path =>
      val inpath = path.abs
      val outpath = inpath + oExt
      val isCpp = inpath.endsWith(cppExt)
      val isLl = inpath.endsWith(llExt)
      val objPath = Paths.get(outpath)

      val rule = if (isCpp) "cpp" else if (isLl) "ll" else "cc"

      writer.write(s"build $objPath: $rule $inpath\n")

      if (config.compileOptions.nonEmpty) {
        writer.write(s"  auxflags = ${config.compileOptions.mkString(" ")}\n")
      }
      writer.write('\n')

      objPath
    }
  }

  /** Object file extension: ".o" */
  val oExt = ".o"

  /** C++ file extension: ".cpp" */
  val cppExt = ".cpp"

  /** LLVM intermediate file extension: ".ll" */
  val llExt = ".ll"

  /** List of source patterns used: ".c, .cpp, .S" */
  val srcExtensions = Seq(".c", cppExt, ".S")

  private def linkOpts(config: Config): Seq[String] =
    config.linkingOptions ++ {
      config.mode match {
        case Mode.ReleaseFull => Seq("-s")
        case _                => Seq.empty
      }
    }

  private def lto(config: Config): Option[String] =
    (config.mode, config.LTO) match {
      case (Mode.Debug, _)             => None
      case (_: Mode.Release, LTO.None) => None
      case (_: Mode.Release, lto)      => Some(lto.name)
    }

  private def flto(config: Config): Seq[String] =
    lto(config).fold[Seq[String]] {
      Seq()
    } { name => Seq(s"-flto=$name") }

  private def target(config: Config): Seq[String] =
    config.compilerConfig.targetTriple match {
      case Some(tt) => Seq("-target", tt)
      case None     => Seq("-Wno-override-module")
    }

  private def opt(config: Config): Seq[String] =
    config.mode match {
      case Mode.Debug       => List("-O0")
      case Mode.ReleaseFast => List("-O2", "-Xclang", "-O2")
      case Mode.ReleaseFull => List("-O3", "-Xclang", "-Ofast")
      case Mode.ReleaseSize => List("-Os", "-Xclang", "-Oz")
    }
}
