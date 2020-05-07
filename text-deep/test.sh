#!/bin/bash
# Each line contains: [<conf> <full_sentence>\t]<qpos> <text_feature> ||| <entity_feature> ||| <labels>
# (Same as train data)
# args: <data_path>  [device]
MODEL_PATH="$1/model"
INPUT_PATH="$1/test.txt"
OUTPUT_PATH="$1/test_out.txt"
#MODEL_PATH="./data/general/model"
#INPUT_PATH="./data/general/test.txt"
#OUTPUT_PATH="./data/general/test_out.txt"

if [ "$#" -gt 1 ]
then
THEANO_FLAGS="optimizer=fast_compile,floatX=float32,device=$2" python -u python/test.py \
  --model="$MODEL_PATH" \
  --input="$INPUT_PATH" \
  --output="$OUTPUT_PATH"
else
THEANO_FLAGS="optimizer=fast_compile,floatX=float32" python -u python/test.py \
  --model="$MODEL_PATH" \
  --input="$INPUT_PATH" \
  --output="$OUTPUT_PATH"
fi

