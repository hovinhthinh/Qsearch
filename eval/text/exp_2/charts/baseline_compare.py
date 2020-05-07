# libraries
import matplotlib.pyplot as plt
import numpy as np


def adjustFigAspect(fig, aspect=1):
    '''
    Adjust the subplot parameters so that the figure has the correct
    aspect ratio.
    '''
    xsize, ysize = fig.get_size_inches()
    minsize = min(xsize, ysize)
    xlim = .4 * minsize / xsize
    ylim = .4 * minsize / ysize
    if aspect < 1:
        xlim *= aspect
    else:
        ylim /= aspect
    fig.subplots_adjust(left=.5 - xlim,
                        right=.5 + xlim,
                        bottom=.5 - ylim,
                        top=.5 + ylim)


# set width of bar
barWidth = 0.25

# values: PR.@3; HIT@3; PR.@5; MMR
googleFinance = [0.307, 0.6, 0.288, 0.484]
esFinance = [0.307, 0.52, 0.248, 0.408]
qsearchFinance = [0.747, 0.920, 0.672, 0.870]

googleTransport = [0.333, 0.56, 0.232, 0.483]
esTransport = [0.213, 0.520, 0.184, 0.379]
qsearchTransport = [0.480, 0.760, 0.412, 0.678]

googleSports = [0.32, 0.52, 0.328, 0.514]
esSports = [0.213, 0.440, 0.184, 0.357]
qsearchSports = [0.587, 0.8, 0.528, 0.746]

googleTech = [0.280, 0.6, 0.272, 0.492]
esTech = [0.347, 0.56, 0.320, 0.468]
qsearchTech = [0.653, 0.880, 0.624, 0.783]

googleAll = [0.310, 0.57, 0.280, 0.493]
esAll  = [0.270, 0.510, 0.234, 0.403]
qsearchAll  = [0.617, 0.840, 0.559, 0.769]

# Set position of bar on X axis
r1 = np.arange(2)
r2 = [x + barWidth for x in r1]
r3 = [x + barWidth for x in r2]

fig, axs = plt.subplots(2, 5, sharex=False, sharey=True)
((ax1, ax2, ax3, ax4, ax5),(ax6, ax7, ax8, ax9, ax10)) = axs;
for axr in axs:
    for axc in axr:
        axc.tick_params(axis='y', labelsize=5, length=2, color='#b9b9ba')
        axc.tick_params(axis='x', labelsize=5, length=0)
        axc.spines['top'].set_linewidth(0.5);
        axc.spines['right'].set_linewidth(0.5)
        axc.spines['bottom'].set_linewidth(0.5)
        axc.spines['left'].set_linewidth(0.5)
        axc.spines['top'].set_edgecolor('#b9b9ba')
        axc.spines['right'].set_edgecolor('#b9b9ba')
        axc.spines['bottom'].set_edgecolor('#b9b9ba')
        axc.spines['left'].set_edgecolor('#b9b9ba')

# Make the plot
ax1.set_title('Finance', fontsize=5, y=0.925)
ax1.bar(r1, [googleFinance[0], googleFinance[2]], color='#496fb5', width=barWidth, linewidth=0.25, edgecolor='#b9b9ba', label='Google')
ax1.bar(r2, [esFinance[0], esFinance[2]], color='#70af47', width=barWidth, linewidth=0.25, edgecolor='#b9b9ba', label='Elasticsearch')
ax1.bar(r3, [qsearchFinance[0], qsearchFinance[2]], color='#f9bd1e', width=barWidth, linewidth=0.25, edgecolor='#b9b9ba', label='Qsearch')
plt.sca(ax1)
plt.xticks([r + barWidth for r in range(2)], ['Pr.@3', 'Pr.@5'])

ax2.set_title('Transport', fontsize=5, y=0.925)
ax2.bar(r1, [googleTransport[0], googleTransport[2]], color='#496fb5', width=barWidth, linewidth=0.25, edgecolor='#b9b9ba', label='Google')
ax2.bar(r2, [esTransport[0], esTransport[2]], color='#70af47', width=barWidth, linewidth=0.25, edgecolor='#b9b9ba', label='Elasticsearch')
ax2.bar(r3, [qsearchTransport[0], qsearchTransport[2]], color='#f9bd1e', width=barWidth, linewidth=0.25, edgecolor='#b9b9ba', label='Qsearch')
plt.sca(ax2)
plt.xticks([r + barWidth for r in range(2)], ['Pr.@3', 'Pr.@5'])

ax3.set_title('Sports', fontsize=5, y=0.925)
ax3.bar(r1, [googleSports[0], googleSports[2]], color='#496fb5', width=barWidth, linewidth=0.25, edgecolor='#b9b9ba', label='Google')
ax3.bar(r2, [esSports[0], esSports[2]], color='#70af47', width=barWidth, linewidth=0.25, edgecolor='#b9b9ba', label='Elasticsearch')
ax3.bar(r3, [qsearchSports[0], qsearchSports[2]], color='#f9bd1e', width=barWidth, linewidth=0.25, edgecolor='#b9b9ba', label='Qsearch')
plt.sca(ax3)
plt.xticks([r + barWidth for r in range(2)], ['Pr.@3', 'Pr.@5'])

ax4.set_title('Technology', fontsize=5, y=0.925)
ax4.bar(r1, [googleTech[0], googleTech[2]], color='#496fb5', width=barWidth, linewidth=0.25, edgecolor='#b9b9ba', label='Google')
ax4.bar(r2, [esTech[0], esTech[2]], color='#70af47', width=barWidth, linewidth=0.25, edgecolor='#b9b9ba', label='Elasticsearch')
ax4.bar(r3, [qsearchTech[0], qsearchTech[2]], color='#f9bd1e', width=barWidth, linewidth=0.25, edgecolor='#b9b9ba', label='Qsearch')
plt.sca(ax4)
plt.xticks([r + barWidth for r in range(2)], ['Pr.@3', 'Pr.@5'])

ax5.set_title('All', fontsize=5, y=0.925)
ax5.bar(r1, [googleAll[0], googleAll[2]], color='#496fb5', width=barWidth, linewidth=0.25, edgecolor='#b9b9ba', label='Google')
ax5.bar(r2, [esAll[0], esAll[2]], color='#70af47', width=barWidth, linewidth=0.25, edgecolor='#b9b9ba', label='Elasticsearch')
ax5.bar(r3, [qsearchAll[0], qsearchAll[2]], color='#f9bd1e', width=barWidth, linewidth=0.25, edgecolor='#b9b9ba', label='Qsearch')
plt.sca(ax5)
plt.xticks([r + barWidth for r in range(2)], ['Pr.@3', 'Pr.@5'])

### second row

# ax6.set_title('Finance', fontsize=5, y=0.925)
ax6.bar(r1, [googleFinance[1], googleFinance[3]], color='#496fb5', width=barWidth, linewidth=0.25, edgecolor='#b9b9ba', label='Google')
ax6.bar(r2, [esFinance[1], esFinance[3]], color='#70af47', width=barWidth, linewidth=0.25, edgecolor='#b9b9ba', label='Elasticsearch')
ax6.bar(r3, [qsearchFinance[1], qsearchFinance[3]], color='#f9bd1e', width=barWidth, linewidth=0.25, edgecolor='#b9b9ba', label='Qsearch')
plt.sca(ax6)
plt.xticks([r + barWidth for r in range(2)], ['Hit@3', 'MRR'])

# ax7.set_title('Transport', fontsize=5, y=0.925)
ax7.bar(r1, [googleTransport[1], googleTransport[3]], color='#496fb5', width=barWidth, linewidth=0.25, edgecolor='#b9b9ba', label='Google')
ax7.bar(r2, [esTransport[1], esTransport[3]], color='#70af47', width=barWidth, linewidth=0.25, edgecolor='#b9b9ba', label='Elasticsearch')
ax7.bar(r3, [qsearchTransport[1], qsearchTransport[3]], color='#f9bd1e', width=barWidth, linewidth=0.25, edgecolor='#b9b9ba', label='Qsearch')
plt.sca(ax7)
plt.xticks([r + barWidth for r in range(2)], ['Hit@3', 'MRR'])

# ax8.set_title('Sports', fontsize=5, y=0.925)
ax8.bar(r1, [googleSports[1], googleSports[3]], color='#496fb5', width=barWidth, linewidth=0.25, edgecolor='#b9b9ba', label='Google')
ax8.bar(r2, [esSports[1], esSports[3]], color='#70af47', width=barWidth, linewidth=0.25, edgecolor='#b9b9ba', label='Elasticsearch')
ax8.bar(r3, [qsearchSports[1], qsearchSports[3]], color='#f9bd1e', width=barWidth, linewidth=0.25, edgecolor='#b9b9ba', label='Qsearch')
plt.sca(ax8)
plt.xticks([r + barWidth for r in range(2)], ['Hit@3', 'MRR'])

# ax9.set_title('Technology', fontsize=5, y=0.925)
ax9.bar(r1, [googleTech[1], googleTech[3]], color='#496fb5', width=barWidth, linewidth=0.25, edgecolor='#b9b9ba', label='Google')
ax9.bar(r2, [esTech[1], esTech[3]], color='#70af47', width=barWidth, linewidth=0.25, edgecolor='#b9b9ba', label='Elasticsearch')
ax9.bar(r3, [qsearchTech[1], qsearchTech[3]], color='#f9bd1e', width=barWidth, linewidth=0.25, edgecolor='#b9b9ba', label='Qsearch')
plt.sca(ax9)
plt.xticks([r + barWidth for r in range(2)], ['Hit@3', 'MRR'])

# ax10.set_title('All', fontsize=5, y=0.925)
ax10.bar(r1, [googleAll[1], googleAll[3]], color='#496fb5', width=barWidth, linewidth=0.25, edgecolor='#b9b9ba', label='Google')
ax10.bar(r2, [esAll[1], esAll[3]], color='#70af47', width=barWidth, linewidth=0.25, edgecolor='#b9b9ba', label='Elasticsearch')
ax10.bar(r3, [qsearchAll[1], qsearchAll[3]], color='#f9bd1e', width=barWidth, linewidth=0.25, edgecolor='#b9b9ba', label='Qsearch')
plt.sca(ax10)
plt.xticks([r + barWidth for r in range(2)], ['Hit@3', 'MRR'])

ax8.legend(loc='upper center', bbox_to_anchor=(0.5, -0.125), ncol=3, fontsize=5, frameon=False)
# plt.figure(figsize=(20,5))
# Create legend & Show graphic
# plt.legend(loc='upper center')
adjustFigAspect(fig, aspect=2.4)
plt.savefig("compare.pdf", bbox_inches='tight')
