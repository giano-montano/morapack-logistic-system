import numpy as np
from scipy.stats import ttest_rel, t
import os


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

class Experiment:
    def __init__(self, experiment_units, experiment_file_name="cell.txt"):
        self.fitness_k1, self.fitness_k2 = parse_fitness(experiment_units)


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
        mean_diff= float(res["mean_diff"])
        t_stat   = float(res["t"])
        df       = int(res["df"])
        p        = float(res["p"])
        se       = float(res["se"])
        dz       = float(res["cohens_dz"])
        ci_low, ci_high = map(float, res["ci"])

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

        if p <= alpha:
            effect = "positive" if mean_diff > 0 else "negative"
            print(f"→ Evidence that the mean paired difference is {effect} and non-zero.")
        else:
            print("→ Insufficient evidence to conclude a non-zero mean paired difference.")

if __name__ == "__main__":
    ex = Experiment(5)
    xdd = ex.perform_paired_t_test()
    ex.print_paired_ttest_summary(xdd)
 