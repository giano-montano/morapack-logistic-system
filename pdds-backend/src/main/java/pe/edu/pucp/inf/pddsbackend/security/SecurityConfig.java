package pe.edu.pucp.inf.pddsbackend.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;

@Configuration
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                // Puedes ajustar lo que sigue según tu arquitectura (REST, web, etc.)
                .csrf(csrf -> csrf.disable())  // si es API, a menudo desactivas CSRF para poder usar POST/PUT/etc. sin token de sesión
                .authorizeHttpRequests(authz -> authz
                        .anyRequest().permitAll()  // permitir todas las peticiones por ahora
                );
        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        // DelegatingPasswordEncoder, acepta varios formatos, útil si cambias codificación más adelante
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }

    // Aquí podrías agregar un UserDetailsService más adelante, cuando ya tengas entidad de usuario
}
