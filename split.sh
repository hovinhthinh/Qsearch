#!/usr/bin/env bash
# split.sh <input> <nSplits> [outputFolder]

args="${@:1}"
export MAVEN_OPTS="-Xms8G -Xmx16G" && mvn exec:java -Dexec.classpathScope=compile -Dexec.mainClass="util.FileSplitter" -Dexec.args="${args}"
