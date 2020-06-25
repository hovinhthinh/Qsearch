#!/usr/bin/env bash

# args: <nExecutor> <mapperClass>, <inputOnHDFS>, <outputOnHDFS>
mvn clean package

spark-submit \
    --class util.distributed.hadoop.SparkMapJob \
    --master yarn-cluster \
    --driver-memory 4G \
    --executor-memory 12G \
    --num-executors $1 \
    target/tabqs-1.0-SNAPSHOT.jar $2 $3 $4
