import os
import numpy as np
import matplotlib
import matplotlib.pyplot as plt

class Plotter:
    images_path = "./data-generator/img/"

    def __init__(self):
        os.environ["MPLBACKEND"] = "Agg"
        matplotlib.use("Agg")

    #general methods
    def save_plot(self,
                  figure,
                  file_name="default_name.png"):
        path = self.images_path + file_name
        figure.savefig(path, bbox_inches="tight", dpi=120)
        plt.close(figure)
        print(f"Saved plot to {path}")

    def plot_distribution_continuous(self,
                                     data,
                                     file_name,
                                     bins=30,
                                     title="Distribution"):
        data = np.asarray(data)

        figure, axis = plt.subplots(figsize=(8, 5))
        axis.hist(data, bins=bins, edgecolor="black", alpha=0.85)

        axis.set_title(title)
        axis.set_xlabel("Value")
        axis.set_ylabel("Frequency")
        axis.grid(True, linestyle="--", alpha=0.5)

        self.save_plot(figure, file_name)

    def plot_distribution_discrete(self,
                                   data_y,
                                   file_name,
                                   title="Distribution"):
        data_y = np.asarray(data_y)
        data_x = np.arange(len(data_y)) + 1
        
        figure, axis = plt.subplots(figsize=(8, 5))
        axis.bar(data_x, data_y, edgecolor="black", alpha=0.85)

        axis.set_title(title)
        axis.set_xlabel("Value")
        axis.set_ylabel("Probability")
        axis.grid(True, linestyle="--", alpha=0.5)

        self.save_plot(figure, file_name)

    #specific methods
    def show_orders_by_storage_distribution(self,
                                            orders_by_day):
        orders_by_storage = np.sum(orders_by_day, axis=0)
        orders_by_storage = orders_by_storage / np.sum(orders_by_storage)
        print(orders_by_storage)
        self.plot_distribution_discrete(orders_by_storage, "orders_by_storage.png", title="Orders (probability) in overall time by storage")
    
    def show_leadership_over_time(
        self,
        orders_by_day,
        file_name="leadership_bump.png",
        top_k=None,
        labels=None,
        title="Leadership (Rank) Over Time",
        connect=True,          
        show_points=True):
        orders_by_day = np.asarray(orders_by_day)
        T, M = orders_by_day.shape

        # ranks per day (1 = leader)
        ranks = np.zeros_like(orders_by_day, dtype=int)
        for t in range(T):
            order = np.argsort(-orders_by_day[t])
            r = np.empty(M, dtype=int)
            r[order] = np.arange(1, M + 1)
            ranks[t] = r

        # optional: reduce clutter to top_k storages by total volume
        if top_k is not None and 1 <= top_k < M:
            totals = orders_by_day.sum(axis=0)
            keep = np.argsort(-totals)[:top_k]
            ranks = ranks[:, keep]
            M = top_k
            labels = [f"S{i+1}" for i in keep] if labels is None else [labels[i] for i in keep]
        else:
            labels = [f"S{i+1}" for i in range(M)] if labels is None else labels

        # discrete day axis
        x = np.arange(1, T + 1)

        fig, ax = plt.subplots(figsize=(max(6, T*0.9), 6))

        for j in range(M):
            y = ranks[:, j]
            if connect and T > 1:
                ax.plot(x, y, linewidth=2, label=labels[j])
            if show_points:
                ax.scatter(x, y, s=40)

        # discrete ticks only
        ax.set_xticks(x)
        ax.set_xlim(0.5, T + 0.5)
        ax.set_xlabel("Day")
        ax.set_ylabel("Rank (1 = leader)")
        ax.set_title(title)

        # rank 1 at top; tidy y-axis
        ax.invert_yaxis()
        ax.set_ylim(0.5, M + 0.5)
        ax.set_yticks(np.arange(1, M + 1))

        # grid only on ranks to emphasize discrete positions
        ax.grid(axis="y", linestyle="--", alpha=0.5)
        ax.grid(axis="x", visible=False)

        if M <= 12:
            ax.legend(loc="best", frameon=False)

        # annotate endpoints (helps when only 2 days)
        if M <= 15:
            ax.annotate("start", (x[0], 1), xytext=(-8, -14), textcoords="offset points")
            ax.annotate("end",   (x[-1], 1), xytext=(8, -14), textcoords="offset points")
            for j in range(M):
                ax.text(x[0] - 0.15, ranks[0, j], labels[j], va="center", ha="right")
                ax.text(x[-1] + 0.15, ranks[-1, j], labels[j], va="center", ha="left")

        self.save_plot(fig, file_name)


if __name__ == "__main__":
    plotter = Plotter()
    arr = np.random.normal(0, 1, 1000)
    plotter.plot_distribution_continuous(arr, file_name="test.png", bins=40, title="Normal Distribution Example")
