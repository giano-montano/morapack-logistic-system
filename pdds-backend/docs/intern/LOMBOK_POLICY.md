# LOMBOK\_POLICY.md

**Versión:** 1.0
**Fecha:** 2025-09-13
**Ámbito:** Proyecto Java / Spring Boot — reglas de uso de Project Lombok por capa y tipo de clase.

---

## Propósito

Este documento fija una política práctica, accionable y conservadora para el uso de Project Lombok en proyectos Java (especialmente Spring Boot + JPA). Busca aprovechar las ventajas de reducción de boilerplate sin introducir riesgos de comportamiento, compatibilidad o diseño.

## Alcance

Aplica a todo el código fuente del repositorio: src/main/java, src/test/java, módulos/subproyectos y plantillas de generación de código en el equipo.

---

## Resumen ejecutivo (1 frase)

Usa Lombok para DTOs, VOs y componentes sin persistencia; usa Lombok en entidades JPA solo para getters/constructores controlados y nunca con @Data sin revisión; define reglas por paquete/capa y valida en CI.

---

## Dependencias y configuración mínima

* Añadir Lombok como dependencia de desarrollo y annotation processor (Gradle/Maven) según la documentación oficial.
* Requerir que TODO desarrollador tenga instalado el plugin de Lombok en su IDE (IntelliJ/Eclipse), documentado en el README de desarrollo.
* Incluir una tarea opcional de delombok ligada a verify o disponible por separado para generar fuentes si se requiere entregar fuentes -sources sin anotaciones.


## Reglas generales (obligatorias)

1. No usar @Data a la ligera. @Data combina @Getter, @Setter, @RequiredArgsConstructor, @ToString y @EqualsAndHashCode y rompe encapsulamiento y puede introducir problemas con **JPA**. Su uso debe estar limitado a clases puramente inmutables o DTOs sin relaciones/persistencia.
2. Preferir anotaciones explícitas: @Getter, @Setter (de forma localizada), @RequiredArgsConstructor, @AllArgsConstructor, @NoArgsConstructor, @Builder, @Value. Esto aclara qué se genera.
3. Mantener inmutabilidad cuando sea posible en capas de transporte (DTO/VO): preferir @Value o record (si aplica) para DTOs inmutables.
4. Control explícito de equals/hashCode: nunca confiar en la generación automática sin revisar la semántica (ver sección JPA / domain objects).
5. Evitar @ToString que incluya relaciones lazy; siempre excluir colecciones/relaciones perezosas.
6. Documentar cualquier excepción a la política en commit.

---

## Reglas por capa / tipo de clase

### 1) Entidades JPA (package ...entity o domain.entity)

* Permitido: @Getter (clase), @NoArgsConstructor(access = AccessLevel.PROTECTED), @AllArgsConstructor (opcional), @Builder solo si se documenta la interacción con JPA y se implementa constructor sin args protected (JPA requirement).
* Permitido con restricciones: @EqualsAndHashCode(onlyExplicitlyIncluded = true) solo cuando se incluye explícitamente el campo de identidad lógico/primario (por ejemplo @EqualsAndHashCode.Include private Long id;) y se documenta por qué esa elección es correcta. Alternativa — implementar equals/hashCode manualmente.
* Prohibido: @Data en entidades. @ToString sin excluir relaciones @ToString(exclude = {"orders", "payments"}).
* Razonamiento/práctica: Los métodos equals/hashCode deben ser estables durante el ciclo de vida de persistencia. No uses campos mutables o generados por BD en equals/hashCode salvo que conozcas las implicaciones y tengas una estrategia (ej.: igualdad por identidad después de persistir, fallback seguro mientras id==null).

Ejemplo recomendado (pseudocódigo):
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@ToString(exclude = {"orders"})
public class Customer {
@Id @GeneratedValue
@EqualsAndHashCode.Include
private Long id;

```
  private String name;

  @OneToMany(mappedBy = "customer", fetch = FetchType.LAZY)
  private List<Order> orders = new ArrayList<>();
```

}

---

### 2) Embeddables / Value Objects (DDD) (@Embeddable)

* Permitido: @Value si inmutable y todas las propiedades son inmutables; @Getter + explicit constructors si necesitas compatibilidad con JPA (JPA exige constructor sin args y mutabilidad controlada en algunos casos).
* Precaución: Java record no es siempre JPA-compatible; si se requiere persistencia, testearlo o usar POJO con Lombok explícito.

---

### 3) DTOs / Requests / Responses (dto, api)

* Permitido y recomendado: @Value (si inmutable), @Builder, @AllArgsConstructor, @NoArgsConstructor(force=true) para frameworks que requieren constructor sin args (p.ej Jackson), @With para copy-on-write.
* Uso de @Data: Se acepta solo en DTOs simples y transitorios; preferir @Getter/@Setter explícitos si la clase tiene lógica o campos derivados.

---

### 4) Servicios / Componentes / Controllers

* Permitido: @RequiredArgsConstructor para inyección de dependencias (final fields) y @Slf4j para logger.
* Prohibido: @Data en beans con lógica; evita setters públicos que permitan romper invariantes.

---

### 5) Repositorios / Mappers / Config

* Repositorios generalmente no necesitan Lombok.
* Mappers (MapStruct) funcionan bien con Lombok, pero configurar MapStruct para reconocer los constructores generados por Lombok puede requerir mapstruct-processor y la configuración de annotationProcessor correcta.

---

### 6) Tests

* En tests puedes usar @Data libremente en fixtures/test DTOs, pero preferir @Builder y @Value para fixtures inmutables.

---

## Anotaciones permitidas / desaconsejadas (lista rápida)

* Permitidas (por defecto): @Getter, @Setter (local / per-field), @RequiredArgsConstructor, @AllArgsConstructor, @NoArgsConstructor(access = ...), @Builder (con precaución en entities), @Value (DTOs/VOs), @With, @Slf4j.
* Usar con restricciones: @EqualsAndHashCode(onlyExplicitlyIncluded = true), @ToString(exclude = {...}).
* Desaconsejadas / prohibidas: @Data en entidades o en clases de dominio con invariantes relevantes; @Setter a nivel de clase en domain objects; @ToString sin exclusiones en entidades con relaciones.

---

## equals() / hashCode() — reglas y patrones

1. Regla general (entities): equality basada en identidad persistente (ID) después de persistir; antes de persistir usar fallback que no rompa colecciones o mapas. Muchas estrategias:

    * Implementar equals que compara id si no es null, de lo contrario == o getClass() comparado.
    * hashCode() puede delegar a getClass().hashCode() mientras id==null, y usar id.hashCode() cuando id!=null.
2. No incluir colecciones ni relaciones lazy en equals/hashCode.
3. Si usas Lombok: preferir onlyExplicitlyIncluded y marcar el campo id con @EqualsAndHashCode.Include o implementar manualmente.

---

## toString() — precauciones

* Nunca incluir colecciones grandes ni relaciones perezosas. Usar @ToString(exclude = {...}) en entidades. Para DTOs y logs, @ToString es útil.

---

## Builders y JPA

* Evitar @Builder en entidades que dependan de un constructor sin args y de la generación de ID por la BD — @Builder puede coexistir pero asegúrate de @NoArgsConstructor(access = AccessLevel.PROTECTED) y documentar el contrato. Para DTOs y objetos de transferencia, @Builder es ideal.

---

## Delombok y salida de fuentes

* Mantener como opción de build una tarea delombok para generar fuentes transformadas si algún proceso de distribución o auditoría lo requiere (por ejemplo, cuando entregas -sources a terceros que no aceptan dependencias en tiempo de compilación).
* No obligar a delombok en cada build (impacta tiempo), pero sí integrarlo en release/verify si el policy del proyecto lo demanda.

---

## Tooling / IDE / CI

* En README.md desarrollar la sección "Desarrollo local" con la instrucción para instalar plugin de Lombok en IDE y validar.
* CI: siempre compilar en la matrix de JDKs objetivo; si actualizas JDK o Spring Boot, añadir trabajo de verificación para Lombok (compilación + tests) antes de merge.
* Considerar job adicional que ejecute delombok y compile el resultado como sanity check en nightly o release pipelines.

---

## Upgrades y compatibilidad

* Al actualizar JDK o Lombok, ejecutar la suite completa y revisar errores de compilación. Mantener la versión de Lombok razonablemente actual (parche/major según política del equipo).
* Mantener changelog de Lombok y verificar issues sobre compatibilidad antes de upgrade.

---

## Checklist para Pull Requests que introduzcan/usen Lombok

* [ ] ¿Se justificó la anotación? (motivo en el PR).
* [ ] ¿La anotación afecta Entities/JPA? Si sí, marcar reviewer con experiencia en JPA.
* [ ] ¿equals/hashCode revisado (si aplica)?
* [ ] ¿toString o @Builder pueden exponer relaciones lazy? ¿Se excluyen campos problemáticos?
* [ ] ¿CI compila con la matrix de JDK? ¿Se añadió test o ejemplo si la clase es crítica?

---

## Ejemplos de malas prácticas (qué evitar)

* @Data en entidades con @OneToMany perezoso -> toString provoca LazyInitializationException o carga masiva.
* @Data generando equals/hashCode sobre campos mutables o colecciones -> objetos en HashSet cambian de bucket tras mutación.
* @Setter a nivel clase en domain objects con invariantes (rompe encapsulamiento).

---

## Politica de excepciones

* Excepciones puntuales pueden aprobarse por la dirección técnica o arquitecto; deben estar documentadas en la issue/PR y añadirse a la sección EXCEPTIONS.md si se vuelven recurrentes.

---

## Anexos (no existen xd)

* Snippet para delombok: instrucciones para agregar plugin lombok-maven-plugin o task de Gradle.
* Glosario: delombok, annotationProcessor, projection, etc.

---

## Mantenimiento del documento

* Revisión anual o con cada cambio mayor de JDK / Spring Boot / Lombok.

---

Fin de LOMBOK\_POLICY.md
