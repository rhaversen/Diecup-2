#!/usr/bin/env python3
"""
Parse optimizer logs (genetic or gradient) and trace parameter evolution over improvements.
Interactive live-updating plots that monitor log files in real-time.
Two windows: scrollable individual plots + fullscreen combined view.
"""
# pyright: reportAttributeAccessIssue=false
# pyright: reportOptionalMemberAccess=false
# pyright: reportArgumentType=false

import re
import sys
import matplotlib.pyplot as plt
from matplotlib.animation import FuncAnimation
from matplotlib.backends.backend_tkagg import FigureCanvasTkAgg
from pathlib import Path
import numpy as np

HAS_TK = False
try:
    import tkinter as tk
    from tkinter import ttk
    HAS_TK = True
except ImportError:
    tk = None  # type: ignore
    ttk = None  # type: ignore


class LogParser:
    """Incremental log file parser that tracks position and only parses new content."""
    
    def __init__(self, filepath):
        self.filepath = Path(filepath)
        self.file_position = 0
        self.improvements = []
        self.last_gen = 0
        self.last_fitness = None
        self.last_mean = None
        self.last_variance = None
        self.last_p95 = None
        self.last_max = None
        self.partial_line = ""
        
        # Regex pattern for genetic optimizer log format
        # "Gen 123 - Fit: 16.1234 (mean=19.42, var=9.50, p95=25.0, max=45)"
        # Numbers use comma OR period as decimal separator (e.g., 8,78 or 8.78)
        num = r'(\d+(?:[.,]\d+)?)'  # Matches: 123, 12.34, 12,34
        self.gen_pattern = re.compile(
            rf'Gen\s+{num}.*?Fit:\s*{num}\s*\(mean={num},\s*var={num}(?:,\s*p95={num},\s*max={num})?'
        )
        # Improvement markers
        self.improvement_marker = re.compile(
            r'\*\*\*\s*(IMPROVEMENT|New best|Confirmed improvement|Accepting candidate|NEW BEST|GLOBAL BEST|BASELINE)'
        )
        self.param_pattern = re.compile(r'^\s+(\w+) = ([-\d,\.]+)')
        
        # Parameter names in order
        self.param_names = [
            "OpportunityWeight", "RarityWeight", "ProgressWeight", "RarityScalar",
            "CollectionWeight", "CollectionScalar", "CompletionWeight", "CatchUpWeight",
            "DiceCostWeight", "VarianceWeight", "GameProgressWeight", "AllDiceBonusWeight",
            "RemainingValueWeight", "EfficiencyWeight", "CommitmentRiskWeight", "MultiCollectThreshold",
            "ContinuationWeight"
        ]
    
    @staticmethod
    def parse_number(s):
        """Parse number with comma as decimal separator."""
        # Replace comma decimal separator, then strip any trailing periods
        return float(s.replace(',', '.').rstrip('.'))
    
    def parse_incremental(self):
        """Parse only new content since last read. Returns True if new improvements found."""
        
        if not self.filepath.exists():
            return False
        
        new_improvements_count = len(self.improvements)
        
        with open(self.filepath, 'r', encoding='utf-8') as f:
            f.seek(self.file_position)
            new_content = f.read()
            self.file_position = f.tell()
        
        if not new_content:
            return False
        
        # Handle partial lines from previous read
        content = self.partial_line + new_content
        lines = content.split('\n')
        
        # Last line might be incomplete
        if not new_content.endswith('\n'):
            self.partial_line = lines[-1]
            lines = lines[:-1]
        else:
            self.partial_line = ""
        
        i = 0
        while i < len(lines):
            line = lines[i]
            
            # Check for generation line
            gen_match = self.gen_pattern.search(line)
            
            if gen_match:
                self.last_gen = int(gen_match.group(1))
                self.last_fitness = self.parse_number(gen_match.group(2))
                self.last_mean = self.parse_number(gen_match.group(3))
                self.last_variance = self.parse_number(gen_match.group(4))
                # P95 and max are optional (groups 5 and 6)
                self.last_p95 = self.parse_number(gen_match.group(5)) if gen_match.group(5) else None
                self.last_max = self.parse_number(gen_match.group(6)) if gen_match.group(6) else None
            
            # Check for improvement marker
            if self.improvement_marker.search(line):
                # Look for parameters in following lines
                params = {}
                j = i + 1
                while j < len(lines) and j < i + 20:
                    param_match = self.param_pattern.match(lines[j])
                    if param_match:
                        param_name = param_match.group(1)
                        param_value = self.parse_number(param_match.group(2))
                        params[param_name] = param_value
                    elif lines[j].strip() and not lines[j].startswith(' '):
                        break
                    j += 1
                
                if params:
                    self.improvements.append({
                        'generation': self.last_gen,
                        'fitness': self.last_fitness,
                        'mean': self.last_mean,
                        'variance': self.last_variance,
                        'p95': self.last_p95,
                        'max': self.last_max,
                        'params': params
                    })
            
            i += 1
        
        return len(self.improvements) > new_improvements_count


class ScrollablePlotter:
    """Tkinter-based scrollable plot window with fixed-height plots."""
    
    def __init__(self, parser, update_interval_ms=2000):
        self.parser = parser
        self.update_interval = update_interval_ms
        self.last_improvement_count = 0
        self.running = True  # Flag to stop updates on window close
        
        # Suppress matplotlib warning for many figures
        plt.rcParams['figure.max_open_warning'] = 50
        
        # Colors for plots
        self.colors = plt.cm.tab20(np.linspace(0, 1, len(parser.param_names)))  # type: ignore
        
        # Tkinter setup
        self.root = tk.Tk()
        self.root.title(f'Genetic Optimizer - {parser.filepath.name}')
        self.root.geometry('1000x800')
        
        # Create main container with two panes
        self.paned = ttk.PanedWindow(self.root, orient=tk.HORIZONTAL)
        self.paned.pack(fill=tk.BOTH, expand=True)
        
        # Store plot references (must init before setup methods)
        self.axes = {}
        self.lines = {}
        self.combined_lines = {}
        
        # Left pane: scrollable individual plots
        self.setup_scrollable_frame()
        
        # Right pane: combined view
        self.setup_combined_view()
        
        # Create all the plots
        self.create_plots()
        
    def setup_scrollable_frame(self):
        """Create scrollable frame for individual plots."""
        # Frame for scrollable content
        left_frame = ttk.Frame(self.paned)
        self.paned.add(left_frame, weight=1)
        
        # Canvas with scrollbar
        self.canvas = tk.Canvas(left_frame)
        scrollbar = ttk.Scrollbar(left_frame, orient=tk.VERTICAL, command=self.canvas.yview)
        self.scrollable_frame = ttk.Frame(self.canvas)
        
        self.scrollable_frame.bind(
            "<Configure>",
            lambda e: self.canvas.configure(scrollregion=self.canvas.bbox("all"))
        )
        
        self.canvas.create_window((0, 0), window=self.scrollable_frame, anchor="nw")
        self.canvas.configure(yscrollcommand=scrollbar.set)
        
        # Mouse wheel scrolling
        def on_mousewheel(event):
            self.canvas.yview_scroll(int(-1*(event.delta/120)), "units")
        self.canvas.bind_all("<MouseWheel>", on_mousewheel)
        
        scrollbar.pack(side=tk.RIGHT, fill=tk.Y)
        self.canvas.pack(side=tk.LEFT, fill=tk.BOTH, expand=True)
        
    def setup_combined_view(self):
        """Create combined view frame."""
        right_frame = ttk.Frame(self.paned)
        self.paned.add(right_frame, weight=1)
        
        # Combined plot figure
        self.combined_fig, self.combined_ax = plt.subplots(figsize=(8, 8))
        self.combined_fig.suptitle('All Parameters Combined', fontsize=18, fontweight='bold')
        
        self.combined_canvas = FigureCanvasTkAgg(self.combined_fig, master=right_frame)
        self.combined_canvas.draw()
        self.combined_canvas.get_tk_widget().pack(fill=tk.BOTH, expand=True)
        
        # Setup combined plot
        for idx, param in enumerate(self.parser.param_names):
            line, = self.combined_ax.plot([], [], '-', color=self.colors[idx], 
                                          linewidth=3, label=param, alpha=0.8)
            self.combined_lines[param] = line
        self.combined_ax.set_xlabel('Generation', fontsize=14)
        self.combined_ax.set_ylabel('Parameter Value', fontsize=14)
        self.combined_ax.tick_params(axis='both', labelsize=12)
        self.combined_ax.grid(True, alpha=0.3)
        self.combined_ax.legend(loc='upper left', fontsize=11, ncol=2)
        self.combined_ax.axhline(y=0, color='gray', linestyle='--', alpha=0.5, linewidth=2)
        
    def create_plots(self):
        """Create all individual plots in the scrollable frame."""
        plot_height = 2.5  # Fixed height for each plot
        plot_width = 8
        
        # Metrics to plot
        metrics = [
            ('fitness', 'Fitness (lower is better)', 'b'),
            ('mean', 'Mean Turns (lower is better)', 'r'),
            ('variance', 'Variance (lower is better)', 'm'),
            ('p95', 'P95 Turns (tail risk)', 'orange'),
            ('max', 'Max Turns (worst case)', 'brown'),
        ]
        
        # Create metric plots
        for metric, title, color in metrics:
            fig, ax = plt.subplots(figsize=(plot_width, plot_height))
            line, = ax.plot([], [], '-o', color=color, markersize=6, linewidth=2.5)
            ax.set_xlabel('Generation', fontsize=12)
            ax.set_ylabel(metric.capitalize(), fontsize=12)
            ax.set_title(title, fontsize=14, fontweight='bold')
            ax.tick_params(axis='both', labelsize=10)
            ax.grid(True, alpha=0.3)
            
            self.axes[metric] = ax
            self.lines[metric] = line
            
            canvas = FigureCanvasTkAgg(fig, master=self.scrollable_frame)
            canvas.draw()
            canvas.get_tk_widget().pack(fill=tk.X, padx=5, pady=5)
        
        # Create parameter plots
        for idx, param in enumerate(self.parser.param_names):
            fig, ax = plt.subplots(figsize=(plot_width, plot_height))
            line, = ax.plot([], [], '-o', color=self.colors[idx], markersize=6, linewidth=2.5)
            ax.axhline(y=0, color='gray', linestyle='--', alpha=0.5, linewidth=1.5)
            ax.set_xlabel('Generation', fontsize=12)
            ax.set_ylabel('Value', fontsize=12)
            ax.set_title(param, fontsize=14, fontweight='bold')
            ax.tick_params(axis='both', labelsize=10)
            ax.grid(True, alpha=0.3)
            
            self.axes[param] = ax
            self.lines[param] = line
            
            canvas = FigureCanvasTkAgg(fig, master=self.scrollable_frame)
            canvas.draw()
            canvas.get_tk_widget().pack(fill=tk.X, padx=5, pady=5)
    
    def update(self):
        """Update all plots with new data."""
        if not self.running:
            return
            
        self.parser.parse_incremental()
        
        improvements = self.parser.improvements
        if not improvements:
            if self.running:
                self.root.after(self.update_interval, self.update)
            return
        
        # Only update if we have new data
        if len(improvements) == self.last_improvement_count:
            if self.running:
                self.root.after(self.update_interval, self.update)
            return
        
        self.last_improvement_count = len(improvements)
        
        # Extract data
        generations = [imp['generation'] for imp in improvements]
        fitness_values = [imp['fitness'] for imp in improvements]
        mean_values = [imp['mean'] for imp in improvements if imp['mean'] is not None]
        variance_values = [imp['variance'] for imp in improvements if imp['variance'] is not None]
        p95_values = [imp['p95'] for imp in improvements if imp.get('p95') is not None]
        max_values = [imp['max'] for imp in improvements if imp.get('max') is not None]
        mean_gens = [imp['generation'] for imp in improvements if imp['mean'] is not None]
        var_gens = [imp['generation'] for imp in improvements if imp['variance'] is not None]
        p95_gens = [imp['generation'] for imp in improvements if imp.get('p95') is not None]
        max_gens = [imp['generation'] for imp in improvements if imp.get('max') is not None]
        
        # Update fitness plot
        self.lines['fitness'].set_data(generations, fitness_values)
        ax = self.axes['fitness']
        ax.relim()
        ax.autoscale_view()
        ax.set_title(f'Fitness (n={len(improvements)}, best={min(fitness_values):.4f})')
        ax.figure.canvas.draw_idle()
        
        # Update mean plot
        if mean_values:
            self.lines['mean'].set_data(mean_gens, mean_values)
            ax = self.axes['mean']
            ax.relim()
            ax.autoscale_view()
            ax.set_title(f'Mean Turns (best={min(mean_values):.2f})')
            ax.figure.canvas.draw_idle()
        
        # Update variance plot
        if variance_values:
            self.lines['variance'].set_data(var_gens, variance_values)
            ax = self.axes['variance']
            ax.relim()
            ax.autoscale_view()
            ax.set_title(f'Variance (best={min(variance_values):.2f})')
            ax.figure.canvas.draw_idle()
        
        # Update P95 plot
        if p95_values:
            self.lines['p95'].set_data(p95_gens, p95_values)
            ax = self.axes['p95']
            ax.relim()
            ax.autoscale_view()
            ax.set_title(f'P95 Turns (best={min(p95_values):.1f})')
            ax.figure.canvas.draw_idle()
        
        # Update max plot
        if max_values:
            self.lines['max'].set_data(max_gens, max_values)
            ax = self.axes['max']
            ax.relim()
            ax.autoscale_view()
            ax.set_title(f'Max Turns (best={min(max_values):.0f})')
            ax.figure.canvas.draw_idle()
        
        # Update parameter plots and combined view
        for param in self.parser.param_names:
            gens = []
            vals = []
            for imp in improvements:
                if param in imp['params']:
                    gens.append(imp['generation'])
                    vals.append(imp['params'][param])
            
            if gens:
                # Individual plot
                self.lines[param].set_data(gens, vals)
                ax = self.axes[param]
                ax.relim()
                ax.autoscale_view()
                ax.set_title(f'{param} = {vals[-1]:.3f}')
                ax.figure.canvas.draw_idle()
                
                # Combined plot
                self.combined_lines[param].set_data(gens, vals)
        
        # Update combined plot
        self.combined_ax.relim()
        self.combined_ax.autoscale_view()
        self.combined_fig.suptitle(f'All Parameters Combined (Gen {generations[-1]}, n={len(improvements)})', fontsize=14)
        self.combined_canvas.draw_idle()
        
        # Schedule next update
        if self.running:
            self.root.after(self.update_interval, self.update)
    
    def on_closing(self):
        """Handle window close event."""
        self.running = False
        self.root.destroy()
    
    def run(self):
        """Start the application."""
        # Set up close handler
        self.root.protocol("WM_DELETE_WINDOW", self.on_closing)
        
        # Do initial parse and update
        self.parser.parse_incremental()
        self.update()
        
        # Start main loop
        self.root.mainloop()


class SimplePlotter:
    """Fallback matplotlib-only plotter if Tkinter is not available."""
    
    def __init__(self, parser, update_interval_ms=2000):
        self.parser = parser
        self.update_interval = update_interval_ms
        self.last_improvement_count = 0
        self.colors = plt.cm.tab20(np.linspace(0, 1, len(parser.param_names)))  # type: ignore
        
    def run(self):
        """Run with two separate matplotlib windows."""
        # Window 1: Combined view (fullscreen)
        self.fig_combined = plt.figure(figsize=(14, 10))
        self.fig_combined.canvas.manager.set_window_title('Combined Parameters View')  # type: ignore
        self.ax_combined = self.fig_combined.add_subplot(111)
        
        self.combined_lines = {}
        for idx, param in enumerate(self.parser.param_names):
            line, = self.ax_combined.plot([], [], '-', color=self.colors[idx], 
                                          linewidth=3, label=param, alpha=0.8)
            self.combined_lines[param] = line
        self.ax_combined.set_xlabel('Generation', fontsize=14)
        self.ax_combined.set_ylabel('Parameter Value', fontsize=14)
        self.ax_combined.set_title('All Parameters Combined', fontsize=16, fontweight='bold')
        self.ax_combined.tick_params(axis='both', labelsize=12)
        self.ax_combined.grid(True, alpha=0.3)
        self.ax_combined.legend(loc='upper left', fontsize=11, ncol=2)
        self.ax_combined.axhline(y=0, color='gray', linestyle='--', alpha=0.5, linewidth=2)
        
        # Window 2: Individual plots (scrollable via matplotlib)
        n_plots = 5 + len(self.parser.param_names)  # fitness, mean, variance, p95, max + params
        fig_height = n_plots * 2.5
        self.fig_individual, self.axes = plt.subplots(n_plots, 1, figsize=(10, fig_height))
        self.fig_individual.canvas.manager.set_window_title('Individual Metrics & Parameters')  # type: ignore
        
        self.lines = {}
        
        # Fitness, mean, variance, p95, max
        metrics = [
            ('fitness', 'Fitness', 'b'), 
            ('mean', 'Mean Turns', 'r'), 
            ('variance', 'Variance', 'm'),
            ('p95', 'P95 Turns', 'orange'),
            ('max', 'Max Turns', 'brown')
        ]
        for i, (key, title, color) in enumerate(metrics):
            ax = self.axes[i]
            self.lines[key], = ax.plot([], [], f'{color}' if isinstance(color, str) and len(color) > 1 else f'{color}-o', 
                                       markersize=6, linewidth=2.5, marker='o')
            ax.set_xlabel('Generation', fontsize=12)
            ax.set_ylabel(key.capitalize(), fontsize=12)
            ax.set_title(title, fontsize=14, fontweight='bold')
            ax.tick_params(axis='both', labelsize=10)
            ax.grid(True, alpha=0.3)
        
        # Parameters
        for idx, param in enumerate(self.parser.param_names):
            ax = self.axes[idx + 5]
            self.lines[param], = ax.plot([], [], '-o', color=self.colors[idx], markersize=6, linewidth=2.5)
            ax.axhline(y=0, color='gray', linestyle='--', alpha=0.5, linewidth=1.5)
            ax.set_xlabel('Generation', fontsize=12)
            ax.set_ylabel('Value', fontsize=12)
            ax.set_title(param, fontsize=14, fontweight='bold')
            ax.tick_params(axis='both', labelsize=10)
            ax.grid(True, alpha=0.3)
        
        plt.tight_layout()
        
        # Animation
        def update(frame):
            self.parser.parse_incremental()
            improvements = self.parser.improvements
            
            if not improvements or len(improvements) == self.last_improvement_count:
                return
            
            self.last_improvement_count = len(improvements)
            generations = [imp['generation'] for imp in improvements]
            
            # Update metrics
            metrics_keys = ['fitness', 'mean', 'variance', 'p95', 'max']
            for idx, key in enumerate(metrics_keys):
                vals = [imp[key] for imp in improvements if imp.get(key) is not None]
                gens = [imp['generation'] for imp in improvements if imp.get(key) is not None]
                if vals:
                    self.lines[key].set_data(gens, vals)
                    self.axes[idx].relim()
                    self.axes[idx].autoscale_view()
            
            # Update parameters
            for idx, param in enumerate(self.parser.param_names):
                gens = [imp['generation'] for imp in improvements if param in imp['params']]
                vals = [imp['params'][param] for imp in improvements if param in imp['params']]
                if gens:
                    self.lines[param].set_data(gens, vals)
                    self.axes[idx + 5].relim()
                    self.axes[idx + 5].autoscale_view()
                    self.combined_lines[param].set_data(gens, vals)
            
            self.ax_combined.relim()
            self.ax_combined.autoscale_view()
            self.ax_combined.set_title(f'All Parameters Combined (Gen {generations[-1]})')
            
            self.fig_combined.canvas.draw_idle()
            self.fig_individual.canvas.draw_idle()
        
        self.parser.parse_incremental()
        update(0)
        
        ani1 = FuncAnimation(self.fig_combined, update, interval=self.update_interval, blit=False, cache_frame_data=False)  # type: ignore
        ani2 = FuncAnimation(self.fig_individual, lambda f: None, interval=self.update_interval, blit=False, cache_frame_data=False)  # type: ignore
        
        plt.show()


def print_summary(improvements):
    """Print a summary of parameter evolution."""
    
    if not improvements:
        print("No improvements found.")
        return
    
    print(f"\n{'='*60}")
    print(f"Found {len(improvements)} improvements")
    print(f"{'='*60}\n")
    
    # Print last few improvements
    recent = improvements[-5:] if len(improvements) > 5 else improvements
    print(f"Most recent {len(recent)} improvements:\n")
    
    for i, imp in enumerate(recent):
        idx = len(improvements) - len(recent) + i + 1
        p95_str = f", P95: {imp['p95']:.1f}" if imp.get('p95') is not None else ""
        max_str = f", Max: {imp['max']:.0f}" if imp.get('max') is not None else ""
        print(f"Improvement {idx} (Gen {imp['generation']}, Fitness: {imp['fitness']:.4f}, "
              f"Mean: {imp.get('mean', 'N/A')}, Var: {imp.get('variance', 'N/A')}{p95_str}{max_str})")
        for param, value in sorted(imp['params'].items()):
            print(f"  {param:25s} = {value:8.4f}")
        print()
    
    # Print parameter ranges
    print(f"{'='*60}")
    print("Parameter Ranges Across All Improvements")
    print(f"{'='*60}\n")
    
    all_params = set()
    for imp in improvements:
        all_params.update(imp['params'].keys())
    
    for param in sorted(all_params):
        values = [imp['params'][param] for imp in improvements if param in imp['params']]
        if values:
            print(f"{param:25s}: min={min(values):8.4f}, max={max(values):8.4f}, "
                  f"first={values[0]:8.4f}, last={values[-1]:8.4f}")


def main():
    # Default to most recent log file
    log_dir = Path(__file__).parent / "logs"
    
    if len(sys.argv) > 1:
        log_file = Path(sys.argv[1])
    else:
        # Find most recent log file
        log_files = sorted(log_dir.glob("genetic_optimizer_*.txt"))
        if not log_files:
            print("No log files found in logs/ directory")
            print("Usage: python parse_logs.py [log_file.txt]")
            sys.exit(1)
        log_file = log_files[-1]
    
    print(f"Monitoring: {log_file}")
    print("Plot will update automatically as new data arrives...")
    print("Close the plot window to exit.\n")
    
    parser = LogParser(log_file)
    
    # Use Tkinter-based scrollable plotter if available, otherwise fallback
    if HAS_TK:
        print("Using Tkinter-based scrollable view with combined parameter window")
        plotter = ScrollablePlotter(parser, update_interval_ms=2000)
    else:
        print("Tkinter not available, using matplotlib fallback")
        plotter = SimplePlotter(parser, update_interval_ms=2000)
    
    plotter.run()
    
    # Print final summary when window is closed
    print_summary(parser.improvements)


if __name__ == "__main__":
    main()
