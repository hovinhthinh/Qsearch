#!/usr/bin/env bash
# kill_tree.sh <pid>

recur() {
  echo -ne "$1 "
  for pid in $(ps -o pid= --ppid "$1"); do recur "$pid"; done
}

all_pids=`recur $1`
echo "kill -9 ${all_pids}"
kill -9 ${all_pids}
