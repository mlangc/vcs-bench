package com.github.mlangc.vcs.bench.util.perf

import cats.data.Chain
import cats.kernel.Eq
import cats.kernel.laws.discipline.MonoidTests
import cats.tests.CatsSuite
import org.scalacheck.Arbitrary
import org.scalacheck.Gen
import zio.duration.Duration

class PerfStatsLawsTest extends CatsSuite {
  private implicit  def eqPerfStats: Eq[PerfStats] = Eq.fromUniversalEquals[PerfStats]

  private implicit def arbPerfStats: Arbitrary[PerfStats] = {
    val genPerfStat: Gen[(Nops, Duration)] =
      for {
        nOps <- Gen.posNum[Nops]
        nanos <- Gen.posNum[Long]
      } yield (nOps, Duration.fromNanos(nanos))

    val genPerfStats: Gen[PerfStats] =
      Gen.containerOf[List, (Nops, Duration)](genPerfStat)
        .map(l => PerfStats(Chain.fromSeq(l)))

    Arbitrary(genPerfStats)
  }

  checkAll("PerfStats.MonoidLaws", MonoidTests[PerfStats].monoid)
}
