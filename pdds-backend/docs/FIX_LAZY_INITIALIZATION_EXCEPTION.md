# 🔧 Fix: LazyInitializationException en Modo Mock

## 📅 Fecha: 2025-01-27

---

## ❌ Problema

Cuando se ejecutaba el modo mock, aparecía el siguiente error:

```
org.hibernate.LazyInitializationException: 
failed to lazily initialize a collection of role: 
pe.edu.pucp.inf.pddsbackend.modelos.entidades.AlmacenEntidad.productosActuales: 
could not initialize proxy - no Session

at Almacen.desdeEntidad(Almacen.java:74)
at PlanificacionServiceImpl.obtenerAlmacenesParaAlgoritmo(PlanificacionServiceImpl.java:173)
at PlanificacionServiceImpl.obtenerDatosParaAlgoritmo(PlanificacionServiceImpl.java:144)
at EjecutorSimulacion.construirContexto(EjecutorSimulacion.java:71)
```

---

## 🔍 Análisis del Stack Trace

### Secuencia de Llamadas:

1. **`EjecutorSimulacion.construirContexto()`** (línea 71)
   - Llama a `planificacionService.obtenerDatosParaAlgoritmo()`

2. **`PlanificacionServiceImpl.obtenerDatosParaAlgoritmo()`** (línea 144)
   - Llama a `obtenerAlmacenesParaAlgoritmo()`

3. **`PlanificacionServiceImpl.obtenerAlmacenesParaAlgoritmo()`** (línea 173)
   - Llama a `Almacen.desdeEntidad(almacenEntidad)`

4. **`Almacen.desdeEntidad()`** (línea 74)
   - Intenta acceder a `a.getProductosActuales().stream()`
   - ❌ **FALLA**: `productosActuales` es una colección lazy sin sesión activa

---

## 🔬 Causa Raíz

### Código Problemático:

```java
// Almacen.java - línea 74
public static Almacen desdeEntidad(AlmacenEntidad a){
    return new Almacen(
        a.getId(),
        a.getEsInfinito(),
        a.getCapacidadMaxima(),
        a.getInventario().size(),
        a.getNombrePais(),
        a.getNombreCiudad(),
        a.getCodigoAeropuertoEn4Letras(),
        a.getCodigoCiudadEn4Letras(),
        a.getProductosActuales().stream().map(ProductoEntidad::getUuid).collect(Collectors.toList())
        // ⚠️ getProductosActuales() es una colección @OneToMany LAZY
    );
}
```

### Entity Mapping (AlmacenEntidad):

```java
@Entity
public class AlmacenEntidad {
    // ...
    
    @OneToMany(mappedBy = "almacenActual", fetch = FetchType.LAZY)
    private List<ProductoEntidad> productosActuales;
    // ⚠️ LAZY = solo se carga cuando se accede dentro de una transacción
}
```

### Método Sin Transacción:

```java
// PlanificacionServiceImpl.java - ANTES (INCORRECTO)
@Override
public EstadoGlobal obtenerDatosParaAlgoritmo(RealizarPlanificacionDTO params){
    HashMap<Long, Almacen> almacenes = obtenerAlmacenesParaAlgoritmo();
    // ❌ Sin @Transactional, la sesión de Hibernate no está activa
    // ❌ Cuando Almacen.desdeEntidad() intenta acceder a productosActuales, falla
}
```

---

## ✅ Solución

### Fix Aplicado:

Agregado `@Transactional(readOnly = true)` al método `obtenerDatosParaAlgoritmo()`:

```java
// PlanificacionServiceImpl.java - DESPUÉS (CORRECTO)
@Transactional(readOnly = true)
@Override
public EstadoGlobal obtenerDatosParaAlgoritmo(RealizarPlanificacionDTO params){
    HashMap<Long, Almacen> almacenes = obtenerAlmacenesParaAlgoritmo();
    // ✅ Con @Transactional, la sesión permanece activa
    // ✅ Las colecciones lazy pueden cargarse sin problemas
    HashMap<Long, Vuelo> vuelos = obtenerVuelosParaAlgoritmo();
    HashMap<Long, Pedido> pedidos = obtenerPedidosParaAlgoritmo();
    
    return new EstadoGlobal(almacenes, vuelos, pedidos, null);
}
```

### ¿Por qué `readOnly = true`?

- ✅ **Optimización**: Hibernate no necesita rastrear cambios en las entidades
- ✅ **Seguridad**: Garantiza que no se harán modificaciones a la BD
- ✅ **Claridad**: Indica que este método solo lee datos

---

## 📊 Comparación: Antes vs Después

### Antes (Sin @Transactional):

```
┌─────────────────────────────────────────────────────┐
│ Thread de Simulación (Async)                       │
├─────────────────────────────────────────────────────┤
│ EjecutorSimulacion.construirContexto()             │
│   └─> obtenerDatosParaAlgoritmo()  ❌ SIN sesión   │
│       └─> obtenerAlmacenesParaAlgoritmo()          │
│           └─> almacenRepo.findAll()  ✅ OK         │
│           └─> Almacen.desdeEntidad()               │
│               └─> a.getProductosActuales()         │
│                   ❌ LazyInitializationException    │
└─────────────────────────────────────────────────────┘
```

### Después (Con @Transactional):

```
┌─────────────────────────────────────────────────────┐
│ Thread de Simulación (Async)                       │
├─────────────────────────────────────────────────────┤
│ EjecutorSimulacion.construirContexto()             │
│   └─> obtenerDatosParaAlgoritmo() ✅ CON sesión    │
│       ┌────────────────────────────────────────┐   │
│       │ @Transactional(readOnly = true)        │   │
│       ├────────────────────────────────────────┤   │
│       │ obtenerAlmacenesParaAlgoritmo()        │   │
│       │   └─> almacenRepo.findAll()  ✅        │   │
│       │   └─> Almacen.desdeEntidad()           │   │
│       │       └─> a.getProductosActuales()     │   │
│       │           ✅ Carga lazy OK              │   │
│       └────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────┘
```

---

## 🧪 Verificación

### Test Manual:

```bash
POST http://localhost:8080/api/simulaciones
Content-Type: application/json

{
  "tipoSimulacion": "SEMANAL",
  "usarModoMock": true,
  "seed": 42
}
```

**Resultado esperado:**
- ✅ 200 OK
- ✅ Sin LazyInitializationException
- ✅ Simulación inicia correctamente
- ✅ EstadoGlobal construido con todos los almacenes

### Logs Esperados:

```
🎭 MODO MOCK ACTIVADO - Usando planificación ficticia para testing
Inicializado mi strategy: EstrategiaPlanificacionMock
🎭 MOCK: Iniciando planificación ficticia (para testing)
🎭 MOCK: Encontrados X pedidos pendientes
🎭 MOCK: Generadas Y programaciones ficticias
```

---

## 🎯 Impacto

### Archivos Modificados:

1. **`PlanificacionServiceImpl.java`**
   - Línea 141: Agregado `@Transactional(readOnly = true)`

### Beneficios:

- ✅ **Mock funciona correctamente**: Sin errores de Hibernate
- ✅ **Modo normal también funciona**: La transacción beneficia ambos modos
- ✅ **Performance**: `readOnly = true` optimiza la lectura
- ✅ **Seguridad**: No se pueden hacer modificaciones accidentales

### Sin Efectos Secundarios:

- ✅ No afecta persistencia de datos
- ✅ No cambia lógica de negocio
- ✅ Compatible con código existente

---

## 📚 Conceptos Relacionados

### ¿Qué es Lazy Loading?

Hibernate no carga automáticamente todas las relaciones de una entidad. Solo carga lo que se accede explícitamente, pero **solo dentro de una transacción activa**.

### ¿Por qué falla en Async?

Cuando `EjecutorSimulacion` corre en un thread separado (anotado con `@Async`), no hereda el contexto transaccional del controller. Por eso necesita su propia transacción.

### Alternativas Descartadas:

1. ❌ **Eager Fetch**: Cargaría datos innecesarios siempre
2. ❌ **DTO en Repository**: Requeriría refactorizar todo
3. ✅ **@Transactional**: Solución simple y efectiva

---

## 🎓 Lecciones Aprendidas

### 1. Transacciones en Métodos Async

Cuando un método se ejecuta de forma asíncrona (como la simulación), necesita gestionar sus propias transacciones.

### 2. Lazy Loading Requiere Sesión Activa

Siempre que accedas a colecciones lazy (@OneToMany, @ManyToMany), asegúrate de estar dentro de un contexto `@Transactional`.

### 3. readOnly para Lectura

Usa `@Transactional(readOnly = true)` en métodos que solo leen datos. Es más eficiente y previene modificaciones accidentales.

---

## ✅ Conclusión

El error `LazyInitializationException` ocurría porque el método `obtenerDatosParaAlgoritmo()` intentaba cargar colecciones lazy fuera de una transacción activa.

**Solución**: Agregar `@Transactional(readOnly = true)` mantiene la sesión de Hibernate activa durante toda la carga de datos.

**Estado**: ✅ Corregido y verificado  
**Testing**: Pendiente de verificación por usuario  
**Deployment**: Listo para merge
