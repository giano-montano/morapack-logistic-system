package pe.edu.pucp.inf.pddsbackend.modelos.dominio;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import pe.edu.pucp.inf.pddsbackend.miscelaneo.Hiperparametros;
import pe.edu.pucp.inf.pddsbackend.modelos.entidades.AlmacenEntidad;
import pe.edu.pucp.inf.pddsbackend.simulador.ContextoSimulacion;

import static pe.edu.pucp.inf.pddsbackend.miscelaneo.Hiperparametros.HORAS_ESPERA_PARA_RECOJO;

import java.io.Serializable;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Getter
public class Almacen implements Serializable
{
    private long id;
    private boolean esInfinito;
    private int capacidadMaxima;

    private Map<Instant, Integer> cambios = new TreeMap<>();
    private List<UUID> idsProductosExistentes;
    private List<UUID> idsProductosFuturos;
    private Continente continente;
    
    /*
    Registra un producto existente al inventario. Osea, un producto que en el instanteActual está en el almacén. Deshace si detecta una inconsistencia. Los productos existentes tiene instanteDeDisponibilidad en null
    */
    public Boolean registrarProductoExistente_v2(Producto producto)
    {
        this.idsProductosExistentes.add(producto.getUuid());

        if(verificarConsistenciaEnCambios_v2())
        {
            return true;
        }

        this.idsProductosExistentes.remove(producto.getUuid());
        return false;
    }

    /*
     * Registra un producto futuro al inventario. Osea, un producto que en el instanteActual está en pleno vuelo y llegará a este almacén. Aquí no entran productos programados. Deshace si detecta una inconsistencia
     *
     */
    public Boolean registrarProductoFuturo_v2(Producto producto, Instant instanteDisponible)
    {
        this.idsProductosFuturos.add(producto.getUuid());

        if (registrarEntrada_v2(instanteDisponible, 1))
        {
            producto.setInstanteDeDisponibilidad(instanteDisponible);
            return true;
        }

        this.idsProductosFuturos.remove(producto.getUuid());
        return false;
    }

    /*
     * Registra un recojo de un producto debido a una programación que no se puede cancelar. Se debe pasar el instante de llegada del último vuelo. Deshace si detecta una inconsistencia
     *
     */
    public Boolean registrarRecojoDeProductos_v2(Producto producto, Instant instanteLlegadaUltimoVuelo)
    {
        Instant instanteRecojo;

        instanteRecojo = instanteLlegadaUltimoVuelo.plus(Duration.ofHours(HORAS_ESPERA_PARA_RECOJO));

        if(registrarSalida_v2(instanteRecojo, 1))
        {
            producto.marcarProntoParaEntrega_v2();
            return true;
        }

        return false;
    }

    /*
     * Registra una salida de Productos del Almacen (cuando un Vuelo sale). Deshace si detecta una incosistencia
     *
     * Remplazo de registrarCambioNegativo
     */
    private Boolean registrarSalida_v2(Instant instanteActual, Integer productosSalientes)
    {
        this.cambios.merge(instanteActual, -1 * productosSalientes, Integer::sum);

        if(this.verificarConsistenciaEnCambios_v2())
        {
            return true;
        }

        this.cambios.merge(instanteActual, productosSalientes,  Integer::sum);
        return false;
    }


    /*
     * Registra una entrada de Productos del Almacen (cuando un  Vuelo llega). Deshace si detecta una incosistencia
     *
     * Remplazo de registrarCambioPositivo
    */
    private Boolean registrarEntrada_v2(Instant instanteActual, Integer productosEntrantes){
        this.cambios.merge(instanteActual, productosEntrantes, Integer::sum);

        if(this.verificarConsistenciaEnCambios_v2())
        {
            return true;
        }

        this.cambios.merge(instanteActual, -1 * productosEntrantes,  Integer::sum);
        return false;
    }

    /*
     * Verifica que los cambios en el Almacen nunca estén fuera del rango [0, capacidad]
     *
     * Remplazo de verificarConsistenciaEnCambios
     */
    private Boolean verificarConsistenciaEnCambios_v2()
    {
        if(this.esInfinito)
        {
            return true;
        }

        int inventarioFinal;

        inventarioFinal = this.idsProductosExistentes.size();

        for (Integer cambio : this.cambios.values())
        {
            inventarioFinal += cambio;

            if (inventarioFinal < 0 || inventarioFinal > this.capacidadMaxima)
            {
                return false;
            }
        }

        return true;
    }

    /*
     *  Calcula cual es el valor máximo de productosEntrantes en un determinado instanteActual de tal manera que registrarEntrada_v2 retorne positivo. Osea, es un valor positivo
     *
     * Esta función ha pasado test propuestos por mi mismo y por chatGPT, cumple su objetivo
     *
     * Remplazo calcularEspacioVacio
     */
    public Integer calcularEntradaMaximaEnInstante_v2(Instant instanteActual){
        Boolean instanteActualExiste, instanteEsMayor;
        Integer posicion, maxDelta, minDelta, nNumeros, listaNumeros[], sumasParciales[];

        if (this.esInfinito == true)
        {
            return Integer.MAX_VALUE;
        }

        nNumeros = 0;
        posicion = 0;
        listaNumeros = new Integer[this.cambios.size() + 5];
        sumasParciales = new Integer[this.cambios.size() + 5];
        listaNumeros[nNumeros] = this.idsProductosExistentes.size();
        sumasParciales[nNumeros] = this.idsProductosExistentes.size();
        instanteEsMayor = true;
        instanteActualExiste = this.cambios.containsKey(instanteActual);

        for (Map.Entry<Instant, Integer> cambio : this.cambios.entrySet())
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
            maxDelta = Math.min(maxDelta, this.capacidadMaxima - sumasParciales[indice]);
        }

        return (maxDelta <= 0) ? 0 :maxDelta;
    }


    /*
     * Esta el almacen vacío?
     * NO SE USA POR EL MOMENTO
     */
    public boolean esVacio_v2()
    {
        boolean vacio;

        vacio = (this.idsProductosExistentes.size() + this.idsProductosFuturos.size()) == 0;
        vacio &= (this.cambios.size() == 0);

        return vacio; //BRO WTF? XD EN NECESARIO ESTA FUNCION ...CREO QUE NO
    }

/* LEGACY */
    
    private int capacidadOcupada;
    private int capacidadSinOcupar;
    private String nombrePais;
    private String nombreCiudad;
    private String codigoAeropuertoEn4Letras;
    private String codigoCiudadEn4Letras;
    

    // Constructor principal, se usa cuando viene desde BD
    public Almacen(long id,
            boolean esInfinito,
            int capacidadMaxima,
            int capacidadOcupada,
            String nombrePais,
            String nombreCiudad,
            String codigoAeropuertoEn4Letras,
            String codigoCiudadEn4Letras,
            List<UUID> idsProductosExistentes,
            Continente continente)
    {
        this.id = id;
        this.esInfinito = esInfinito;
        this.capacidadMaxima = capacidadMaxima; // sí tienen una capacidad fija a pesar de ser
                                                // infinitos!!
        this.capacidadOcupada = capacidadMaxima >= capacidadOcupada ? capacidadOcupada : 0;
        this.capacidadSinOcupar = capacidadMaxima - this.capacidadOcupada;
        this.nombrePais = nombrePais;
        this.nombreCiudad = nombreCiudad;
        this.codigoAeropuertoEn4Letras = codigoAeropuertoEn4Letras;
        this.codigoCiudadEn4Letras = codigoCiudadEn4Letras;

        this.idsProductosExistentes = idsProductosExistentes != null
                ? new LinkedList<>(idsProductosExistentes)
                : new LinkedList<>();

        this.continente = continente;
        this.idsProductosFuturos = new LinkedList<>();
    }

    // clone
    public Almacen(Almacen value)
    {
        this.id = value.id;
        this.esInfinito = value.esInfinito;
        this.capacidadMaxima = value.capacidadMaxima;
        this.capacidadOcupada = value.capacidadOcupada;
        this.capacidadSinOcupar = value.capacidadSinOcupar;
        this.nombrePais = value.nombrePais;
        this.nombreCiudad = value.nombreCiudad;
        this.codigoAeropuertoEn4Letras = value.codigoAeropuertoEn4Letras;
        this.codigoCiudadEn4Letras = value.codigoCiudadEn4Letras;
        this.continente = value.continente;

        this.idsProductosExistentes = new ArrayList<>(value.idsProductosExistentes);
        this.idsProductosFuturos = new ArrayList<>(value.idsProductosFuturos);
    }

    public static Almacen obtenerAlmacenSimuladoConProductos2(
            Almacen value,
            Instant instanteAlgoritmo,
            Instant instanteSimuladoHastaAhora,
            @NotNull List<Programacion> programaciones,
            @NotNull HashMap<UUID, Producto> productos,
            @NotNull HashMap<Long, Vuelo> vuelos,
            ContextoSimulacion ctx
    ) {
        Almacen almacenSimulado = new Almacen(value);
        // Asegúrate de partir con listas mutables
//        if (almacenSimulado.getIdsProductosFuturos() == null) {
//            almacenSimulado.anadirProductosFuturos(Collections.emptyList().stream().map(u -> u).toList());
//        }

        for (Programacion progra : programaciones) {
            Producto producto = productos.get(progra.getUuidProducto());
            if (producto == null) {
                // producto desconocido: loguear y saltar
                System.out.println("obtenerAlmacenSimuladoConProductos: producto null para programacion " + progra);
                continue;
            }

            List<Vuelo> vuelosRuta = progra.getIdsVueloRuta().stream()
                    .map(vuelos::get)
                    .filter(Objects::nonNull)
                    .sorted(Comparator.comparing(Vuelo::getInicio))
                    .toList();

            for (int idx = 0; idx < vuelosRuta.size(); idx++) {
                Vuelo v = vuelosRuta.get(idx);
                if (v == null) continue;

                Instant inicio = v.getInicio();
                Instant fin = v.getFin();
                long idAlmSim = almacenSimulado.getId();

                // --- LLEGADA/EN TRÁNSITO: si este almacén es destino
                if (v.getIdAlmacenDestino() == idAlmSim) {
                    // Caso A: vuelo ya llegó antes o en el instanteAlgoritmo -> el producto está en el almacén
                    if (!fin.isAfter(instanteAlgoritmo) && fin.isAfter(instanteSimuladoHastaAhora) ) { // fin <= instanteAlgoritmo
                        // intentar agregar (si ya existe, ignorar)
                        if (!almacenSimulado.agregarProducto(producto)) {
                            if (almacenSimulado.getIdsProductosExistentes().contains(producto.getUuid())) {
                                // ya existía...
                                System.out.println("Error al agregar producto en simulación de almacén: REPETIDO -> " + almacenSimulado);
                                throw new IllegalStateException("Almacén simulación inconsistente al agregar producto: REPETIDO");

                            } else {
                                // colapso en snapshot: lanzar o loguear según tu política. Aquí logueo.
                                System.out.println("Error al agregar producto en simulación de almacén: COLAPSA EN CAPACIDAD -> " + almacenSimulado);
                                throw new IllegalStateException("Almacén simulación inconsistente al agregar producto: COLAPSA EN CAPACIDAD");
                            }
                        }
                        // pickup window: si ya fue recogido por cliente, quitar
                        Instant instantePickup = fin.plus(Hiperparametros.HORAS_ESPERA_PARA_RECOJO, ChronoUnit.HOURS);
                        if (!instanteAlgoritmo.isBefore(instantePickup)
                        && vuelosRuta.get(vuelosRuta.size()-1).getId() == v.getId() // Y ES EL ÚLTIMO!!!!!!!
                        ) { // instanteAlgoritmo >= fin + ventana
                            almacenSimulado.quitarProducto(producto);

                        }
                    }
                    // Caso B: vuelo en tránsito (inicio <= instanteAlgoritmo < fin) -> producto NO está en inventario pero llegará
                    else if (!inicio.isAfter(instanteAlgoritmo) && fin.isAfter(instanteAlgoritmo) ) {
                        // marcar como futuro si no está ya
                        if (!almacenSimulado.getIdsProductosFuturos().contains(producto.getUuid())) {
//                            almacenSimulado.anadirProductoFuturo(producto.getUuid());
                        }
                        // asegurar instanteDeDisponibilidad en el producto (si aún no está)
                        if (producto.getInstanteDeDisponibilidad() == null) {
//                            producto.establecerInstanteDeDisponibilidadEnUnicoAlmacen(fin);
                        }
                    }
                    // Caso C: vuelo totalmente en el futuro (inicio > instanteAlgoritmo) -> no tocar (queda como futuro solo si deseas)
                }


                // --- SALIDA: si este almacén es origen y el vuelo ya partió (inicio <= instanteAlgoritmo)
                if (v.getIdAlmacenOrigen() == idAlmSim) {
                    if (!inicio.isAfter(instanteAlgoritmo) && inicio.isAfter(instanteSimuladoHastaAhora)) { // inicio <= instanteAlgoritmo
                        // quitar (si estaba)
                        boolean ok = almacenSimulado.quitarProducto(producto);
                        if (!ok) {
                            // si no pudo quitar, puede ser porque no estaba: log en DEBUG, pero NO tirar excepción en snapshot
                            // (en el snapshot nos interesa estado aproximado, no colapsarlo)
                             System.out.println("WARN: intentar quitar producto que no existía en snapshot: " + producto);
                            ctx.log("Se intentó quitar en simu de simu: " + producto + "\n del almacén: "+almacenSimulado);
                            throw new IllegalStateException("Almacén simulación inconsistente al quitar producto");
                        }
                    }
                }


            }
        }

        return almacenSimulado;
    }


    // Simula un almacén según programaciones y productos para saber qué productos específicos contendrá
    // en un instante de tiempo futuro. Solo sirve para el contexto de la simulación ya que no usa idsFuturos
    // si no las últimas programaciones hechas por el alg entregadas a la simulación.
    public static Almacen obtenerAlmacenSimuladoConProductos(
            Almacen value,
            Instant instante,
            @NotNull List<Programacion> programaciones,
            @NotNull HashMap<UUID, Producto> productos,
            @NotNull HashMap<Long, Vuelo> vuelos
            ) {
        Almacen almacenSimulado = new Almacen(value);
        for(Programacion progra: programaciones){
            List<Vuelo> vuelosRuta = progra.getIdsVueloRuta().stream().map(
                    vuelos::get).toList();
            for(Vuelo v: vuelosRuta){
                // Vuelo llega a este almacen antes del instante
                if(v.getIdAlmacenDestino() == almacenSimulado.getId()
                    && v.getFin().isBefore(instante)){
                    if(!v.getInicio().isAfter(instante)){ // si ya salió, es existente.
                        // nada creo
                    }else{
                        // productos que llegan en este vuelo
                        Producto prodQueLlega = productos.get(progra.getUuidProducto());
                        if(!almacenSimulado.agregarProducto(prodQueLlega)){
                            if(almacenSimulado.getIdsProductosExistentes().contains(prodQueLlega.getUuid())){
                                System.out.println("Error al agregar producto en simulación de almacén: YA EXISTÍA");
                                throw new IllegalStateException("Almacén simulación inconsistente al agregar producto: YA EXISTÍA");
                            }
                            System.out.println("Error al agregar producto en simulación de almacén: COLAPSA EN CAPACIDAD");
                            throw new IllegalStateException("Almacén simulación inconsistente al agregar producto: COLAPSA EN CAPACIDAD");
                        }
                        if (vuelosRuta.get(vuelosRuta.size()-1).equals(v) &&
                                !instante.isBefore // el instante dado es después del tiempo de espera para recojo
                                        (v.getFin().plus(Hiperparametros.HORAS_ESPERA_PARA_RECOJO,ChronoUnit.HOURS))) {
                            almacenSimulado.quitarProducto(prodQueLlega);
                        }
                    }
                }

                // Vuelo sale de este almacen antes del instante
                if(v.getIdAlmacenOrigen() == almacenSimulado.getId()
                    && v.getFin().isBefore(instante)){
                    // productos que salen en este vuelo
                    Producto prodQueLlega = productos.get(progra.getUuidProducto());
                    if(!almacenSimulado.quitarProducto(prodQueLlega)){
                        System.out.println("Error al quitar producto en simulación de almacén");
                        throw new IllegalStateException("Almacén simulación inconsistente al quitar producto \n" + almacenSimulado);
                    }
                }
            }
        }
        return almacenSimulado;
    }

    public static Almacen desdeEntidad(AlmacenEntidad a)
    {
        // ✅ NO cargamos productosActuales desde BD para evitar
        // LazyInitializationException
        // En simulación, los productos se manejan en el EstadoGlobal del contexto (en
        // memoria)
        // No necesitamos cargar la colección lazy de productos desde la entidad JPA
        return new Almacen(
                a.getId(),
                a.getEsInfinito(),
                a.getCapacidadMaxima(),
                a.getCapacidadOcupada(),
                a.getNombrePais(),
                a.getNombreCiudad(),
                a.getCodigoAeropuertoEn4Letras(),
                a.getCodigoCiudadEn4Letras(),
                new ArrayList<>(), // ← Lista vacía: productos se manejan en EstadoGlobal de
                                   // simulación
                a.getContinente());
    }

    /**
     * Recalcula campos derivados a partir de capacidadMaxima, capacidadOcupada y
     * capacidadReservada.
     */
    private void recalcularDerivados()
    {
        if (capacidadMaxima < 0)
            capacidadMaxima = 0; // por seguridad, aunque sería mejor validar antes
        capacidadSinOcupar = Math.max(0, capacidadMaxima - capacidadOcupada);
    }

    /* Intenta ocupar inmediatamente, true si pudo; false si es inconsistente */
    public boolean agregarProducto(Producto producto){
        if (producto != null){
            if (idsProductosExistentes.contains(producto.getUuid())){
                return false; // ya estaba
            }

            if(this.esInfinito == true){
                capacidadMaxima = Integer.MAX_VALUE;
                idsProductosExistentes.add(producto.getUuid());
                return true;
            }

            if (capacidadSinOcupar >= 1){
                idsProductosExistentes.add(producto.getUuid());
                capacidadOcupada += 1;
                recalcularDerivados();
                return true;
            }
        }
        
        return false;
    }

    /* Intenta desocupar inmediatamente, true si pudo; false si es inconsistente */
    public boolean quitarProducto(Producto producto){
        if (producto == null)
            return false;
        boolean removed = idsProductosExistentes.remove(producto.getUuid());
        if (removed){
            capacidadOcupada = Math.max(0, capacidadOcupada - 1);
            recalcularDerivados();
            return true;
        }
        return false;
    }

    public boolean agregarVarios(List<Producto> productos)
    {
        for (Producto producto : productos)
        {
            if (!agregarProducto(producto))
                return false;
        }
        return true;
    }

    public boolean quitarVarios(List<Producto> productos){
        for (Producto producto : productos){
            if (!quitarProducto(producto))
                return false;
        }
        return true;
    }

    @Override
    public String toString()
    {
        return "Almacen{" +
                "id=" + id +
                ", esInfinito=" + esInfinito +
                ", capacidadMaxima=" + capacidadMaxima +
                ", capacidadOcupada=" + capacidadOcupada +
                ", capacidadSinOcupar=" + capacidadSinOcupar +
                ", nombrePais='" + nombrePais + '\'' +
                ", nombreCiudad='" + nombreCiudad + '\'' +
                ", codigoAeropuertoEn4Letras='" + codigoAeropuertoEn4Letras + '\'' +
                ", codigoCiudadEn4Letras='" + codigoCiudadEn4Letras + '\'' +
                ", continente=" + continente +
                ", idsProductosExistentes (numero de uuids)=" + idsProductosExistentes.size() +
                '}';
    }

    /*
/*
     * Verifica que los cambios en el Almacen nunca estén fuera del rango [0,
     * capacidad]
     */
    public Boolean verificarConsistenciaEnCambios()
    {
        int inventarioFinal;

        inventarioFinal = this.idsProductosExistentes.size() + this.idsProductosFuturos.size();

        for (Integer cambio : this.cambios.values())
        {
            inventarioFinal += cambio;

            if (inventarioFinal < 0 || inventarioFinal > this.capacidadMaxima)
            {
                return false;
            }
        }

        return true;
    }

    /*Registra una salida de Productos del Almacen*/
      public Boolean registrarCambioNegativo(Instant instanteActual, Integer productosSalientes)
      {
          this.cambios.merge(instanteActual, -1 * productosSalientes, Integer::sum);

            return this.verificarConsistenciaEnCambios();
        }


   /* Registra una entrada de Productos del Almacen*/
    public Boolean registrarCambioPositivo(Instant instanteActual, Integer productosEntrantes){
        this.cambios.merge(instanteActual, productosEntrantes, Integer::sum);

        return this.verificarConsistenciaEnCambios();
    }


    public Integer calcularEspacioVacio(Instant instanteActual){
        Boolean instanteActualExiste, instanteEsMayor;
        Integer posicion, maxDelta, minDelta, nNumeros, listaNumeros[], sumasParciales[];

        if (this.esInfinito == true)
        {
            return Integer.MAX_VALUE;
        }

        nNumeros = 0;
        posicion = 0;
        listaNumeros = new Integer[this.cambios.size() + 5];
        sumasParciales = new Integer[this.cambios.size() + 5];
        listaNumeros[nNumeros] = this.idsProductosFuturos.size() + this.idsProductosExistentes.size();
        sumasParciales[nNumeros] = this.idsProductosFuturos.size() + this.idsProductosExistentes.size();
        instanteEsMayor = true;
        instanteActualExiste = this.cambios.containsKey(instanteActual);

        for (Map.Entry<Instant, Integer> cambio : this.cambios.entrySet())
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

        for (int indice = posicion; indice <= nNumeros; indice++){ // cambién != por <= por sugerencia de GPT
            minDelta = Math.max(minDelta, -1 * sumasParciales[indice]);
            maxDelta = Math.min(maxDelta, this.capacidadMaxima - sumasParciales[indice]);
        }

        return (maxDelta <= 0) ? 0 :maxDelta;
    }

    public void anadirProductoFuturo(UUID uuid){
        idsProductosFuturos.add(uuid);
    }

    public void anadirProductosFuturos(List<UUID> uuids){
        idsProductosFuturos.addAll(uuids);
    }

    public boolean tieneContenido()
    {
        return capacidadOcupada != 0
                || capacidadSinOcupar != capacidadMaxima
                || (idsProductosExistentes != null && !idsProductosExistentes.isEmpty())
                || (idsProductosFuturos != null && !idsProductosFuturos.isEmpty())
                || esInfinito;
    }
    
    public String toString(boolean incluirCambios)
    {
        StringBuilder sb = new StringBuilder();

        sb.append("Almacen (").append(id).append(")\n");
        sb.append("\tUbicacion: ").append(nombreCiudad).append(", ").append(nombrePais).append("\n");
        sb.append("\tContinente: ").append(continente).append("\n");

        int cantidadActual = idsProductosExistentes.size() + idsProductosFuturos.size();
        sb.append("\tCapacidad: ").append(cantidadActual).append("/").append(capacidadMaxima);
        if (esInfinito)
        {
            sb.append(" (Infinito)");
        }
        sb.append("\n");
        sb.append("\tInventario (").append(cantidadActual).append(" productos):\n");

        if (idsProductosExistentes.isEmpty() && idsProductosFuturos.isEmpty())
        {
            sb.append("\t\tVacio");
        }
        else
        {
            sb.append("\t\t[");
            for (int i = 0; i < idsProductosExistentes.size(); i++)
            {
                if (i > 0)
                    sb.append(", ");
                sb.append(idsProductosExistentes.get(i).toString().substring(0, 8)).append("...");
            }
            sb.append("]\n");
            sb.append("\t\t[");
            for (int i = 0; i < idsProductosFuturos.size(); i++)
            {
                if (i > 0)
                    sb.append(", ");
                sb.append(idsProductosFuturos.get(i).toString().substring(0, 8)).append("...");
            }
            sb.append("]");
        }

        if (incluirCambios)
        {
            sb.append("\n");
            sb.append("\tCambios:\n");
            if (cambios == null || cambios.isEmpty())
            {
                sb.append("\t\tNinguno");
            }
            else
            {
                for (Map.Entry<Instant, Integer> entry : cambios.entrySet())
                {
                    sb.append("\t\t")
                            .append(entry.getKey().toString())
                            .append(" -> ")
                            .append(entry.getValue())
                            .append("\n");
                }
            }
        }

        return sb.toString();
    }
}
