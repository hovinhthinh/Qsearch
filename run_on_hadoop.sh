#!/usr/bin/env bash

# args: <mapperClass>, <inputOnHDFS>, <outputOnHDFS>
mvn clean install
hadoop jar target/tabqs-1.0-SNAPSHOT.jar util.hadoop.MapOnlyJob -D mapreduce.map.memory.mb=8192 $1 $2 $3