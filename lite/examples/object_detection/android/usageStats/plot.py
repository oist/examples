from typing import Union, Any, List

import pandas as pd
import matplotlib.pyplot as plt
import scipy.stats
from scipy.stats import linregress
import sys

# Plot BatteryLevel, State, DistanceL, DistanceR, SpeedL, SpeedR on four plots
from pandas import Series, DataFrame
from pandas.core.generic import NDFrame
from pandas.io.parsers import TextFileReader

file = sys.argv[1]
df: pd.DataFrame = pd.read_csv(file)

regression = linregress(df.index[20:], df['BatteryLevel'][20:])
print(f"R-squared: {regression.rvalue**2:.6f}")
print(f"Slope: {regression.slope:.6f}")

axes: List[plt.Axes]
fig, axes = plt.subplots(nrows=4, ncols=1)

axes[0].plot(df['BatteryLevel'])
axes[0].plot(df.index[20:], (regression.intercept + regression.slope*df.index)[20:], 'r', label="linReg")
axes[0].set_ylim(2.6, 3.2)
axes[1].plot(df['State'])
axes[2].plot(df['DistanceL'])
axes[2].plot(df['DistanceR'])
axes[3].plot(df['SpeedL'])
axes[3].plot(df['SpeedR'])

# df.plot(ax=axes[0], y=['BatteryLevel'])
# df.plot.scatter(ax=axes[1], x=['index'], y=['State'])
# df.plot(ax=axes[2], y=['DistanceL', 'DistanceR'])
# df.plot(ax=axes[3], y=['SpeedL', 'SpeedR'])

plt.show()
