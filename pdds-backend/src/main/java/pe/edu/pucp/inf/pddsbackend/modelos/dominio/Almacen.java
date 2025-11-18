package pe.edu.pucp.inf.pddsbackend.modelos.dominio;

import java.io.Serializable;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class Almacen implements Serializable
{
    @EqualsAndHashCode.Include
    private final UUID id;

    private final Boolean esInfinito;
    private final Integer capacidad, utc;
    private final String ciudad, pais;
    private final Continente continente;

    private List<Producto> inventario;
    private Map<Instant, Integer> cambios;

    /*
     * Constructor inicial. No olvidar llamar a setInventario() para que el objeto
     * este bien definido
     */
    public Almacen(String id,
            Integer capacidad,
            Integer capacidadUsada,
            Integer utc,
            String ciudad,
            String pais,
            Continente continente)
    {
        this.id = UUID.nameUUIDFromBytes(id.getBytes());
        this.capacidad = capacidad;
        this.utc = utc;
        this.ciudad = ciudad;
        this.pais = pais;
        this.continente = continente;
        this.esInfinito = (this.capacidad < 0) ? true : false;

        this.inventario = new ArrayList<>();
        this.cambios = new TreeMap<>();
    }

    /*
     * Recupera la lista de Productos del inventario que no estan asignados a ningún Pedido
     */
    public List<Producto> obtenerProductosNoAsignados(Instant instanteActual)
    {
        List<Producto> productosNoAsignados;

        productosNoAsignados = new ArrayList<>();

        for(Producto producto : this.inventario)
        {
            if(!producto.estaAsignado() && producto.estaDisponible(instanteActual))
            {
                productosNoAsignados.add(producto);
            }
        }
        
        return productosNoAsignados;
    }

    /*
     * Registra una salida de Productos del Almacen
     */
    public Boolean registrarCambioNegativo(Instant instanteActual, Integer productosSalientes)
    {
        this.cambios.merge(instanteActual, -1 * productosSalientes, Integer::sum);

        return this.verificarConsistenciaEnCambios();
    }
    
    /*
     * Registra una entrada de Productos del Almacen
     */
    public Boolean registrarCambioPositivo(Instant instanteActual, Integer productosEntrantes)
    {
        this.cambios.merge(instanteActual, productosEntrantes, Integer::sum);

        return this.verificarConsistenciaEnCambios();
    }

    /*
     * Deshace la salida de Productos del Almacen
     */
    public void deshacerCambioNegativo(Instant instanteActual, Integer productosSalientes)
    {
        Integer cambio;

        cambio = this.cambios.get(instanteActual);

        if(cambio + productosSalientes == 0)
        {
            this.cambios.remove(instanteActual);
        }else{
            this.cambios.merge(instanteActual, productosSalientes, Integer::sum);
        }
    }

    /*
     * Deshace la entrada de Productos del Almacen
     */
    public void deshacerCambioPositivo(Instant instanteActual, Integer productosEntrantes)
    {
        Integer cambio;

        cambio = this.cambios.get(instanteActual);

        if(cambio != null){
            if(cambio - productosEntrantes == 0)
            {
                this.cambios.remove(instanteActual);
            }else{
                this.cambios.merge(instanteActual, -1 * productosEntrantes, Integer::sum);
            }
        }
        
    }

    /*
     * Verifica que los cambios en el Almacen nunca estén fuera del rango [0, capacidad]
     */
    public Boolean verificarConsistenciaEnCambios( )
    {	
        int inventarioFinal;

        inventarioFinal = this.inventario.size();

        for(Integer cambio : this.cambios.values())
        {
            inventarioFinal += cambio;
                
            if(inventarioFinal < 0 || this.capacidad < inventarioFinal)
            {
                return false;
            }
        }
        
        return true;
    }

    public Integer calcularEspacioVacio(Instant instanteActual)
    {
        Boolean instanteActualExiste, instanteEsMayor;
        Integer posicion, maxDelta, minDelta, nNumeros, listaNumeros[], sumasParciales[];

        if(this.esInfinito == true)
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

        for(Map.Entry<Instant, Integer> cambio : this.cambios.entrySet())
        {
            nNumeros++;

            if(instanteActualExiste == true && instanteActual.equals(cambio.getKey()))
            {
                posicion = nNumeros;
            }

            if(instanteActualExiste == false && instanteActual.isBefore(cambio.getKey()))
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

        
        if(instanteEsMayor == true && instanteActualExiste == false)
        {
            nNumeros++;
            listaNumeros[nNumeros] = 0;
            sumasParciales[nNumeros] = sumasParciales[nNumeros - 1];
            posicion = nNumeros;
        }

        minDelta = Integer.MIN_VALUE;
        maxDelta = Integer.MAX_VALUE;
        
        for(int indice = posicion; indice != nNumeros; indice++)
        {
            minDelta = Math.max(minDelta, -1 * sumasParciales[indice]);                
            maxDelta = Math.min(maxDelta, this.capacidad - sumasParciales[indice]);
        }

        return maxDelta = (maxDelta <= 0)? 0 : maxDelta;
    }

    /*
     * Método para comparar dos almacenes y saber si están en continentes diferentes
     */
    public static Boolean esIntercontinental(Almacen origen, Almacen destino)
    {
        return origen.continente != destino.continente;
    }

    /*
     * Método para comparar dos almacenes y saber si son el mismo
     */
    public static Boolean esIgual(Almacen origen, Almacen destino)
    {
        return origen.id == destino.id;
    }

    /*
     * Impresión
     */
    public String toString()
    {
        StringBuilder sb = new StringBuilder();

        sb.append("Almacen (").append(id).append(")\n");
        sb.append("\tUbicacion: ").append(ciudad).append(", ").append(pais).append("\n");
        sb.append("\tContinente: ").append(continente).append(" (UTC").append(utc >= 0 ? "+" : "")
                .append(utc).append(")\n");
        sb.append("\tCapacidad: ").append(inventario.size()).append("/").append(capacidad);
        if (esInfinito)
        {
            sb.append(" (Infinito)");
        }
        sb.append("\n");
        sb.append("\tInventario (").append(inventario.size()).append(" productos):\n");

        if (inventario.isEmpty())
        {
            sb.append("\t\tVacio");
        }
        else
        {
            sb.append("\t\t[");
            for (int i = 0; i < inventario.size(); i++)
            {
                if (i > 0)
                    sb.append(", ");
                sb.append(inventario.get(i).getId().toString().substring(0, 8)).append("...");
            }
            sb.append("]");
        }

        return sb.toString();
    }
}
