#!/usr/bin/env bash
# Each line contains: [<conf> <full_sentence>\t]<qpos> <text_feature> ||| <entity_feature> ||| <labels>
# args: <data_path> [device]
DATA_PATH=$1
MODEL_PATH="$1/model"

#DATA_PATH="./data/general"

CONFIG="config/config.json"
TRAIN_PATH="${DATA_PATH}/train.txt"
DEV_PATH="${DATA_PATH}/dev.txt"

if [ "$#" -gt 1 ]
then
THEANO_FLAGS="mode=FAST_RUN,floatX=float32,device=$2" python -u python/train.py \
   --config=$CONFIG \
   --model=$MODEL_PATH \
   --train=$TRAIN_PATH \
   --dev=$DEV_PATH
else
THEANO_FLAGS="mode=FAST_RUN,floatX=float32" python -u python/train.py \
   --config=$CONFIG \
   --model=$MODEL_PATH \
   --train=$TRAIN_PATH \
   --dev=$DEV_PATH
fi
