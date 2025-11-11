# 📋 Ejemplos JSON para Endpoint de Planificación

## Endpoint
```
POST /api/planificaciones
Content-Type: application/json
```

---

## 📝 Estructura del DTO: `RealizarPlanificacionDTO`

### Parámetros

| Campo | Tipo | Requerido | Descripción | Valor por defecto |
|-------|------|-----------|-------------|-------------------|
| **`estrategiaFija`** | `String` (Enum) | ❌ No | Estrategia del algoritmo: `RAPIDA`, `PROFUNDA`, `AUTO` | `RAPIDA` |
| **`parametros`** | `Array<Object>` | ❌ No | Parámetros opcionales personalizados para el algoritmo | `null` |
| **`instanteActual`** | `String` (ISO-8601) | ✅ Sí | Fecha/hora actual de la simulación | - |
| **`instanteDesdeTomarPedidos`** | `String` (ISO-8601) | ❌ No | Fecha desde la cual tomar pedidos (si no se especifica, se toman pedidos de los últimos 30 días) | 30 días antes de `instanteActual` |
| **`idSimulacion`** | `Long` | ❌ No | ID de la simulación asociada | `null` |
| **`seed`** | `Long` | ❌ No | Semilla para reproducibilidad del algoritmo aleatorio | Random |
| **`subCarpetaReportes`** | `String` | ❌ No | Nombre de subcarpeta donde guardar reportes | `null` |
| **`loggear`** | `Boolean` | ❌ No | Si se debe generar logs detallados | `true` |
| **`usarModoMock`** | `Boolean` | ❌ No | **MODO TESTING**: Usa planificaciones hardcodeadas (no ejecuta GRASP) | `false` |

---

## 📦 Ejemplos de JSON

### 1. 🚀 Planificación básica (uso real)

```json
{
  "instanteActual": "2025-11-07T15:30:00Z",
  "idSimulacion": 1
}
```

### 2. 🎯 Planificación con todos los parámetros

```json
{
  "estrategiaFija": "PROFUNDA",
  "parametros": [],
  "instanteActual": "2025-11-07T15:30:00Z",
  "instanteDesdeTomarPedidos": "2025-11-01T00:00:00Z",
  "idSimulacion": 1,
  "seed": 42,
  "subCarpetaReportes": "prueba_planificacion_20251107",
  "loggear": true,
  "usarModoMock": false
}
```

### 3. 🧪 Modo Testing (planificaciones hardcodeadas)

```json
{
  "instanteActual": "2025-11-07T15:30:00Z",
  "idSimulacion": null,
  "usarModoMock": true,
  "loggear": false
}
```

### 4. 🔬 Con semilla fija para reproducibilidad

```json
{
  "estrategiaFija": "RAPIDA",
  "instanteActual": "2025-11-07T15:30:00Z",
  "idSimulacion": 1,
  "seed": 123456789,
  "subCarpetaReportes": "experimento_seed_123456789",
  "loggear": true
}
```

### 5. 📊 Con reportes detallados

```json
{
  "estrategiaFija": "AUTO",
  "instanteActual": "2025-11-07T15:30:00Z",
  "idSimulacion": 1,
  "subCarpetaReportes": "Simulacion_1_Reporte_GRASP",
  "loggear": true
}
```

### 6. ⚡ Planificación rápida sin logs

```json
{
  "estrategiaFija": "RAPIDA",
  "instanteActual": "2025-11-07T15:30:00Z",
  "idSimulacion": 1,
  "loggear": false
}
```

---

## 📤 Respuesta del Endpoint: `PlanificacionResponseDTO`

La respuesta incluye:
- Lista de rutas programadas (pedidos asignados a vuelos)
- Fitness de la solución
- Tiempo de ejecución del algoritmo
- Estadísticas de la planificación

Ejemplo de respuesta:
```json
{
  "rutasProgramadas": [
    {
      "idPedido": 1,
      "cantidadTotalOParcial": 100,
      "idsVuelosEnOrden": [10, 15, 23],
      "almacenOrigen": { "id": 1, "nombre": "Almacén Lima" },
      "almacenDestino": { "id": 5, "nombre": "Almacén Madrid" }
    }
  ],
  "fitness": 0.85,
  "tiempoEjecucionMs": 1250,
  "pedidosAtendidos": 15,
  "pedidosPendientes": 3
}
```

---

## 🔍 Notas Importantes

### 🕐 Formato de fechas (`instanteActual`)
Las fechas deben estar en formato **ISO-8601** con zona horaria UTC:
- ✅ Correcto: `"2025-11-07T15:30:00Z"`
- ✅ Correcto: `"2025-11-07T15:30:00.000Z"`
- ❌ Incorrecto: `"2025-11-07 15:30:00"`
- ❌ Incorrecto: `"07/11/2025"`

### ✈️ Ventana de vuelos considerados
El algoritmo busca vuelos con las siguientes restricciones temporales:
- **Inicio mínimo**: `instanteActual + 2 horas` (buffer para ejecución del algoritmo)
- **Llegada máxima**: `instanteActual + 3 días`

Ejemplo: Si `instanteActual = "2025-11-07T15:00:00Z"`:
- Se consideran vuelos que **despegan después de** `2025-11-07T17:00:00Z` (15:00 + 2h)
- Y que **llegan antes de** `2025-11-10T15:00:00Z` (15:00 + 3 días)

### 🎲 Estrategias disponibles
- **`RAPIDA`**: Algoritmo optimizado para velocidad (default)
- **`PROFUNDA`**: Algoritmo más exhaustivo (mejor solución, más tiempo)
- **`AUTO`**: Selección automática según contexto

### 🧪 Modo Mock (`usarModoMock: true`)
⚠️ **Solo para testing**:
- NO ejecuta el algoritmo GRASP real
- Retorna planificaciones hardcodeadas predefinidas
- Útil para pruebas de frontend/integración sin esperar cálculos
- NO persiste en base de datos

### 📁 Reportes (`subCarpetaReportes`)
Si se especifica, los logs se guardan en:
```
/reports/{subCarpetaReportes}/Reporte-GRASP-{seed}-{timestamp}.log
```

---

## 🧪 Ejemplo de Curl para Testing

```bash
curl -X POST http://localhost:8080/api/planificaciones \
  -H "Content-Type: application/json" \
  -d '{
    "instanteActual": "2025-11-07T15:30:00Z",
    "idSimulacion": 1,
    "seed": 42,
    "loggear": true
  }'
```

---

## 💡 Casos de Uso Comunes

### 1️⃣ Planificación manual (usuario trigger)
```json
{
  "instanteActual": "2025-11-07T15:30:00Z",
  "idSimulacion": 1
}
```

### 2️⃣ Replanificación después de evento (vuelo cancelado)
```json
{
  "instanteActual": "2025-11-07T16:00:00Z",
  "idSimulacion": 1,
  "instanteDesdeTomarPedidos": "2025-11-07T00:00:00Z",
  "subCarpetaReportes": "replanificacion_vuelo_cancelado"
}
```

### 3️⃣ Experimento numérico con semilla fija
```json
{
  "estrategiaFija": "PROFUNDA",
  "instanteActual": "2025-11-07T12:00:00Z",
  "seed": 987654321,
  "subCarpetaReportes": "experimento_001_seed_987654321",
  "loggear": true
}
```

---

## 🐛 Debugging

Si necesitas depurar el algoritmo:
1. Activa logs: `"loggear": true`
2. Usa una semilla fija: `"seed": 42`
3. Especifica subcarpeta de reportes: `"subCarpetaReportes": "debug_test"`
4. Revisa los archivos en `/reports/debug_test/`

---

**Fecha de actualización:** 7 de noviembre, 2025
