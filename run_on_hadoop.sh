#!/usr/bin/env bash

# args: <mapperClass>, <inputOnHDFS>, <outputOnHDFS>
mvn clean package
hadoop jar target/tabqs-1.0-SNAPSHOT-job.jar util.hadoop.MapOnlyJob -Dmapreduce.map.memory.mb=8192 $1 $2 $3
