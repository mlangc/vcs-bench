package com.github.mlangc.vcs.bench.model

import cats.data.{Chain, NonEmptyList}
import cats.syntax.option._
import com.github.mlangc.vcs.bench.UIOR
import org.apache.commons.lang3.RandomStringUtils
import scalaz.zio.{UIO, ZIO, random}
import scalaz.zio.random.Random
import scalaz.zio.stream.ZStream
import scalaz.zio.syntax._

import scala.annotation.tailrec

object History {
  def stream: ZStream[Random, Nothing, Operation] = {
    ???
  }

  def generateNew(len: Int): ZIO[Random, Nothing, History] =
    stream.take(len).foldLeft(History.empty)((history, operation) => history :+ operation)

  def generate(len: Int): ZIO[Random, Nothing, History] = {
    def loop(paths: Set[Path], operations: List[Operation], n: Int): ZIO[Random, Nothing, List[Operation]] = {
      if (n >= len) ZIO.succeed(operations) else {
        nextOperation(paths, operations).flatMap { case (paths, operation) =>
            loop(paths, operation :: operations, n + 1)
        }
      }
    }

    for {
      operations <- loop(Set.empty, List.empty, 0)
    } yield Chain.fromSeq(operations).reverse
  }

  def paths(history: History): Set[Path] = {
    @tailrec
    def loop(paths: Set[Path], history: History): Set[Path] = {
      history.uncons match {
        case None => paths

        case Some((op, ops)) => op match {
          case Operation.Create(path, _) => loop(paths + path, ops)
          case Operation.Delete(path) => loop(paths - path, ops)
          case Operation.Copy(_, dst) => loop(paths + dst, ops)
          case Operation.Move(src, dst) => loop(paths + dst - src, ops)
          case _ => loop(paths, ops)
        }
      }
    }

    loop(Set.empty, history)
  }

  def empty: History = Chain.empty

  private def nextOperation(paths: Set[Path],
                            operations: List[Operation])
  : ZIO[Random, Nothing, (Set[Path], Operation)] = {
    def loop: ZIO[Random, Nothing, (Set[Path], Operation)] = {
      tryNextOperation(paths, operations).flatMap {
        case Some(res) => ZIO.succeed(res)
        case None => loop
      }
    }

    loop
  }

  private def tryNextOperation(paths: Set[Path],
                               operations: List[Operation])
  : ZIO[Random, Nothing, Option[(Set[Path], Operation)]] = {
    val numOperations = 6
    def tryNextOperationWithInd(ind: Int): ZIO[Random, Nothing, Option[(Set[Path], Operation)]] = {
      ind match {
        case 0 =>
          if (operations.headOption.map(_.isCommit).getOrElse(true)) ZIO.succeed(none)
          else nextCommit().map(commit => (paths, commit).some)

        case 1 =>
          nextPath(paths).flatMap {
            case None => ZIO.succeed(None)
            case Some(src) =>
              nextPath().flatMap { dst =>
                if (paths.contains(dst)) ZIO.succeed(None)
                else ZIO.succeed((paths + dst, Operation.Copy(src, dst)).some)
              }
          }

        case 2 =>
          nextPath(paths).flatMap {
            case None => ZIO.succeed(None)
            case Some(src) =>
              nextPath().flatMap { dst =>
                if (paths.contains(dst)) ZIO.succeed(None)
                else ZIO.succeed((paths + dst - src, Operation.Move(src, dst)).some)
              }
          }

        case 3 =>
          nextPath(paths).flatMap {
            case None => ZIO.succeed(None)
            case Some(path) =>
              nextLine().map { line =>
                (paths, Operation.Edit(path, lines => lines :+ line)).some
              }
          }

        case 4 =>
          nextPath().flatMap { path =>
            if (paths.contains(path)) ZIO.succeed(None) else {
              for {
                lines <- nextLines()
              } yield (paths + path, Operation.Create(path, lines)).some
            }
          }

        case 5 =>
          nextPath(paths).flatMap {
            case None => ZIO.succeed(None)
            case Some(path) => ZIO.succeed((paths - path, Operation.Delete(path)).some)
          }

        case _ =>
          ZIO.succeed(None)
      }
    }

    random.nextInt(numOperations)
      .flatMap(tryNextOperationWithInd)
  }

  private def nextPath(paths: Set[Path]): UIOR[Random, Option[Path]] = {
    if (paths.isEmpty) ZIO.succeed(none) else {
      random.nextInt(paths.size)
        .map(i => paths.toSeq(i).some)
    }
  }

  private def nextPath(): ZIO[Random, Nothing, Path] = {
    val genSingleComponent: UIO[String] = randomPathComponent(8)

    for {
      baseDir <- genSingleComponent
      fileName <- genSingleComponent
      nDirs <- random.nextInt(5)
      dirs <- Iterable.fill(nDirs)(genSingleComponent).collectAll
    } yield {
      NonEmptyList(baseDir, dirs :+ (fileName + ".txt"))
        .map(PathComponent.coerce)
    }
  }

  private def randomPathComponent(len: Int): UIO[String] = UIO {
    RandomStringUtils.randomAlphanumeric(len)
  }

  private def nextLine(): UIO[String] = UIO {
    RandomStringUtils.randomAscii(8, 128)
  }

  private def nextLines(): UIOR[Random, Lines] = {
    for {
      numLines <- random.nextInt(21).map(_ + 15)
      lines <- Iterable.fill(numLines)(nextLine()).collectAll
    } yield Chain.fromSeq(lines)
  }

  private def nextCommit(): UIO[Operation.Commit] = {
    UIO(RandomStringUtils.randomAscii(8, 64)).map(Operation.Commit)
  }
}
