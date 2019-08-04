package com.github.mlangc.vcs.bench.util.zio

import zio.duration.Duration

case class ResultWithDuration[A](value: A, duration: Duration)
