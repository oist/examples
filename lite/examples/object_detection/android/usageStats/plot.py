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
# file = './100msSR.csv'
df: pd.DataFrame = pd.read_csv(file)

regression = linregress(df.index[20:], df['BatteryLevel'][20:])
print(f"R-squared: {regression.rvalue**2:.6f}")
print(f"Slope: {regression.slope:.6f}")

fig, axes = plt.subplots(nrows=4, ncols=1)

sliceStart = 0
sliceEnd = -1

axes[0].plot(df['BatteryLevel'][sliceStart:sliceEnd])
axes[0].plot(df.index[sliceStart + 20:sliceEnd], (regression.intercept + regression.slope * df.index)[sliceStart + 20:sliceEnd], 'r', label="linReg")
axes[0].set_ylim(2.6, 3.2)
axes[1].plot(df['State'][sliceStart:sliceEnd])
axes[2].plot(df['DistanceL'][sliceStart:sliceEnd])
axes[2].plot(df['DistanceR'][sliceStart:sliceEnd])
axes[3].plot(df['SpeedLExp'][sliceStart:sliceEnd])
axes[3].plot(df['SpeedRExp'][sliceStart:sliceEnd])
axes[3].plot(df['SpeedLBuff'][sliceStart:sliceEnd])
axes[3].plot(df['SpeedRBuff'][sliceStart:sliceEnd])
axes[3].plot(df['OutputL'][sliceStart:sliceEnd]*250)
axes[3].plot(df['OutputR'][sliceStart:sliceEnd]*250)
axes[3].legend(['SpeedLExp', 'SpeedRExp', 'SpeedLBuff', 'SpeedRBuff', 'OutputL', 'OutputR'])

# df.plot(ax=axes[0], y=['BatteryLevel'])
# df.plot.scatter(ax=axes[1], x=['index'], y=['State'])
# df.plot(ax=axes[2], y=['DistanceL', 'DistanceR'])
# df.plot(ax=axes[3], y=['SpeedL', 'SpeedR'])

plt.show()
