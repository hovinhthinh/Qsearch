#!/usr/bin/env bash
# run.sh <Xmx> <MainClass> <args...>
# Ex: run.sh 12G <MainClass> <args...>

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

args="${@:3}"
export MAVEN_OPTS="-Xmx$1 -XX:ParallelGCThreads=4" && mvn exec:java -Dexec.classpathScope=compile -Dexec.mainClass="$2" -Dexec.args="$args"

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
