package com.github.mlangc.vcs.bench.interpreter

import java.io.File
import java.nio.file.Files
import java.util.stream.Collectors

import cats.data.Chain
import cats.data.NonEmptyList
import com.github.mlangc.vcs.bench.{BaseTest, Environments, UIOR}
import com.github.mlangc.vcs.bench.convenience.zio._
import com.github.mlangc.vcs.bench.model.History
import com.github.mlangc.vcs.bench.model.Operation
import com.github.mlangc.vcs.bench.util.TmpFilesSupport
import eu.timepit.refined.auto._
import scalaz.zio.Task
import scalaz.zio.TaskR
import scalaz.zio.ZManaged
import scalaz.zio.interop.IOAutocloseableOps
import scalaz.zio.random.Random

import scala.collection.JavaConverters._

abstract class GenericInterpreterTest extends BaseTest with TmpFilesSupport {
  protected trait NativeVcmInterface {
    def isProperlyInitialized: Task[Boolean]
    def untrackedFiles: Task[List[File]]
    def getNumCommits: Task[Int]
  }

  protected def debug: Boolean = false
  protected def lenOfRandomHistory: Int = 50
  protected def getInterpreter(testDir: File): ZManaged[InterpreterEnv, Throwable, VcmInterpreter]
  protected def mkNativeVcmInterface(basePath: File): TaskR[InterpreterEnv, NativeVcmInterface]
  protected override def keepTmpDirs: Boolean = debug

  "Init a repo" - {
    "with a single file" in {
      val history = Chain(
        Operation.Create(
          NonEmptyList.of("laus", "bube.txt"), Chain("hundsbub", "schiacha")
        ), Operation.Commit("Do host as!")
      )

      runTestWithHistory(history)
    }

    "with a randomly generated history" in {
      runTestWithHistory(History.generate(lenOfRandomHistory))
    }
  }

  private def runTestWithHistory(getHistory: UIOR[Random, History]): Unit = {
    unsafeRun(getHistory.map(commitChanges) >>= testWithHistory)
  }

  private def runTestWithHistory(history: History): Unit = {
    unsafeRun(testWithHistory(commitChanges(history)))
  }

  private def testWithHistory(history: History): Task[Unit] = {
    val paths = History.paths(history)

    Environments.live.flatMap { env =>
      getInterpreterWithBaseDir.use { case (interpreter, baseDir) =>
        interpreter.run(history) *> mkNativeVcmInterface(baseDir).flatMap { ni =>
          for {
            properlyInitialized <- ni.isProperlyInitialized
            untrackedFiles <- ni.untrackedFiles
            numCommits <- ni.getNumCommits
            numRegularFiles <- countRegularFilesBelow(baseDir)
            _ <- Task {
              assert(properlyInitialized)
              assert(untrackedFiles.isEmpty)
              assert(numCommits === countCommits(history))
              assert(numRegularFiles === paths.size.toLong)
            }
          } yield ()
        }
      }.provide(env)
    }
  }

  private def commitChanges(history: History): History = {
    history.iterator.toSeq.lastOption match {
      case Some(op) if !op.isCommit => history :+ Operation.Commit("Commit remaining changes")
      case _ => history
    }
  }

  private def getInterpreterWithBaseDir: ZManaged[InterpreterEnv, Throwable, (VcmInterpreter, File)] = {
    for {
      tmpTestDir <- getTmpTestDir
      interpreter <- getInterpreter(tmpTestDir)
    } yield (interpreter, tmpTestDir)
  }

  private def countRegularFilesBelow(dir: File): TaskR[InterpreterEnv, Long] = {
    blockingTask(Files.walk(dir.toPath)).bracketAuto { stream =>
      blockingTask {
        def isHidden(path: java.nio.file.Path): Boolean = {
          path.iterator().asScala.exists(_.toString.startsWith("."))
        }

        val paths = stream.filter(!isHidden(_))
          .map[java.io.File](_.toFile)
          .filter(_.isFile)
          .collect(Collectors.toList[File])

        paths.size().toLong
      }
    }
  }

  private def countCommits(history: History): Int = {
    history.iterator.count(_.isCommit)
  }
}
