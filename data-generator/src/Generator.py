import numpy as np
from datetime import datetime, timedelta

def softmax(x):
    exp_x = np.exp(x - np.max(x))
    return exp_x / np.sum(exp_x)

class Generator:
    data_path = "./data-generator/data/"

    def __init__(self, 
                 products_per_day_function,
                 storages_popularity,
                 random_generator,
                 n_days=360,
                 n_storages=30,
                 persistence=0.5,
                 latent_noise=0.5,
                 popularity_noise=250,
                 average_order_size=10,
                 order_noise=250,
                 timestamp_mean=720,
                 timestamp_deviation=200
                 ):
        if len(storages_popularity) == n_storages:
            #constants
            self.n_days = n_days
            self.n_storages = n_storages
            self.storages_popularity = storages_popularity
            self.persistence = persistence
            self.latent_noise = latent_noise
            self.popularity_noise = popularity_noise
            self.average_order_size = average_order_size
            self.order_noise = order_noise
            self.timestamp_mean = timestamp_mean
            self.timestamp_deviation = timestamp_deviation

            #generated data
            self.latent_scores = np.zeros((n_days + 1, n_storages))
            self.products_by_day = np.zeros((n_days + 1, n_storages))
            self.orders_by_day = np.empty((n_days + 1, n_storages), dtype=object)
            self.orders_schedule_by_day = np.empty((n_days + 1, n_storages), dtype=object)
            self.final_orders = [] 
            
            #functions
            self.random_generator = random_generator
            self.function_products_per_day = products_per_day_function
            
        else:
            raise ValueError("shape of storages_popularity must be equal to n_storages")

        
    def calculate_latent_scores(self,
                                log_storages_popularity,
                                yesterday_latent_scores):
        latent_scores = np.zeros(self.n_storages)
        noise = self.random_generator.generate_noise(self.n_storages, self.latent_noise)

        latent_scores = (1 - self.persistence) * log_storages_popularity + self.persistence * yesterday_latent_scores + noise

        return latent_scores
    
    def add_popularity_noise(self,
                             probabilities):
        alpha_probabilities = self.popularity_noise * probabilities
        daily_storages_popularity_probability = self.random_generator.generate_dirichlet(alpha_probabilities, 1)
        
        return daily_storages_popularity_probability


    def generate_products_by_day(self,
                                 t):
        self.latent_scores[0] = np.log(self.storages_popularity)
        log_storages_popularity = np.log(self.storages_popularity)

        n_products = self.function_products_per_day(t)
        self.latent_scores[t] = self.calculate_latent_scores(log_storages_popularity, 
                                                             self.latent_scores[t-1])
        daily_storages_popularity_probability = softmax(self.latent_scores[t])
        daily_storages_popularity_probability = self.add_popularity_noise(daily_storages_popularity_probability)
        daily_products = self.random_generator.generate_multinomial(n_products, daily_storages_popularity_probability, 1)
        
        return daily_products
    
    def generate_n_orders(self,
                          products):
        if(products == 1):
            n_orders = 1
        else:

            cut_probability = (products / float(self.average_order_size) - 1.0) / (products - 1)
            cut_probability = float(np.clip(cut_probability, 0.0, 1.0))
            cuts = self.random_generator.generate_binomial(products -1, cut_probability)
            n_orders = cuts + 1

        return n_orders

    def generate_orders_by_storage(self,
                                   products,
                                   n_orders):
        alpha_proportion = np.full(n_orders, 1.0 / n_orders)
        orders_proportion = self.random_generator.generate_dirichlet(self.order_noise * alpha_proportion, 1)
        orders_sizes = np.floor(orders_proportion * products).astype(int) 
        leftover = products - orders_sizes.sum()
        
        if leftover > 0:
            orders_sizes[0] += leftover
        elif leftover < 0:
            raise ValueError("the leftover is negative")
        
        return orders_sizes

    def generate_timestamps(self, n_orders):
        orders_timestamp = self.random_generator.generate_normal(n_orders, self.timestamp_mean, self.timestamp_deviation)
        orders_timestamp = np.clip(orders_timestamp, 0, 1440) 
        orders_timestamp = np.rint(orders_timestamp).astype(int)
        orders_timestamp.sort()
        base = datetime(2000, 1, 1)
        timestamps = np.array([(base + timedelta(minutes=int(m))).time() for m in orders_timestamp])

        return timestamps


    def move_forward_in_time(self):
        for day in range(1, self.n_days + 1):
            orders_in_a_day = []
            self.products_by_day[day] = self.generate_products_by_day(day)

            for storage, products_by_storage in enumerate(self.products_by_day[day]):
                if(products_by_storage == 0):
                    n_orders = 0
                    orders_by_storage = np.zeros(0)
                    orders_schedules_by_storage = np.zeros(0)
                else:
                    n_orders = self.generate_n_orders(products_by_storage)
                    orders_by_storage = self.generate_orders_by_storage(products_by_storage, n_orders)
                    orders_schedules_by_storage = self.generate_timestamps(n_orders)

                if(len(orders_by_storage) == len(orders_schedules_by_storage) == n_orders):
                    for (_,order_size), (_,order_timestamp) in zip(enumerate(orders_by_storage), enumerate(orders_schedules_by_storage)):
                        orders_in_a_day.append((day, order_timestamp, storage, order_size))
                else:
                    raise ValueError("orders_by_storage and orders_schedules_by_storage doesn't match")
                
                self.orders_by_day[day, storage] = orders_by_storage
                self.orders_schedule_by_day[day, storage] = orders_schedules_by_storage

            orders_in_a_day.sort(key=lambda x: x[1])
            self.final_orders.extend(orders_in_a_day)

    def print_data(self, file_name="default_name.txt"):
        with open(self.data_path + file_name, "w") as file:
            for (day, order_timestamp, storage, order_size) in self.final_orders:
                timestr = order_timestamp.strftime('%H-%M') if hasattr(order_timestamp, "strftime") else str(order_timestamp)
                line = f"{day}-{timestr}-{storage}-{order_size}-0000001"
                file.write(line + "\n") 
                

if __name__ == "__main__":

    def products_per_day_function(t):
        products = 400 + pow(t,2)
    
        return products

    def generate_base_storages_popularity(n_storages):
        storages_popularity = np.random.normal(0, 1, n_storages)
        storages_popularity = softmax(storages_popularity)
        
        return storages_popularity
    
    n_days=360
    n_storages=30
    storages_popularity = generate_base_storages_popularity(5)





