import os
import matplotlib.pyplot as plt
import numpy as np

# Directory containing the results
results_dir = "simulation_results"

# List to store turns data for each strategy
strategy_data = {}

# Read data from files
for filename in os.listdir(results_dir):
    if filename.endswith(".txt"):
        strategy_name = filename.split('_')[0]
        with open(os.path.join(results_dir, filename), "r") as file:
            turns = [int(line.strip()) for line in file]
            if strategy_name not in strategy_data:
                strategy_data[strategy_name] = []
            strategy_data[strategy_name].extend(turns)

# Determine global min and max for consistent bins
all_turns = [turn for turns in strategy_data.values() for turn in turns]
min_turns = min(all_turns)
max_turns = max(all_turns)
bins = np.arange(min_turns, max_turns + 1)  # Create bins for each integer value

# Function to calculate moving average
def moving_average(data, window_size):
    return np.convolve(data, np.ones(window_size) / window_size, mode='valid')

# Plot line graphs and display statistics for each strategy
fig, ax = plt.subplots(figsize=(10, 6))

# Vertical offset for text
text_offset = 0.95

# Plot each strategy's line graph
for strategy_name, turns in strategy_data.items():
    # Calculate histogram
    hist, bin_edges = np.histogram(turns, bins=bins)
    bin_centers = (bin_edges[:-1] + bin_edges[1:]) / 2

    # Smooth the line using moving average
    window_size = 5
    smooth_hist = moving_average(hist, window_size)
    smooth_bins = bin_centers[:len(smooth_hist)]

    # Plot smooth line graph
    ax.plot(smooth_bins, smooth_hist, label=strategy_name)

    # Calculate statistics
    average_turns = np.mean(turns)
    median_turns = np.median(turns)
    q1_turns = np.percentile(turns, 25)
    q3_turns = np.percentile(turns, 75)

    # Display statistics on the plot
    stats_text = (
        f"{strategy_name}:\n"
        f"Avg: {average_turns:.2f}\n"
        f"Med: {median_turns}\n"
        f"Q1: {q1_turns}\n"
        f"Q3: {q3_turns}"
    )
    ax.text(0.05, text_offset, stats_text, transform=ax.transAxes, fontsize=10, verticalalignment='top', 
            bbox=dict(boxstyle='round,pad=0.5', edgecolor='black', facecolor='white', alpha=0.7))
    text_offset -= 0.15  # Move down for the next block of text

# Customize plot
ax.set_title('Distribution of Turns for Different Strategies')
ax.set_xlabel('Turns')
ax.set_ylabel('Frequency')
ax.legend()

# Show plot
plt.show()
