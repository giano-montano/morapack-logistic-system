# Logs Detallados de Simulación

## 🎯 Objetivo

La simulación ahora imprime logs detallados en consola para **debugging** y para preparar la **conexión con WebSocket** del frontend.

## ⚠️ Comportamiento Importante

- **Vuelos vacíos NO se muestran en consola** - Solo se registran vuelos que llevan productos
- **Solución completa se imprime** al recibirla de la planificación
- Logs claros y concisos para facilitar debugging

## 📋 Tipos de Eventos con Logs Mejorados

### 0. 📊 Solución Recibida (al completar planificación)

**Información mostrada:**
```
📊 ========= SOLUCIÓN RECIBIDA =========
⏰ Hora: 2025-01-15T12:00:05Z
📦 Total Programaciones: 5
🔍 Detalle de Programaciones:
  1) Pedido ID=15 | Producto UUID=a1b2c3d4-... | Ruta (vuelos): [42, 58, 73]
  2) Pedido ID=18 | Producto UUID=e5f6g7h8-... | Ruta (vuelos): [42]
  3) Pedido ID=22 | Producto UUID=i9j0k1l2-... | Ruta (vuelos): [58, 73]
  4) Pedido ID=25 | Producto UUID=m3n4o5p6-... | Ruta (vuelos): [101, 115]
  5) Pedido ID=30 | Producto UUID=q7r8s9t0-... | Ruta (vuelos): [115]
=========================================
```

**Datos incluidos:**
- ⏰ Hora de la solución
- 📦 Número total de programaciones
- 🔍 Detalle de CADA programación:
  - ID del pedido
  - UUID del producto asignado
  - Lista completa de vuelos (IDs) en orden

### 1. 🛫 Salida de Vuelo (EventoVueloSalida)

**⚠️ SOLO SE MUESTRA SI EL VUELO LLEVA PRODUCTOS**

**Información mostrada:**
```
🛫 =============== VUELO SALIENDO ===============
⏰ Hora: 2025-01-15T10:30:00Z
✈️  ID Vuelo: 42
📦 Almacén Origen: ID=5 (Ocupado: 80/150)
🎯 Almacén Destino: ID=12
📊 Cantidad Productos: 3 (Capacidad vuelo: 17/20)
📦 Productos en este vuelo:
   • Producto UUID: a1b2c3d4-... | Entregado: false | Existe: true
   • Producto UUID: e5f6g7h8-... | Entregado: false | Existe: true
   • Producto UUID: i9j0k1l2-... | Entregado: false | Existe: true
===============================================
```

**Datos incluidos:**
- ⏰ Hora exacta de salida
- ✈️ ID del vuelo
- 📦 ID almacén origen + capacidad ocupada/máxima
- 🎯 ID almacén destino
- 📊 Cantidad de productos + capacidad del vuelo
- 📦 **Lista de UUIDs de cada producto** que va en el vuelo
- Estado de cada producto (existe, entregado)

### 2. 🛬 Llegada de Vuelo (EventoVueloLlegada)

**⚠️ SOLO SE MUESTRA SI EL VUELO TRAE PRODUCTOS**

**Información mostrada:**
```
🛬 =============== VUELO LLEGANDO ===============
⏰ Hora: 2025-01-15T14:45:00Z
✈️  ID Vuelo: 42
📍 Almacén Origen: ID=5
🎯 Almacén Destino: ID=12 (Ocupado: 65/150)
📊 Cantidad Productos: 3
📦 Productos en este vuelo:
   • Producto UUID: a1b2c3d4-... | Entregado: false
   • Producto UUID: e5f6g7h8-... | Entregado: false
   • Producto UUID: i9j0k1l2-... | Entregado: false
===============================================
```

**Datos incluidos:**
- ⏰ Hora exacta de llegada
- ✈️ ID del vuelo
- 📍 ID almacén origen (de donde salió)
- 🎯 ID almacén destino + capacidad actual
- 📊 Cantidad de productos descargados
- 📦 **Lista de UUIDs de productos** que llegan

### 3. 📦 Entrega de Pedido (EventoEntregaPedidoTras2h)

**Información mostrada:**
```
📦 ============= ENTREGA DE PEDIDO =============
⏰ Hora: 2025-01-15T16:45:00Z
📋 ID Pedido: 15
🎯 Almacén Destino: ID=12
📦 Producto UUID: a1b2c3d4-...
📊 Estado Pedido: 2/5 entregados
===============================================
```

**Datos incluidos:**
- ⏰ Hora de entrega (2 horas después de llegada)
- 📋 ID del pedido
- 🎯 ID del almacén donde se recoge
- 📦 UUID del producto entregado
- 📊 Progreso del pedido (X/Y entregados)

### 4. 📋 Trigger de Planificación (EventoTriggerPlanificacion)

**Información mostrada (inicio):**
```
📋 =========== TRIGGER PLANIFICACIÓN ===========
⏰ Hora: 2025-01-15T12:00:00Z
🔢 Número de planificación: 2
📊 Pedidos pendientes: 7
===============================================
```

**Información mostrada (resultado):**
```
✅ ========= PLANIFICACIÓN COMPLETADA =========
⏰ Hora: 2025-01-15T12:00:05Z
📦 Programaciones generadas: 5
⚡ Tiempo ejecución: 4523 ms
📈 Fitness: 0.85
===============================================
```

**O si hay timeout:**
```
⏱️  ========= TIMEOUT PLANIFICACIÓN =========
⏰ Hora: 2025-01-15T12:05:00Z
⚠️  El algoritmo excedió el tiempo máximo
===============================================
```

**Datos incluidos:**
- ⏰ Hora de inicio/fin de planificación
- 🔢 Número de planificación en la simulación
- 📊 Cantidad de pedidos pendientes
- 📦 Número de programaciones generadas
- ⚡ Tiempo de ejecución del algoritmo
- 📈 Fitness de la solución

## 🔗 Preparación para WebSocket

Estos logs están diseñados para ser fácilmente convertidos a mensajes WebSocket:

### Estructura sugerida para mensajes WebSocket:

```json
{
  "tipo": "VUELO_SALIDA",
  "timestamp": "2025-01-15T10:30:00Z",
  "datos": {
    "idVuelo": 42,
    "almacenOrigen": {
      "id": 5,
      "capacidadOcupada": 80,
      "capacidadMaxima": 150
    },
    "almacenDestino": {
      "id": 12
    },
    "productos": [
      {
        "uuid": "a1b2c3d4-...",
        "entregado": false,
        "existe": true
      }
    ],
    "cantidadProductos": 3,
    "capacidadVuelo": {
      "ocupada": 3,
      "maxima": 20
    }
  }
}
```

### Tipos de mensajes WebSocket sugeridos:

```typescript
type TipoEvento = 
  | "VUELO_SALIDA"
  | "VUELO_LLEGADA" 
  | "PEDIDO_ENTREGADO"
  | "PLANIFICACION_INICIO"
  | "PLANIFICACION_COMPLETADA"
  | "PLANIFICACION_TIMEOUT"
  | "SIMULACION_INICIADA"
  | "SIMULACION_FINALIZADA";

interface MensajeWebSocket {
  tipo: TipoEvento;
  timestamp: string; // ISO 8601
  datos: any; // Específico según tipo
}
```

## 📝 Logs en Archivo vs Consola

### Consola (System.out.println):
- ✅ Formato visual con emojis
- ✅ Fácil de leer durante desarrollo
- ✅ Separadores claros entre eventos
- ✅ Información detallada pero concisa

### Archivo de Log (ctx.log):
- ✅ Formato más compacto
- ✅ Incluye timestamp automático
- ✅ Para análisis posterior
- ✅ Sin emojis (compatible con cualquier visor)

## 🎨 Emojis Utilizados

| Emoji | Significado |
|-------|-------------|
| 🛫 | Vuelo saliendo |
| 🛬 | Vuelo llegando |
| 📦 | Producto/Paquete |
| 📋 | Planificación |
| ⏰ | Timestamp |
| ✈️ | ID Vuelo |
| 🎯 | Destino |
| 📍 | Origen |
| 📊 | Estadísticas/Métricas |
| ✅ | Éxito |
| ❌ | Error |
| ⚠️ | Advertencia |
| ⏱️ | Timeout |
| 🔢 | Número/Contador |
| 📈 | Fitness/Calidad |
| ⚡ | Velocidad/Tiempo |

## 🚀 Próximos Pasos para WebSocket

### En el Backend:

1. **Crear servicio WebSocket:**
```java
@Service
public class SimulacionWebSocketService {
    private final SimpMessagingTemplate messagingTemplate;
    
    public void enviarEventoVueloSalida(EventoVueloSalidaDTO evento) {
        messagingTemplate.convertAndSend("/topic/simulacion", evento);
    }
}
```

2. **Inyectar en eventos:**
```java
// En EventoVueloSalida.procesar()
if (webSocketService != null) {
    webSocketService.enviarEventoVueloSalida(crearDTO());
}
```

3. **DTOs para WebSocket:**
```java
@Data
public class EventoVueloSalidaDTO {
    private String tipo = "VUELO_SALIDA";
    private Instant timestamp;
    private Long idVuelo;
    private AlmacenSimpleDTO almacenOrigen;
    private AlmacenSimpleDTO almacenDestino;
    private List<ProductoSimpleDTO> productos;
}
```

### En el Frontend (Angular):

```typescript
// Conectar WebSocket
const stompClient = new StompClient({
  brokerURL: 'ws://localhost:8080/ws-simulacion'
});

// Suscribirse a eventos
stompClient.subscribe('/topic/simulacion', (message) => {
  const evento = JSON.parse(message.body);
  
  switch(evento.tipo) {
    case 'VUELO_SALIDA':
      mostrarVueloSaliendo(evento);
      break;
    case 'VUELO_LLEGADA':
      mostrarVueloLlegando(evento);
      break;
    // ... otros casos
  }
});
```

## ✅ Beneficios de estos Logs

1. **Debugging fácil:** Ver exactamente qué pasa en cada momento
2. **Trazabilidad:** Seguir un producto desde su origen hasta entrega
3. **Preparación WebSocket:** Estructura clara para mensajes en tiempo real
4. **Visualización:** Información lista para animaciones en frontend
5. **Métricas:** Datos para dashboards y reportes

## 📌 Ejemplo de Flujo Completo en Logs

```
� TRIGGER PLANIFICACIÓN - #1 | 10 pedidos pendientes
  └─ Ejecutando algoritmo...

📊 SOLUCIÓN RECIBIDA
  ├─ 1) Pedido 15 | Producto a1b2c3d4 | Ruta: [42, 58]
  ├─ 2) Pedido 18 | Producto e5f6g7h8 | Ruta: [42]
  └─ 3) Pedido 22 | Producto i9j0k1l2 | Ruta: [58]

✅ PLANIFICACIÓN COMPLETADA - 3 programaciones | 2.3s

�🛫 VUELO SALIENDO - Vuelo 42 | Origen 5 → Destino 12 | 2 productos
  ├─ Producto a1b2c3d4 (para pedido 15)
  └─ Producto e5f6g7h8 (para pedido 18)

🛬 VUELO LLEGANDO - Vuelo 42 | Destino 12 | 2 productos
  └─ Descargando en almacén 12

🛫 VUELO SALIENDO - Vuelo 58 | Origen 12 → Destino 20 | 2 productos
  ├─ Producto a1b2c3d4 (continuando ruta)
  └─ Producto i9j0k1l2 (para pedido 22)

🛬 VUELO LLEGANDO - Vuelo 58 | Destino 20 | 2 productos
  └─ Descargando en almacén 20

📦 ENTREGA PEDIDO - Pedido 15 | Producto a1b2c3d4
  └─ Cliente recoge desde almacén 20

📦 ENTREGA PEDIDO - Pedido 18 | Producto e5f6g7h8
  └─ Cliente recoge desde almacén 12

� ENTREGA PEDIDO - Pedido 22 | Producto i9j0k1l2
  └─ Cliente recoge desde almacén 20
```

**Nota:** Los vuelos que no llevan productos NO aparecen en los logs de consola.
