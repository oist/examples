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
# file = './stall_detection.csv'
df: pd.DataFrame = pd.read_csv(file)

regression = linregress(df.index[20:], df['BatteryLevel'][20:])
print(f"R-squared: {regression.rvalue**2:.6f}")
print(f"Slope: {regression.slope:.6f}")

fig, axes = plt.subplots(nrows=4, ncols=1, sharex=True)

sliceStart = 0
sliceEnd = -1
scaling = 150

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
axes[2].plot(df['SpeedLBuff'][sliceStart:sliceEnd])
axes[2].plot(df['SpeedRBuff'][sliceStart:sliceEnd])
axes[2].plot(df['OutputL'][sliceStart:sliceEnd]*scaling)
axes[2].plot(df['OutputR'][sliceStart:sliceEnd]*scaling)
axes[2].plot(df['ExpectedL'][sliceStart:sliceEnd]*scaling)
axes[2].plot(df['ExpectedR'][sliceStart:sliceEnd]*scaling)
axes[2].legend(['SpeedLBuff', 'SpeedRBuff', 'OutputL', 'OutputR', 'ExpectedR', 'ExpectedR'])
axes[2].set_title("Speed")
axes[3].plot(df['stallCntL'][sliceStart:sliceEnd], label="stallCntL")
axes[3].plot(df['stallCntR'][sliceStart:sliceEnd], label="stallCntR")
axes[3].plot(df['stuckL'][sliceStart:sliceEnd], label="stuckL")
axes[3].plot(df['stuckR'][sliceStart:sliceEnd], label="stuckR")
axes[3].plot(df['freeCntL'][sliceStart:sliceEnd], label="freeCntL")
axes[3].plot(df['freeCntR'][sliceStart:sliceEnd], label="freeCntR")
axes[3].plot(df['maxStallL'][sliceStart:sliceEnd], label="maxStallL")
axes[3].plot(df['maxStallR'][sliceStart:sliceEnd], label="maxStallR")
axes[3].legend()

# df.plot(ax=axes[0], y=['BatteryLevel'])
# df.plot.scatter(ax=axes[1], x=['index'], y=['State'])
# df.plot(ax=axes[2], y=['DistanceL', 'DistanceR'])
# df.plot(ax=axes[3], y=['SpeedL', 'SpeedR'])

plt.show()
