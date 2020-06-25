#!/usr/bin/env bash
# split.sh <input> <nSplits> [outputFolder]

args="${@:1}"
mvn exec:java -Dexec.classpathScope=compile -Dexec.mainClass="util.FileSplitter" -Dexec.args="${args}"
