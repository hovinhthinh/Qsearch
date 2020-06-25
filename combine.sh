#!/usr/bin/env bash
# split.sh <input>
# combine all files in <input>.slices/part*.gz  to <input>

echo "Combine results"
zcat $1.slices/part*.gz | gzip > $1