#!/usr/bin/env bash
# Use to run a task to process a input file with multithreading. Each element of the input file and output file should be in a single line.
# <input> and <output> of the MainClass should be the first 2 arguments. MainClass processes input and output files in GZIP format.
# run_parallel.sh <nProcesses> <MainClass> <input> <output> <more args...>
# Real output file will be also in GZIP format.

textifyDuration() {
   local duration=$1
   local shiff=$duration
   local secs=$((shiff % 60));  shiff=$((shiff / 60));
   local mins=$((shiff % 60));  shiff=$((shiff / 60));
   local hours=$shiff
   local splur; if [ $secs  -eq 1 ]; then splur=''; else splur='s'; fi
   local mplur; if [ $mins  -eq 1 ]; then mplur=''; else mplur='s'; fi
   local hplur; if [ $hours -eq 1 ]; then hplur=''; else hplur='s'; fi
   if [[ $hours -gt 0 ]]; then
      txt="$hours hour$hplur, $mins minute$mplur, $secs second$splur"
   elif [[ $mins -gt 0 ]]; then
      txt="$mins minute$mplur, $secs second$splur"
   else
      txt="$secs second$splur"
   fi
   echo "Total time: $txt"
}

start_time="$(TZ=UTC0 printf '%(%s)T\n' '-1')"

# START JOB

echo "Split data"
islices="$3.slices" && mkdir ${islices}
./split.sh $3 $1 ${islices}

echo "Run in parallel"
oslices="$4.slices" && mkdir ${oslices}
np=$(($1-1))
for i in $(seq -f "%g" 0 ${np}); do
  args="${islices}/part$i.gz ${oslices}/part$i.gz ${@:5}"
  export MAVEN_OPTS="-Xms8G -Xmx12G -XX:ParallelGCThreads=4" && \
    mvn exec:java -Dexec.classpathScope=compile -Dexec.mainClass="$2" -Dexec.args="$args" \
      1>${oslices}/part$i.out 2>${oslices}/part$i.err &
done

wait

echo "Combine results"
zcat ${oslices}/part*.gz | gzip > $4

echo "Clean"
# Remove input parts.
rm -rf ${islices}
# Remove output parts.
rm -rf ${oslices}

# END JOB

# Send notification
running_time=$(( $(TZ=UTC0 printf '%(%s)T\n' '-1') - start_time ))
running_time=`textifyDuration $running_time`
hostname=`hostname`
pwd=`pwd`
command="${@:0}"
( printf '%s\n' "Dear ${USER},"
  printf '\n%s\n' "Your command is done:"
  printf '\n     %s\n' "$hostname:$pwd\$ $command"
  printf '\n%s\n' "$running_time"
  printf '\n%s\n' "Cheers,"
  printf '%s\n' "Your mail bot"
) | mail -s "[$hostname] Your job is done" ${USER}@mpi-inf.mpg.de


