#!/usr/bin/env bash
# run_slurm.sh <nProcesses> <MapClass> <input> <output>

echo "Split data"
islices="$3.slices" && mkdir ${islices}
./split.sh $3 $1 ${islices}

slurm_output=`realpath $4`.slices && mkdir ${slurm_output}

temp_script=$(mktemp)
echo "Temporary slurm script is generated at: ${temp_script}"

echo "========== SCRIPT-CONTENT =========="
bash gen_slurm_script.sh $@ > ${temp_script}
cat ${temp_script}
echo "========== END-OF-CONTENT =========="
sbatch ${temp_script}

