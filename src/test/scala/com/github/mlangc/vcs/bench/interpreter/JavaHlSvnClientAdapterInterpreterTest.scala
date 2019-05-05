package com.github.mlangc.vcs.bench.interpreter

import com.github.mlangc.vcs.bench.interpreter.SvnClientAdapterInterpreter.Flavour

class JavaHlSvnClientAdapterInterpreterTest extends SvnClientAdapterInterpreterTest {
  protected def flavour: Flavour = Flavour.JavaHl
}