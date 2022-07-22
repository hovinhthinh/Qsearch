#!/bin/bash
# args: <model_path>
# interactive input: each line contain smt like this: "7 Chalmers hit out onto the green to <QUANTITY> away , but Scott ran into rough on the fringe on the left side of the green . ||| B O O O O O O O O O O O O O O O O O O O O O O O O O"
# where the first position is the quantity. The sentence is tokenized.

THEANO_FLAGS="optimizer=fast_compile,floatX=float32" python -u python/interactive.py --model="$1"
