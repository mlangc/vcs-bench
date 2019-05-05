package com.github.mlangc.vcs.bench.util.perf

import cats.Monoid
import cats.data.Chain
import com.github.mlangc.vcs.bench.util.perf.PerfStats.Summary
import scalaz.zio.duration.Duration
import cats.syntax.option._

import scala.math.sqrt

case class PerfStats(evals: Chain[(Nops, Duration)]) {
  def combine(that: PerfStats): PerfStats = {
    PerfStats(this.evals ++ that.evals)
  }

  def summary: Option[Summary] = {
    def opsPerSec(eval: (Nops, Duration)): Double = {
      val secs = eval._2.toMillis.toDouble / 1000
      eval._1 / secs
    }

    val (n, sum) = evals.foldLeft(0 -> 0.0) { case ((n, sum), eval) =>
      (n + 1, sum + opsPerSec(eval))
    }

    if (n <= 0) None else {
      val xm = sum / n

      if (n == 1) Summary(n, xm, None).some else {
        val ss = evals.foldLeft(0.0) { (sum, eval) =>
          val diff = opsPerSec(eval) - xm
          sum + diff*diff
        }

        val sx = sqrt(ss /(n - 1))
        Summary(n, xm, sx.some).some
      }
    }
  }

  def nOps: Nops = evals.foldLeft(0)((acc, eval) => acc + eval._1)
}

object PerfStats {
  val Zero = PerfStats(evals = Chain.empty)

  def apply(evals: (Nops, Duration)*): PerfStats = new PerfStats(Chain.fromSeq(evals))

  implicit val perfStatsMonoid: Monoid[PerfStats] = new Monoid[PerfStats] {
    def empty: PerfStats = Zero
    def combine(x: PerfStats, y: PerfStats): PerfStats = x.combine(y)
  }

  case class Summary(n: NRuns, xm: OpsPerSec, sx: Option[OpsPerSec])
}
