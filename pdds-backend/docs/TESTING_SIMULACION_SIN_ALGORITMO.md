# Testing de Simulación sin Algoritmo GRASP

## 🎯 Objetivo

Probar la simulación y los WebSockets **sin ejecutar** el algoritmo GRASP (que puede tardar o no funcionar 100%).

## ✅ Solución: Programaciones Hardcodeadas Automáticas

Cuando activas `usar_modo_mock: true`, el sistema:
1. **NO ejecuta** el algoritmo GRASP
2. **Genera automáticamente** 1-2 programaciones usando los primeros pedidos y vuelos de tu BD
3. **Asigna productos** con UUIDs aleatorios (se crearán dinámicamente)

## 📝 Uso

### JSON para activar modo testing

```json
{
  "duracion_minutos": 1440,
  "intervalo_trigger_planificacion_minutos": 720,
  "dia_inicio_simulacion": 8,
  "usar_modo_mock": true,
  "params_planificacion": {
    "estrategia_fija": "AUTO",
    "usar_modo_mock": true
  }
}
```

## 🔧 Qué hace el método `crearProgramacionesHardcodeadas()`

```java
// Toma el primer pedido y primer vuelo de la BD
Programacion prog1 = new Programacion(
    pedido.getId(),           // Primer pedido encontrado
    UUID.randomUUID(),        // Producto nuevo (se crea al vuelo)
    [vuelo.getId()]          // Primer vuelo encontrado
);
```

### Ejemplo de salida en logs:

```
🧪 MODO TESTING: Generando programaciones HARDCODEADAS para prueba
🧪 Creando programaciones HARDCODEADAS para testing...
✅ Programación 1: Pedido=5, Vuelo=12, Producto=a3d4e5f6-...
✅ Programación 2: Pedido=7, Vuelo=15, Producto=b7c8d9e0-...
🧪 Total programaciones hardcodeadas: 2
```

## ✅ Ventajas

- **Super simple**: Solo 70 líneas de código
- **Sin configuración**: Usa datos reales de tu BD automáticamente
- **Productos dinámicos**: Los UUIDs se generan al vuelo (el sistema los creará)
- **No importan las rutas**: Solo necesita que existan pedidos y vuelos

## 🎭 Comportamiento

### Con `usar_modo_mock: true`:
- ✅ Carga pedidos y vuelos de BD
- ✅ Crea 1-2 programaciones simples automáticamente
- ✅ La simulación procesa esas programaciones
- ✅ WebSockets envían eventos normalmente
- ❌ NO ejecuta algoritmo GRASP (ahorra tiempo)

### Con `usar_modo_mock: false`:
- ✅ Ejecuta algoritmo GRASP completo
- ✅ Genera planificaciones óptimas
- ⏱️ Puede tardar varios segundos/minutos

## 🔍 Logs para verificar

Cuando está en modo testing verás:

```
🧪 MODO TESTING: Generando programaciones HARDCODEADAS para prueba
🧪 Creando programaciones HARDCODEADAS para testing...
✅ Programación 1: Pedido=5, Vuelo=12, Producto=uuid...
✅ Programación 2: Pedido=7, Vuelo=15, Producto=uuid...
🧪 Total programaciones hardcodeadas: 2
```

## ⚠️ Requisitos

Para que funcione necesitas en tu BD:
- ✅ Al menos 1 pedido
- ✅ Al menos 1 vuelo
- ✅ NO necesitas productos (se crean automáticamente)

Si no hay datos, retornará lista vacía pero sin error.

## 🚀 Casos de Uso

### 1. Testing rápido de WebSockets
```json
{"usar_modo_mock": true, "duracion_minutos": 60}
```
→ Simulación de 1 hora con datos simples

### 2. Testing de flujo de eventos
```json
{"usar_modo_mock": true, "duracion_minutos": 1440}
```
→ Simulación de 1 día completo sin esperar GRASP

### 3. Producción (algoritmo real)
```json
{"usar_modo_mock": false}
```
→ Ejecuta GRASP normalmente

## 📝 Uso

### JSON para simulación en modo testing

```json
{
  "duracion_minutos": 10080,
  "intervalo_trigger_planificacion_minutos": 1440,
  "dia_inicio_simulacion": 8,
  "usar_modo_mock": true,
  "params_planificacion": {
    "estrategia_fija": "AUTO",
    "usar_modo_mock": true
  }
}
```

### Qué pasa cuando `usar_modo_mock: true`

1. La simulación corre **normalmente** (vuelos, llegadas, salidas, etc.)
2. Cada vez que llega un `EventoTriggerPlanificacion`, en lugar de ejecutar GRASP:
   - ❌ NO se ejecuta `estrategiaPlanificacion.planificar()`
   - ✅ SE retorna una lista vacía de programaciones `[]`
   - ⚡ La simulación continúa **sin** planificar envíos
3. Los WebSockets funcionan normalmente enviando el estado de la simulación

## 🔧 Ventajas vs Mock Complejo

### ❌ Mock anterior (eliminado)
- Requería clase `EstrategiaPlanificacionMock` completa
- Tenía que generar programaciones falsas
- Problemas con colecciones inmutables
- Muchos puntos de fallo

### ✅ Bypass simple (actual)
- 6 líneas de código
- No genera nada falso, solo retorna lista vacía
- Sin problemas de inmutabilidad
- La simulación corre igual, solo **sin planificar**

## 🧪 Escenarios de Testing

### 1. Testing de WebSockets
```json
{
  "usar_modo_mock": true,
  "duracion_minutos": 1440
}
```
✅ Verifica que los eventos lleguen correctamente al frontend
✅ Valida la estructura de los mensajes WebSocket
✅ No necesita que GRASP funcione

### 2. Testing de Vuelos y Almacenes
```json
{
  "usar_modo_mock": true,
  "duracion_minutos": 10080
}
```
✅ Verifica llegadas/salidas de vuelos
✅ Valida actualización de inventarios
✅ Sin preocuparse por planificaciones

### 3. Testing del Algoritmo (producción)
```json
{
  "usar_modo_mock": false,
  "estrategia_fija": "AUTO"
}
```
✅ Ejecuta GRASP normalmente
✅ Genera planificaciones reales

## 🎭 Comportamiento Esperado en Modo Testing

### Lo que SÍ funciona:
- ✅ Carga de vuelos desde BD
- ✅ Carga de almacenes desde BD
- ✅ Eventos de llegada de vuelos
- ✅ Eventos de salida de vuelos
- ✅ Actualización de inventarios
- ✅ Envío de eventos por WebSocket
- ✅ Creación de entidad `SimulacionEntidad` en BD

### Lo que NO ocurre:
- ❌ No se ejecuta algoritmo GRASP
- ❌ No se generan programaciones
- ❌ No se planifican envíos
- ❌ Los pedidos quedan sin asignar

## 🔍 Logs para Verificar

### Cuando está en modo testing:
```
🧪 MODO TESTING: Retornando planificación vacía (sin ejecutar algoritmo GRASP)
```

### Cuando NO está en modo testing:
```
Inicializado mi strategy: EstrategiaGraspHibrido@...
A ver esa solución!:
[programaciones generadas]
```

## ⚠️ Importante

El modo testing (`usar_modo_mock: true`) está pensado para:
1. Desarrollar/depurar el frontend sin backend funcional al 100%
2. Testear WebSockets
3. Validar flujo de eventos

**NO es para producción.** En producción siempre usar `usar_modo_mock: false`.

## 📚 Archivos Modificados

- `PlanificacionServiceImpl.java` - Agregado bypass en línea 123-129
- `EventoTriggerPlanificacion.java` - Ya propaga `usarModoMock` correctamente
- `RealizarPlanificacionDTO.java` - Ya tiene campo `usarModoMock`

## 🚀 Próximos Pasos (Opcional)

Si en el futuro necesitas **planificaciones predefinidas** (no vacías), puedes:

1. Crear un JSON con programaciones de prueba
2. Modificar el bypass para cargar ese JSON
3. Retornar esas programaciones en lugar de lista vacía

Pero para testing básico de WebSockets, **la lista vacía es suficiente**.
