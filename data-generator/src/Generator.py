import numpy as np

def softmax(x):
    exp_x = np.exp(x - np.max(x))
    return exp_x / np.sum(exp_x)

class Generator:
    def __init__(self, 
                 orders_per_day_function,
                 storages_popularity,
                 random_generator,
                 n_days=360,
                 n_storages=30,
                 persistence=0.5,
                 latent_noise=0.5,
                 popularity_noise=250
                 ):
        if len(storages_popularity) == n_storages:
            #generated data
            self.n_days = n_days
            self.n_storages = n_storages
            self.orders_by_day = np.zeros((n_days + 1, n_storages))

            #constants
            self.storages_popularity = storages_popularity
            self.persistence = persistence
            self.latent_noise = latent_noise
            self.popularity_noise = popularity_noise

            #functions
            self.random_generator = random_generator
            self.function_orders_per_day = orders_per_day_function

            #time depending
            self.t = 1
            self.latent_scores = np.zeros((n_days + 1, n_storages))
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


    def generate_orders(self,
                        t):
        self.latent_scores[0] = np.log(self.storages_popularity)
        log_storages_popularity = np.log(self.storages_popularity)

        n_orders = self.function_orders_per_day(t)
        self.latent_scores[t] = self.calculate_latent_scores(log_storages_popularity, 
                                                             self.latent_scores[t-1])
        daily_storages_popularity_probability = softmax(self.latent_scores[t])
        daily_storages_popularity_probability = self.add_popularity_noise(daily_storages_popularity_probability)
        daily_orders = self.random_generator.generate_multinomial(n_orders, daily_storages_popularity_probability, 1)
        
        return daily_orders
    
    def generate_orders_by_day(self):
        for t in range(1, self.n_days + 1):
            self.orders_by_day[t] = self.generate_orders(t)
            #allocate orders on a 24h-range

if __name__ == "__main__":

    def orders_per_day_function(t):
        orders = 400 + pow(t,2)
    
        return orders

    def generate_base_storages_popularity(n_storages):
        storages_popularity = np.random.normal(0, 1, n_storages)
        storages_popularity = softmax(storages_popularity)
        
        return storages_popularity
    
    n_days=360
    n_storages=30
    storages_popularity = generate_base_storages_popularity(5)

    generator = Generator(orders_per_day_function,
                          storages_popularity,
                          n_days=2,
                          n_storages=5)

    generator.generate_orders_by_day()



