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
            #create_dataset_for_experiment_cell(str(index), bp, aos)
            index += 1

    print("Summary")

    index = 1
    for bp in base_products:
        for aos in average_order_size:
            print (f"Cell {index} with base_products: {bp}, average_order_size: {aos}")
            index += 1


if __name__ == "__main__":
    create_experiment_cell()
    print("hellow world")
    

    #ANALYSIS OF THE INSTANCE 
    #plotter = Plotter()
    #plotter.show_products_by_storage_distribution(generator.products_by_day, instances_name)
    #plotter.show_leadership_over_time(generator.products_by_day)

    #FOR LATER
    #storages_popularity = generate_base_storages_popularity(n_storages, random_generator)
