package pe.edu.pucp.inf.pddsbackend.modelos.dominio;

import lombok.Getter;
import pe.edu.pucp.inf.pddsbackend.miscelaneo.Bitacora;
import pe.edu.pucp.inf.pddsbackend.miscelaneo.Hiperparametros;
import pe.edu.pucp.inf.pddsbackend.modelos.entidades.AlmacenEntidad;
import static pe.edu.pucp.inf.pddsbackend.algorithms.model.EstadoGlobal.deepCopy;
import java.io.Serializable;
import java.time.Duration;
import java.time.Instant;
import java.util.*;

@Getter
public class Almacen implements Serializable {
    private final long id;
    private final String nombrePais;
    private final String nombreCiudad;
    private final String codigoAeropuertoEn4Letras;
    private final String codigoCiudadEn4Letras;

    private final boolean infinito;
    private final int capacidad;
    private final Continente continente;
    private List<Producto> inventario;
    private Map<Producto, Instant> inventarioFuturo;
    private TreeMap<Instant, Integer> cambios;

    private double latitud;
    private double longitud;

    /*
     * Constructor para la BD
     */
    public Almacen(
            long id,
            boolean infinito,
            int capacidadMaxima,
            int capacidadOcupada,
            String nombrePais,
            String nombreCiudad,
            String codigoAeropuertoEn4Letras,
            String codigoCiudadEn4Letras,
            List<UUID> idsProductosExistentes,
            Continente continente,
            double latitud,
            double longitud) {
        this.id = id;
        this.nombrePais = nombrePais;
        this.nombreCiudad = nombreCiudad;
        this.codigoAeropuertoEn4Letras = codigoAeropuertoEn4Letras;
        this.codigoCiudadEn4Letras = codigoCiudadEn4Letras;

        this.infinito = infinito;
        this.capacidad = capacidadMaxima;
        this.continente = continente;
        this.inventario = new LinkedList<>();
        this.inventarioFuturo = new HashMap<>();
        this.cambios = new TreeMap<>();

        this.latitud = latitud;
        this.longitud = longitud;
    }

    /*
     * Constructor copia profunda usando serialización
     */
    public Almacen(Almacen value) {
        Almacen copia = deepCopy(value);
        this.id = copia.id;
        this.infinito = copia.infinito;
        this.capacidad = copia.capacidad;
        this.nombrePais = copia.nombrePais;
        this.nombreCiudad = copia.nombreCiudad;
        this.codigoAeropuertoEn4Letras = copia.codigoAeropuertoEn4Letras;
        this.codigoCiudadEn4Letras = copia.codigoCiudadEn4Letras;
        this.continente = copia.continente;
        this.inventario = copia.inventario;
        this.inventarioFuturo = copia.inventarioFuturo;

        this.latitud = copia.latitud;
        this.longitud = copia.longitud;
    }

    /*
     * Convierte una entidad de almacen a un dominio de almacen
     */
    public static Almacen desdeEntidad(AlmacenEntidad a) {
        return new Almacen(
                a.getId(),
                a.getEsInfinito(),
                a.getCapacidadMaxima(),
                a.getCapacidadOcupada(),
                a.getNombrePais(),
                a.getNombreCiudad(),
                a.getCodigoAeropuertoEn4Letras(),
                a.getCodigoCiudadEn4Letras(),
                new ArrayList<>(),
                a.getContinente(),
                a.getLatitud(),
                a.getLongitud());
    }
    
    /* 
     * Registra un producto existente al inventario. O sea, un producto que en el instanteActual está en el almacén. Deshace si detecta una inconsistencia
     */
    public boolean registrarProducto(Producto producto) {
        if(this.infinito) {
            String mensaje = "ERROR (Registro de productos): No se debe agregar productos al inventario del almacen infinito";
            Bitacora.escribir(mensaje);
            throw new IllegalStateException(mensaje);
        }
        
        this.inventario.add(producto);

        if(verificarConsistenciaEnCambios()) {
            return true;
        }

        this.inventario.remove(producto);
        return false;
    }

    /* 
     * Registra un producto existente al inventario. O sea, un producto que en el instanteActual está en el almacén. Deshace si detecta una inconsistencia
     */
    public boolean registrarProducto(List<Producto> productos) {
        if(this.infinito) {
            String mensaje = "ERROR (Registro de productos): No se debe agregar productos al inventario del almacen infinito";
            Bitacora.escribir(mensaje);
            throw new IllegalStateException(mensaje);
        }
        
        this.inventario.addAll(productos);

        if(verificarConsistenciaEnCambios()) {
            return true;
        }

        this.inventario.removeAll(productos);
        return false;
    }
    public boolean registrarProductov2(List<Producto> productos) {
        if(this.infinito) {
            String mensaje = "ERROR (Registro de productos): No se debe agregar productos al inventario del almacen infinito";
            Bitacora.escribir(mensaje);
            throw new IllegalStateException(mensaje);
        }

        this.inventario.addAll(productos);

//        if(verificarConsistenciaEnCambios()) {
            return true;
//        }

//        this.inventario.removeAll(productos);
//        return false;
    }
    /*
     * Registra un producto futuro al inventario.
     * O sea, un producto que en el instanteActual está en pleno vuelo y llegará a este almacén.
     * Deshace si detecta una inconsistencia. Marca cambios
     */
    public boolean registrarProductoFuturo(Producto producto, Instant instanteEntrada) {
        this.inventarioFuturo.put(producto, instanteEntrada);

        if (registrarEntrada(instanteEntrada, 1)) {
            return true;
        }

        this.inventarioFuturo.remove(producto, instanteEntrada);
        return false;
    }

    /*
     * Registra un producto futuro al inventario.
     * O sea, un producto que en el instanteActual está en pleno vuelo y llegará a este almacén.
     * Si detecta una inconsistencia, LE LLEGA AL PINCHO
     */
    public boolean registrarProductoFuturoIlegalmente(Producto producto, Instant instanteEntrada) {
        this.inventarioFuturo.put(producto, instanteEntrada);

        if (registrarEntradaIlegalmente(instanteEntrada, 1)) {
            return true;
        }
        Bitacora.escribir("registrarProductoFuturoIlegalmente: No debería llegar aquí");
//        this.inventarioFuturo.remove(producto, instanteEntrada);
        return false;
    }

    /*
     * Registra un recojo de un producto debido a una programación que no se puede cancelar.
     */
    public boolean registrarRecojoDeProductos(Producto producto, Instant instanteRecojo) {
        instanteRecojo = instanteRecojo.plus(Duration.ofHours(Hiperparametros.HORAS_ESPERA_PARA_RECOJO)); // ja q webon
        if(registrarSalida(instanteRecojo, 1)) {    
            return true;
        }

        return false;
    }

    /*
     * Registra un recojo de un producto debido a una programación que no se puede cancelar.
     */
    public boolean registrarRecojoDeProductosIlegalmente(Producto producto, Instant instanteRecojo) {
        instanteRecojo = instanteRecojo.plus(Duration.ofHours(Hiperparametros.HORAS_ESPERA_PARA_RECOJO)); // ja q webon
        if(registrarSalidaIllegal(instanteRecojo, 1)) {    
            return true;
        }

        return false;
    }

    /*
     * Registra una salida de Productos del Almacen (cuando un Vuelo sale). Deshace si detecta una inconsistencia. En caso de los almacenes infinitos retorna true
     */
    public boolean registrarSalida(Instant instanteSalida, Integer productosSalientes) {
        if(this.infinito) {
            return true;
        }
        this.cambios.merge(instanteSalida, -1 * productosSalientes, Integer::sum);

        if(this.verificarConsistenciaEnCambios() && !this.infinito) {
            return true;
        }

        this.cambios.merge(instanteSalida, productosSalientes,  Integer::sum);
        return false;
    }


    /*
     * Registra una entrada de Productos del Almacen (cuando un  Vuelo llega). Deshace si detecta una inconsistencia
     */
    public boolean registrarEntrada(Instant instanteEntrada, Integer productosEntrantes){
        this.cambios.merge(instanteEntrada, productosEntrantes, Integer::sum);

        if(this.verificarConsistenciaEnCambios() && !this.infinito) {
            return true;
        }

        this.cambios.merge(instanteEntrada, -1 * productosEntrantes,  Integer::sum);
        return false;
    }

    /*
     * Registra una entrada de Productos del Almacen (cuando un  Vuelo llega).
     * Si detecta una inconsistencia, le llega al pincho :v
     */
    public boolean registrarEntradaIlegalmente(Instant instanteEntrada, Integer productosEntrantes){
        if(this.infinito) {
            return true;
        }
        this.cambios.merge(instanteEntrada, productosEntrantes, Integer::sum);
        return true;
    }
    /*
     * Registra una salida de Productos del Almacen (cuando un Vuelo sale). Deshace si detecta una inconsistencia. En caso de los almacenes infinitos retorna true
     */
    public boolean registrarSalidaIllegal(Instant instanteSalida, Integer productosSalientes) {
        if(this.infinito) {
            return true;
        }
        this.cambios.merge(instanteSalida, -1 * productosSalientes, Integer::sum);
        return true;
    }

    /*
     * Verifica que los cambios en el Almacen nunca estén fuera del rango [0, capacidad]
     */
    public boolean verificarConsistenciaEnCambios() {
        if(this.infinito) {
            return true;
        }

        int inventarioFinal = this.inventario.size();
        
        if(inventarioFinal <= this.capacidad) {
            for (Integer cambio : this.cambios.values()) {
                inventarioFinal += cambio;

                if (inventarioFinal < 0 || inventarioFinal > this.capacidad) {
                    return false;
                }
            }
        }

        return true;
    }

    /*
     *  Calcula cual es el valor máximo de productosEntrantes en un determinado instanteActual de tal manera que registrarEntrada_v2 retorne positivo. Osea, es un valor positivo
     */
    public Integer calcularEspacioVacioMaximoEnInstante(Instant instanteActual){
        return calcularEspacioVacioMaximoEnInstante(instanteActual, this.cambios, this.inventario.size(), this.capacidad, this.infinito);
    }

    /*
     *  Calcula cual es el valor máximo de productosEntrantes en un determinado instanteActual de tal manera que registrarEntrada_v2 retorne positivo. Osea, es un valor positivo
     *  Considera solo los cambios hasta antes del instanteColapso (intervalo [inicio, instanteColapso))
     *  Si instanteColapso es null, considera todos los cambios
     */
    public Integer calcularEspacioVacioMaximoEnInstanteConColapso(Instant instanteActual, Instant instanteColapso){
        // Filtrar cambios: solo aquellos antes del instanteColapso [inicio, instanteColapso)
        // Si instanteColapso es null, usar todos los cambios
        Map<Instant, Integer> cambiosFiltrados = (instanteColapso != null) 
            ? this.cambios.headMap(instanteColapso, false) 
            : this.cambios;
        return calcularEspacioVacioMaximoEnInstante(instanteActual, cambiosFiltrados, this.inventario.size(), this.capacidad, this.infinito);
    }

    /*
     *  Calcula cual es el valor máximo de productosEntrantes en un determinado instanteActual de tal manera que registrarEntrada_v2 retorne positivo. Osea, es un valor positivo
     */
    private static Integer calcularEspacioVacioMaximoEnInstante(Instant instanteActual, Map<Instant, Integer> cambios, int inventarioInicial, int capacidad, boolean infinito){
        Boolean instanteActualExiste, instanteEsMayor;
        Integer posicion, maxDelta, minDelta, nNumeros, listaNumeros[], sumasParciales[];

        if (infinito == true)
        {
            return Integer.MAX_VALUE;
        }

        nNumeros = 0;
        posicion = 0;
        listaNumeros = new Integer[cambios.size() + 5];
        sumasParciales = new Integer[cambios.size() + 5];
        listaNumeros[nNumeros] = inventarioInicial;
        sumasParciales[nNumeros] = inventarioInicial;
        instanteEsMayor = true;
        instanteActualExiste = cambios.containsKey(instanteActual);

        for (Map.Entry<Instant, Integer> cambio : cambios.entrySet())
        {
            nNumeros++;

            if (instanteActualExiste == true && instanteActual.equals(cambio.getKey()))
            {
                posicion = nNumeros;
            }

            if (instanteActualExiste == false && instanteActual.isBefore(cambio.getKey()))
            {
                instanteActualExiste = true;
                listaNumeros[nNumeros] = 0;
                sumasParciales[nNumeros] = sumasParciales[nNumeros - 1];
                posicion = nNumeros;
                nNumeros++;
                instanteEsMayor = false;
            }

            listaNumeros[nNumeros] = cambio.getValue();
            sumasParciales[nNumeros] = sumasParciales[nNumeros - 1] + cambio.getValue();
        }

        if (instanteEsMayor == true && instanteActualExiste == false){
            nNumeros++;
            listaNumeros[nNumeros] = 0;
            sumasParciales[nNumeros] = sumasParciales[nNumeros - 1];
            posicion = nNumeros;
        }

        minDelta = Integer.MIN_VALUE;
        maxDelta = Integer.MAX_VALUE;

        for (int indice = posicion; indice <= nNumeros; indice++)
        {
            minDelta = Math.max(minDelta, -1 * sumasParciales[indice]);
            maxDelta = Math.min(maxDelta, capacidad - sumasParciales[indice]);
        }

        return (maxDelta <= 0) ? 0 :maxDelta;
    }


    /*
    * Valida si una entrada de productos de esa cantidad sería factible sin modificar permanentemente los cambios
    */
    public boolean verificaEntrada(Instant instanteActual, Integer productosEntrantes)
    {
        boolean consistente;

        if (!this.infinito)
        {
            this.cambios.merge(instanteActual, productosEntrantes, Integer::sum);
            consistente = this.verificarConsistenciaEnCambios();
            this.cambios.merge(instanteActual, -1 * productosEntrantes, Integer::sum);

            if (this.cambios.get(instanteActual) == 0)
            {
                this.cambios.remove(instanteActual);
            }

            return consistente;
        }

        String mensaje = "ERROR (Verificar entrada): Un almacen infinito no debería recibir una entrada de productos";
        Bitacora.escribir(mensaje);
        throw new IllegalStateException(mensaje);
        
    }

    /*
     * Obtiene los productos de tipo A en el inventario y en el inventario futuro con instanteEntrada <= instante
     * Es decir, productos que ya están disponibles (o lo estarán) en el instante dado
     */
    public List<Producto> obtenerProductos(Instant instante){
        if(this.infinito){
            return List.of();
        }

        List<Producto> productos = new ArrayList<>();
        
        for(Producto producto : this.inventario) {
            if(producto.validarNoPlanificado_A()) {
                productos.add(producto);
            }
        }
        
        for(Map.Entry<Producto, Instant> entry : this.inventarioFuturo.entrySet()) {
            Producto producto = entry.getKey();
            Instant instanteEntrada = entry.getValue();
            
            if(producto.validarNoPlanificado_A() && instanteEntrada.isBefore(instante)) { // antes ! isAfter
                productos.add(producto);
            }
        }
        
        return productos;
    }

    /*
     * Registra un producto existente al inventario. Sincronizado
     */
    public synchronized boolean registrarProductoSincronizado(Producto producto) {
        return registrarProducto(producto);
    }

    /*
     * Registra una lista de productos existentes al inventario. Sincronizado
     */
    public synchronized boolean registrarProductoSincronizado(List<Producto> productos) {
        return registrarProducto(productos);
    }

    /*
     * Borra un producto del inventario. Sincronizado
     */
    public synchronized boolean borrarProductoSincronizado(Producto producto) {
        if(!this.inventario.isEmpty()) {
            if(this.inventario.remove(producto)) {
                return true;
            }
        }
        return false;
    }

    public boolean borrarProductoSincronizadov2(Producto producto) {
//        if(!this.inventario.isEmpty()) {
//            if(this.inventario.remove(producto)) {
//                return true;
//            }
//        }
        return true;
    }

    /*
     * Borra una lista de productos del inventario. Sincronizado
     */
    public boolean quitarVariosSimu(List<Producto> productos){
        if(!this.inventario.isEmpty()) {
            if(this.inventario.removeAll(productos)) {
                return true;
            }
        }
        return false;
    }

    /*
     * Vacía la lista de cambios y el map de productos futuros del almacén
     */
    public void limpiarCambiosYProductosFuturos() {
        this.cambios.clear();
        this.inventarioFuturo.clear();
        //Bitacora.escribir("Almacén ID=%d (%s): Cambios y productos futuros limpiados", this.id, this.nombreCiudad);
    }

    /*
     * Compara dos almacenes y verifica si son de continentes diferentes
     */
    public static boolean verificarIntercontinental(Almacen origen, Almacen destino){
        return origen.continente.equals(destino.continente);
    }

    /*
     * Comparar almacen por UUID
     */
    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        Almacen almacen = (Almacen) obj;
        return almacen.id == this.id;
    }

    @Override
    public int hashCode() {
        return Long.hashCode(id);
    }

    @Override
    public String toString()
    {  
        int ocupado = inventario.size();
        int disponible = capacidad - ocupado;
        int futuros = inventarioFuturo.size();
        
        return String.format("Almacen[ID=%d, %s (%s), %s=%s, cap=%d, usado=%d, libre=%d, futuros=%d]",
                id,
                nombreCiudad,
                codigoCiudadEn4Letras,
                continente,
                infinito ? "INF" : "NORMAL",
                capacidad,
                ocupado,
                disponible,
                futuros);
    }

    /*
     * Imprime información detallada de debug del almacén incluyendo todos los cambios programados
     */
    public String impresionDebug() {
        StringBuilder sb = new StringBuilder();
        
        sb.append("╔════════════════════════════════════════════════════════════════════════════════╗\n");
        sb.append(String.format("║ ALMACÉN DEBUG - ID: %d %-54s║\n", id, "", this.infinito ? " (INFINITO)" : ""));
        sb.append("╠════════════════════════════════════════════════════════════════════════════════╣\n");
        
        // Información general
        sb.append(String.format("║ Ubicación: %s, %s (%s)%40s║\n", 
                nombreCiudad, nombrePais, codigoCiudadEn4Letras, ""));
        sb.append(String.format("║ Código Aeropuerto: %s%56s║\n", 
                codigoAeropuertoEn4Letras, ""));
        sb.append(String.format("║ Continente: %s%63s║\n", 
                continente, ""));
        sb.append(String.format("║ Tipo: %s%69s║\n", 
                infinito ? "INFINITO" : "NORMAL", ""));
        sb.append(String.format("║ Capacidad Máxima: %d%60s║\n", 
                capacidad, ""));
        
        // Inventario actual
        int ocupado = inventario.size();
        int disponible = capacidad - ocupado;
        sb.append("╠════════════════════════════════════════════════════════════════════════════════╣\n");
        sb.append(String.format("║ INVENTARIO ACTUAL:%61s║\n", ""));
        sb.append(String.format("║   • Productos en almacén: %d%51s║\n", 
                ocupado, ""));
        sb.append(String.format("║   • Espacio disponible: %d%53s║\n", 
                disponible, ""));
        sb.append(String.format("║   • Porcentaje ocupado: %.2f%%%51s║\n", 
                capacidad > 0 ? (ocupado * 100.0 / capacidad) : 0.0, ""));
        
        // Inventario futuro
        sb.append("╠════════════════════════════════════════════════════════════════════════════════╣\n");
        sb.append(String.format("║ INVENTARIO FUTURO (en tránsito): %d productos%31s║\n", 
                inventarioFuturo.size(), ""));
        
        if (!inventarioFuturo.isEmpty()) {
            // Ordenar por instante de llegada
            List<Map.Entry<Producto, Instant>> sortedFuturo = new ArrayList<>(inventarioFuturo.entrySet());
            sortedFuturo.sort(Map.Entry.comparingByValue());
            
            int count = 0;
            for (Map.Entry<Producto, Instant> entry : sortedFuturo) {
                count++;
                if (count <= 10) { // Mostrar solo los primeros 10
                    sb.append(String.format("║   %2d. Producto %s llegará %s%15s║\n",
                            count,
                            entry.getKey().getId().toString().substring(0, 8),
                            entry.getValue(),
                            ""));
                } else if (count == 11) {
                    sb.append(String.format("║   ... y %d productos más%51s║\n", 
                            inventarioFuturo.size() - 10, ""));
                    break;
                }
            }
        }
        
        // Lista de cambios detallada
        sb.append("╠════════════════════════════════════════════════════════════════════════════════╣\n");
        sb.append(String.format("║ CAMBIOS PROGRAMADOS: %d eventos%46s║\n", 
                cambios.size(), ""));
        
        if (!cambios.isEmpty()) {
            sb.append("╠════════════════════════════════════════════════════════════════════════════════╣\n");
            sb.append("║  #  │ Instante                    │ Cambio │ Acumulado │ Disponible │ Estado ║\n");
            sb.append("╠═════╪═════════════════════════════╪════════╪═══════════╪════════════╪════════╣\n");
            
            int inventarioAcumulado = ocupado;
            int numeroEvento = 0;
            
            for (Map.Entry<Instant, Integer> cambio : cambios.entrySet()) {
                numeroEvento++;
                Instant instante = cambio.getKey();
                Integer delta = cambio.getValue();
                inventarioAcumulado += delta;
                int espacioDisponible = capacidad - inventarioAcumulado;
                
                String signo = delta >= 0 ? "+" : "";
                String estadoStr;
                if (inventarioAcumulado < 0) {
                    estadoStr = "ERROR!";
                } else if (inventarioAcumulado > capacidad) {
                    estadoStr = "EXCEDE";
                } else if (inventarioAcumulado == capacidad) {
                    estadoStr = "LLENO ";
                } else if (inventarioAcumulado == 0) {
                    estadoStr = "VACÍO ";
                } else {
                    estadoStr = "OK    ";
                }
                
                sb.append(String.format("║ %3d │ %s │ %s%4d  │   %5d   │    %5d   │  %s ║\n",
                        numeroEvento,
                        instante,
                        signo,
                        delta,
                        inventarioAcumulado,
                        espacioDisponible,
                        estadoStr));
            }
            
            // Resumen final
            sb.append("╠════════════════════════════════════════════════════════════════════════════════╣\n");
            sb.append(String.format("║ ESTADO FINAL PROYECTADO:%54s║\n", ""));
            sb.append(String.format("║   • Inventario final: %d%56s║\n", 
                    inventarioAcumulado, ""));
            sb.append(String.format("║   • Espacio disponible final: %d%48s║\n", 
                    capacidad - inventarioAcumulado, ""));
            
            boolean consistente = verificarConsistenciaEnCambios();
            sb.append(String.format("║   • Consistencia: %s%54s║\n", 
                    consistente ? "✓ VÁLIDA" : "✗ INVÁLIDA", ""));
        } else {
            sb.append("║   (Sin cambios programados)%52s║\n");
        }
        
        sb.append("╚════════════════════════════════════════════════════════════════════════════════╝\n");
        
        return sb.toString();
    }
}
