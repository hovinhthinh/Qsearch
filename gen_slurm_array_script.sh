#!/usr/bin/env bash
ALLOC_MEM_PER_TASK="16G"
TASK_JAVA_MEM="15G"

# Generate a slurm array script,
# gen_slurm_script.sh <nProcesses> <MapClass> <input> <output>
# for each task: input = <input>.slices/part$id.gz ; output = <output>.slices/part$id.gz
#                stdout = <output>.slices/part$id.out ; stderr = <output>.slices/part$id.err
#                monitor = <output>.slices/part$id.log

np=$(($1-1))
slurm_output=`realpath $4`.slices && mkdir ${slurm_output}
args="${TASK_JAVA_MEM} $2 $3.slices/part\${SLURM_ARRAY_TASK_ID}.gz $4.slices/part\${SLURM_ARRAY_TASK_ID}.gz ${slurm_output}/part\${SLURM_ARRAY_TASK_ID}.out ${slurm_output}/part\${SLURM_ARRAY_TASK_ID}.err"
echo "#!/bin/bash
#SBATCH -p cpu20
#SBATCH -t 2-00:00:00
#SBATCH --mail-type=END,FAIL
#SBATCH --output=${slurm_output}/part%a.log
#SBATCH --ntasks=1
#SBATCH --cpus-per-task=2
#SBATCH --array=0-${np}
#SBATCH --mem=${ALLOC_MEM_PER_TASK}

eval \"\$(conda shell.bash hook)\"
conda activate maven

cd /home/hvthinh/TabQs/ && export MAVEN_OPTS=\"-Xms512M -Xmx512M -XX:+UseSerialGC\" && \\
mvn exec:java -Dexec.classpathScope=compile -Dexec.mainClass=\"util.distributed.MapClient\" -Dexec.args=\"$args\""


