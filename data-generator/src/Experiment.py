import numpy as np
import time
from scipy.stats import ttest_rel, t

from DistributionGenerator import DistributionGenerator
from Generator import Generator

data_path = "./data-generator/data/evaluations/"

def parse_fitness(n_values,file_name="cell.txt"):
    fitness_k1 = np.full(n_values, 0.0)
    fitness_k2 = np.full(n_values, 0.0)
    index = 0

    with open(data_path + file_name, "r",) as file:
        for line in file:
            line = line.split()
            
            fitness_k1[index] = float(line[0])
            fitness_k2[index] = float(line[1])
            index +=  1

    if(index != n_values):
        raise ValueError("index is different from 0.0")

    return fitness_k1, fitness_k2

def create_dataset_for_experiment_cell(cell, base_products, average_order_size):
    #HYPERPARAMETERS
    products_per_day_function = lambda t: base_products + t**0
    n_days=1
    n_storages=30
    storages_popularity = np.full(n_storages, 1.0/float(n_storages))

    for experiment in range(0, 100):
        #SEED
        seed = int(time.time_ns())
        random_generator = DistributionGenerator(seed)

        generator = Generator(products_per_day_function,
                            storages_popularity,
                            random_generator,
                            n_days=n_days,
                            n_storages=n_storages,
                            persistence=0,         
                            latent_noise=0,        
                            popularity_noise=100000,    
                            average_order_size=average_order_size,
                            order_noise=250,
                            timestamp_mean=720,
                            timestamp_deviation=200)
        
        #SYNTHETIC DATA GENERATION ITSELF
        generator.move_forward_in_time()
        
        #WRITE SYNTHETIC DATA TO A FILE
        instances_name = "./cell_" + cell + "/" + cell + "_exp-" + str(experiment) + "_seed-" + str(seed) + "_days-" + str(n_days) + "_storages-" + str(n_storages)
        generator.print_data(file_name= instances_name + ".txt")

        del generator
        del random_generator

def create_experiment_cell():
    base_products = [340, 360]
    average_order_size = [10, 70, 120]

    index = 1
    for bp in base_products:
        for aos in average_order_size:
            create_dataset_for_experiment_cell(str(index), bp, aos)
            index += 1

    print("Summary")

    index = 1
    for bp in base_products:
        for aos in average_order_size:
            print (f"Cell {index} with base_products: {bp}, average_order_size: {aos}")
            index += 1

class Experiment:
    def __init__(self, experiment_units, experiment_file_name="cell.txt"):
        self.fitness_k1, self.fitness_k2 = parse_fitness(experiment_units, experiment_file_name)


    def perform_paired_t_test(self, alpha=0.05, side="two-sided"):
        x = np.asarray(self.fitness_k1, float); y = np.asarray(self.fitness_k2, float)
        d = x - y
        mask = ~np.isnan(d)
        d = d[mask]
        n = d.size
        mean_d = d.mean()
        sd_d = d.std(ddof=1)
        se = sd_d / np.sqrt(n)
        t_stat, p_two = ttest_rel(x, y, nan_policy='omit')
        df = n - 1

        if side == "greater":   # H1: mean(x) > mean(y)
            p = t.sf(t_stat, df)
        elif side == "less":    # H1: mean(x) < mean(y)
            p = t.cdf(t_stat, df)
        else:
            p = p_two

        tcrit = t.ppf(1 - alpha/2, df)
        ci_low, ci_high = mean_d - tcrit*se, mean_d + tcrit*se

        dz = mean_d / sd_d  

        return dict(n=n, mean_diff=mean_d, t=t_stat, df=df, p=p,
                    ci=(ci_low, ci_high), cohens_dz=dz, se=se)

    def print_paired_ttest_summary(self, res: dict, alpha: float = 0.05, side: str = "two-sided"):
        n        = int(res["n"])
        mean_diff= float(res["mean_diff"])   # this is mean(x - y)
        t_stat   = float(res["t"])
        df       = int(res["df"])
        p        = float(res["p"])
        se       = float(res["se"])
        dz       = float(res["cohens_dz"])
        ci_low, ci_high = map(float, res["ci"])

        # Minimization: compute which has the lower mean
        mean_k1 = float(np.nanmean(self.fitness_k1))
        mean_k2 = float(np.nanmean(self.fitness_k2))
        winner  = "fitness_k1" if mean_k1 < mean_k2 else "fitness_k2"
        delta   = mean_k1 - mean_k2  # <0 -> k1 lower; >0 -> k2 lower

        direction = "two-sided" if side == "two-sided" else ("greater (x>y)" if side=="greater" else "less (x<y)")
        sig = "SIGNIFICANT" if p <= alpha else "not significant"

        print("Paired t-test (paired samples)")
        print("-" * 34)
        print(f"n (pairs):         {n}")
        print(f"df:                {df}")
        print(f"H1 (alternative):  {direction}")
        print()
        print(f"Mean difference:   {mean_diff:.6f}  (x - y)")
        print(f"Standard error:    {se:.6f}")
        print(f"Cohen's d_z:       {dz:.3f}")
        print(f"95% CI:            [{ci_low:.6f}, {ci_high:.6f}]")
        print()
        print(f"t-statistic:       {t_stat:.4f}")
        print(f"p-value:           {p}")
        print(f"Decision (α={alpha}): {sig.upper()}")
        print()
        print(f"Means:  mean(fitness_k1) = {mean_k1:.6f}   |   mean(fitness_k2) = {mean_k2:.6f}")

        if p <= alpha:
            # Statistically supported winner (minimization goal)
            print(f"→ Minimization winner: {winner} (lower mean). Δ = mean(k1) - mean(k2) = {delta:+.6f}.")
        else:
            # Descriptive winner only
            print(f"→ Lower mean (descriptive, not significant at α={alpha}): {winner}. "
                f"Δ = mean(k1) - mean(k2) = {delta:+.6f}.")

if __name__ == "__main__":
    ex = Experiment(100, "cell_6.txt")
    xdd = ex.perform_paired_t_test()
    ex.print_paired_ttest_summary(xdd)
 