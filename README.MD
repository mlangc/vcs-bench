# Version Control System Benchmark
This is the benchmark code for the related [blog post on Medium](https://medium.com/@m.langer798/subversion-vs-git-on-the-jvm-823b77893417).

## Running the Benchmark
Using [SBT](https://www.scala-sbt.org/), the benchmark can be run by executing

    $ sbt run

in a terminal. To tweak the configuration, adapt the `cfg` object at the beginning
of the `com.github.mlangc.vcs.bench.RaceInterpreters` class.

## Understanding the Code
This benchmark is implemented in purely functional [Scala](https://www.scala-lang.org/) using 
[ZIO](https://zio.dev).