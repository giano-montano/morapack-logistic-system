package pe.edu.pucp.inf.pddsbackend.modelos.dominio;

import lombok.Getter;
import lombok.Setter;

import java.util.LinkedList;
import java.util.List;
import java.util.UUID;

@Getter
public class Programacion
{ // Lo que antes era ProgramacionEntidad

    // private final long id;
    private long idPedido;
    private final UUID uuidProducto; // exista ya o no, tiene id
    private final LinkedList<Long> idsVueloRuta;
    private long idPlanificacion; // no interesa mucho por ahora, capaz safa
    @Setter
    private boolean activo = true; // recordemos que se irán descartando programaciones anteriores
                                   // en cada planif
    // o sea le pondremos activo=false a la "tanda" anterior. <- LEGACY

    private boolean aPuntoDeCumplirse = false; // NUEVO: lo actualiza el evento:
    // SERVIRÁN PARA INICIALIZAR CAMBIOS POR TIEMPO EN ALMACÉN


    // Constructor principal para programaciones que se vayan haciendo en el
    // algoritmo
    public Programacion(
            long idPedido,
            UUID uuidProducto,
            LinkedList<Long> ruta)
    {
        this.idPedido = idPedido;
        this.uuidProducto = uuidProducto;
        this.idsVueloRuta = ruta;
        this.aPuntoDeCumplirse = false;
    }

    public Programacion(Programacion original)
    {
        // id = original.id;
        this.idPedido = original.idPedido;
        this.uuidProducto = original.uuidProducto;
        this.idsVueloRuta = original.idsVueloRuta;
        this.idPlanificacion = original.idPlanificacion;
        this.aPuntoDeCumplirse = original.aPuntoDeCumplirse;
        this.activo = original.activo;

    }

    public LinkedList<Long> getIdsVueloRuta()
    {
        return new LinkedList<>(idsVueloRuta);
    }

    public void marcarComoAPuntoDeCumplirse(){
        aPuntoDeCumplirse = true;
    }




    @Override
    public String toString()
    {
        return "Programacion{" +
                "idPedido=" + idPedido +
                ", uuidProducto=" + uuidProducto +
                ", idsVueloRuta=" + idsVueloRuta +
                ", idPlanificacion=" + idPlanificacion +
                ", activo=" + activo +
                '}';
    }
}
