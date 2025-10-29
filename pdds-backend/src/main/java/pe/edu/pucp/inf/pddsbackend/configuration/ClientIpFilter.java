package pe.edu.pucp.inf.pddsbackend.configuration;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class ClientIpFilter extends OncePerRequestFilter {

    private static final ThreadLocal<String> clientIpHolder = new ThreadLocal<>();

    public static String getClientIp() {
        return clientIpHolder.get();
    }

    public static void clear() {
        clientIpHolder.remove();
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws java.io.IOException, ServletException {
        try {
            String ip = extractClientIp(request);
            clientIpHolder.set(ip);

            filterChain.doFilter(request, response);
        } finally {
            clear();  // importante limpiar
        }
    }

    private String extractClientIp(HttpServletRequest request) {
        String xfwd = request.getHeader("X-Forwarded-For");
        if (xfwd != null && !xfwd.isBlank()) {
            // puede venir una lista de IPs
            String first = xfwd.split(",")[0].trim();
            if (!first.isEmpty()) {
                return first;
            }
        }
        String xReal = request.getHeader("X-Real-IP");
        if (xReal != null && !xReal.isBlank()) {
            return xReal.trim();
        }
        return request.getRemoteAddr();
    }
}
