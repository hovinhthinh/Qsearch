#!/usr/bin/env bash

# args: <mapperClass>, <inputOnHDFS>, <outputOnHDFS>
mvn clean package
hadoop jar target/tabqs-1.0-SNAPSHOT.jar util.distributed.hadoop.MapOnlyJob -Dmapreduce.map.memory.mb=12288 $1 $2 $3
