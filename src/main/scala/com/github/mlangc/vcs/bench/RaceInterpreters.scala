package com.github.mlangc.vcs.bench

import java.io.File

import cats.instances.map._
import cats.syntax.monoid._
import com.github.mlangc.vcs.bench.interpreter.SvnClientAdapterInterpreter.Flavour
import com.github.mlangc.vcs.bench.interpreter._
import com.github.mlangc.vcs.bench.logging.ZioLogger
import com.github.mlangc.vcs.bench.model.History
import com.github.mlangc.vcs.bench.util.TmpFilesSupport
import com.github.mlangc.vcs.bench.util.perf.PerfStats
import com.github.mlangc.vcs.bench.util.zio.StopWatch
import eu.timepit.refined.api.Refined
import eu.timepit.refined.auto._
import eu.timepit.refined.numeric.Positive
import scalaz.zio._
import scalaz.zio.blocking.Blocking

object RaceInterpreters extends App with TmpFilesSupport {
  private val logger = new ZioLogger[RaceInterpreters.type]

  object cfg {
    val historyLen: Int Refined Positive = 5000
    val numRaces: Int Refined Positive = 10
    val customTmpDir: Option[File] = Some(new File("/home/mlangc/tmp/vcs-bench"))
  }

  def run(args: List[String]): ZIO[Any, Nothing, Int] = {
    val prog = for {
      _ <- logger.info(s"Racing interpreters with histories of length ${cfg.historyLen} for ${cfg.numRaces} times")
      raceResults <- doRaces
      _ <- logger.info(renderRaceResults(raceResults))
    } yield 0

    Environments.live.flatMap(prog.provide): Task[Int]
  }.orDie

  override protected def useCustomTmpDir: Option[File] = cfg.customTmpDir

  private sealed trait InterpreterName
  private object InterpreterName {
    case object SvnKit extends InterpreterName
    case object Jgit extends InterpreterName
    case object SvnClientJavaHl extends InterpreterName
    case object SvnClientSvnKit extends InterpreterName
  }

  private type RaceResults = Map[InterpreterName, PerfStats]

  private case class Interpreters(jgit: JgitInterpreter,
                                  svnKit: SvnKitInterpreter,
                                  svnClientJavaHl: SvnClientAdapterInterpreter,
                                  svnClientSvnKit: SvnClientAdapterInterpreter)

  private def doRaces: ZIO[AppEnv, Throwable, Map[InterpreterName, PerfStats]] =
    ZIO.mergeAll(Iterable.fill(cfg.numRaces)(doSingleRace))(
      Map.empty[InterpreterName, PerfStats])(_ |+| _)

  private def doSingleRace: ZIO[AppEnv, Throwable, RaceResults] =
    for {
      history <- History.generate(cfg.historyLen)
      res <- doSingleRace(history)
    } yield res

  private def doSingleRace(history: History): TaskR[AppEnv, RaceResults] =
    for {
      jGitCounter <- Ref.make(0)
      svnKitCounter <- Ref.make(0)
      svnClientJavaHlCounter <- Ref.make(0)
      svnClientSvnKitCounter <- Ref.make(0)
      results <- {
        getInterpreters.use { interpreters =>
          val runWithJgit = runWithOpCounter(jGitCounter, interpreters.jgit, history)
          val runWithSvnClientJavaHl = runWithOpCounter(svnClientJavaHlCounter, interpreters.svnClientJavaHl, history)
          val runWithSvnClientSvnKit = runWithOpCounter(svnClientSvnKitCounter, interpreters.svnClientSvnKit, history)
          val runWithSvnKit = runWithOpCounter(svnKitCounter, interpreters.svnKit, history)

          for {
            _ <- logger.info("Starting race...")
            res <- StopWatch.timed(ZIO.raceAll(runWithJgit, Iterable(runWithSvnClientJavaHl, runWithSvnKit, runWithSvnClientSvnKit)))
            _ <- logger.info(s"Race finished after ${res.duration.toMillis}ms")
            jGitOps <- jGitCounter.get
            svnKitOps <- svnKitCounter.get
            svnClientJavaHlOps <- svnClientJavaHlCounter.get
            svnClientSvnKitOps <- svnClientSvnKitCounter.get
          } yield {
            val duration = res.duration
            Map(
              InterpreterName.SvnClientJavaHl -> PerfStats(svnClientJavaHlOps -> duration),
              InterpreterName.SvnClientSvnKit -> PerfStats(svnClientSvnKitOps -> duration),
              InterpreterName.Jgit -> PerfStats(jGitOps -> duration),
              InterpreterName.SvnKit -> PerfStats(svnKitOps -> duration)): RaceResults
          }
        }
      }
    } yield results


  private def runWithOpCounter(counter: Ref[Int], interpreter: VcmInterpreter, history: History): TaskR[InterpreterEnv, Unit] = {
    history.uncons match {
      case None => ZIO.unit
      case Some((op, ops)) =>
        interpreter.run(op) *> counter.update(_ + 1) *> runWithOpCounter(counter, interpreter, ops)
    }
  }

  private def getJgitInterpreter: ZManaged[InterpreterEnv, Throwable, JgitInterpreter] = {
    for {
      projectDir <- getTmpTestDir
      interpreter <- JgitInterpreter.init(projectDir)
    } yield interpreter
  }

  private def getSvnClientAdapterInterpreter(flavour: Flavour): ZManaged[InterpreterEnv, Throwable, SvnClientAdapterInterpreter] = {
    for {
      projectDir <- getTmpTestDir
      repoDir <- getTmpTestDir
      interpreter <- SvnClientAdapterInterpreter.init(projectDir, repoDir, flavour)
    } yield interpreter
  }

  private def getSvnKitInterpreter: ZManaged[Blocking, Throwable, SvnKitInterpreter] = {
    for {
      projectDir <- getTmpTestDir
      repoDir <- getTmpTestDir
      interpreter <- SvnKitInterpreter.init(projectDir, repoDir)
    } yield interpreter
  }

  private def getInterpreters: ZManaged[interpreter.InterpreterEnv, Throwable, Interpreters] = {
    for {
      jGitInterpreter <- getJgitInterpreter
      svnKitInterpreter <- getSvnKitInterpreter
      svnClientInterpreterJavaHl <- getSvnClientAdapterInterpreter(Flavour.JavaHl)
      svnClientInterpreterSvnKit <- getSvnClientAdapterInterpreter(Flavour.SvnKit)
    } yield Interpreters(
      jGitInterpreter, svnKitInterpreter, svnClientInterpreterJavaHl, svnClientInterpreterSvnKit)
  }

  private def renderRaceResults(raceResults: RaceResults): String = {
    "Race results\n" + {
      raceResults
        .toSeq
        .sortBy(-_._2.nOps)
        .map((renderRaceResult _).tupled)
        .map("  " + _)
        .mkString("\n")
    }
  }

  private def renderRaceResult(interpreterName: InterpreterName, stats: PerfStats): String = {
    stats.summary match {
      case Some(PerfStats.Summary(n, opsPerSecond, Some(sx))) =>
        f"$interpreterName%15s: $opsPerSecond%8.2f ± ${sx / math.sqrt(n.toDouble) * 1.96}%6.2f ops/s"
      case _ => ""
    }
  }
}
