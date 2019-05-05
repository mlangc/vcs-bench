package com.github.mlangc.vcs.bench.interpreter

import com.github.mlangc.vcs.bench.interpreter.SvnClientAdapterInterpreter.Flavour

class SvnKitSvnClientAdapterInterpreterTest extends SvnClientAdapterInterpreterTest {
  protected def flavour: Flavour = Flavour.SvnKit
}
