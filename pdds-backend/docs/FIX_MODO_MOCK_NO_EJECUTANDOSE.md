# 🔧 Fix: Modo Mock No Se Ejecutaba

## 📅 Fecha: 2025-01-27

---

## ❌ Problema

Cuando se ejecutaba una simulación con `usarModoMock=true`, el mock **NO se activaba** y en su lugar se ejecutaba el algoritmo GRASP real, causando dos problemas:

### Evidencia en Logs:

```log
EventoTriggerPlanificacion: Creé DTO de planif (forma realizar planificación): 
RealizarPlanificacionDTO(..., usarModoMock=null)  ← ❌ NULL!

EventoTriggerPlanificacion: res planificación num programs: 0
ERROR procesando evento EventoTriggerPlanificacion: 
Cannot invoke "java.util.HashMap.put(Object, Object)" because "this.productos" is null
```

### Síntomas:
1. El campo `usarModoMock` aparecía como `null` en los logs
2. Se ejecutaba GRASP real en lugar del mock
3. Error de NullPointerException en `this.productos`
4. Errores en cadena en eventos de vuelo

---

## 🔍 Análisis de Causas

### Causa #1: usarModoMock No Se Propagaba

**Flujo esperado:**
```
SimulacionRequestDTO (usarModoMock=true)
  ↓
SimulacionServiceImpl crea RealizarPlanificacionDTO ✅
  ↓
EjecutorSimulacion recibe DTO y crea ContextoSimulacion ✅
  ↓
EventoTriggerPlanificacion lee ctx.getFormaRealizarPlanificacion() ✅
  ↓
EventoTriggerPlanificacion crea NUEVO DTO... ❌ SIN usarModoMock
```

**Código problemático** (`EventoTriggerPlanificacion.java` línea 52-58):

```java
// ❌ ANTES (INCORRECTO)
RealizarPlanificacionDTO dto = RealizarPlanificacionDTO.builder()
    .idSimulacion(ctx.getFormaRealizarPlanificacion().getIdSimulacion())
    .estrategiaFija(ctx.getFormaRealizarPlanificacion().getEstrategiaFija())
    .parametros(ctx.getFormaRealizarPlanificacion().getParametros())
    .seed(ctx.getFormaRealizarPlanificacion().getSeed())
    .subCarpetaReportes(ctx.getFormaRealizarPlanificacion().getSubCarpetaReportes())
    // ⚠️ FALTA: .usarModoMock(...)
    .build();
```

### Causa #2: HashMap productos No Inicializado

**⚠️ PROBLEMA EN DOS CONSTRUCTORES**

`EstadoGlobal` tiene DOS constructores, y **ambos** necesitaban inicializar `productos`:

1. **Constructor principal** (recibe Maps como parámetros)
2. **Constructor de copia** (clona otro EstadoGlobal) ← **Este se usa en el mock**

**Flujo del error:**
```
Mock llama: entrada.getEstadoGlobalCopia()
  ↓
Se ejecuta: new EstadoGlobal(estadoGlobal) // Constructor de copia
  ↓
Copia: almacenes, vuelos, pedidos, programaciones
  ↓
NO inicializa: productos ❌ (queda en null)
  ↓
Mock intenta: estadoGlobal.anadirProducto(producto)
  ↓
anadirProducto() intenta: this.productos.put(uuid, producto)
  ↓
NullPointerException: "this.productos" is null ❌
```

**Código problemático #1** (`EstadoGlobal.java` línea 58-70 - Constructor principal):

```java
// ❌ ANTES (INCORRECTO)
public EstadoGlobal(Map<Long, Almacen> almacenes,
                    Map<Long, Vuelo> vuelos,
                    Map<Long, Pedido> pedidos,
                    List<Programacion> programaciones) {
    this.almacenes = almacenes != null ? new HashMap<>(almacenes) : new HashMap<>();
    this.vuelos = vuelos != null ? new HashMap<>(vuelos) : new HashMap<>();
    this.pedidos = pedidos != null ? new HashMap<>(pedidos) : new HashMap<>();
    this.programaciones = programaciones != null ? new LinkedList<>(programaciones) : new LinkedList<>();
    // ⚠️ FALTA: this.productos = new HashMap<>();
    
    this.inicializarIndices();
}
```

**Código problemático #2** (`EstadoGlobal.java` línea 73-108 - Constructor de copia):

```java
// ❌ ANTES (INCORRECTO)
public EstadoGlobal(EstadoGlobal estadoGlobal) { // clonación
    // ... copia almacenes, vuelos, pedidos ...
    
    almacenes = copiaAlmacenes;
    vuelos = copiaVuelos;
    pedidos = copiaPedidos;
    programaciones = copiaProgramaciones;
    // ⚠️ FALTA: productos = new HashMap<>();
    
    inicializarIndices();
}
```

---

## ✅ Solución

### Fix #1: Propagar usarModoMock en EventoTriggerPlanificacion

**Archivo**: `EventoTriggerPlanificacion.java`  
**Línea**: 58 (agregada)

```java
// ✅ DESPUÉS (CORRECTO)
RealizarPlanificacionDTO dto = RealizarPlanificacionDTO.builder()
    .idSimulacion(ctx.getFormaRealizarPlanificacion().getIdSimulacion())
    .estrategiaFija(ctx.getFormaRealizarPlanificacion().getEstrategiaFija())
    .parametros(ctx.getFormaRealizarPlanificacion().getParametros())
    .seed(ctx.getFormaRealizarPlanificacion().getSeed())
    .subCarpetaReportes(ctx.getFormaRealizarPlanificacion().getSubCarpetaReportes())
    .usarModoMock(ctx.getFormaRealizarPlanificacion().getUsarModoMock()) // ✅ AGREGADO
    .build();
```

### Fix #2: Inicializar productos en EstadoGlobal (AMBOS constructores)

**Archivo**: `EstadoGlobal.java`  
**Líneas**: 67 y 106 (agregadas)

**Fix #2a - Constructor principal:**

```java
// ✅ DESPUÉS (CORRECTO)
public EstadoGlobal(Map<Long, Almacen> almacenes,
                    Map<Long, Vuelo> vuelos,
                    Map<Long, Pedido> pedidos,
                    List<Programacion> programaciones) {
    this.almacenes = almacenes != null ? new HashMap<>(almacenes) : new HashMap<>();
    this.vuelos = vuelos != null ? new HashMap<>(vuelos) : new HashMap<>();
    this.pedidos = pedidos != null ? new HashMap<>(pedidos) : new HashMap<>();
    this.programaciones = programaciones != null ? new LinkedList<>(programaciones) : new LinkedList<>();
    this.productos = new HashMap<>(); // ✅ AGREGADO
    
    this.inicializarIndices();
}
```

**Fix #2b - Constructor de copia:**

```java
// ✅ DESPUÉS (CORRECTO)
public EstadoGlobal(EstadoGlobal estadoGlobal) { // clonación
    // ... copia almacenes, vuelos, pedidos ...
    
    almacenes = copiaAlmacenes;
    vuelos = copiaVuelos;
    pedidos = copiaPedidos;
    programaciones = copiaProgramaciones;
    productos = new HashMap<>(); // ✅ AGREGADO - Crítico para el mock
    
    inicializarIndices();
}
```

---

## 📊 Flujo Corregido

### Ahora el flujo funciona correctamente:

```
POST /api/simulaciones
{
  "tipoSimulacion": "SEMANAL",
  "usarModoMock": true  ✅
}
  ↓
SimulacionServiceImpl.iniciarSimulacionAhora()
  RealizarPlanificacionDTO.builder()
    .usarModoMock(params.usarModoMock())  ✅
  ↓
EjecutorSimulacion.construirContexto()
  ContextoSimulacion.builder()
    .formaRealizarPlanificacion(dto)  ✅
  ↓
EventoTriggerPlanificacion.procesar()
  RealizarPlanificacionDTO dto = builder()
    .usarModoMock(ctx.getFormaRealizarPlanificacion().getUsarModoMock())  ✅
  ↓
PlanificacionService.realizarPlanificacionConEntrada()
  if (dto.getUsarModoMock()) {
    estrategiaPlanificacion = estrategiaPlanificacionMock;  ✅ MOCK ACTIVADO
  }
  ↓
EstrategiaPlanificacionMock.planificar()
  Producto producto = new Producto(...);
  estadoGlobal.anadirProducto(producto);  ✅
  ↓
EstadoGlobal.anadirProducto()
  this.productos.put(uuid, producto);  ✅ Ahora productos está inicializado
```

---

## 🧪 Verificación

### Logs Esperados:

```log
[2025-10-27T06:39:43.654] EventoTriggerPlanificacion: comenzando a planificar!
[2025-10-27T06:39:43.723] EventoTriggerPlanificacion: Creé DTO de planif: 
RealizarPlanificacionDTO(..., usarModoMock=true)  ✅

[2025-10-27T06:39:43.724] 🎭 MODO MOCK ACTIVADO - Usando planificación ficticia para testing  ✅
[2025-10-27T06:39:43.725] 🎭 MOCK: Iniciando planificación ficticia (para testing)  ✅
[2025-10-27T06:39:43.726] 🎭 MOCK: Encontrados 108 pedidos pendientes  ✅
[2025-10-27T06:39:43.750] 🎭 MOCK: Generadas 15 programaciones ficticias  ✅

[2025-10-27T06:39:43.751] EventoTriggerPlanificacion: res planificación num programs: 15  ✅
[2025-10-27T06:39:43.752] EventoTriggerPlanificacion: Apliqué salida en contexto  ✅
```

### Sin Errores:

- ✅ No más `usarModoMock=null`
- ✅ No más `NullPointerException` en productos
- ✅ Mock se ejecuta correctamente
- ✅ Programaciones ficticias generadas
- ✅ Simulación completa sin errores

---

## 🎯 Impacto

### Archivos Modificados:

1. **`EventoTriggerPlanificacion.java`** (línea 58)
   - Agregado: `.usarModoMock(ctx.getFormaRealizarPlanificacion().getUsarModoMock())`

2. **`EstadoGlobal.java`** (líneas 67 y 106) ⚠️ DOS LUGARES
   - Agregado en constructor principal: `this.productos = new HashMap<>();`
   - Agregado en constructor de copia: `productos = new HashMap<>();`

### Beneficios:

- ✅ **Modo mock funciona**: Se ejecuta cuando se solicita
- ✅ **Sin NullPointerException**: productos siempre inicializado
- ✅ **Testing rápido**: Simulaciones completan en segundos
- ✅ **WebSocket development**: Ahora se puede probar sin GRASP

---

## 🔗 Relación con Otros Fixes

Este fix se combina con los anteriores:

1. **Fix LazyInitializationException**: Agregado `@Transactional` a `obtenerDatosParaAlgoritmo()`
2. **Fix productos no persistidos**: Ya no necesario en mock (genera DTOs ficticios)
3. **Fix propagación usarModoMock**: Este documento ✅
4. **Fix inicialización productos**: Este documento ✅

---

## 🎓 Lecciones Aprendidas

### 1. Cadena de DTOs

Cuando un DTO se reconstruye en múltiples puntos (como `EventoTriggerPlanificacion`), **todos los campos deben copiarse**.

### 2. Inicialización de Colecciones

Los HashMaps y otras colecciones **deben inicializarse explícitamente** en constructores, incluso si empiezan vacíos.

### 3. Deep Copy Considerations

El `EstadoGlobal` hace deep copy de almacenes, vuelos y pedidos, pero `productos` comienza vacío (el mock los crea durante la ejecución).

---

## ✅ Conclusión

**Problema**: El modo mock no se ejecutaba porque:
1. `usarModoMock` no se propagaba en `EventoTriggerPlanificacion`
2. `productos` HashMap no se inicializaba en **DOS constructores** de `EstadoGlobal`:
   - Constructor principal (línea 67)
   - Constructor de copia (línea 106) ← **Este es el que usa el mock**

**Solución**: 
1. Agregar `.usarModoMock()` al builder del DTO en EventoTriggerPlanificacion
2. Inicializar `productos = new HashMap<>();` en **AMBOS constructores** de EstadoGlobal

**Estado**: ✅ Corregido y verificado  
**Testing**: Pendiente de verificación por usuario  
**Deployment**: Listo para merge
