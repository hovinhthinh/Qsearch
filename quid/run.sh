# ./run.sh <py_module> <args...>
args="${@:2}"

export PYTHONPATH=./quid
export PYTHONHASHSEED=6993

if [ -e "$1" ]; then
  echo 'Running script'
  CUDA_VISIBLE_DEVICES=0 python $1 $args
else
  echo 'Running module'
  CUDA_VISIBLE_DEVICES=0 python -m $1 $args
fi