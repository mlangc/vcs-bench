package com.github.mlangc.vcs.bench.model

import cats.data.{Chain, NonEmptyList}
import com.github.ghik.silencer.silent
import com.github.mlangc.vcs.bench.BaseTest
import com.github.mlangc.vcs.bench.model.History.{empty, generate, paths}
import eu.timepit.refined.auto._

import scala.annotation.tailrec

class HistoryTest extends BaseTest {
  "Make sure that generated histories are valid and of the right length" - {
    "when empty" in {
      assertGenerateValidHistory(0)
    }

    "when quite small" in {
      assertGenerateValidHistory(1)
      assertGenerateValidHistory(2)
      assertGenerateValidHistory(3)
    }

    "with modest sizes" in {
      assertGenerateValidHistory(50)
      assertGenerateValidHistory(500)
      assertGenerateValidHistory(5000)
    }
  }

  "Extracting paths should work as expected" - {
    "with an empty history" in {
      assert(paths(Chain.empty).isEmpty)
    }

    "with a single create op" in {
      val singlePath: Path = NonEmptyList.of("hunds", "bub")
      val history = Chain(Operation.Create(singlePath, Chain("lausiger")))
      assert(paths(history) === Set(singlePath))
    }

    "with create, copy, commit, move, edit and delete" in {
      val path1: Path = NonEmptyList.of("d1", "f1.txt")
      val path2: Path = NonEmptyList.of("d2", "f2.txt")
      val path3: Path = NonEmptyList.of("d3", "f3.txt")

      val history = Chain(
        Operation.Create(path1, Chain("f1")),
        Operation.Copy(path1, path2),
        Operation.Commit("Msg1"),
        Operation.Move(path2, path3),
        Operation.Edit(path1, _ :+ "blah blah blah"),
        Operation.Commit("Msg2"),
        Operation.Delete(path1),
        Operation.Commit("Msg3")
      )

      assert(paths(history) === Set(path3))
    }
  }

  @silent("discarded non-Unit")
  private def assertGenerateValidHistory(len: Int): Unit = {
    val (history, validPart) = unsafeRun {
      for {
        history <- generate(len)
        validPart = extractValidPart(history)
      } yield (history, validPart)
    }

    assert(history === validPart)
    assert(history.size === len.toLong)
  }

  private def extractValidPart(history: History): History = {
    @tailrec
    def loop(paths: Set[Path],
             remaining: History,
             validated: History): History = {
      remaining.uncons match {
        case None => validated
        case Some((operation, remaining)) =>
          operation match {
            case Operation.Commit(_) =>
              if (validated.headOption.contains((_: Operation).isCommit)) {
                validated
              } else {
                loop(paths, remaining, validated :+ operation)
              }

            case Operation.Create(path, _) =>
              if (paths.contains(path)) {
                validated
              } else {
                loop(paths + path, remaining, validated :+ operation)
              }

            case Operation.Edit(path, _) =>
              if (!paths.contains(path)) {
                validated
              } else {
                loop(paths, remaining, validated :+ operation)
              }

            case Operation.Delete(path) =>
              if (!paths.contains(path)) {
                validated
              } else {
                loop(paths - path, remaining, validated :+ operation)
              }

            case Operation.Move(src, dst) =>
              if (!paths.contains(src) || paths.contains(dst)) {
                validated
              } else {
                loop(paths - src + dst, remaining, validated :+ operation)
              }

            case Operation.Copy(src, dst) =>
              if (!paths.contains(src) || paths.contains(dst)) {
                validated
              } else {
                loop(paths + dst, remaining, validated :+ operation)
              }
          }
      }
    }

    loop(paths = Set.empty, remaining = history, validated = empty)
  }
}
