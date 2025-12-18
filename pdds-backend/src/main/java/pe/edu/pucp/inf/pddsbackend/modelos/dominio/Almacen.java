package pe.edu.pucp.inf.pddsbackend.modelos.dominio;

import lombok.Getter;
import pe.edu.pucp.inf.pddsbackend.miscelaneo.Bitacora;
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

    /*
     * Constructor para la BD
     */
    public Almacen(long id, boolean infinito, int capacidadMaxima, int capacidadOcupada, String nombrePais, String nombreCiudad, String codigoAeropuertoEn4Letras, String codigoCiudadEn4Letras, List<UUID> idsProductosExistentes, Continente continente) {
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
                a.getContinente());
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
    /*
     * Registra un producto futuro al inventario. Osea, un producto que en el instanteActual está en pleno vuelo y llegará a este almacén. Deshace si detecta una inconsistencia
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
     * Registra un recojo de un producto debido a una programación que no se puede cancelar.
     */
    public boolean registrarRecojoDeProductos(Producto producto, Instant instanteRecojo) {
        if(registrarSalida(instanteRecojo, 1)) {    
            return true;
        }

        return false;
    }

    /*
     * Registra una salida de Productos del Almacen (cuando un Vuelo sale). Deshace si detecta una inconsistencia. En caso de los almacenes infinitos
     */
    public boolean registrarSalida(Instant instanteSalida, Integer productosSalientes) {
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
     * Verifica que los cambios en el Almacen nunca estén fuera del rango [0, capacidad]
     */
    private boolean verificarConsistenciaEnCambios() {
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
     * Obtiene los productos de tipo C en el inventario y en el inventario futuro con instanteEntrada >= instante
     */
    public List<Producto> obtenerProductos(Instant instante){
        List<Producto> productos = new ArrayList<>();
        
        for(Producto producto : this.inventario) {
            if(producto.validarPlanificadoNoExistente_C()) {
                productos.add(producto);
            }
        }
        
        for(Map.Entry<Producto, Instant> entry : this.inventarioFuturo.entrySet()) {
            Producto producto = entry.getKey();
            Instant instanteEntrada = entry.getValue();
            
            if(producto.validarPlanificadoNoExistente_C() && !instanteEntrada.isBefore(instante)) {
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
     * Compara dos almacenes y verifica si son de continentes diferentes
     */
    public static boolean verificarIntercontinental(Almacen origen, Almacen destino){
        return origen.continente.equals(destino.continente);
    }

    @Override
    public String toString()
    {
        return "Almacen{" +
                "id=" + id +
                ", nombrePais='" + nombrePais + '\'' +
                ", nombreCiudad='" + nombreCiudad + '\'' +
                ", codigoAeropuertoEn4Letras='" + codigoAeropuertoEn4Letras + '\'' +
                ", codigoCiudadEn4Letras='" + codigoCiudadEn4Letras + '\'' +
                ", infinito=" + infinito +
                ", capacidad=" + capacidad +
                ", continente=" + continente +
                ", inventario=" + inventario +
                ", inventarioFuturo=" + inventarioFuturo +
                ", cambios=" + cambios +
                '}';
    }
}
