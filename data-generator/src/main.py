import numpy as np
import time

from Plotter import Plotter
from DistributionGenerator import DistributionGenerator
from Generator import Generator
from Generator import softmax


def generate_base_storages_popularity(n_storages, random_generator):
    storages_popularity = random_generator.generate_normal(n_storages, 0, 1)
    storages_popularity = softmax(storages_popularity)
    print("Main: storage popularity ", storages_popularity)
    return storages_popularity

def main():
    #SEED
    seed = int(time.time())
    random_generator = DistributionGenerator(seed)

    #HYPERPARAMETERS
    products_per_day_function = lambda t: 360 + t**0
    n_days=1
    n_storages=30
    storages_popularity = np.full(n_storages, 1.0/float(n_storages))

    generator = Generator(products_per_day_function,
                          storages_popularity,
                          random_generator,
                          n_days=n_days,
                          n_storages=n_storages,
                          persistence=0.5,
                          latent_noise=0.5,
                          popularity_noise=250,
                          average_order_size=10,
                          order_noise=250,
                          timestamp_mean=720,
                          timestamp_deviation=200)

    #SYNTHETIC DATA GENERATION ITSELF
    generator.move_forward_in_time()

    #WRITE SYNTHETIC DATA TO A FILE
    generator.print_data(file_name= "seed-" + str(seed) + "_days-" + str(n_days) + "_storages-" + str(n_storages) + ".txt")
    
    #FOR LATER
    #storages_popularity = generate_base_storages_popularity(n_storages, random_generator)
    #plotter = Plotter()
    #plotter.show_products_by_storage_distribution(generator.products_by_day)
    #plotter.show_leadership_over_time(generator.products_by_day)

if __name__ == "__main__":
    main()


   
