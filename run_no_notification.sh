#!/usr/bin/env bash
# run.sh <Xmx> <MainClass> <args...>
# Ex: run_no_notification.sh 12G <MainClass> <args...>
# This script is used for parallel running only, so do NOT touch this.

args="${@:3}"
export MAVEN_OPTS="-Xms$1 -Xmx$1 -XX:+UseParallelOldGC -XX:GCTimeRatio=19 -XX:ParallelGCThreads=4" && \
  mvn exec:java -Dexec.classpathScope=compile -Dexec.mainClass="$2" -Dexec.args="$args"
