# 🔌 Guía de WebSocket para Simulación PDDS

## 📋 Resumen

Se ha implementado WebSocket para transmitir eventos de simulación en tiempo real al frontend. Los eventos incluyen:
- ✈️ Salidas y llegadas de vuelos
- 📦 Entregas de pedidos
- 🎯 Ciclos de planificación
- 📊 Estado general de la simulación

---

## 🧪 Cómo Probar el WebSocket

### Opción 1: Archivo HTML de Prueba (Más Rápido)

1. **Inicia el backend:**
   ```bash
   cd d:/25-2/proyecto_dp1/pdds-backend
   ./mvnw spring-boot:run
   ```

2. **Abre el archivo de prueba:**
   - Abre `test-websocket.html` en tu navegador
   - O visita: `file:///d:/25-2/proyecto_dp1/pdds-backend/test-websocket.html`

3. **Configura la conexión:**
   - URL Backend: `http://localhost:8080`
   - ID Simulación: Usa el ID que genere tu simulación (ej: `sim-123`, `test-001`)

4. **Conecta:**
   - Click en "🔗 Conectar"
   - Deberías ver "✅ Conectado al servidor"

5. **Ejecuta una simulación desde Postman o Swagger:**
   - Endpoint: `POST /api/simulacion/ejecutar`
   - Usa el mismo `idSimulacion` que pusiste en el HTML
   - Los eventos aparecerán en tiempo real en el navegador

### Opción 2: Consola del Navegador (Para Testing Rápido)

Abre la consola del navegador en cualquier página y ejecuta:

```javascript
// 1. Conectar
const socket = new SockJS('http://localhost:8080/ws/simulacion');
const stompClient = Stomp.over(socket);

stompClient.connect({}, function(frame) {
    console.log('✅ Conectado:', frame);
    
    // 2. Suscribirse a una simulación
    stompClient.subscribe('/topic/simulacion/test-001', function(message) {
        const evento = JSON.parse(message.body);
        console.log('📡 Evento recibido:', evento);
    });
});
```

---

## 🏗️ Arquitectura Implementada

### Backend (Spring Boot)

```
websocket/
├── config/
│   └── WebSocketConfig.java          # Configuración STOMP + SockJS
├── dto/
│   ├── EventoSimulacionBaseDTO.java  # Clase base con polimorfismo
│   ├── EventoVueloSalidaDTO.java     # DTO para salidas de vuelo
│   ├── EventoVueloLlegadaDTO.java    # DTO para llegadas de vuelo
│   ├── EventoEntregaPedidoDTO.java   # DTO para entregas
│   ├── EventoPlanificacionDTO.java   # DTO para planificación
│   └── EventoEstadoSimulacionDTO.java # DTO para estado general
└── service/
    └── SimulacionWebSocketService.java # Servicio para enviar eventos
```

### Endpoints WebSocket

- **Conexión:** `ws://localhost:8080/ws/simulacion`
- **Topics de suscripción:**
  - `/topic/simulacion/{idSimulacion}` - Todos los eventos
  - `/topic/simulacion/{idSimulacion}/estado` - Solo estado general

---

## 🔗 Integración con Eventos de Simulación

Para enviar eventos desde la simulación, inyecta el servicio WebSocket:

```java
@Service
public class EventoVueloSalida implements Evento {
    
    @Autowired
    private SimulacionWebSocketService webSocketService;
    
    @Override
    public void ejecutar(ContextoSimulacion ctx) {
        // ... lógica existente ...
        
        // Enviar evento por WebSocket
        if (capacidadTotalACargar > 0) {
            webSocketService.enviarEventoVueloSalida(
                ctx.getIdSimulacion(),
                ctx.getHoraActual(),
                vuelo.getId().toString(),
                vuelo.getCodigo(),
                origen.getId(),
                origen.getNombre(),
                destino.getId(),
                destino.getNombre(),
                vuelo.getCapacidad(),
                capacidadTotalACargar,
                productosUUIDs
            );
        }
    }
}
```

### Métodos Disponibles del Servicio

```java
// Vuelo salida
webSocketService.enviarEventoVueloSalida(
    idSimulacion, horaSimulacion,
    idVuelo, codigoVuelo,
    idAlmacenOrigen, nombreAlmacenOrigen,
    idAlmacenDestino, nombreAlmacenDestino,
    capacidadVuelo, capacidadOcupada,
    productosUUIDs
);

// Vuelo llegada
webSocketService.enviarEventoVueloLlegada(
    idSimulacion, horaSimulacion,
    idVuelo, codigoVuelo,
    idAlmacenDestino, nombreAlmacenDestino,
    cantidadDescargada, productosDescargados,
    entregasInmediatas, productosEnTransito
);

// Entrega pedido
webSocketService.enviarEventoEntregaPedido(
    idSimulacion, horaSimulacion,
    idPedido, productoUUID,
    idAlmacen, nombreAlmacen,
    exitoso, mensaje
);

// Planificación inicio
webSocketService.enviarEventoPlanificacionInicio(
    idSimulacion, horaSimulacion,
    pedidosPendientes
);

// Planificación completada
webSocketService.enviarEventoPlanificacionCompletada(
    idSimulacion, horaSimulacion,
    pedidosPendientes, programacionesGeneradas,
    duracionMs, programaciones
);

// Estado general
webSocketService.enviarEstadoSimulacion(
    idSimulacion, horaSimulacion,
    totalVuelosActivos, totalPedidosPendientes,
    totalPedidosEntregados, productosEnAlmacenes,
    porcentajeCompletado
);
```

---

## 🎨 Integración en Angular Frontend

### 1. Instalar dependencias

```bash
npm install sockjs-client @stomp/stompjs
```

### 2. Crear servicio WebSocket

```typescript
// websocket.service.ts
import { Injectable } from '@angular/core';
import * as SockJS from 'sockjs-client';
import { Stomp, CompatClient } from '@stomp/stompjs';
import { Subject, Observable } from 'rxjs';

@Injectable({
  providedIn: 'root'
})
export class WebSocketService {
  private stompClient: CompatClient | null = null;
  private eventosSubject = new Subject<any>();

  conectar(idSimulacion: string): void {
    const socket = new SockJS('http://localhost:8080/ws/simulacion');
    this.stompClient = Stomp.over(socket);

    this.stompClient.connect({}, (frame) => {
      console.log('Conectado:', frame);

      // Suscribirse a eventos de la simulación
      this.stompClient?.subscribe(
        `/topic/simulacion/${idSimulacion}`,
        (message) => {
          const evento = JSON.parse(message.body);
          this.eventosSubject.next(evento);
        }
      );
    });
  }

  desconectar(): void {
    if (this.stompClient) {
      this.stompClient.disconnect();
    }
  }

  getEventos(): Observable<any> {
    return this.eventosSubject.asObservable();
  }
}
```

### 3. Usar en componente

```typescript
// simulacion.component.ts
import { Component, OnInit, OnDestroy } from '@angular/core';
import { WebSocketService } from './websocket.service';

@Component({
  selector: 'app-simulacion',
  templateUrl: './simulacion.component.html'
})
export class SimulacionComponent implements OnInit, OnDestroy {
  eventos: any[] = [];

  constructor(private wsService: WebSocketService) {}

  ngOnInit() {
    const idSimulacion = 'sim-123'; // Obtener del contexto
    
    this.wsService.conectar(idSimulacion);
    
    this.wsService.getEventos().subscribe(evento => {
      console.log('Evento recibido:', evento);
      this.eventos.unshift(evento); // Agregar al inicio
      
      // Procesar según tipo
      switch(evento.tipoEvento) {
        case 'VUELO_SALIDA':
          this.animarVueloSalida(evento);
          break;
        case 'VUELO_LLEGADA':
          this.animarVueloLlegada(evento);
          break;
        case 'ENTREGA_PEDIDO':
          this.mostrarNotificacionEntrega(evento);
          break;
        case 'PLANIFICACION':
          this.actualizarEstadoPlanificacion(evento);
          break;
      }
    });
  }

  ngOnDestroy() {
    this.wsService.desconectar();
  }

  animarVueloSalida(evento: any) {
    // Tu lógica para animar vuelos en el mapa
  }

  animarVueloLlegada(evento: any) {
    // Tu lógica para animar llegadas
  }

  mostrarNotificacionEntrega(evento: any) {
    // Mostrar notificación de entrega
  }

  actualizarEstadoPlanificacion(evento: any) {
    // Actualizar UI de planificación
  }
}
```

---

## 📊 Estructura de Eventos

### EventoVueloSalidaDTO
```json
{
  "tipoEvento": "VUELO_SALIDA",
  "idSimulacion": "sim-123",
  "horaSimulacion": "2024-01-15T10:30:00",
  "timestampReal": "2024-01-15T10:30:05",
  "idVuelo": "1",
  "codigoVuelo": "VP001",
  "idAlmacenOrigen": 1,
  "nombreAlmacenOrigen": "Lima",
  "idAlmacenDestino": 2,
  "nombreAlmacenDestino": "Cusco",
  "capacidadVuelo": 100,
  "capacidadOcupada": 45,
  "productosUUIDs": ["uuid1", "uuid2", ...]
}
```

### EventoVueloLlegadaDTO
```json
{
  "tipoEvento": "VUELO_LLEGADA",
  "idSimulacion": "sim-123",
  "horaSimulacion": "2024-01-15T12:30:00",
  "idVuelo": "1",
  "codigoVuelo": "VP001",
  "idAlmacenDestino": 2,
  "nombreAlmacenDestino": "Cusco",
  "cantidadDescargada": 45,
  "productosDescargados": ["uuid1", "uuid2"],
  "entregasInmediatas": 30,
  "productosEnTransito": 15
}
```

### EventoEntregaPedidoDTO
```json
{
  "tipoEvento": "ENTREGA_PEDIDO",
  "idSimulacion": "sim-123",
  "horaSimulacion": "2024-01-15T14:30:00",
  "idPedido": 42,
  "productoUUID": "uuid123",
  "idAlmacen": 2,
  "nombreAlmacen": "Cusco",
  "exitoso": true,
  "mensaje": "Producto entregado exitosamente"
}
```

### EventoPlanificacionDTO
```json
{
  "tipoEvento": "PLANIFICACION",
  "idSimulacion": "sim-123",
  "horaSimulacion": "2024-01-15T08:00:00",
  "fase": "COMPLETADA",  // "INICIO" | "COMPLETADA" | "TIMEOUT" | "ERROR"
  "pedidosPendientes": 150,
  "programacionesGeneradas": 145,
  "duracionMs": 5234,
  "programaciones": [
    {
      "idPedido": 42,
      "productoUUID": "uuid123",
      "rutaVuelos": ["VP001", "VP045"]
    }
  ]
}
```

### EventoEstadoSimulacionDTO
```json
{
  "tipoEvento": "ESTADO_SIMULACION",
  "idSimulacion": "sim-123",
  "horaSimulacion": "2024-01-15T10:00:00",
  "totalVuelosActivos": 12,
  "totalPedidosPendientes": 145,
  "totalPedidosEntregados": 305,
  "productosEnAlmacenes": {
    "1": 45,
    "2": 67,
    "3": 23
  },
  "porcentajeCompletado": 67.8
}
```

---

## 🔧 Configuración CORS

Si tienes problemas de CORS, ajusta `WebSocketConfig.java`:

```java
@Override
public void registerStompEndpoints(StompEndpointRegistry registry) {
    registry.addEndpoint("/ws/simulacion")
            .setAllowedOriginPatterns("*") // En producción, especifica dominios exactos
            .withSockJS();
}
```

---

## ✅ Checklist de Integración

- [x] Dependencia `spring-boot-starter-websocket` agregada a pom.xml
- [x] WebSocketConfig creado y configurado
- [x] DTOs creados para todos los tipos de eventos
- [x] SimulacionWebSocketService implementado
- [ ] Servicio inyectado en clases de eventos (EventoVueloSalida, etc.)
- [ ] Llamadas a webSocketService agregadas en eventos
- [ ] Frontend Angular configurado con SockJS/STOMP
- [ ] Componentes suscritos a eventos WebSocket
- [ ] Animaciones/visualizaciones conectadas a eventos

---

## 🐛 Troubleshooting

### Error: "Cannot find SockJS"
- Asegúrate de incluir las librerías en el HTML o instalarlas con npm

### Error: "Connection refused"
- Verifica que el backend esté corriendo en el puerto correcto
- Revisa que no haya firewall bloqueando WebSocket

### No se reciben eventos
- Verifica que el `idSimulacion` coincida entre frontend y backend
- Revisa la consola del navegador para errores de suscripción
- Confirma que los eventos se estén enviando desde el backend (agrega logs)

### Eventos duplicados
- Asegúrate de desuscribirte al destruir componentes Angular
- Evita múltiples conexiones simultáneas

---

## 📚 Próximos Pasos

1. **Integrar en eventos de simulación:**
   - Agregar llamadas a `webSocketService` en cada clase de evento

2. **Frontend Angular:**
   - Crear servicio WebSocket
   - Conectar al mapa de Leaflet para animaciones
   - Mostrar notificaciones en tiempo real

3. **Mejoras opcionales:**
   - Autenticación de WebSocket con tokens
   - Reconexión automática en caso de desconexión
   - Historial de eventos en base de datos
   - Replay de simulaciones anteriores

---

## 📞 Soporte

Si tienes dudas o problemas:
1. Verifica los logs del backend
2. Revisa la consola del navegador
3. Usa el archivo `test-websocket.html` para debugging
4. Confirma que todos los servicios estén inyectados correctamente

¡Buena suerte con la integración! 🚀
