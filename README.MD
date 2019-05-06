# Version Control System Benchmark
This is the benchmark code for the related blog post on Medium.

## Running the Benchmark
Using [SBT](https://www.scala-sbt.org/), the benchmark can be run by executing

    $ sbt run

in a terminal. To tweak the configuration, adapt the `cfg` object at the beginning
of the `com.github.mlangc.vcs.bench.RaceInterpreters` class.

## Understanding the Code
This benchmark is implemented in purely functional [Scala](https://www.scala-lang.org/) using 
[scalaz.ZIO](https://scalaz.github.io/scalaz-zio/).