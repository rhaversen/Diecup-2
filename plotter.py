import os
import matplotlib.pyplot as plt
import numpy as np
from collections import defaultdict

# Directory containing the results
results_dir = "simulation_results"

# Dictionary to store the latest data for each strategy
strategy_data = defaultdict(list)

# Read data from files, keeping only the most recent file for each strategy
strategy_files = defaultdict(list)
for filename in os.listdir(results_dir):
    if filename.endswith(".txt"):
        strategy_name = filename.split('_')[0]
        strategy_files[strategy_name].append(filename)

# For each strategy, use the most recent file
for strategy_name, files in strategy_files.items():
    # Sort by timestamp (assuming format StrategyName_YYYYMMDD_HHMMSS.txt)
    files.sort(reverse=True)
    latest_file = files[0]
    
    with open(os.path.join(results_dir, latest_file), "r") as file:
        turns = [int(line.strip()) for line in file]
        strategy_data[strategy_name] = turns

# Determine global min and max for consistent bins
all_turns = [turn for turns in strategy_data.values() for turn in turns]
if not all_turns:
    print("No data found in simulation_results directory!")
    exit()

min_turns = min(all_turns)
max_turns = max(all_turns)
bins = np.arange(min_turns, max_turns + 2)  # +2 to include max_turns

# Function to calculate moving average
def moving_average(data, window_size):
    if len(data) < window_size:
        return data
    return np.convolve(data, np.ones(window_size) / window_size, mode='valid')

# Create figure with subplots
fig = plt.figure(figsize=(16, 10))

# Main plot for distributions
ax1 = plt.subplot(2, 2, (1, 2))  # Top half
ax2 = plt.subplot(2, 2, 3)       # Bottom left
ax3 = plt.subplot(2, 2, 4)       # Bottom right

# Colors for each strategy
colors = plt.get_cmap('tab10')(np.linspace(0, 1, len(strategy_data)))

# Store statistics for summary
stats_summary = []

# Plot distributions and collect statistics
for i, (strategy_name, turns) in enumerate(strategy_data.items()):
    color = colors[i]
    
    # Calculate histogram
    hist, bin_edges = np.histogram(turns, bins=bins)
    bin_centers = (bin_edges[:-1] + bin_edges[1:]) / 2

    # Smooth the line using moving average
    window_size = min(5, len(hist))
    if window_size > 1:
        smooth_hist = moving_average(hist, window_size)
        smooth_bins = bin_centers[:len(smooth_hist)]
    else:
        smooth_hist = hist
        smooth_bins = bin_centers

    # Plot smooth line graph
    ax1.plot(smooth_bins, smooth_hist, label=strategy_name, color=color, linewidth=2)

    # Calculate statistics
    average_turns = np.mean(turns)
    median_turns = np.median(turns)
    std_turns = np.std(turns)
    q1_turns = np.percentile(turns, 25)
    q3_turns = np.percentile(turns, 75)
    min_turn = min(turns)
    max_turn = max(turns)

    stats_summary.append({
        'strategy': strategy_name,
        'avg': average_turns,
        'median': median_turns,
        'std': std_turns,
        'q1': q1_turns,
        'q3': q3_turns,
        'min': min_turn,
        'max': max_turn,
        'color': color,
        'count': len(turns)
    })

# Customize main distribution plot
ax1.set_title('Distribution of Turns for Different Strategies', fontsize=14, fontweight='bold')
ax1.set_xlabel('Number of Turns', fontsize=12)
ax1.set_ylabel('Frequency', fontsize=12)
ax1.legend(bbox_to_anchor=(1.05, 1), loc='upper left')
ax1.grid(True, alpha=0.3)

# Sort strategies by average performance for summary displays
stats_summary.sort(key=lambda x: x['avg'])

# Create box plot comparison
ax2.set_title('Performance Comparison (Box Plot)', fontsize=12, fontweight='bold')
box_data = []
box_labels = []
box_colors = []

for stat in stats_summary:
    turns = strategy_data[stat['strategy']]
    box_data.append(turns)
    box_labels.append(stat['strategy'])
    box_colors.append(stat['color'])

bp = ax2.boxplot(box_data, patch_artist=True)
ax2.set_xticklabels(box_labels)
for patch, color in zip(bp['boxes'], box_colors):
    patch.set_facecolor(color)
    patch.set_alpha(0.7)

ax2.set_ylabel('Number of Turns', fontsize=10)
ax2.tick_params(axis='x', rotation=45)
ax2.grid(True, alpha=0.3)

# Create statistics table
ax3.axis('tight')
ax3.axis('off')
ax3.set_title('Performance Statistics', fontsize=12, fontweight='bold')

# Prepare table data
table_data = []
headers = ['Strategy', 'Avg', 'Med', 'Std', 'Q1', 'Q3', 'Min', 'Max', 'Runs']

for stat in stats_summary:
    row = [
        stat['strategy'][:12] + '...' if len(stat['strategy']) > 12 else stat['strategy'],
        f"{stat['avg']:.1f}",
        f"{stat['median']:.1f}",
        f"{stat['std']:.1f}",
        f"{stat['q1']:.1f}",
        f"{stat['q3']:.1f}",
        f"{stat['min']}",
        f"{stat['max']}",
        f"{stat['count']}"
    ]
    table_data.append(row)

# Create table
table = ax3.table(cellText=table_data, colLabels=headers, cellLoc='center', loc='center')
table.auto_set_font_size(False)
table.set_fontsize(9)
table.scale(1.2, 1.5)

# Color code the table rows
for i, stat in enumerate(stats_summary):
    for j in range(len(headers)):
        table[(i+1, j)].set_facecolor(stat['color'])
        table[(i+1, j)].set_alpha(0.3)

# Adjust layout and show
plt.tight_layout()
plt.subplots_adjust(right=0.85)

# Print summary to console
print("\n" + "="*60)
print("STRATEGY PERFORMANCE SUMMARY")
print("="*60)
print(f"{'Strategy':<20} {'Avg':<6} {'Med':<6} {'Std':<6} {'Runs':<6}")
print("-"*60)
for stat in stats_summary:
    print(f"{stat['strategy']:<20} {stat['avg']:<6.1f} {stat['median']:<6.1f} {stat['std']:<6.1f} {stat['count']:<6}")
print("="*60)

plt.show()
