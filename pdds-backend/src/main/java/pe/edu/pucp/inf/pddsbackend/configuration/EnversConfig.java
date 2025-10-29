package pe.edu.pucp.inf.pddsbackend.configuration;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.envers.repository.config.EnableEnversRepositories;
import org.springframework.transaction.annotation.EnableTransactionManagement;

@Configuration
@EnableEnversRepositories(basePackages = "pe.edu.pucp.inf.pddsbackend.repositories")
@EnableTransactionManagement
public class EnversConfig {
    // si necesitas customizar, aquí va
}
