# libraries

import matplotlib
import matplotlib.pyplot as plt
from matplotlib import gridspec

# building height
data1 = [[409, 710, 800, 896, 953, 1124, 1170, 1191, 1253],
         [ 78.05,  82.17, 81.82,82.51, 82.92, 82.58, 82.16, 82.31, 82.46]]
# mountain elevation
data2 = [[1228, 1704, 1973, 2341, 2612, 2783, 3082, 3181, 3289, 3376],
         [95.29, 94.53 ,94.49, 94.11 ,93.97,92.54, 92.02,91.51 ,  91.36, 89.56  ]]
# stadium capacity
data3 = [[2003, 2672, 2949, 3244, 3252, 3324, 3333, 3333, 3351, 3496],
         [ 67.02,  68.96, 69.73,  70.08, 69.99, 69.37, 69.44, 69.44,69.38 ,70.10]]
# river length
data4 = [[713, 1494, 2091, 2132, 2739, 2866, 2957, 3004, 3019, 3141],
         [ 66.74, 66.81, 58.98,  59.14,  61.01,  61.03, 60.55,  60.71, 60.71, 58.19 ]]
# powerstation capacity
data5 = [[283, 380, 394],
         [74.85, 75.83, 75.23]]
# earthquake-magnitude
data6 = [[198, 224, 226, 228, 236],
         [87.50, 88.46 , 88.46, 88.46,  88.46]]


for i in [data1, data2, data3, data4, data5, data6]:
    for j in range(len(i[1])):
        i[1][j] /= 100
    print(i[1])

font = {'family': 'normal',
        'size': 9}

matplotlib.rc('font', **font)

fig = plt.figure()

gs = gridspec.GridSpec(11, 54)

plot1 = fig.add_subplot(gs[0:3, 1:23])
plot2 = fig.add_subplot(gs[0:3, 30:54])
plot3 = fig.add_subplot(gs[4:7, 0:24])
plot4 = fig.add_subplot(gs[4:7, 30:54])
plot5 = fig.add_subplot(gs[8:11, 9:16])
plot6 = fig.add_subplot(gs[8:11, 25:37])

################
nCols = len(data1[0])
plot1.bar(range(1, nCols + 1), data1[0], color='tab:blue', label='#facts')
plot1.set_xticks(range(1, nCols + 1))
plot1.grid(axis='y', linestyle='--')
plot1.set_ylabel('#facts')
plot1.set_title('building-height')
ax2 = plot1.twinx()
ax2.plot(range(1, nCols + 1), data1[1], color='red', label='precision', marker='x')
ax2.set_ylim([0, 1])

################
nCols = len(data2[0])
plot2.bar(range(1, nCols + 1), data2[0], color='tab:blue', label='#facts')
plot2.set_xticks(range(1, nCols + 1))
plot2.grid(axis='y', linestyle='--')
ax2 = plot2.twinx()
ax2.plot(range(1, nCols + 1), data2[1], color='red', label='precision', marker='x')
ax2.set_ylim([0, 1])
ax2.set_ylabel('prec.')
plot2.set_title('mountain-elevation')

################
nCols = len(data3[0])
plot3.bar(range(1, nCols + 1), data3[0], color='tab:blue', label='#facts')
plot3.set_xticks(range(1, nCols + 1))
plot3.set_ylabel('#facts')
plot3.grid(axis='y', linestyle='--')
ax2 = plot3.twinx()
ax2.plot(range(1, nCols + 1), data3[1], color='red', label='precision', marker='x')
ax2.set_ylim([0, 1])
plot3.set_title('stadium-capacity')

################
nCols = len(data4[0])
plot4.bar(range(1, nCols + 1), data4[0], color='tab:blue', label='#facts')
plot4.set_xticks(range(1, nCols + 1))
plot4.grid(axis='y', linestyle='--')
ax2 = plot4.twinx()
ax2.plot(range(1, nCols + 1), data4[1], color='red', label='precision', marker='x')
ax2.set_ylim([0, 1])
ax2.set_ylabel('prec.')
plot4.set_title('river-length')

################
nCols = len(data5[0])
plot5.bar(range(1, nCols + 1), data5[0], color='tab:blue', label='#facts')
plot5.set_xticks(range(1, nCols + 1))
plot5.grid(axis='y', linestyle='--')
plot5.set_ylabel('#facts')
ax2 = plot5.twinx()
ax2.plot(range(1, nCols + 1), data5[1], color='red', label='precision', marker='x')
ax2.set_ylim([0, 1])
plot5.set_title('powerstation-capacity')

################
nCols = len(data6[0])
plot6.bar(range(1, nCols + 1), data6[0], color='tab:blue', label='#facts')
plot6.set_xticks(range(1, nCols + 1))
plot6.grid(axis='y', linestyle='--')

ax2 = plot6.twinx()
ax2.plot(range(1, nCols + 1), data6[1], color='red', label='precision', marker='x')
ax2.set_ylim([0, 1])
ax2.set_ylabel('prec.')
plot6.set_title('earthquake-magnitude')

plot6.legend(loc='center right', bbox_to_anchor=(2.2, 0.65))
ax2.legend(loc='center right', bbox_to_anchor=(2.2, 0.4))
gs.tight_layout(fig, h_pad=0, w_pad=0)
plt.show()
