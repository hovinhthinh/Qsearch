#!/usr/bin/env bash
# run_slurm.sh <nProcesses> <MapClass> <input> <output> <slurm_output/nohup.out>

temp_script=$(mktemp)
echo "Temporary slurm script is generated at: ${temp_script}"

echo "========== SCRIPT-CONTENT =========="
bash gen_slurm_script.sh $@ > ${temp_script}
cat ${temp_script}
echo "========== END-OF-CONTENT =========="
sbatch ${temp_script}

