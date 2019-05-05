package com.github.mlangc.vcs.bench.model

import cats.kernel.Eq

sealed trait Operation {
  def isCommit: Boolean = false
}

object Operation {
  case class Commit(message: String) extends Operation {
    override def isCommit: Boolean = true
  }

  case class Delete(path: Path) extends Operation
  case class Copy(src: Path, dst: Path) extends Operation
  case class Move(src: Path, dst: Path) extends Operation
  case class Edit(path: Path, apply: Lines => Lines) extends Operation
  case class Create(path: Path, lines: Lines) extends Operation

  implicit val eqOperation: Eq[Operation] = Eq.fromUniversalEquals
}