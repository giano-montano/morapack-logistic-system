# Despliegue de Morapack (Front + Back) en VM — Procedimiento operativo

**Nombre:** Despliegue Morapack VM

**Versión del procedimiento:** 1.0

**Fecha:** 2025-11-25

**Autor:** Equipo 1A 

---

## 1. Propósito
Este documento describe, de forma práctica y reproducible, el procedimiento para desplegar la solución *Morapack* (frontend Angular + backend Spring Boot) en una máquina virtual Ubuntu con Nginx y HTTPS (Let's Encrypt). Está redac­tado siguiendo la estructura documental de tipo ISO (objetivo, alcance, responsabilidades, procedimiento, verificación y registros) para facilitar auditoría y mantenimiento.

## 2. Alcance
Aplica a la VM donde se ha desarrollado/puesto el contenido en el *home* del usuario `1inf54-981-1a`. Cubre:
- Preparación de ficheros en `/home/1inf54-981-1a`
- Configuración de permisos
- Configuración de Nginx para servir SPA y proxear `/api` y `/ws` al backend
- Instalación / obtención de certificados Let's Encrypt con `certbot`
- Servicio `systemd` para ejecutar el JAR Spring Boot
- Verificación, mantenimiento y recomendaciones de seguridad

> Nota: el dominio utilizado en el despliegue de ejemplo es `1inf54-981-1a.inf.pucp.edu.pe`.

## 3. Responsabilidades
- **Administrador del sistema (owner):** ejecutar los comandos y validar accesos.
- **Desarrollador:** verificar que el build del frontend y el JAR del backend estén actualizados.
- **Auditor / Soporte:** revisar logs, certificados y pruebas de funcionalidad.

## 4. Referencias
- Certbot (Let's Encrypt) — uso práctico con `--nginx`.
- Recomendaciones TLS modernas (Mozilla) para `nginx`.

---

## 5. Requisitos previos
- Ubuntu (ej. 22.04/24.04) con `nginx` instalado y corriendo.
- `certbot` instalado (recomendado vía Snap o apt según distro).
- Java (JRE/JDK) instalado para ejecutar el JAR (ej. `openjdk-17-jre-headless`).
- DNS: el dominio `1inf54-981-1a.inf.pucp.edu.pe` apunta a la IP pública de la VM.
- Puertos 80 y 443 abiertos en firewall/host y accesibles desde Internet.

---

## 6. Estructura de ficheros en la VM (asumida)
- Frontend (Angular build): `/home/1inf54-981-1a/morapack-frontend` (contiene `index.html`, `assets/`, `*.js`, `*.css`)
- Backend JAR: `/home/1inf54-981-1a/pdds-backend-0.0.1-SNAPSHOT.jar`

> Ajustar rutas de ser necesario.

---

## 7. Procedimiento de despliegue

### 7.1 Preparar permisos y propietarios (todo en `home` por practicidad)

Ejecuta como usuario con sudo en la VM (copia/pega):

```bash
# Asegura que nginx pueda atravesar el home (bit x para 'other')
sudo chmod o+x /home/1inf54-981-1a

# Asegura permisos/propietario en el contenido del frontend
sudo chown -R 1inf54-981-1a:www-data /home/1inf54-981-1a/morapack-frontend
sudo find /home/1inf54-981-1a/morapack-frontend -type d -exec chmod 755 {} \;
sudo find /home/1inf54-981-1a/morapack-frontend -type f -exec chmod 644 {} \;
```

**Notas:**
- `www-data` es el usuario/grupo por defecto de `nginx` en Ubuntu. Mantener al menos permisos de lectura para `www-data` es necesario para servir archivos estáticos.
- Alternativamente, si prefieres que `www-data` sea propietario total: `sudo chown -R www-data:www-data /home/1inf54-981-1a/morapack-frontend`.

### 7.2 Crear `map` para WebSocket (contexto `http` de nginx)
Crear archivo `/etc/nginx/conf.d/upgrade.conf` — esto debe cargarse dentro del bloque `http` automáticamente (nginx incluye `conf.d/*.conf`):

```nginx
# /etc/nginx/conf.d/upgrade.conf
map $http_upgrade $connection_upgrade {
    default upgrade;
    ''      close;
}
```

### 7.3 Crear configuración del sitio en nginx
Archivo: `/etc/nginx/sites-available/morapack`

```nginx
# /etc/nginx/sites-available/morapack

# HTTP: redirección y challenge
server {
    listen 80;
    listen [::]:80;
    server_name 1inf54-981-1a.inf.pucp.edu.pe;

    # Permitimos el challenge ACME
    location /.well-known/acme-challenge/ {
        root /home/1inf54-981-1a/morapack-frontend;
        try_files $uri =404;
    }

    # Redirigir todo lo demás a HTTPS
    location / {
        return 301 https://$host$request_uri;
    }
}

# HTTPS: sitio principal
server {
    listen 443 ssl http2;
    listen [::]:443 ssl http2;
    server_name 1inf54-981-1a.inf.pucp.edu.pe;

    root /home/1inf54-981-1a/morapack-frontend;
    index index.html;

    ssl_certificate /etc/letsencrypt/live/1inf54-981-1a.inf.pucp.edu.pe/fullchain.pem;
    ssl_certificate_key /etc/letsencrypt/live/1inf54-981-1a.inf.pucp.edu.pe/privkey.pem;
    include /etc/letsencrypt/options-ssl-nginx.conf;
    ssl_dhparam /etc/letsencrypt/ssl-dhparams.pem;

    # Proxy: /api/ -> backend
    location ^~ /api/ {
        proxy_pass http://127.0.0.1:8080;
        proxy_http_version 1.1;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
        proxy_read_timeout 90;
        client_max_body_size 50M;
    }

    # WebSocket: /ws/ -> backend (usa map en conf.d)
    location ^~ /ws/ {
        proxy_pass http://127.0.0.1:8080;
        proxy_http_version 1.1;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
        proxy_set_header Upgrade $http_upgrade;
        proxy_set_header Connection $connection_upgrade;
        proxy_read_timeout 90;
        proxy_set_header X-Forwarded-Host $host;
        proxy_set_header X-Forwarded-Port $server_port;
        proxy_buffering off;
        client_max_body_size 50M;
    }

    # SPA fallback
    location / {
        try_files $uri $uri/ /index.html;
    }

    # Cachear assets estáticos
    location ~* \.(?:ico|css|js|jpg|jpeg|png|gif|svg|webp|woff2?)$ {
        expires 30d;
        add_header Cache-Control "public";
    }

    gzip on;
    gzip_types text/plain application/javascript application/json text/css image/svg+xml;
}
```

Enlazar y recargar nginx:

```bash
sudo ln -s /etc/nginx/sites-available/morapack /etc/nginx/sites-enabled/morapack
sudo nginx -t && sudo systemctl reload nginx
```

### 7.4 Configurar `systemd` para el backend
Archivo: `/etc/systemd/system/pdds-backend.service`

```ini
[Unit]
Description=PDDS Backend Spring Boot (Morapack)
After=network.target

[Service]
User=springuser
WorkingDirectory=/home/1inf54-981-1a
ExecStart=/usr/bin/java -Xms256m -Xmx512m -jar /home/1inf54-981-1a/pdds-backend-0.0.1-SNAPSHOT.jar --server.address=127.0.0.1 --server.port=8080 --spring.profiles.active=prod
SuccessExitStatus=143
Restart=always
RestartSec=5
StandardOutput=journal
StandardError=journal
Environment=DB_URL=jdbc:mysql://127.0.0.1:3306/bdpddsprod
Environment=DB_USERNAME=XXXXXXXXXXXXXXXXX
Environment=DB_PASSWORD=XXXXXXXXXXXXXXXXX
Environment=JWT_SECRET=XXXXXXXXXXXXXXXXX
Environment=LOCAL_FRONTEND_URL=http://1INF54-981-1A.inf.pucp.edu.pe

[Install]
WantedBy=multi-user.target
```

Habilitar e iniciar:

```bash
sudo systemctl daemon-reload
sudo systemctl enable --now pdds-backend.service
sudo journalctl -u pdds-backend.service -f
```

**Nota:** Se usó un usuario nologin separado `springuser`, ajustar los permisos respectivos.  

### 7.5 Obtener certificado TLS con Certbot
Una vez que nginx esté sirviendo en puerto 80 y `server_name` configurado, usa:

```bash
sudo certbot --nginx -d 1inf54-981-1a.inf.pucp.edu.pe
```

- `--nginx` intentará modificar tu conf para añadir bloques HTTPS y redirección. Si prefieres control manual, usa `certonly` con `--webroot`:

```bash
sudo certbot certonly --webroot -w /home/1inf54-981-1a/morapack-frontend -d 1inf54-981-1a.inf.pucp.edu.pe
```

Probar renovación (simulado):

```bash
sudo certbot renew --dry-run
```

### 7.6 Verificaciones finales
- Verificar que `index.html` es servido:

```bash
curl -v -H "Host: 1inf54-981-1a.inf.pucp.edu.pe" http://127.0.0.1/
```

- Probar HTTPS (desde fuera o con `-k` si certificado auto-firmado):

```bash
curl -vk https://1inf54-981-1a.inf.pucp.edu.pe/
```

- Probar ruta API proxy:

```bash
curl -v http://127.0.0.1:8080/actuator/health  # si tienes actuator
curl -v https://1inf54-981-1a.inf.pucp.edu.pe/api/tu-endpoint
```

- Revisar logs si hay errores:

```bash
sudo tail -n 200 /var/log/nginx/error.log
sudo tail -n 200 /var/log/nginx/access.log
sudo journalctl -u pdds-backend.service -n 200
```

---

## 8. Gestión de errores comunes y resolución
- **500 Internal Server Error** al solicitar `/` → Revisar `/var/log/nginx/error.log` por `permission denied` o `open() failed`. Verifica `root` y que `index.html` exista.
- **403 Forbidden** → Permisos insuficientes en directorios; aplicar `chmod`/`chown` proporcionados.
- **Certificado no encontrado/errores SSL** → Verificar existencia de `/etc/letsencrypt/live/...` y permisos. Re-ejecutar `certbot` si es necesario.
- **Proxy 502/504** en `/api/*` → Backend no disponible o puerto incorrecto. `curl http://127.0.0.1:8080/` en la VM para comprobar.
- **WebSocket no funciona** → Asegurar que `Upgrade` y `Connection` usen el `map` `$connection_upgrade` y `proxy_buffering off`.

---

## 9. Seguridad y buenas prácticas
- **Binding del backend**: ejecutar Spring Boot con `--server.address=127.0.0.1` para no exponer el puerto público.
- **Cabeceras proxy**: Nginx ya pasa `X-Forwarded-For` y `X-Forwarded-Proto` — configurar Spring Boot con `server.forward-headers-strategy=framework` o `ForwardedHeaderFilter` según versión.
- **HSTS**: habilitar `Strict-Transport-Security` tras verificar que no necesitas HTTP por largo tiempo (usar con precaución y considerar `preload` sólo con pruebas previas).
- **Backups**: mantener copias del JAR y del contenido `dist/` antes de despliegues.
- **Least privilege**: en producción, considera crear un usuario de servicio para el backend y no usar el usuario personal.

---

## 10. Mantenimiento y renovación de certificados
- `certbot` instala un cron/servicio que renueva automáticamente; validar con `sudo certbot renew --dry-run`.
- Renovar manualmente si `--dry-run` falla y revisar logs en `/var/log/letsencrypt/`.

---

## 11. Procedimiento de rollback (rápido)
1. Si un despliegue rompe el sitio, restaurar la carpeta `dist/` desde la copia de seguridad previa (`cp -r /home/backup/.. /home/...`).
2. Reiniciar el servicio backend: `sudo systemctl restart pdds-backend.service`.
3. Recargar nginx: `sudo systemctl reload nginx`.
4. Revisar logs y revertir cambios en `/etc/nginx/sites-available/morapack` si fue modificado.

---

## 12. Registros y evidencias (qué guardar)
- Salida de `nginx -t` y `systemctl status pdds-backend.service` tras despliegue.
- Resultados de `curl -v` (HTTP y HTTPS) y logs relevantes.
- Copia del `sites-available/morapack` y del `pdds-backend.service` en el repositorio de infraestructura.

---

## 13. Historial de cambios (control de versiones del procedimiento)
- 1.0 (2025-11-25): Versión inicial — documento generado basado en la sesión y pruebas realizadas en la VM.

---

### Contacto / Soporte
Para dificultades puntuales pega aquí los *snippets* de `sudo tail -n 200 /var/log/nginx/error.log` y la salida de `sudo systemctl status pdds-backend.service` y se analizará con detalle.

---

_Fin del documento._

