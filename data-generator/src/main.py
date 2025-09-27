import numpy as np
import time

from Plotter import Plotter
from DistributionGenerator import DistributionGenerator
from Generator import Generator
from Generator import softmax

def products_per_day_function(t):
    products = 400 + pow(t,2)

    return products

def generate_base_storages_popularity(n_storages, random_generator):
    storages_popularity = random_generator.generate_normal(n_storages, 0, 1)
    storages_popularity = softmax(storages_popularity)
    print("Main: storage popularity ", storages_popularity)
    return storages_popularity

def main():
    seed = int(time.time()); print("Main: seed ", seed)
    random_generator = DistributionGenerator(seed) #1758960033
    plotter = Plotter()

    n_days=20
    n_storages=5
    storages_popularity = generate_base_storages_popularity(n_storages, random_generator)

    generator = Generator(products_per_day_function,
                          storages_popularity,
                          random_generator,
                          n_days=n_days,
                          n_storages=n_storages)

    generator.move_forward_in_time()

    plotter.show_products_by_storage_distribution(generator.products_by_day)
    plotter.show_leadership_over_time(generator.products_by_day)

if __name__ == "__main__":
    main()
   
