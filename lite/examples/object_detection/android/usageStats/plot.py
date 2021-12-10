from typing import Union, Any, List

import pandas as pd
import matplotlib.pyplot as plt
import scipy.stats
from scipy.stats import linregress
import sys
from matplotlib import cm

# Plot BatteryLevel, State, DistanceL, DistanceR, SpeedL, SpeedR on four plots
from pandas import Series, DataFrame
from pandas.core.generic import NDFrame
from pandas.io.parsers import TextFileReader

file = sys.argv[1]
# file = './stall_detection_HighBattLevel.csv'
df: pd.DataFrame = pd.read_csv(file)

regression = linregress(df.index[20:], df['BatteryLevel'][20:])
print(f"R-squared: {regression.rvalue**2:.6f}")
print(f"Slope: {regression.slope:.6f}")

fig, axes = plt.subplots(nrows=4, ncols=1, sharex=True)

sliceStart = 0
sliceEnd = -1
scaling = 100

axes[0].plot(df['BatteryLevel'][sliceStart:sliceEnd])
axes[0].plot(df.index[sliceStart + 20:sliceEnd], (regression.intercept + regression.slope * df.index)[sliceStart + 20:sliceEnd], 'r', label="linReg")
axes[0].set_ylim(2.6, 3.2)
axes[0].set_title("Battery Level")
axes[1].plot(df['State'][sliceStart:sliceEnd])
axes[1].set_title("State")

# axes[2].plot(df['DistanceL'][sliceStart:sliceEnd])
# axes[2].plot(df['DistanceR'][sliceStart:sliceEnd])
# axes[3].plot(df['SpeedLExp'][sliceStart:sliceEnd])
# axes[3].plot(df['SpeedRExp'][sliceStart:sliceEnd])
axes[2].plot(df['SpeedLBuff'][sliceStart:sliceEnd], color='r', alpha=0.1)
axes[2].plot(df['SpeedRBuff'][sliceStart:sliceEnd], color='b', alpha=0.1)
# axes[2].plot(df['OutputL'][sliceStart:sliceEnd]*150, color='r', alpha=0.5, linestyle=':')
# axes[2].plot(df['OutputR'][sliceStart:sliceEnd]*150, color='b', alpha=0.5, linestyle=':')
axes[2].plot(df['ExpectedL'][sliceStart:sliceEnd]*scaling, color='r', alpha=0.5)
axes[2].plot(df['ExpectedR'][sliceStart:sliceEnd]*scaling, color='b', alpha=0.5)
axes[2].legend(['SpeedL_Measured_LP', 'SpeedR_Measured_LP', 'SpeedL_Expected', 'SpeedR_ExpectedR'])
axes[2].set_title("Speed")
axes[3].plot(df['stallCntL'][sliceStart:sliceEnd], label="stallCntL", color='r', alpha=0.1)
axes[3].plot(df['stallCntR'][sliceStart:sliceEnd], label="stallCntR", color='b', alpha=0.1)
axes[3].plot(df['stuckL'][sliceStart:sliceEnd]*20, label="stuckL", color='r', alpha=1, linestyle=':')
axes[3].plot(df['stuckR'][sliceStart:sliceEnd]*20, label="stuckR", color='b', alpha=1, linestyle=':')
axes[3].plot(df['freeCntL'][sliceStart:sliceEnd], label="freeCntL", color='r', alpha=0.5)
axes[3].plot(df['freeCntR'][sliceStart:sliceEnd], label="freeCntR", color='b', alpha=0.5)
axes[3].plot(df['maxStallL'][sliceStart:sliceEnd]*30, label="maxStallL", color='r', alpha=1, linestyle='--')
axes[3].plot(df['maxStallR'][sliceStart:sliceEnd]*30, label="maxStallR", color='b', alpha=1, linestyle='--')
axes[3].legend()

fig2, axes2 = plt.subplots(nrows=2, ncols=1)

axes2[0].plot(df['OutputL'][sliceStart:sliceEnd], df['SpeedLBuff'][sliceStart:sliceEnd], color='r', alpha=1, linestyle='', marker='.')
axes2[0].plot(df['OutputR'][sliceStart:sliceEnd], df['SpeedRBuff'][sliceStart:sliceEnd], color='b', alpha=1, linestyle='', marker='.')
axes2[0].set_title('setOutput args Vs LP Speed Measured')
axes2[0].set_xlim(-1.2,1.2)
axes2[1].plot(df['ExpectedL'][sliceStart:sliceEnd], df['SpeedLBuff'][sliceStart:sliceEnd], color='r', alpha=1, linestyle='', marker='.')
axes2[1].plot(df['ExpectedR'][sliceStart:sliceEnd], df['SpeedRBuff'][sliceStart:sliceEnd], color='b', alpha=1, linestyle='', marker='.')
axes2[1].set_title('expected speed Vs LP Speed Measured')

# df.plot(ax=axes[0], y=['BatteryLevel'])
# df.plot.scatter(ax=axes[1], x=['index'], y=['State'])
# df.plot(ax=axes[2], y=['DistanceL', 'DistanceR'])
# df.plot(ax=axes[3], y=['SpeedL', 'SpeedR'])

plt.show()
