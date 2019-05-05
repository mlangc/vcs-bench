package com.github.mlangc.vcs.bench

import org.scalactic.TypeCheckedTripleEquals
import org.scalatest.FreeSpec
import scalaz.zio.DefaultRuntime

abstract class BaseTest extends FreeSpec with DefaultRuntime with TypeCheckedTripleEquals
