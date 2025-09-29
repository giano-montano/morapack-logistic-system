import numpy as np

class DistributionGenerator:
    def __init__(self, seed=18112001):
        self.rng = np.random.default_rng(seed)
    
    def generate_normal(self, size=1000, mu=0, sigma=1):
        return self.rng.normal(mu, sigma, size)
    
    def generate_poisson(self, size=1000, lam=5):
        return self.rng.poisson(lam, size)
    
    def generate_uniform(self, size=1000, low=0, high=1):
        return self.rng.uniform(low, high, size)
    
    def generate_exponential(self, size=1000, scale=1.0):
        return self.rng.exponential(scale, size)
    
    def generate_dirichlet(self, alpha, size=1000):
        return self.rng.dirichlet(alpha)
    
    def generate_multinomial(self, n, probabilities, size=1000):
        return self.rng.multinomial(n, probabilities) #size can be problematic

    def generate_binomial(self, size, probability):
        return self.rng.binomial(size, probability)

if __name__ == "__main__":
    generator = DistributionGenerator()
