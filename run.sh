#!/usr/bin/env bash
# run.sh <Xmx> <MainClass> <args...>
# Ex: run.sh 12G <MainClass> <args...>
args="${@:3}"
export MAVEN_OPTS="-Xms$1 -Xmx$1 -XX:+UseParallelOldGC -XX:ParallelGCThreads=4" && mvn exec:java -Dexec.classpathScope=compile -Dexec.mainClass="$2" -Dexec.args="$args"
