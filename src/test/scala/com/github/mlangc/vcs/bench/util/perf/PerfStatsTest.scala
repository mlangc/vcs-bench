package com.github.mlangc.vcs.bench.util.perf

import java.util.concurrent.TimeUnit

import cats.data.Chain
import cats.syntax.option._
import com.github.mlangc.vcs.bench.util.perf.PerfStats.Summary
import org.scalactic.TypeCheckedTripleEquals
import org.scalatest.FreeSpec
import zio.duration.Duration


class PerfStatsTest extends FreeSpec with TypeCheckedTripleEquals {
  "Test our summary function" - {
    "on empty stats" in {
      assert(PerfStats.Zero.summary.isEmpty)
    }

    "on stats with one evaluation" in {
      val stats = PerfStats(Chain.one(1 -> Duration.apply(1, TimeUnit.SECONDS)))
      assert(stats.summary === Summary(1, 1.0, None).some)
    }

    "on stats with three evaluations" in {
      val stats = PerfStats(Chain(
          1 -> Duration.apply(1, TimeUnit.SECONDS),
          60*3 -> Duration.apply(1, TimeUnit.MINUTES),
          3600*2 -> Duration.apply(1, TimeUnit.HOURS)))

      assert(stats.summary === Summary(3, 2.0, 1.0.some).some)
    }
  }
}
