#!/usr/bin/env bash
# Use to run a task to process a input file with multithreading. Each element of the input file and output file should be in a single line.
# run_map_parallel.sh <nProcesses> <MapClass> <input> <output>

# START JOB

args="${@:1}"
export MAVEN_OPTS="-Xmx8G" && mvn exec:java -Dexec.classpathScope=compile -Dexec.mainClass=util.distributed.ParallelMapClient -Dexec.args="8G $args"

wait

# END JOB
