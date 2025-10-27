# Análisis de Lógica de Eventos de Simulación

## 📋 Eventos Identificados

1. **EventoSimulacion** (Interface)
2. **EventoVueloSalida** - Vuelo sale del almacén
3. **EventoVueloLlegada** - Vuelo llega al almacén
4. **EventoEntregaPedidoTras2h** - Cliente recoge pedido
5. **EventoLlegadaPedido** - Nuevo pedido entra al sistema
6. **EventoTriggerPlanificacion** - Ejecuta el algoritmo
7. **EventoTriggerPlanificacionPeriodica** - Planificación recurrente

## ✅ Lógica Correcta

### 1. Prioridades (Orden de Ejecución)

```
Priority 0: EventoEntregaPedidoTras2h (primero)
Priority 1: EventoVueloSalida (segundo)
Priority 2: EventoVueloLlegada (tercero)
Priority 3: EventoLlegadaPedido (cuarto)
Priority 4: EventoTriggerPlanificacion (último)
```

**✅ CORRECTO**: El orden tiene sentido:
- Entregas primero (liberar espacio)
- Salidas de vuelos (cargar productos)
- Llegadas de vuelos (descargar productos)
- Nuevos pedidos (agregar demanda)
- Planificación (decide próximas acciones)

### 2. EventoVueloSalida - Análisis

**Flujo:**
1. Busca el vuelo por ID ✅
2. Busca almacén origen ✅
3. Obtiene productos a cargar con `ctx.obtenerProductosEnVueloId(idVuelo)` ✅
4. Solo muestra log si hay productos ✅
5. Quita productos del almacén (si no es infinito) ✅
6. Agrega productos al vuelo ✅

**❌ PROBLEMA ENCONTRADO:** Log duplicado
- Línea 73: Log dentro del `if (capacidadTotalACargar > 0)`
- Línea 91: Log duplicado fuera del `if`

**⚠️ ADVERTENCIA:** Si el almacén NO es infinito pero no tiene productos, lanza excepción correctamente.

### 3. EventoVueloLlegada - Análisis

**Flujo:**
1. Busca el vuelo por ID ✅
2. Busca almacén destino ✅
3. Obtiene productos del vuelo ✅
4. Solo muestra log si hay productos ✅
5. Agrega productos al almacén destino ✅
6. Quita productos del vuelo ✅
7. **Identifica si es vuelo final** en la ruta ✅
8. **Programa entrega** 2h después ✅

**✅ LÓGICA CORRECTA**

**⚠️ POSIBLE PROBLEMA:** 
```java
List<Programacion> rutasDondeElVueloEsFinal = ctx.getSolucionesAcumuladas().getLast()...
```
- Si `solucionesAcumuladas` está vacía, `getLast()` lanza `NoSuchElementException`
- **Ya corregido anteriormente** en ContextoSimulacion

### 4. EventoEntregaPedidoTras2h - Análisis

**Flujo:**
1. Busca pedido por ID
2. Busca almacén destino
3. Muestra log detallado ✅
4. Marca producto como entregado en pedido ✅
5. Quita producto del almacén ✅

**⚠️ PROBLEMA LÓGICO:**
```java
if (ctx.getEstado().entregarProductoEnPedido(idPedido, productoAEntregar)){}
    ctx.log("✅ EventoEntregaPedido: Producto entregado al cliente");
```

**Indentación incorrecta**: El log siempre se ejecuta, no está dentro del `if`

### 5. EventoTriggerPlanificacion - Análisis

**Flujo:**
1. Muestra inicio de planificación ✅
2. Crea DTO con parámetros ✅
3. Construye `EntradaProblemaPlanificacion` ✅
4. Ejecuta algoritmo con timeout ✅
5. **Muestra solución recibida** ✅
6. Valida si hay colapso ✅
7. Aplica solución al contexto ✅

**✅ LÓGICA CORRECTA**

### 6. EventoLlegadaPedido - Análisis

**Flujo:**
1. Busca pedido en el estado
2. Log simple
3. **NO hace nada más**

**⚠️ INCOMPLETO**: 
- No actualiza ningún estado
- Solo registra que llegó
- Podría disparar planificación por umbral (comentado)

### 7. EventoTriggerPlanificacionPeriodica - Análisis

**Flujo:**
1. Programa un `EventoTriggerPlanificacion` inmediato ✅
2. Lee configuración de BD para actualizar intervalo ✅
3. Se reprograma a sí mismo para el futuro ✅

**✅ LÓGICA CORRECTA**

## 🐛 Problemas Encontrados

### 1. ❌ EventoVueloSalida - Log Duplicado
**Archivo:** `EventoVueloSalida.java`
**Líneas:** 73 y 91

```java
// Línea 73 - dentro del if
ctx.log(String.format("🛫 VUELO SALIDA: ID=%d..."));

// Línea 91 - fuera del if (DUPLICADO)
ctx.log(String.format("🛫 VUELO SALIDA: ID=%d..."));
```

**Solución:** Eliminar el log de la línea 91

### 2. ⚠️ EventoEntregaPedidoTras2h - Indentación Incorrecta
**Archivo:** `EventoEntregaPedidoTras2h.java`
**Línea:** ~50

```java
if (ctx.getEstado().entregarProductoEnPedido(idPedido, productoAEntregar)){}
    ctx.log("✅ EventoEntregaPedido: Producto entregado al cliente");
```

**Problema:** El log se ejecuta siempre, no solo si la entrega fue exitosa

**Solución:** Corregir indentación o lógica del if

### 3. ⚠️ Imports No Usados
**Archivos múltiples**

- `EventoVueloSalida.java`: `SalidaProblemaPlanificacion`, `Programacion`, `ArrayList`
- `EventoTriggerPlanificacion.java`: `HashMap`

**Solución:** Limpiar imports

## ✅ Aspectos Positivos

1. **Manejo de errores**: Todos los eventos validan que existan las entidades
2. **Logs claros**: Mensajes descriptivos con emojis
3. **Separación de responsabilidades**: Cada evento hace UNA cosa
4. **Prioridades bien definidas**: Orden lógico de ejecución
5. **Validaciones de capacidad**: Previenen colapsos
6. **Solo logs relevantes**: Vuelos vacíos no se muestran

## 🔧 Recomendaciones

### Alta Prioridad
1. ✅ **Eliminar log duplicado** en EventoVueloSalida (línea 91)
2. ✅ **Corregir lógica del if** en EventoEntregaPedidoTras2h

### Media Prioridad
3. **Limpiar imports** no usados
4. **Agregar validación** en EventoVueloLlegada para `solucionesAcumuladas` vacías (si no se hizo)

### Baja Prioridad
5. **EventoLlegadaPedido**: Considerar si debe hacer algo más
6. **Logs a archivo**: Unificar formato de mensajes

## 📊 Flujo Completo Correcto

```
1. EventoTriggerPlanificacion
   ↓ Genera solución
   
2. EventoVueloSalida (con productos)
   ↓ Productos salen del almacén origen
   ↓ Productos se cargan en vuelo
   
3. EventoVueloLlegada (con productos)
   ↓ Productos llegan al almacén destino
   ↓ Productos se descargan del vuelo
   ↓ Si es vuelo final, programa entrega
   
4. EventoEntregaPedidoTras2h (2h después)
   ↓ Cliente recoge producto
   ↓ Producto sale del almacén
   ↓ Pedido se marca como entregado
```

## 🎯 Conclusión

**La lógica general es CORRECTA** con solo 2 bugs menores:
1. Log duplicado (cosmético)
2. Indentación incorrecta en if (lógico menor)

Ambos son fáciles de corregir.
