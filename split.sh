#!/usr/bin/env bash
# split.sh <input> <nSplits> [outputFolder]

mvn exec:java -Dexec.classpathScope=compile -Dexec.mainClass="util.FileSplitter" -Dexec.args="$@"
