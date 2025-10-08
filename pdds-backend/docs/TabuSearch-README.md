# Algoritmo Tabu Search para Planificación de Envíos

## Descripción General

Este proyecto implementa un algoritmo de **Tabu Search** (Búsqueda Tabú) para resolver el problema de planificación de envíos en una red logística. El algoritmo optimiza la asignación de pedidos a rutas de vuelos considerando:

- **Capacidades de vuelos** limitadas
- **Ventanas de tiempo** para entregas
- **Minimización de costos** operativos
- **Maximización de pedidos atendidos**

## Arquitectura del Algoritmo

### Componentes Principales

1. **TabuSearchAlgorithmStrategy**: Implementación principal del algoritmo
2. **TabuSearchUtils**: Utilidades y funciones auxiliares
3. **Clases de Modelo**: Estructuras de datos para representar el problema

### Flujo del Algoritmo

```
1. Inicialización
   ├── Crear contexto con datos de entrada
   ├── Generar solución inicial (heurística constructiva)
   └── Inicializar lista tabú

2. Bucle Principal (hasta criterio de parada)
   ├── Generar vecindario de soluciones
   ├── Evaluar candidatos (considerando lista tabú)
   ├── Seleccionar mejor candidato válido
   ├── Actualizar solución actual y lista tabú
   └── Verificar mejora de la mejor solución global

3. Finalización
   └── Retornar mejor solución encontrada
```

## Parámetros Configurables

### Parámetros del Algoritmo
- **MAX_ITERATIONS**: Número máximo de iteraciones (1000)
- **TABU_LIST_SIZE**: Tamaño de la lista tabú (50)
- **MAX_NO_IMPROVEMENT**: Iteraciones sin mejora antes de reiniciar (100)
- **NEIGHBORHOOD_SIZE**: Tamaño del vecindario generado (20)

### Pesos de la Función de Fitness
- **pesoAtendidos**: Puntos por productos entregados (+100.0)
- **pesoRetrasos**: Penalización por retrasos (-50.0)
- **pesoEficiencia**: Penalización por vuelos adicionales (-1.0)

## Operadores de Vecindario

El algoritmo utiliza 4 tipos de movimientos para generar soluciones vecinas:

### 1. Intercambio de Rutas (swapRoutes)
- Intercambia las rutas entre dos envíos diferentes
- Útil para redistribuir recursos entre destinos

### 2. Reasignación de Pedidos (reassignOrder)
- Asigna un pedido a una ruta diferente
- Permite explorar alternativas de ruteo

### 3. Inserción de Rutas (insertRoute)
- Agrega una nueva ruta para pedidos no atendidos
- Aumenta la cobertura de la solución

### 4. Eliminación de Rutas (removeRoute)
- Elimina una ruta existente
- Permite intensificación en otras áreas

## Función de Fitness

La función de fitness evalúa la calidad de una solución considerando:

```java
fitness = (productos_entregados × peso_atendidos) + 
          (retrasos_horas × peso_retrasos) + 
          (numero_vuelos × peso_eficiencia)
```

### Objetivos
- **Maximizar** productos entregados
- **Minimizar** retrasos en entregas
- **Minimizar** número de vuelos utilizados

## Uso del Algoritmo

### Ejemplo Básico

```java
// Crear datos de entrada
PlanificationProblemInput input = PlanificationProblemInput.builder()
    .vuelos(listaVuelos)
    .almacenes(listaAlmacenes)
    .pedidos(listaPedidos)
    .build();

// Ejecutar algoritmo
TabuSearchAlgorithmStrategy algorithm = new TabuSearchAlgorithmStrategy();
PlanificationSolutionOutput output = algorithm.planificar(input);

// Analizar resultados
String report = TabuSearchUtils.generateSolutionReport(
    output.getEnvios(), 
    input.pedidos(), 
    input.vuelos()
);
System.out.println(report);
```

### Validación de Soluciones

```java
// Verificar factibilidad
List<String> errores = TabuSearchUtils.validateSolution(
    output.getEnvios(), 
    input.vuelos(), 
    input.pedidos()
);

if (errores.isEmpty()) {
    System.out.println("✓ Solución válida");
} else {
    System.out.println("Errores encontrados:");
    errores.forEach(System.out::println);
}
```

### Estadísticas Detalladas

```java
TabuSearchUtils.SolutionStats stats = TabuSearchUtils.calculateStats(
    output.getEnvios(), 
    input.pedidos()
);

System.out.println("Pedidos atendidos: " + stats.porcentajePedidosAtendidos + "%");
System.out.println("Productos entregados: " + stats.porcentajeProductosEntregados + "%");
```

## Configuraciones Predefinidas

### Configuración Rápida (Desarrollo)
```java
TabuSearchConfig.getFast()
// 100 iteraciones, lista tabú de 20, vecindario de 10
```

### Configuración Estándar (Producción)
```java
TabuSearchConfig.getDefault()
// 1000 iteraciones, lista tabú de 50, vecindario de 20
```

### Configuración Exhaustiva (Investigación)
```java
TabuSearchConfig.getThorough()
// 5000 iteraciones, lista tabú de 100, vecindario de 50
```

## Extensibilidad

### Agregar Nuevos Operadores de Vecindario

1. Implementar método en `TabuSearchAlgorithmStrategy`
2. Agregar al switch en `generateNeighborhood()`
3. Definir tipo de movimiento correspondiente

```java
private Solution nuevoOperador(Solution solution, TabuSearchContext context) {
    // Implementar lógica del operador
    Solution newSolution = copySolution(solution);
    
    // Modificar la solución
    // ...
    
    // Recalcular fitness
    newSolution.setFitness(calculateFitness(newSolution.getEnvios(), context));
    
    return newSolution;
}
```

### Personalizar Función de Fitness

Modificar el método `calculateFitness()` para incluir nuevos criterios:

```java
// Ejemplo: agregar penalización por uso de almacenes específicos
double penalizacionAlmacenes = calcularPenalizacionAlmacenes(envios);
fitness += penalizacionAlmacenes * pesoAlmacenes;
```

### Criterios de Parada Adicionales

Agregar nuevas condiciones en el bucle principal:

```java
while (iterations < MAX_ITERATIONS && 
       noImprovementCount < MAX_NO_IMPROVEMENT &&
       nuevoCriterio()) {
    // Lógica del algoritmo
}
```

## Mejores Prácticas

### 1. Ajuste de Parámetros
- Iniciar con configuración rápida para desarrollo
- Ajustar tamaño de lista tabú según complejidad del problema
- Balancear exploración vs. explotación con el tamaño del vecindario

### 2. Análisis de Resultados
- Usar siempre `validateSolution()` para verificar factibilidad
- Analizar estadísticas para identificar áreas de mejora
- Comparar múltiples ejecuciones para evaluar consistencia

### 3. Escalabilidad
- Para problemas grandes, aumentar MAX_ITERATIONS gradualmente
- Considerar paralelización del cálculo de vecindario
- Implementar criterios de parada dinámicos

### 4. Debugging
- Habilitar logging detallado durante desarrollo
- Usar métodos de utilidad para inspeccionar soluciones intermedias
- Validar datos de entrada antes de ejecutar algoritmo

## Testing

### Ejecutar Tests Unitarios
```bash
mvn test -Dtest=TabuSearchAlgorithmStrategyTest
```

### Tests Incluidos
- `testTabuSearchWithSimpleScenario`: Escenario básico de prueba
- `testTabuSearchUtilities`: Validación de utilidades
- `testDifferentConfigurations`: Comparación de configuraciones

## Consideraciones de Rendimiento

### Complejidad Temporal
- **Por iteración**: O(N × M) donde N = tamaño vecindario, M = evaluación fitness
- **Total**: O(I × N × M) donde I = número de iteraciones

### Optimizaciones Implementadas
- Caching de mapas de acceso rápido
- Evaluación lazy de fitness
- Reutilización de estructuras de datos

### Recomendaciones
- Ajustar parámetros según tamaño del problema
- Monitorear uso de memoria en problemas grandes
- Considerar criterios de parada adaptativos

## Limitaciones Actuales

1. **Función de fitness simple**: Puede no capturar todas las complejidades del negocio
2. **Operadores básicos**: Conjunto limitado de movimientos de vecindario
3. **Sin paralelización**: Evaluación secuencial del vecindario
4. **Memoria temporal**: No persiste estado entre ejecuciones

## Roadmap de Mejoras

### Corto Plazo
- [ ] Agregar más operadores de vecindario
- [ ] Implementar criterios de parada adaptativos
- [ ] Mejorar función de fitness con más factores

### Mediano Plazo
- [ ] Paralelización del cálculo de vecindario
- [ ] Implementar diversificación automática
- [ ] Agregar métricas de convergencia

### Largo Plazo
- [ ] Hibridización con otros metaheurísticos
- [ ] Aprendizaje automático para ajuste de parámetros
- [ ] Optimización multi-objetivo

---

## Soporte y Contribuciones

Para reportar bugs o sugerir mejoras, por favor crear un issue en el repositorio del proyecto.

**Autor**: GitHub Copilot  
**Fecha**: Septiembre 2025  
**Versión**: 1.0.0
