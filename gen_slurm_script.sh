#!/usr/bin/env bash
ALLOC_MEM_PER_TASK="16G"
TASK_JAVA_MEM="12G"

# Generate a slurm array script,
# gen_slurm_script.sh <nProcesses> <MapClass> <input> <output>
# for each task: input = <input>.slices/part$id.gz ; output = <output>.slices/part$id.gz
#                stdout = <output>.slices/part$id.out ; stderr = <output>.slices/part$id.err

np=$(($1-1))
slurm_output=`realpath $4`.slices && mkdir ${slurm_output}

echo "#!/bin/bash
#SBATCH -p cpu20
#SBATCH -t 2-00:00:00
#SBATCH --mail-type=END,FAIL
#SBATCH --output=${slurm_output}/part%a.out
#SBATCH --error=${slurm_output}/part%a.err
#SBATCH --ntasks=$1
#SBATCH --cpus-per-task=1
#SBATCH --array=0-${np}
#SBATCH --mem=${ALLOC_MEM_PER_TASK}

eval \"\$(conda shell.bash hook)\"

cd /home/hvthinh/TabQs/ && ./run_no_notification.sh ${TASK_JAVA_MEM} util.distributed.MapInteractiveRunner \\
  $2 \\
  $3.slices/part\${SLURM_ARRAY_TASK_ID}.gz \\
  $4.slices/part\${SLURM_ARRAY_TASK_ID}.gz"


