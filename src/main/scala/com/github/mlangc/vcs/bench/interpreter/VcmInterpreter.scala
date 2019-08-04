package com.github.mlangc.vcs.bench.interpreter

import com.github.mlangc.vcs.bench.model.History
import com.github.mlangc.vcs.bench.model.Operation
import zio.TaskR
import zio.ZIO

trait VcmInterpreter {
  def run(op: Operation): TaskR[InterpreterEnv, Unit]

  final def run(ops: History): TaskR[InterpreterEnv, Unit] = ops.uncons match {
    case None => ZIO.unit
    case Some((op, ops)) => run(op) *> run(ops)
  }
}
