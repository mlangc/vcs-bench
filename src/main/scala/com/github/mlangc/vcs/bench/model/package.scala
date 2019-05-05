package com.github.mlangc.vcs.bench

import cats.data.Chain
import cats.data.NonEmptyList
import eu.timepit.refined.api.Refined
import eu.timepit.refined.boolean._
import eu.timepit.refined.char._
import eu.timepit.refined.collection._
import eu.timepit.refined.generic._
import eu.timepit.refined.refineV

import shapeless.Witness

package object model {
  type PathComponentRefinement = Forall[LetterOrDigit Or Equal[Witness.`'.'`.T]] And NonEmpty
  type PathComponent = String Refined PathComponentRefinement
  type Path = NonEmptyList[PathComponent]

  type Line = String
  type Lines = Chain[Line]

  type History = Chain[Operation]

  object PathComponent {
    def coerce(str: String): PathComponent = {
      refineV[PathComponentRefinement](str).fold(
        str => throw new AssertionError(str),
        str => str)
    }
  }
}
