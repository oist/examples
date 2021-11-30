from typing import Union, Any

import pandas as pd
import matplotlib.pyplot as plt
import sys

# Plot BatteryLevel, State, DistanceL, DistanceR, SpeedL, SpeedR on four plots
from pandas import Series, DataFrame
from pandas.core.generic import NDFrame
from pandas.io.parsers import TextFileReader

file = sys.argv[0]
file = './1638259925101.csv'
df: pd.DataFrame = pd.read_csv(file)

fig, axes = plt.subplots(nrows=4, ncols=1)

axes[0].plot(df['BatteryLevel'])
axes[1].scatter([0, 1, 2, 3], df['State'])
axes[2].plot(df['DistanceL'])
axes[2].plot(df['DistanceR'])
axes[3].plot(df['SpeedL'])
axes[3].plot(df['SpeedR'])

# df.plot(ax=axes[0], y=['BatteryLevel'])
# df.plot.scatter(ax=axes[1], x=['index'], y=['State'])
# df.plot(ax=axes[2], y=['DistanceL', 'DistanceR'])
# df.plot(ax=axes[3], y=['SpeedL', 'SpeedR'])

plt.show()
