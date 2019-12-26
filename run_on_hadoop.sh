#!/usr/bin/env bash

# args: <mapperClass>, <inputOnHDFS>, <outputOnHDFS>
mvn clean install
hadoop jar target/tabqs-1.0-SNAPSHOT.jar -D mapreduce.map.memory.mb=8192 util.hadoop.MapOnlyJob $1 $2 $3