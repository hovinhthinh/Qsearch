# libraries

import matplotlib
import matplotlib.pyplot as plt
from matplotlib import gridspec

# city altitude
data1 = [[91, 1243, 1350, 1390, 1396],
         [0.9, 0.933, 0.967, 0.933, 0.967]]
# company revenue
data2 = [[398, 464, 527, 951, 1004, 1252, 1283, 1740, 1874, 1928],
         [0.8, 0.733, 0.7, 0.767, 0.5, 0.533, 0.533, 0.467, 0.333, 0.333]]  # fixed
# building height
data3 = [[101, 158, 195, 215, 236],
         [0.796, 0.849, 0.84, 0.816, 0.789]]  # fixed
# mountain elevation
data4 = [[98, 210, 281, 343, 360, 392, 420, 457, 488, 518],
         [0.878, 0.876, 0.847, 0.85, 0.849, 0.827, 0.808, 0.799, 0.791, 0.772]]
# stadium capacity
data5 = [[696, 823, 1001, 1017, 1031, 1045, 1086, 1206, 1321, 1337],
         [0.726, 0.718, 0.7, 0.715, 0.686, 0.687, 0.691, 0.629, 0.518, 0.559]]
# river length
data6 = [[178, 206, 377, 531, 589],
         [0.69, 0.663, 0.65, 0.597, 0.588]]

font = {'family': 'normal',
        'size': 8}

matplotlib.rc('font', **font)

fig = plt.figure()

# gs = gridspec.GridSpec(2, 20, width_ratios=[[1, 2, 1],[ 2, 2, 1]])
gs = gridspec.GridSpec(11, 10)

plot1 = fig.add_subplot(gs[0:3, 0:3])
plot2 = fig.add_subplot(gs[0:3, 4:10])
plot3 = fig.add_subplot(gs[4:7, 0:3])
plot4 = fig.add_subplot(gs[4:7, 4:10])
plot5 = fig.add_subplot(gs[8:11, 0:6])
plot6 = fig.add_subplot(gs[8:11, 7:10])
# plot1, plot2, plot3, plot4, plt5, plot6 = [plt.subplot(gs[i]) for i in range(6)]

################
nCols = len(data1[0])
plot1.bar(range(1, nCols + 1), data1[0], color='tab:blue', label='#facts')
plot1.set_xticks(range(1, nCols + 1))
plot1.grid(axis='y', linestyle='--')
# plot1.set_ylim([0, 2000])
plot1.set_ylabel('#facts')
plot1.set_title('city-altitude')

ax2 = plot1.twinx()
ax2.plot(range(1, nCols + 1), data1[1], color='red', label='precision', marker='x')
ax2.set_ylim([0, 1])


# plot1.ylabel('prec.')

################
nCols = len(data2[0])
plot2.bar(range(1, nCols + 1), data2[0], color='tab:blue', label='#facts')
plot2.set_xticks(range(1, nCols + 1))
plot2.grid(axis='y', linestyle='--')
# plot2.set_ylim([0, 2000])
ax2 = plot2.twinx()
ax2.plot(range(1, nCols + 1), data2[1], color='red', label='precision', marker='x')
ax2.set_ylim([0, 1])
ax2.set_ylabel('prec.')
plot2.set_title('company-revenue')

################
nCols = len(data3[0])
plot3.bar(range(1, nCols + 1), data3[0], color='tab:blue', label='#facts')
plot3.set_xticks(range(1, nCols + 1))
plot3.grid(axis='y', linestyle='--')

# plot3.set_ylim([0, 2000])
plot3.set_ylabel('#facts')
ax2 = plot3.twinx()
ax2.plot(range(1, nCols + 1), data3[1], color='red', label='precision', marker='x')
ax2.set_ylim([0, 1])
plot3.set_title('building-height')

################
nCols = len(data4[0])
plot4.bar(range(1, nCols + 1), data4[0], color='tab:blue', label='#facts')
plot4.set_xticks(range(1, nCols + 1))
plot4.grid(axis='y', linestyle='--')

# plot4.set_ylim([0, 2000])
ax2 = plot4.twinx()
ax2.plot(range(1, nCols + 1), data4[1], color='red', label='precision', marker='x')
ax2.set_ylim([0, 1])
ax2.set_ylabel('prec.')
plot4.set_title('mountain-elevation')


################
nCols = len(data5[0])
plot5.bar(range(1, nCols + 1), data5[0], color='tab:blue', label='#facts')
plot5.set_xticks(range(1, nCols + 1))
plot5.set_ylabel('#facts')
plot5.grid(axis='y', linestyle='--')

# plot5.set_ylim([0, 2000])
ax2 = plot5.twinx()
ax2.plot(range(1, nCols + 1), data5[1], color='red', label='precision', marker='x')
ax2.set_ylim([0, 1])
plot5.set_title('stadium-capacity')

################
nCols = len(data6[0])
plot6.bar(range(1, nCols + 1), data6[0], color='tab:blue', label='#facts')
plot6.set_xticks(range(1, nCols + 1))
plot6.grid(axis='y', linestyle='--')

# plot6.set_ylim([0, 2000])
ax2 = plot6.twinx()
ax2.plot(range(1, nCols + 1), data6[1], color='red', label='precision', marker='x')
ax2.set_ylim([0, 1])
ax2.set_ylabel('prec.')
plot6.set_title('river-length')

plot6.legend(loc='upper center', bbox_to_anchor=(-1.05, -0.2))
ax2.legend(loc='upper center', bbox_to_anchor=(-0.3, -0.2))
# gs.tight_layout(fig, h_pad=0, w_pad=0)
plt.show()
