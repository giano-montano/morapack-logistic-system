# [OPCIONAL] Planificaciones Predefinidas para Testing

## 🎯 Cuándo usar esto

Solo si necesitas que la simulación **SÍ tenga planificaciones** pero sin ejecutar el algoritmo GRASP.

## 📋 Opción 1: Lista Vacía (Implementado)

**Ya funciona** - Ver `TESTING_SIMULACION_SIN_ALGORITMO.md`

```json
{
  "usar_modo_mock": true
}
```
→ Retorna `programaciones: []`

## 📋 Opción 2: Cargar Planificaciones desde JSON (No implementado)

### Paso 1: Crear archivo con programaciones de prueba

`src/main/resources/planificaciones-prueba.json`:

```json
{
  "programaciones": [
    {
      "id_pedido": 1,
      "vuelos": [
        {
          "id_vuelo": 101,
          "cantidad_producto": 50
        },
        {
          "id_vuelo": 102,
          "cantidad_producto": 30
        }
      ]
    },
    {
      "id_pedido": 2,
      "vuelos": [
        {
          "id_vuelo": 103,
          "cantidad_producto": 100
        }
      ]
    }
  ]
}
```

### Paso 2: Modificar bypass en PlanificacionServiceImpl

```java
@Override
public ResultadoAlgoritmoDTO realizarPlanificacionConEntrada(
        RealizarPlanificacionDTO params, EntradaProblemaPlanificacion dataEntradaAlgoritmo) throws Exception {
    
    // MODO TESTING: Cargar planificaciones desde JSON
    if (params.getUsarModoMock() != null && params.getUsarModoMock()) {
        System.out.println("🧪 MODO TESTING: Cargando planificaciones predefinidas");
        
        SalidaProblemaPlanificacion solucionPrueba = new SalidaProblemaPlanificacion();
        
        // Leer JSON
        ObjectMapper mapper = new ObjectMapper();
        ClassPathResource resource = new ClassPathResource("planificaciones-prueba.json");
        PlanificacionesPruebaDTO dto = mapper.readValue(resource.getInputStream(), 
                                                         PlanificacionesPruebaDTO.class);
        
        // Convertir a programaciones
        List<Programacion> programaciones = convertirDTOAProgramaciones(dto, dataEntradaAlgoritmo);
        solucionPrueba.setProgramaciones(programaciones);
        
        return new ResultadoAlgoritmoDTO(solucionPrueba, 0.0, 0L);
    }
    
    // ... código normal
}

private List<Programacion> convertirDTOAProgramaciones(
        PlanificacionesPruebaDTO dto, EntradaProblemaPlanificacion entrada) {
    
    List<Programacion> resultado = new ArrayList<>();
    
    for (ProgramacionPruebaDTO progDTO : dto.getProgramaciones()) {
        Programacion prog = new Programacion();
        
        // Buscar pedido por ID
        Pedido pedido = entrada.getPedidos().get(progDTO.getIdPedido());
        if (pedido == null) continue; // Skip si no existe
        
        prog.setPedido(pedido);
        prog.setListaRutasProgramadas(new ArrayList<>());
        
        // Convertir vuelos
        for (VueloPruebaDTO vueloDTO : progDTO.getVuelos()) {
            Vuelo vuelo = entrada.getVuelos().get(vueloDTO.getIdVuelo());
            if (vuelo == null) continue;
            
            RutaProgramada ruta = new RutaProgramada();
            ruta.setVuelo(vuelo);
            ruta.setCantidadProducto(vueloDTO.getCantidadProducto());
            prog.getListaRutasProgramadas().add(ruta);
        }
        
        resultado.add(prog);
    }
    
    return resultado;
}
```

### Paso 3: DTOs necesarios

```java
@Data
public class PlanificacionesPruebaDTO {
    private List<ProgramacionPruebaDTO> programaciones;
}

@Data
public class ProgramacionPruebaDTO {
    private Long idPedido;
    private List<VueloPruebaDTO> vuelos;
}

@Data
public class VueloPruebaDTO {
    private Long idVuelo;
    private int cantidadProducto;
}
```

## 📋 Opción 3: Pasar Planificaciones en el Request (Más flexible)

### Request JSON extendido

```json
{
  "duracion_minutos": 10080,
  "usar_modo_mock": true,
  "planificaciones_prueba": [
    {
      "id_pedido": 1,
      "vuelos": [
        {"id_vuelo": 101, "cantidad": 50},
        {"id_vuelo": 102, "cantidad": 30}
      ]
    }
  ],
  "params_planificacion": {
    "estrategia_fija": "AUTO"
  }
}
```

### Modificar SimulacionRequestDTO

```java
@Data
public class SimulacionRequestDTO {
    // ... campos existentes
    private List<ProgramacionPruebaDTO> planificacionesPrueba;
}
```

### Usar en PlanificacionServiceImpl

```java
if (params.getUsarModoMock() != null && params.getUsarModoMock()) {
    System.out.println("🧪 MODO TESTING con planificaciones predefinidas");
    
    SalidaProblemaPlanificacion solucion = new SalidaProblemaPlanificacion();
    
    // Convertir las planificaciones que vienen en el request
    List<Programacion> progs = convertirDTOAProgramaciones(
        params.getPlanificacionesPrueba(), 
        dataEntradaAlgoritmo
    );
    
    solucion.setProgramaciones(progs);
    return new ResultadoAlgoritmoDTO(solucion, 0.0, 0L);
}
```

## 🤔 ¿Cuál opción elegir?

### ✅ Opción 1: Lista vacía (YA IMPLEMENTADA)
**Usar si:**
- Solo quieres testear WebSockets
- No necesitas que se planifique nada
- Quieres la solución más simple

### ⚙️ Opción 2: JSON estático
**Usar si:**
- Quieres planificaciones fijas que se reutilizan
- Necesitas casos de prueba consistentes
- No quieres modificar el request cada vez

### 🎯 Opción 3: JSON en request
**Usar si:**
- Quieres probar diferentes escenarios dinámicamente
- Necesitas flexibilidad en cada test
- El frontend puede generar las planificaciones de prueba

## 💡 Recomendación

Para testing de WebSockets → **Opción 1** (ya está lista)

Para testing funcional → **Opción 3** (más flexible)

## ⚠️ Validaciones Importantes

Si implementas opciones 2 o 3, validar:

1. **IDs existen:** Los `id_pedido` e `id_vuelo` deben existir en la BD
2. **Capacidad:** Que `cantidad_producto` no exceda capacidad del vuelo
3. **Fechas:** Que los vuelos sean posteriores al momento de planificación
4. **Productos:** Que el producto del pedido coincida con productos en almacenes origen

Si alguna validación falla, **omitir** esa programación (no lanzar error, seguir con las demás).

## 📝 Estado Actual

**Implementado:**
- ✅ Bypass con lista vacía

**No implementado (pero fácil de agregar):**
- ⭕ Carga desde JSON estático
- ⭕ Paso de planificaciones en request
- ⭕ Validaciones de planificaciones de prueba

¿Necesitas implementar alguna de las opciones 2 o 3? Si no, con la Opción 1 ya puedes testear los WebSockets.
