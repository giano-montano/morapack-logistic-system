# pdds-backend

API backend de la aplicación logística **pdds-backend** (Spring Boot).

---

## Contenido

* [Descripción](#descripción)
* [Stack tecnológico](#stack-tecnológico)
* [Requisitos](#requisitos)
* [Variables de entorno](#variables-de-entorno)
* [Arrancar localmente (dev)](#arrancar-localmente-dev)
* [Construir y ejecutar (prod)](#construir-y-ejecutar-prod)
* [Perfiles y configuración (dev / prod)](#perfiles-y-configuración-dev--prod)
* [Actuator / health / logging](#actuator--health--logging)
* [Notas de configuración importantes](#notas-de-configuración-importantes)
* [Problemas frecuentes y soluciones (JMX, IDE)](#problemas-frecuentes-y-soluciones-jmx-ide)
* [Estructura mínima del proyecto](#estructura-mínima-del-proyecto)

---

## Descripción

Servicio REST construido con Spring Boot que expone la lógica y persistencia del sistema logístico. Está pensado para desplegarse como un servicio independiente y ser consumido por el frontend.

---

## Stack tecnológico

* Java 17+
* Spring Boot (starter: web, data-jpa, luego se agregará security y actuator probablemente)
* Hibernate (JPA)
* MySQL (conector `mysql-connector-j`)
* Maven

---

## Requisitos

* Java 17+ instalado
* Maven (para desarrollo) o JAR empaquetado
* MySQL o servicio compatible accesible
* Variables de entorno configuradas (ver sección)

---

## Variables de entorno (recomendadas)

> **Nota**: no uses sufijos `DEV/PROD`. Cada entorno (tu máquina local, servidor prod) define el valor apropiado.

```text
# Base / DataSource
DB_URL             # ej: jdbc:mysql://localhost:3306/pdds?serverTimezone=UTC
DB_USERNAME        # usuario BD
DB_PASSWORD        # contraseña BD

# JWT (si usas JWT)
JWT_SECRET         # secreto robusto (alta entropía)

# Frontend / CORS
FRONTEND_URL       # ej: http://localhost:3000

# Opcional (mail, smtp) si se llega a usar correos
SMTP_USER
SMTP_PASS
```

### Ejemplo `.env` (local)

```env
DB_URL=jdbc:mysql://localhost:3306/pdds_dev?serverTimezone=UTC
DB_USERNAME=root
DB_PASSWORD=changeme
JWT_SECRET=miSecretoMuyLargo123!
FRONTEND_URL=http://localhost:3000
```

---

## Arrancar localmente (dev)

1. Exporta/define las variables de entorno (ej. PowerShell):

```powershell
$env:DB_URL="jdbc:mysql://localhost:3306/pdds_dev?serverTimezone=UTC"
$env:DB_USERNAME="root"
$env:DB_PASSWORD="changeme"
$env:JWT_SECRET="miSecretoLocal"
$env:FRONTEND_URL="http://localhost:3000"
```

Linux/macOS:

```bash
export DB_URL="jdbc:mysql://localhost:3306/pdds_dev?serverTimezone=UTC"
export DB_USERNAME=root
export DB_PASSWORD=changeme
```

2. Ejecutar con perfil `dev`:

```bash
mvn spring-boot:run -Dspring-boot.run.profiles=dev
# o
mvn -Dspring-boot.run.profiles=dev spring-boot:run
```

El perfil `dev` activa `spring.jpa.hibernate.ddl-auto=update` y `spring.sql.init.mode=always` según tu `application-dev.yml` (útil para desarrollo).

---

## Construir y ejecutar (prod)

1. Empaqueta:

```bash
mvn clean package -DskipTests
```

2. Ejecuta:

```bash
java -jar target/pdds-backend-0.0.1-SNAPSHOT.jar --spring.profiles.active=prod
```

En producción, configura las variables de entorno `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, `JWT_SECRET` en el host/servicio donde se ejecute (systemd, docker, CI/CD, etc.).

---

## Perfiles y configuración (dev / prod)

* **application.yml** (base): valores comunes y seguros (no hardcodear secrets), `spring.jmx.enabled=false` (recomendado), `server.forward-headers-strategy=native`.
* **application-dev.yml**: `ddl-auto=update`, `sql.init.mode=always`, `show-sql=true` (solo dev).
* **application-prod.yml**: `ddl-auto` debe usarse con precaución (si generas desde código está permitido), `sql.init.mode=never`, logging a INFO y actuator limitado.

---

## Actuator / health / logging

* En `dev` puede exponerse `/actuator/health`, `/actuator/info`, `/actuator/metrics`, `/actuator/loggers`.
* En `prod` limita exposición (solo `health` e `info`), y no muestres detalles de `health`.
* Logging: usa niveles `DEBUG` en dev y `INFO` en prod. Configura archivo de logs en prod (`/var/log/pdds-backend.log` u otro).

---

## Notas de configuración importantes

* **Hibernate dialect**: si usas MySQL8, `org.hibernate.dialect.MySQL8Dialect` (lo tienes en properties). Si no quieres declarar dialecto, asegúrate que el driver y la URL permitan deducción automática.
* **`spring.jmx.enabled=false`** en `application.yml` evita que Spring registre MBeans. **No obstante**, el conector JMX puede aparecer si el IDE u otra herramienta se conecta por *Attach API* — ver sección de troubleshooting.
* **`server.forward-headers-strategy=native`** si colocas la app tras NGINX u otro proxy (sin prefijos de contexto). Usa `FRAMEWORK` si necesitas `X-Forwarded-Prefix`.
* **No dejes secretos en el repo**. Usa variables de entorno o un vault.

---

## Problemas frecuentes y soluciones

### Hibernate: `Unable to determine Dialect without JDBC metadata`

* Revisa `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`. Si la conexión falla, Hibernate no detecta dialecto.
* Si persiste, fuerza el dialecto con:

  ```yaml
  spring:
    jpa:
      properties:
        hibernate:
          dialect: org.hibernate.dialect.MySQL8Dialect
  ```

### Build quedándose en `Executing pre-compile tasks…`

* Soluciones rápidas:

    * `File → Invalidate Caches / Restart` en IntelliJ.
    * Cerrar IntelliJ y borrar `.idea` + `.iml` y reimportar proyecto (último recurso).
    * Desactivar plugins sospechosos.
    * Compilar desde CLI: `mvn clean package` para comprobar que el problema es del IDE.

---

## Ejemplo mínimo `application.yml` (referencia)

```yaml
spring:
  application:
    name: pdds-backend
  jmx:
    enabled: false
  profiles:
    active: dev
  datasource:
    url: ${DB_URL}
    username: ${DB_USERNAME}
    password: ${DB_PASSWORD}
  jpa:
    open-in-view: false
    show-sql: false
    properties:
      hibernate:
        dialect: org.hibernate.dialect.MySQL8Dialect

server:
  forward-headers-strategy: native

jwt:
  secret: ${JWT_SECRET}

app:
  frontend-url: ${FRONTEND_URL}
```

`application-dev.yml` (resumen):

```yaml
spring:
  config:
    activate:
      on-profile: dev
  jpa:
    show-sql: true
    hibernate:
      ddl-auto: update
  sql:
    init:
      mode: always
  datasource:
    url: ${DB_URL}?createDatabaseIfNotExist=true
    username: ${DB_USERNAME}
    password: ${DB_PASSWORD}

logging:
  level:
    root: INFO
    pdds-backend: DEBUG
```

---

## Estructura mínima (esperada)

```
backend/
├── src/
│   ├── main/
│   ├── test/
├── pom.xml
├── application.yml
├── application-dev.yml
└── README.md    <-- este archivo
```

---

## Contacto / Contribución

* Añade issues y PRs puntuales. Documenta variables nuevas en `README.md`.
* Para dudas rápidas: indica salida de logs relevantes y `mvn -X` si hay un fallo de runtime.

---
