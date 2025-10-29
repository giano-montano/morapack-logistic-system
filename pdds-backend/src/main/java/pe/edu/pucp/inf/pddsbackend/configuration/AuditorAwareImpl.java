package pe.edu.pucp.inf.pddsbackend.configuration;

import org.springframework.data.domain.AuditorAware;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
public class AuditorAwareImpl implements AuditorAware<String> {
    @Override
    public Optional<String> getCurrentAuditor() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated() && !(auth instanceof AnonymousAuthenticationToken)) {
            // usuario autenticado: devuelve el username o como lo definas
            return Optional.of(auth.getName());
        }
        // usuario NO autenticado: usar la IP si está disponible
        String ip = ClientIpFilter.getClientIp();
        if (ip != null && !ip.isBlank()) {
            return Optional.of(ip);
        }
        // fallback
        return Optional.of("system");
    }
}