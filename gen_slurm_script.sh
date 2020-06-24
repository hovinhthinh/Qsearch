#!/usr/bin/env bash
# Generate a slurm script, which calls the run_map_parallel.sh script.
# gen_slurm_script.sh <nProcesses> <MapClass> <input> <output> <slurm_output/nohup.out>

args="${@:1:4}"
slurm_output=`realpath $5`
echo "#!/bin/bash
#SBATCH -p cpu20
#SBATCH -t 2-00:00:00
#SBATCH -o ${slurm_output}

eval \"\$(conda shell.bash hook)\"

cd /home/hvthinh/TabQs/ && ./run_map_parallel.sh $args"


