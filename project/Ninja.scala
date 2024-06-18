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
import scala.scalanative.sbtplugin.ScalaNativePlugin.autoImport._
import scala.scalanative.sbtplugin.Utilities._
import scala.scalanative.linker.ReachabilityAnalysis

import sbt._
import Keys._
import java.nio.file.{ Files, Path, Paths }
import scala.scalanative.sbtplugin.ScalaNativePlugin
import scala.sys.process._
import scala.concurrent._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.Duration
import scala.collection.JavaConverters._
import java.nio.file.StandardOpenOption
import java.io.Writer
import java.nio.charset.StandardCharsets.UTF_8

/** Internal utilities to interact with Ninja. */
object Ninja extends AutoPlugin {
  implicit private val sharedScope = scala.scalanative.util.Scope.unsafe()

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
    ninjaCompileFile := target.value.toPath / "compile.ninja",
    runNinja := runNinjaTask.value,
  )

  lazy val runNinjaTask =
    Def.task {
      val ninjaFile = ninja.value

      Process(Seq("ninja", "-f", ninjaFile.abs)).!
    }

  import scala.reflect.runtime.{ universe => ru }

  private val m = ru.runtimeMirror(getClass.getClassLoader)
  private val nativeLibMod = ru.typeOf[NativeLib.type].termSymbol.asModule
  private val mm = m.reflectModule(nativeLibMod)
  private val im = m.reflect(mm.instance)

  private def unpackNativeCode(nativelib: NativeLib): Path = {
    unpackNativeCodeMethod(nativelib).asInstanceOf[Path]
  }

  private val unpackNativeCodeMethod = {
    val nlm = ru.typeOf[NativeLib.type].decl(ru.TermName("unpackNativeCode")).asMethod
    im.reflectMethod(nlm)
  }

  private def findNativePaths(destPath: Path): Seq[Path] = {
    findNativePathsMethod(destPath).asInstanceOf[Seq[Path]]
  }

  private val findNativePathsMethod = {
    val nlm = ru.typeOf[NativeLib.type].decl(ru.TermName("findNativePaths")).asMethod
    im.reflectMethod(nlm)
  }

  private def configureNativeLibrary(
      initialConfig: Config,
      analysis: ReachabilityAnalysis.Result,
      destPath: Path,
  ): Config = {
    configureNativeLibraryMethod(initialConfig, analysis, destPath).asInstanceOf[Config]
  }

  private val configureNativeLibraryMethod = {
    val nlm = ru.typeOf[NativeLib.type].decl(ru.TermName("configureNativeLibrary")).asMethod
    im.reflectMethod(nlm)
  }

  lazy val ninjaCompileTask =
    Def.task {
      val outfile = ninjaCompileFile.value
      val logger = streams.value.log.toLogger

      val config = {
        val mainClass = (Compile / selectMainClass).value
        val classpath = (Compile / fullClasspath).value.map(_.data.toPath)
        val baseDir = crossTarget.value

        scala.scalanative.build.Config.empty
          .withLogger(logger)
          .withMainClass(mainClass)
          .withClassPath(classpath)
          .withCompilerConfig(nativeConfig.value)
          .withBaseDir(baseDir.toPath())
      }

      val writer = Files.newBufferedWriter(outfile, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.CREATE)
      logger.info(s"generating $outfile")

      val fclasspath = NativeLib.filterClasspath(config.classPath)
      val fconfig = config.withClassPath(fclasspath)

      // create optimized code and generate ll
      val entries = ScalaNative.entries(fconfig)

      val result = for {
        linked <- ScalaNative.link(fconfig, entries)
        _ = ScalaNative.logLinked(fconfig, linked, "ninja")
        optimized <- ScalaNative.optimize(fconfig, linked)
        codegen <- ScalaNative.codegen(fconfig, optimized)
        generated <- Future.sequence(codegen)
      } yield {
        // find native libs
        val nativelibs = NativeLib.findNativeLibs(fconfig)

        // compile all libs
        val objectPaths = {
          val libObjectPaths = nativelibs
            .map { unpackNativeCode }
            .map { destPath =>
              val paths = findNativePaths(destPath)
              val projConfig = configureNativeLibrary(config, optimized, destPath)
              addBuildStatements(writer, projConfig, paths)
            }
            .flatten

          // compile generated ll
          val llObjectPaths = addBuildStatements(writer, fconfig, generated)

          libObjectPaths ++ llObjectPaths
        }

        writer.write(addExe(objectPaths))
        writer.write(addDefaultTarget("$program"))
        writer.close()
      }
      Await.result(result, Duration.Inf)
    }

  lazy val ninjaTask =
    Def.task {
      val logger = streams.value.log.toLogger
      val baseDir = crossTarget.value
      val config = {
        val mainClass = (Compile / selectMainClass).value
        val classpath = (Compile / fullClasspath).value.map(_.data.toPath)

        scala.scalanative.build.Config.empty
          .withLogger(logger)
          .withBaseDir(baseDir.toPath)
          .withMainClass(mainClass)
          .withClassPath(classpath)
          .withCompilerConfig(nativeConfig.value)
      }
      val outpath = config.artifactPath
      val ninjaBuild = (target.value / "build.ninja").toPath

      val fclasspath = NativeLib.filterClasspath(config.classPath)
      val fconfig = config.withClassPath(fclasspath)
      val incdir = sourceDirectory.value / "main" / "c" / "include"

      val entries = ScalaNative.entries(fconfig)

      val linkResult = ScalaNative.link(fconfig, entries).map { linked =>
        ScalaNative.logLinked(fconfig, linked, "ninja")

        val writer =
          Files.newBufferedWriter(ninjaBuild, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.CREATE)
        logger.info(s"generating $ninjaBuild")

        writer.write(addRules(config, linked, outpath, incdir.toPath))

        writer.write(s"include ${ninjaCompileFile.value}\n\n")
        writer.close()

        ninjaBuild
      }

      Await.result(linkResult, Duration.Inf)
    }

  def addExe(objectsPaths: Seq[Path]): String = {
    val paths = for {
      p <- objectsPaths
    } yield p.abs

    s"build $$program: exe ${paths.mkString(" ")}\n\n"
  }

  def addDefaultTarget(name: String): String = s"default $name\n\n"

  def addRules(config: Config, linkerResult: ReachabilityAnalysis.Result, outpath: Path, incdir: Path): String = {
    val configFlags = {
      if (config.compilerConfig.multithreadingSupport)
        Seq("-DSCALANATIVE_MULTITHREADING_ENABLED")
      else Nil
    }
    val cflags = opt(config) ++: flto(config) ++: ninja_target(config) ++: configFlags :+ "-fvisibility=hidden"

    val links = {
      val srclinks = linkerResult.links.map(_.name)
      val gclinks = config.gc.links
      // We need extra linking dependencies for:
      // * libdl for our vendored libunwind implementation.
      // * libpthread for process APIs and parallel garbage collection.
      // * Dbghelp for windows implementation of unwind libunwind API
      val platformsLinks =
        if (config.targetsWindows) Seq("dbghelp")
        else if (config.targetsOpenBSD || config.targetsNetBSD)
          Seq("pthread")
        else Seq("pthread", "dl")
      srclinks ++ gclinks ++ platformsLinks
    }
    val linkopts = linkOpts(config) ++ links.map("-l" + _)
    val linkflags = flto(config) ++ ninja_target(config)
    val ltoName = lto(config).getOrElse("none")

    s"""|clang = ${config.clang.abs}
        |clangpp = ${config.clangPP.abs}
        |
        |cflags = ${cflags.mkString(" ")}
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
      if (path.endsWith("windows/time.c")) {
        // FIXME patch time.c and remove all #define's and tzset function
        val lines = for {
          line <- scala.io.Source.fromFile(path.toFile).getLines
          if !line.startsWith("#define") && !line.contains("_tzset")
        } yield line

        Files.write(path, lines.toList.asJava, UTF_8)
      }
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
        // disable UB sanitizer in debug mode
        case Mode.Debug       => Seq("-fno-sanitize=all")
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

  private def ninja_target(config: Config): Seq[String] =
    config.compilerConfig.targetTriple match {
      case Some(tt) => Seq("-target", tt)
      case None     => Seq("-Wno-override-module")
    }

  private def opt(config: Config): Seq[String] =
    config.mode match {
      // disable UB sanitizer in debug mode
      case Mode.Debug       => List("-O0", "-fno-sanitize=all")
      case Mode.ReleaseFast => List("-O2", "-Xclang", "-O2")
      case Mode.ReleaseFull => List("-O3", "-Xclang", "-O3")
      case Mode.ReleaseSize => List("-Os", "-Xclang", "-Oz")
    }
}
