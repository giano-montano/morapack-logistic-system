package pe.edu.pucp.inf.pddsbackend.modelos.dominio;

import lombok.Getter;
import pe.edu.pucp.inf.pddsbackend.miscelaneo.Bitacora;
import pe.edu.pucp.inf.pddsbackend.modelos.entidades.AlmacenEntidad;

import static pe.edu.pucp.inf.pddsbackend.miscelaneo.Hiperparametros.HORAS_ESPERA_PARA_RECOJO;

import java.io.Serializable;
import java.time.Duration;
import java.time.Instant;
import java.util.*;

@Getter
public class Almacen implements Serializable {
    private long id;
    private boolean infinito;
    private int capacidad;

    List<Producto> inventario;
    Map<Instant, Producto> inventarioFuturo;
    TreeMap<Instant, Integer> cambios;

    Continente continente;

    private String nombrePais;
    private String nombreCiudad;
    private String codigoAeropuertoEn4Letras;
    private String codigoCiudadEn4Letras;

    // Constructor principal, se usa cuando viene desde BD
    public Almacen(long id,
                   boolean infinito,
                   int capacidadMaxima,
                   int capacidadOcupada,
                   String nombrePais,
                   String nombreCiudad,
                   String codigoAeropuertoEn4Letras,
                   String codigoCiudadEn4Letras,
                   List<UUID> idsProductosExistentes,
                   Continente continente) {
        this.id = id;
        this.infinito = infinito;
        this.capacidad = capacidadMaxima; // sí tienen una capacidad fija a pesar de ser infinitos!!

        this.inventario = new LinkedList<>();
        this.inventarioFuturo = new HashMap<>();
        this.cambios = new TreeMap<>();

        this.continente = continente;

        this.nombrePais = nombrePais;
        this.nombreCiudad = nombreCiudad;
        this.codigoAeropuertoEn4Letras = codigoAeropuertoEn4Letras;
        this.codigoCiudadEn4Letras = codigoCiudadEn4Letras;
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

    // clone
    public Almacen(Almacen value)
    {
        this.id = value.id;
        this.infinito = value.infinito;
        this.capacidad = value.capacidad;

        this.nombrePais = value.nombrePais;
        this.nombreCiudad = value.nombreCiudad;
        this.codigoAeropuertoEn4Letras = value.codigoAeropuertoEn4Letras;
        this.codigoCiudadEn4Letras = value.codigoCiudadEn4Letras;
        this.continente = value.continente;

        this.inventario = new ArrayList<>(value.inventario);
        this.inventarioFuturo = new HashMap<>(value.inventarioFuturo);
    }



    
    /* Registra un producto existente al inventario.
    O sea, un producto que en el instanteActual está en el almacén.
    Deshace si detecta una inconsistencia. Los productos existentes tiene instanteDeDisponibilidad en null
    */
    public Boolean registrarProductoExistente_v2(Producto producto)
    {
        if(this.infinito)
        {
            String mensaje = "ERROR (Registro de productos): No se debe agregar productos al inventario del almacen infinito";
            Bitacora.escribir(mensaje);
            throw new IllegalStateException(mensaje);
        }
        this.inventario.add(producto);

        if(verificarConsistenciaEnCambios_v2())
        {
            return true;
        }

        this.inventario.remove(producto);
        return false;
    }

    /*
     * Registra un producto futuro al inventario. Osea, un producto que en el instanteActual está en pleno vuelo y llegará a este almacén. Aquí no entran productos programados. Deshace si detecta una inconsistencia
     *
     */
    public Boolean registrarProductoFuturo_v2(Producto producto, Instant instanteDisponible)
    {
        this.inventarioFuturo.put(instanteDisponible, producto); // !

        if (registrarEntrada_v2(instanteDisponible, 1))
        {
//            producto.setInstanteDeDisponibilidad(instanteDisponible);
            return true;
        }

        this.inventarioFuturo.remove(instanteDisponible, producto);
        return false;
    }

    /*
     * Registra un recojo de un producto debido a una programación que no se puede cancelar. Se debe pasar el instante de llegada del último vuelo. Deshace si detecta una inconsistencia
     *
     */
    public Boolean registrarRecojoDeProductos_v2(Producto producto, Instant instanteLlegadaUltimoVuelo, boolean incancelable, Instant instantePlanificacion)
    {
        Instant instanteRecojo;

        instanteRecojo = instanteLlegadaUltimoVuelo.plus(Duration.ofHours(HORAS_ESPERA_PARA_RECOJO));

        if(registrarSalida_v2(instanteRecojo, 1))
        {
            if(incancelable)
            {
                producto.marcarProntoParaEntrega_v2();
            }else{
                //producto.marcarComoProgramado(instantePlanificacion);
                producto.marcarComoProgramado_v2(instantePlanificacion);
            }
            
            return true;
        }

        return false;
    }

    /*
     * Registra una salida de Productos del Almacen (cuando un Vuelo sale). Deshace si detecta una inconsistencia. En caso de los almacenes infinitos
     *
     * Remplazo de registrarCambioNegativo
     */
    public Boolean registrarSalida_v2(Instant instanteActual, Integer productosSalientes)
    {
        this.cambios.merge(instanteActual, -1 * productosSalientes, Integer::sum);

        if(this.verificarConsistenciaEnCambios_v2() && !this.infinito)
        {
            return true;
        }

        this.cambios.merge(instanteActual, productosSalientes,  Integer::sum);
        return false;
    }


    /*
     * Registra una entrada de Productos del Almacen (cuando un  Vuelo llega). Deshace si detecta una inconsistencia
     *
     * Remplazo de registrarCambioPositivo
    */
    public Boolean registrarEntrada_v2(Instant instanteActual, Integer productosEntrantes){
        this.cambios.merge(instanteActual, productosEntrantes, Integer::sum);

        if(this.verificarConsistenciaEnCambios_v2() && !this.infinito)
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
        if(this.infinito)
        {
            return true;
        }

        int inventarioFinal;

        inventarioFinal = this.inventario.size();

        for (Integer cambio : this.cambios.values())
        {
            inventarioFinal += cambio;

            if (inventarioFinal < 0 || inventarioFinal > this.capacidad)
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

        if (this.infinito == true)
        {
            return Integer.MAX_VALUE;
        }

        nNumeros = 0;
        posicion = 0;
        listaNumeros = new Integer[this.cambios.size() + 5];
        sumasParciales = new Integer[this.cambios.size() + 5];
        listaNumeros[nNumeros] = this.inventario.size();
        sumasParciales[nNumeros] = this.inventario.size();
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
            maxDelta = Math.min(maxDelta, this.capacidad - sumasParciales[indice]);
        }

        return (maxDelta <= 0) ? 0 :maxDelta;
    }

    /*
    * Valida si una salida de productos de esa cantidad sería factible
    * sin modificar permanentemente los cambios
    */
    public boolean verificarSalida_v2(Instant instanteActual, Integer productosSalientes)
    {
        boolean consistente;

        if (!this.infinito)
        {            
            this.cambios.merge(instanteActual, -1 * productosSalientes, Integer::sum);
            consistente = this.verificarConsistenciaEnCambios_v2();
            this.cambios.merge(instanteActual, productosSalientes, Integer::sum);

            if(this.cambios.get(instanteActual) == 0)
            {
                this.cambios.remove(instanteActual);
            }

            return consistente;    
        }

        return true;
    }

    /*
    * Valida si una entrada de productos de esa cantidad sería factible sin modificar permanentemente los cambios
    */
    public boolean verificarEntrada_v2(Instant instanteActual, Integer productosEntrantes)
    {
        boolean consistente;

        if (!this.infinito)
        {
            this.cambios.merge(instanteActual, productosEntrantes, Integer::sum);
            consistente = this.verificarConsistenciaEnCambios_v2();
            this.cambios.merge(instanteActual, -1 * productosEntrantes, Integer::sum);

            if (this.cambios.get(instanteActual) == 0)
            {
                this.cambios.remove(instanteActual);
            }

            return consistente;
        }else{
            String mensaje = "ERROR (Verificar entrada): Un almacen infinito no debería recibir una entrada de productos";
            Bitacora.escribir(mensaje);
            throw new IllegalStateException(mensaje);
        }
    }

    public boolean agregarProdSimu(Producto producto){
        if(inventario.size()+1>capacidad)
            return false;
        inventario.add(producto);
        return true;
    }
    public boolean agregarVariosSimu(List<Producto> ps){
        for (Producto producto : ps){
            if(!agregarProdSimu(producto))
                return false;
        }
        return true;
    }

    public boolean quitarProdSimu(Producto producto){
        if(inventario.isEmpty())
            return false;
        if (!inventario.remove(producto))
            return false;
        return true;
    }
    public boolean quitarVariosSimu(List<Producto> ps){
        for (Producto producto : ps){
            if(!quitarProdSimu(producto))
                return false;
        }
        return true;
    }

    @Override
    public String toString()
    {
        return "Almacen{" +
                "id=" + id +
                ", esInfinito=" + infinito +
                ", capacidadMaxima=" + capacidad +
//                ", capacidadOcupada=" + capacidadOcupada +
//                ", capacidadSinOcupar=" + capacidadSinOcupar +
                ", nombrePais='" + nombrePais + '\'' +
                ", nombreCiudad='" + nombreCiudad + '\'' +
                ", codigoAeropuertoEn4Letras='" + codigoAeropuertoEn4Letras + '\'' +
                ", codigoCiudadEn4Letras='" + codigoCiudadEn4Letras + '\'' +
                ", continente=" + continente +
//                ", idsProductosExistentes (numero de uuids)=" + idsProductosExistentes.size() +
                '}';
    }

/*
//     * Verifica que los cambios en el Almacen nunca estén fuera del rango [0,
//     * capacidad]
//     */
//    public Boolean verificarConsistenciaEnCambios()
//    {
//        int inventarioFinal;
//
//        inventarioFinal = this.idsProductosExistentes.size() + this.idsProductosFuturos.size();
//
//        for (Integer cambio : this.cambios.values())
//        {
//            inventarioFinal += cambio;
//
//            if (inventarioFinal < 0 || inventarioFinal > this.capacidad)
//            {
//                return false;
//            }
//        }
//
//        return true;
//    }

//    /*Registra una salida de Productos del Almacen*/
//      public Boolean registrarCambioNegativo(Instant instanteActual, Integer productosSalientes)
//      {
//          this.cambios.merge(instanteActual, -1 * productosSalientes, Integer::sum);
//
//            return this.verificarConsistenciaEnCambios();
//        }
//
//
//   /* Registra una entrada de Productos del Almacen*/
//    public Boolean registrarCambioPositivo(Instant instanteActual, Integer productosEntrantes){
//        this.cambios.merge(instanteActual, productosEntrantes, Integer::sum);
//
//        return this.verificarConsistenciaEnCambios();
//    }


//    public Integer calcularEspacioVacio(Instant instanteActual){
//        Boolean instanteActualExiste, instanteEsMayor;
//        Integer posicion, maxDelta, minDelta, nNumeros, listaNumeros[], sumasParciales[];
//
//        if (this.infinito == true)
//        {
//            return Integer.MAX_VALUE;
//        }
//
//        nNumeros = 0;
//        posicion = 0;
//        listaNumeros = new Integer[this.cambios.size() + 5];
//        sumasParciales = new Integer[this.cambios.size() + 5];
//        listaNumeros[nNumeros] = this.idsProductosFuturos.size() + this.idsProductosExistentes.size();
//        sumasParciales[nNumeros] = this.idsProductosFuturos.size() + this.idsProductosExistentes.size();
//        instanteEsMayor = true;
//        instanteActualExiste = this.cambios.containsKey(instanteActual);
//
//        for (Map.Entry<Instant, Integer> cambio : this.cambios.entrySet())
//        {
//            nNumeros++;
//
//            if (instanteActualExiste == true && instanteActual.equals(cambio.getKey()))
//            {
//                posicion = nNumeros;
//            }
//
//            if (instanteActualExiste == false && instanteActual.isBefore(cambio.getKey()))
//            {
//                instanteActualExiste = true;
//                listaNumeros[nNumeros] = 0;
//                sumasParciales[nNumeros] = sumasParciales[nNumeros - 1];
//                posicion = nNumeros;
//                nNumeros++;
//                instanteEsMayor = false;
//            }
//
//            listaNumeros[nNumeros] = cambio.getValue();
//            sumasParciales[nNumeros] = sumasParciales[nNumeros - 1] + cambio.getValue();
//        }
//
//        if (instanteEsMayor == true && instanteActualExiste == false){
//            nNumeros++;
//            listaNumeros[nNumeros] = 0;
//            sumasParciales[nNumeros] = sumasParciales[nNumeros - 1];
//            posicion = nNumeros;
//        }
//
//        minDelta = Integer.MIN_VALUE;
//        maxDelta = Integer.MAX_VALUE;
//
//        for (int indice = posicion; indice <= nNumeros; indice++){ // cambién != por <= por sugerencia de GPT
//            minDelta = Math.max(minDelta, -1 * sumasParciales[indice]);
//            maxDelta = Math.min(maxDelta, this.capacidad - sumasParciales[indice]);
//        }
//
//        return (maxDelta <= 0) ? 0 :maxDelta;
//    }

    
    public String toString(boolean incluirCambios)
    {
        StringBuilder sb = new StringBuilder();

        sb.append("Almacen (").append(id).append(")\n");
        sb.append("\tUbicacion: ").append(nombreCiudad).append(", ").append(nombrePais).append("\n");
        sb.append("\tContinente: ").append(continente).append("\n");

//        int cantidadActual = idsProductosExistentes.size() + idsProductosFuturos.size();
//        sb.append("\tCapacidad: ").append(cantidadActual).append("/").append(capacidad);
//        if (infinito)
//        {
//            sb.append(" (Infinito)");
//        }
//        sb.append("\n");
//        sb.append("\tInventario (").append(cantidadActual).append(" productos):\n");
//
//        if (idsProductosExistentes.isEmpty() && idsProductosFuturos.isEmpty())
//        {
//            sb.append("\t\tVacio");
//        }
//        else
//        {
//            sb.append("\t\t[");
//            for (int i = 0; i < idsProductosExistentes.size(); i++)
//            {
//                if (i > 0)
//                    sb.append(", ");
//                sb.append(idsProductosExistentes.get(i).toString().substring(0, 8)).append("...");
//            }
//            sb.append("]\n");
//            sb.append("\t\t[");
//            for (int i = 0; i < idsProductosFuturos.size(); i++)
//            {
//                if (i > 0)
//                    sb.append(", ");
//                sb.append(idsProductosFuturos.get(i).toString().substring(0, 8)).append("...");
//            }
//            sb.append("]");
//        }
//
//        if (incluirCambios)
//        {
//            sb.append("\n");
//            sb.append("\tCambios:\n");
//            if (cambios == null || cambios.isEmpty())
//            {
//                sb.append("\t\tNinguno");
//            }
//            else
//            {
//                for (Map.Entry<Instant, Integer> entry : cambios.entrySet())
//                {
//                    sb.append("\t\t")
//                            .append(entry.getKey().toString())
//                            .append(" -> ")
//                            .append(entry.getValue())
//                            .append("\n");
//                }
//            }
//        }

        return sb.toString();
    }
}
