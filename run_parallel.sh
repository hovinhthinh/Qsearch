#!/usr/bin/env bash
# Use to run a task to process a input file with multithreading. Each element of the input file and output file should be in a single line.
# <input> and <output> of the MainClass should be the first 2 arguments. MainClass processes input and output files in GZIP format.
# run_parallel.sh <nProcesses> <MainClass> <input> <output> <more args...>
# Real output file will be also in GZIP format.
np=$(($1-1))
echo "Split data"
args="$3 $1"
mvn exec:java -Dexec.classpathScope=compile -Dexec.mainClass="util.FileSplitter" -Dexec.args="$args" 1>>$4.out 2>>$4.err

echo "Run in parallel"
for i in $(seq -f "%02g" 0 ${np}); do
args="$3.part$i.gz $4.part$i.gz ${@:5}"
export MAVEN_OPTS="-Xmx8G" && mvn exec:java -Dexec.classpathScope=compile -Dexec.mainClass="$2" -Dexec.args="$args" 1>>$4.out 2>>$4.err &
done

wait

echo "Combine result"
zcat $4.part*.gz | gzip > $4
echo "Clean"
rm $3.part*.gz $4.part*.gz

