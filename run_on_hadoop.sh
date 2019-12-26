#!/usr/bin/env bash

# args: <mapperClass>, <inputOnHDFS>, <outputOnHDFS>
mvn clean install
hadoop jar -Dmapreduce.map.memory.mb=8192 target/tabqs-1.0-SNAPSHOT.jar util.hadoop.MapOnlyJob $1 $2 $3