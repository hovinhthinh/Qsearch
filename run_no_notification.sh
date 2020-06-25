#!/usr/bin/env bash
# run.sh <Xmx> <MainClass> <args...>
# Ex: run_no_notification.sh 12G <MainClass> <args...>

args="${@:3}"
export MAVEN_OPTS="-Xms8G -Xmx$1 -XX:ParallelGCThreads=4" && mvn exec:java -Dexec.classpathScope=compile -Dexec.mainClass="$2" -Dexec.args="$args"
