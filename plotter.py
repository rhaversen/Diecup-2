import os
import matplotlib.pyplot as plt
import numpy as np
from collections import defaultdict
from datetime import datetime
import matplotlib.dates as mdates
from matplotlib.figure import Figure

results_dir = "simulation_results"

strategy_data = defaultdict(list)
current_mode = "all"
selected_strategy = None
fig: Figure | None = None

def parse_timestamp(filename):
    parts = filename.split('_')
    if len(parts) >= 3:
        date_str = parts[-2]
        time_str = parts[-1].replace('.txt', '')
        
        try:
            dt = datetime.strptime(f"{date_str}_{time_str}", "%Y%m%d_%H%M%S")
            return dt
        except ValueError:
            return None
    return None

def load_all_strategies():
    strategy_files = defaultdict(list)
    for filename in os.listdir(results_dir):
        if filename.endswith(".txt"):
            parts = filename.split('_')
            if len(parts) >= 3:
                strategy_name = '_'.join(parts[:-2])
            else:
                strategy_name = parts[0]
            strategy_files[strategy_name].append(filename)

    data = {}
    for strategy_name, files in strategy_files.items():
        files.sort(reverse=True)
        latest_file = files[0]
        
        with open(os.path.join(results_dir, latest_file), "r") as file:
            turns = [int(line.strip()) for line in file]
            data[strategy_name] = turns
    return data

def load_single_strategy(strategy_name):
    strategy_files = []
    for filename in os.listdir(results_dir):
        if filename.endswith(".txt") and filename.startswith(strategy_name):
            strategy_files.append(filename)
    
    if not strategy_files:
        return {}
    
    strategy_files.sort()
    
    data = {}
    for filename in strategy_files:
        timestamp = parse_timestamp(filename)
        if timestamp:
            with open(os.path.join(results_dir, filename), "r") as file:
                turns = [int(line.strip()) for line in file]
                data[timestamp] = turns
        else:
            with open(os.path.join(results_dir, filename), "r") as file:
                turns = [int(line.strip()) for line in file]
                data[filename] = turns
    
    return data

def moving_average(data, window_size):
    if len(data) < window_size:
        return data
    return np.convolve(data, np.ones(window_size) / window_size, mode='valid')

def switch_to_single_strategy(strategy_name):
    global current_mode, strategy_data, selected_strategy
    current_mode = "single"
    selected_strategy = strategy_name
    strategy_data = load_single_strategy(strategy_name)
    update_plot()

def switch_to_all_strategies():
    global current_mode, strategy_data, selected_strategy
    current_mode = "all"
    selected_strategy = None
    strategy_data = load_all_strategies()
    update_plot()

def update_plot():
    global current_strategy_lines, fig
    current_strategy_lines = {}
    if fig is None:
        fig = plt.figure(figsize=(16, 10))
    fig.clf()
    if not strategy_data:
        fig.suptitle("No data available.", fontsize=16)
        plt.draw()
        return
    all_turns = [turn for turns in strategy_data.values() for turn in turns]
    min_turns = min(all_turns)
    max_turns = max(all_turns)
    bins = np.arange(min_turns, max_turns + 2)
    if current_mode == "single":
        ax1 = fig.add_subplot(2, 1, 1)
        ax2 = fig.add_subplot(2, 1, 2)
        colors = plt.get_cmap('viridis')(np.linspace(0, 1, len(strategy_data)))
        time_stats = []
        sorted_items = sorted(strategy_data.items())
        for i, (timestamp, turns) in enumerate(sorted_items):
            color = colors[i]
            average_turns = np.mean(turns)
            median_turns = np.median(turns)
            std_turns = np.std(turns)
            time_stats.append({
                'timestamp': timestamp,
                'avg': average_turns,
                'median': median_turns,
                'std': std_turns,
                'color': color,
                'count': len(turns)
            })
            hist, bin_edges = np.histogram(turns, bins=bins, density=True)
            bin_centers = (bin_edges[:-1] + bin_edges[1:]) / 2
            window_size = min(5, len(hist))
            if window_size > 1:
                smooth_hist = moving_average(hist, window_size)
                smooth_bins = bin_centers[:len(smooth_hist)]
            else:
                smooth_hist = hist
                smooth_bins = bin_centers
            if isinstance(timestamp, datetime):
                time_label = timestamp.strftime("%m/%d %H:%M")
            else:
                time_label = str(timestamp)[:20]
            ax1.plot(smooth_bins, smooth_hist, label=time_label, color=color, linewidth=2)
        ax1.set_title(f'Normalized Distribution Evolution for {selected_strategy}', fontsize=14, fontweight='bold')
        ax1.set_xlabel('Number of Turns', fontsize=12)
        ax1.set_ylabel('Density', fontsize=12)
        ax1.legend(bbox_to_anchor=(1.05, 1), loc='upper left')
        ax1.grid(True, alpha=0.3)
        time_stats.sort(key=lambda x: x['timestamp'])
        timestamps = [stat['timestamp'] for stat in time_stats]
        averages = [stat['avg'] for stat in time_stats]
        medians = [stat['median'] for stat in time_stats]
        stds = [stat['std'] for stat in time_stats]
        ax2.plot(timestamps, averages, 'o-', label='Average', linewidth=2, markersize=6)
        ax2.plot(timestamps, medians, 's-', label='Median', linewidth=2, markersize=6)
        ax2.fill_between(timestamps, np.array(averages) - np.array(stds), 
                         np.array(averages) + np.array(stds), alpha=0.3, label='±1 Std Dev')
        ax2.set_title('Performance Progression Over Time', fontsize=12, fontweight='bold')
        ax2.set_xlabel('Time', fontsize=12)
        ax2.set_ylabel('Number of Turns', fontsize=12)
        if all(isinstance(t, datetime) for t in timestamps):
            ax2.xaxis.set_major_formatter(mdates.DateFormatter('%m/%d %H:%M'))
            ax2.xaxis.set_major_locator(mdates.HourLocator(interval=max(1, len(timestamps)//10)))
            plt.setp(ax2.xaxis.get_majorticklabels(), rotation=45, ha='right')
        else:
            plt.setp(ax2.xaxis.get_majorticklabels(), rotation=45, ha='right')
        ax2.legend()
        ax2.grid(True, alpha=0.3)
        back_ax = fig.add_axes((0.85, 0.92, 0.12, 0.06))
        back_ax.axis('off')
        back_btn = back_ax.text(0.5, 0.5, 'Back to Overview', fontsize=12, color='blue', ha='center', va='center', fontweight='bold', bbox=dict(facecolor='white', edgecolor='blue', boxstyle='round,pad=0.5'))
        back_btn.set_picker(True)
        current_strategy_lines[back_btn] = '__back__'
    else:
        ax1 = fig.add_subplot(2, 2, (1, 2))
        ax2 = fig.add_subplot(2, 2, 3)
        ax3 = fig.add_subplot(2, 2, 4)
        colors = plt.get_cmap('tab10')(np.linspace(0, 1, len(strategy_data)))
        stats_summary = []
        for i, (strategy_name, turns) in enumerate(strategy_data.items()):
            color = colors[i]
            hist, bin_edges = np.histogram(turns, bins=bins)
            bin_centers = (bin_edges[:-1] + bin_edges[1:]) / 2
            window_size = min(5, len(hist))
            if window_size > 1:
                smooth_hist = moving_average(hist, window_size)
                smooth_bins = bin_centers[:len(smooth_hist)]
            else:
                smooth_hist = hist
                smooth_bins = bin_centers
            line, = ax1.plot(smooth_bins, smooth_hist, label=strategy_name, color=color, linewidth=2)
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
        ax1.set_title('Distribution of Turns for Different Strategies', fontsize=14, fontweight='bold')
        ax1.set_xlabel('Number of Turns', fontsize=12)
        ax1.set_ylabel('Frequency', fontsize=12)
        ax1.grid(True, alpha=0.3)
        stats_summary.sort(key=lambda x: x['avg'])
        box_data = []
        box_labels = []
        box_colors = []
        for stat in stats_summary:
            turns = strategy_data[stat['strategy']]
            box_data.append(turns)
            box_labels.append(stat['strategy'])
            box_colors.append(stat['color'])
        bp = ax2.boxplot(box_data, patch_artist=True)
        ax2.set_title('Performance Comparison (Box Plot)', fontsize=12, fontweight='bold')
        ax2.set_xticklabels(box_labels)
        for patch, color in zip(bp['boxes'], box_colors):
            patch.set_facecolor(color)
            patch.set_alpha(0.7)
        ax2.set_ylabel('Number of Turns', fontsize=10)
        ax2.tick_params(axis='x', rotation=45)
        ax2.grid(True, alpha=0.3)
        ax3.axis('tight')
        ax3.axis('off')
        ax3.set_title('Performance Statistics', fontsize=12, fontweight='bold')
        table_data = []
        headers = ['Strategy', 'Avg', 'Med', 'Q3', 'Std', 'Q1', 'Min', 'Max', 'Runs']
        for stat in stats_summary:
            row = [
                stat['strategy'][:15] + '...' if len(stat['strategy']) > 15 else stat['strategy'],
                f"{stat['avg']:.1f}",
                f"{stat['median']:.1f}",
                f"{stat['q3']:.1f}",
                f"{stat['std']:.1f}",
                f"{stat['q1']:.1f}",
                f"{stat['min']}",
                f"{stat['max']}",
                f"{stat['count']}"
            ]
            table_data.append(row)
        table = ax3.table(cellText=table_data, colLabels=headers, cellLoc='center', loc='center')
        table.auto_set_font_size(False)
        table.set_fontsize(9)
        table.scale(1.2, 1.5)
        for i, stat in enumerate(stats_summary):
            for j in range(len(headers)):
                table[(i+1, j)].set_facecolor(stat['color'])
                table[(i+1, j)].set_alpha(0.3)
        # Sidebar for strategy selection, height matches main graph area
        sidebar_left = 0.87
        sidebar_bottom = ax1.get_position().y0
        sidebar_width = 0.12
        sidebar_height = ax1.get_position().height
        sidebar_ax = fig.add_axes((sidebar_left, sidebar_bottom, sidebar_width, sidebar_height))
        sidebar_ax.axis('off')
        sidebar_ax.set_title('Select Strategy', fontsize=12, fontweight='bold', pad=10)
        y_positions = np.linspace(0.95, 0.05, len(stats_summary))
        for idx, stat in enumerate(stats_summary):
            txt = sidebar_ax.text(0.5, y_positions[idx], stat['strategy'], fontsize=11, color=stat['color'], ha='center', va='center', fontweight='bold', bbox=dict(facecolor='white', edgecolor=stat['color'], boxstyle='round,pad=0.3'))
            txt.set_picker(True)
            current_strategy_lines[txt] = stat['strategy']
    plt.tight_layout()
    plt.subplots_adjust(right=0.85)
    plt.draw()

def on_pick(event):
    if event.artist in current_strategy_lines:
        val = current_strategy_lines[event.artist]
        if val == '__back__':
            switch_to_all_strategies()
        else:
            switch_to_single_strategy(val)

def on_click(event):
    pass

def create_figure_and_plot():
    global fig
    fig = plt.figure(figsize=(16, 10))
    update_plot()
    fig.canvas.mpl_connect('pick_event', on_pick)

current_strategy_lines = {}
strategy_data = load_all_strategies()
fig = None
create_figure_and_plot()
plt.show()
