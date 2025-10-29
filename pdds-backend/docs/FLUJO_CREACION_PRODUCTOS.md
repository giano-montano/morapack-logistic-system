# 🔄 Flujo de Creación de Productos

## 📌 Resumen

Este documento explica cómo se crean y persisten los productos en el sistema PDDS, tanto en el algoritmo real (GRASP) como en el modo mock.

---

## 🎯 Conceptos Clave

### Producto (Dominio)
- **Clase**: `pe.edu.pucp.inf.pddsbackend.modelos.dominio.Producto`
- **Propósito**: Representa un producto durante la ejecución del algoritmo
- **UUID**: Generado automáticamente con `UUID.randomUUID()`
- **Estado**: Solo existe en memoria durante la planificación

### ProductoEntidad (Persistencia)
- **Clase**: `pe.edu.pucp.inf.pddsbackend.modelos.entidades.ProductoEntidad`
- **Propósito**: Entidad JPA para persistir productos en la base de datos
- **Tabla**: `producto`
- **Relaciones**: 
  - `almacenInfinitoOrigen` (ManyToOne)
  - `vuelosRuta` (ManyToMany)

---

## 🔄 Flujo Completo de Creación

### 1️⃣ Durante la Planificación (Algoritmo)

```java
// El algoritmo (GRASP o Mock) crea productos EN MEMORIA
Producto producto = new Producto(
    idAlmacenOrigen,  // De dónde sale
    rutaVuelos        // Lista de IDs de vuelos
);

// Se genera UUID automáticamente en el constructor
UUID uuid = producto.getUuid(); // Ej: "f47ac10b-58cc-4372-a567-0e02b2c3d479"

// Se añade al estado global (solo en memoria)
estadoGlobal.anadirProducto(producto);
```

**⚠️ IMPORTANTE**: En este punto el producto NO está en la base de datos, solo existe en memoria dentro del `EstadoGlobal`.

### 2️⃣ Generación de Programaciones

```java
// El algoritmo crea programaciones que referencian el UUID del producto
Programacion programacion = new Programacion(
    idPedido,           // ID del pedido a satisfacer
    producto.getUuid(), // UUID del producto creado
    rutaVuelos          // IDs de vuelos de la ruta
);

// Esta programación se añade a la solución
solucion.getProgramaciones().add(programacion);
```

### 3️⃣ Mapeo de Solución a Response DTO

Cuando el algoritmo termina, se llama a `mapearSolucionAResponse()`:

```java
@Transactional(readOnly = true)
protected PlanificacionResponseDTO mapearSolucionAResponse(ResultadoAlgoritmoDTO resultado) {
    for (Programacion programacion : solucion.getProgramaciones()) {
        // 🔥 AQUÍ SE PERSISTE EL PRODUCTO
        ProductoEntidad producto = obtenerProductoEntidadOCrearlo(programacion);
        
        // El producto ahora está en la BD y se incluye en el DTO de respuesta
        ProductoSolucionDTO productoDto = new ProductoSolucionDTO(
            producto.getUuid(),
            producto.getAlmacenInfinitoOrigen().getId(),
            producto.getExiste()
        );
    }
}
```

### 4️⃣ Persistencia en Base de Datos

El método `obtenerProductoEntidadOCrearlo()` es el responsable de persistir:

```java
private ProductoEntidad obtenerProductoEntidadOCrearlo(Programacion programacion) {
    // 1. Buscar si ya existe en la BD
    Optional<ProductoEntidad> productoExistente = 
        productoRepository.findByUuid(programacion.getUuidProducto());
    
    if (productoExistente.isPresent()) {
        return productoExistente.get();
    }
    
    // 2. Si no existe, obtener datos de los vuelos
    List<VueloEntidad> vuelos = 
        vueloRepository.findAllById(programacion.getIdsVueloRuta());
    
    if (vuelos.isEmpty()) {
        throw new IllegalStateException("No se encontraron vuelos...");
    }
    
    // 3. Crear ProductoEntidad
    ProductoEntidad nuevoProducto = ProductoEntidad.builder()
        .uuid(programacion.getUuidProducto())
        .existe(false)
        .fechaPlanificacion(instanteUltimoPlanificacion)
        .vuelosRuta(vuelos)
        .fechaExistencia(vuelos.get(0).getFechaHoraInicioUtc())
        .almacenInfinitoOrigen(vuelos.get(0).getAlmacenOrigen())
        .build();
    
    // 4. ⚠️ PERSISTIR EN LA BASE DE DATOS
    return productoRepository.save(nuevoProducto);
}
```

---

## 🎭 Modo Mock vs GRASP Real

### Modo Mock

```java
@Component
public class EstrategiaPlanificacionMock extends EstrategiaPlanificacion {
    
    private Producto crearProductoMock(EstadoGlobal estadoGlobal, 
                                       Almacen almacenOrigen, 
                                       LinkedList<Long> ruta) {
        // Crear producto con UUID aleatorio
        Producto producto = new Producto(almacenOrigen.getId(), ruta);
        
        // Añadir al estado global (solo memoria)
        estadoGlobal.anadirProducto(producto);
        
        return producto;
    }
}
```

**Características**:
- ✅ Genera UUIDs válidos automáticamente
- ✅ Rutas simples (1-2 vuelos)
- ✅ No valida restricciones complejas
- ✅ Ejecución instantánea (< 1 segundo)

### GRASP Real

```java
@Component
public class EstrategiaGraspHibrido extends EstrategiaPlanificacion {
    
    private void construirSolucionGreedy() {
        // Validaciones complejas
        // Cálculo de fitness
        // Optimización iterativa
        
        // Crea productos solo cuando encuentra solución válida
        Producto producto = new Producto(almacen, ruta);
        estadoGlobal.anadirProducto(producto);
    }
}
```

**Características**:
- ✅ Validaciones exhaustivas
- ✅ Optimización de rutas
- ✅ Manejo de capacidades
- ⏱️ Ejecución lenta (30-300 segundos)
- ⚠️ Puede fallar si no hay solución factible

---

## 🔍 Verificación de Persistencia

### SQL Query para Verificar Productos

```sql
-- Ver todos los productos creados
SELECT 
    uuid,
    existe,
    fecha_planificacion,
    fecha_existencia,
    almacen_infinito_origen_id
FROM producto
ORDER BY fecha_planificacion DESC;

-- Contar productos por simulación
SELECT COUNT(*) as total_productos
FROM producto
WHERE fecha_planificacion >= '2025-01-27';
```

### Endpoint REST para Verificar

```bash
# Listar todos los productos
GET http://localhost:8080/api/productos

# Buscar producto por UUID
GET http://localhost:8080/api/productos/{uuid}
```

---

## ⚠️ Problemas Comunes

### Problema 1: "No se encontraron vuelos para la programación"

**Causa**: Los IDs de vuelos en `programacion.getIdsVueloRuta()` no existen en la BD.

**Solución**: Verificar que el algoritmo (o mock) esté usando IDs de vuelos válidos del `EstadoGlobal`.

### Problema 2: "Productos con UUID duplicado"

**Causa**: Se intenta crear dos productos con el mismo UUID.

**Solución**: El método `obtenerProductoEntidadOCrearlo()` ya maneja esto con `findByUuid()` antes de crear.

### Problema 3: "Productos no aparecen en la BD después de planificación"

**Causa**: No se estaba llamando a `productoRepository.save()`.

**Solución**: ✅ Ya corregido en este commit. El método ahora persiste correctamente.

---

## 🧪 Testing del Flujo Completo

### Test con Modo Mock

```json
POST http://localhost:8080/api/simulaciones
Content-Type: application/json

{
  "tipoSimulacion": "SEMANAL",
  "usarModoMock": true,
  "seed": 42
}
```

**Verificación**:
1. Ejecutar simulación
2. Esperar a que termine (aprox. 10-30 segundos)
3. Consultar productos: `GET /api/productos`
4. Verificar que los UUIDs coinciden con las programaciones

### Test con GRASP Real

```json
POST http://localhost:8080/api/simulaciones
Content-Type: application/json

{
  "tipoSimulacion": "SEMANAL",
  "usarModoMock": false,
  "estrategiaFija": "AUTO"
}
```

**Verificación**: Igual que arriba, pero tomará más tiempo.

---

## 📊 Diagrama de Secuencia

```
┌─────────┐     ┌───────────┐     ┌─────────┐     ┌──────────┐
│Algoritmo│     │EstadoGlobal│     │Service   │     │Database  │
└────┬────┘     └─────┬─────┘     └────┬────┘     └────┬─────┘
     │                │                 │               │
     │ new Producto() │                 │               │
     │───────────────>│                 │               │
     │                │                 │               │
     │ anadirProducto()                 │               │
     │───────────────>│                 │               │
     │                │ (solo memoria)  │               │
     │                │                 │               │
     │ new Programacion(uuid, ruta)     │               │
     │────────────────────────────────>│               │
     │                │                 │               │
     │                │ obtenerProducto │               │
     │                │ EntidadOCrearlo()              │
     │                │<────────────────│               │
     │                │                 │               │
     │                │                 │ findByUuid()  │
     │                │                 │──────────────>│
     │                │                 │               │
     │                │                 │ (no existe)   │
     │                │                 │<──────────────│
     │                │                 │               │
     │                │                 │ save(producto)│
     │                │                 │──────────────>│
     │                │                 │               │
     │                │                 │ ✅ persistido │
     │                │                 │<──────────────│
```

---

## 🎓 Conclusión

El flujo de creación de productos sigue estos pasos:

1. **Algoritmo** crea `Producto` (dominio) con UUID en memoria
2. **Algoritmo** genera `Programacion` que referencia ese UUID
3. **Service** mapea solución y llama a `obtenerProductoEntidadOCrearlo()`
4. **Este método** persiste el `ProductoEntidad` en la BD si no existe

✅ **Flujo correcto**: Los productos se crean SOLO cuando la planificación es exitosa.

❌ **Error anterior**: Se creaba el objeto pero no se persistía con `.save()`.

🎭 **Modo Mock**: Genera productos ficticios pero válidos para testing sin depender del algoritmo GRASP.
