#!/usr/bin/env bash
# Use to run a task to process a input file with multithreading. Each element of the input file and output file should be in a single line.
# run_map_parallel.sh <nProcesses> <MapClass> <input> <output>

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

args="${@:1}"
export MAVEN_OPTS="-Xmx8G" && mvn exec:java \
    -Dexec.classpathScope=compile \
    -Dexec.mainClass=util.distributed.ParallelMapClient \
    -Dexec.args="12G $args"

# END JOB

# Send notification
running_time=$(( $(TZ=UTC0 printf '%(%s)T\n' '-1') - start_time ))
running_time=`textifyDuration $running_time`
hostname=`hostname`
pwd=`pwd`
command="${@:0}"
( printf '%s\n' "Dear Thinh,"
  printf '\n%s\n' "Your command is done:"
  printf '\n     %s\n' "$hostname:$pwd\$ $command"
  printf '\n%s\n' "$running_time"
  printf '\n%s\n' "Cheers,"
  printf '%s\n' "Your mail bot"
) | mail -s "[$hostname] Your job is done" hvthinh@mpi-inf.mpg.de