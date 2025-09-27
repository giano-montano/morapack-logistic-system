import numpy as np
import time

from Plotter import Plotter
from DistributionGenerator import DistributionGenerator
from Generator import Generator
from Generator import softmax

def orders_per_day_function(t):
    orders = 400 + pow(t,2)

    return orders

def generate_base_storages_popularity(n_storages, random_generator):
    storages_popularity = random_generator.generate_normal(n_storages, 0, 1)
    storages_popularity = softmax(storages_popularity)
    print(storages_popularity)
    return storages_popularity

def main():
    seed = int(time.time()); print(seed)
    random_generator = DistributionGenerator(1758960033)
    plotter = Plotter()

    n_days=20
    n_storages=5
    storages_popularity = generate_base_storages_popularity(n_storages, random_generator)

    generator = Generator(orders_per_day_function,
                          storages_popularity,
                          random_generator,
                          n_days=n_days,
                          n_storages=n_storages)

    generator.generate_orders_by_day()
    plotter.show_orders_by_storage_distribution(generator.orders_by_day)
    plotter.show_leadership_over_time(generator.orders_by_day)

if __name__ == "__main__":
    main()


