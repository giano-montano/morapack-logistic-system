package pe.edu.pucp.inf.pddsbackend.modelos.dominio;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import pe.edu.pucp.inf.pddsbackend.miscelaneo.Hiperparametros;
import pe.edu.pucp.inf.pddsbackend.modelos.entidades.AlmacenEntidad;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Getter
public class Almacen
{
    // propios del dominio:
    private long id;
    private boolean esInfinito;
    private int capacidadMaxima;
    private int capacidadOcupada;
    private int capacidadSinOcupar;
    private String nombrePais;
    private String nombreCiudad;
    private String codigoAeropuertoEn4Letras;
    private String codigoCiudadEn4Letras;
    private Continente continente;

    private List<UUID> idsProductosExistentes; // se volvió fuente de verdad

    private List<UUID> idsProductosFuturos; // <- PENDIENTEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEE

    // Los cambios son para validar capacidades
    private Map<Instant, Integer> cambios = new TreeMap<>();

    // índices:

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
}
