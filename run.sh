#!/usr/bin/env bash
# run.sh <Xmx> <MainClass> <args...>
# Ex: run.sh 8G <MainClass> <args...>
args="${@:3}"
export MAVEN_OPTS="-Xmx$1" && mvn exec:java -Dexec.classpathScope=compile -Dexec.mainClass="$2" -Dexec.args="$args"

