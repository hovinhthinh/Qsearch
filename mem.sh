#!/bin/sh
ps -U hvthinh --no-headers -o rss | awk '{sum+=$1} END {print int(sum/1024/1024) "GB"}'