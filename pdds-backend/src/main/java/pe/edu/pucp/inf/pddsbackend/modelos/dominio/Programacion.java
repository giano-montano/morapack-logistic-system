package pe.edu.pucp.inf.pddsbackend.modelos.dominio;

import lombok.Getter;
import lombok.Setter;

import java.util.LinkedList;
import java.util.UUID;

@Getter
public class Programacion { // Lo que antes era ProgramacionEntidad

    //    private final long id;
    private  long idPedido;
    private final UUID uuidProducto; // exista ya o no, tiene id
    private final LinkedList<Long> idsVueloRuta;
    private  long idPlanificacion; // no interesa mucho por ahora, capaz safa
    @Setter
    private boolean activo=true; // recordemos que se irán descartando programaciones anteriores en cada planif
    //o sea le pondremos activo=false a la "tanda" anterior.

    // Constructor principal para programaciones que se vayan haciendo en el algoritmo
    public Programacion(
            long idPedido,
            UUID uuidProducto,
            LinkedList<Long> ruta
    ) {
        this.idPedido = idPedido;
        this.uuidProducto = uuidProducto;
        this.idsVueloRuta = ruta;
    }

    public Programacion(Programacion original) {
//        id = original.id;
        idPedido = original.idPedido;
        uuidProducto = original.uuidProducto;
        idsVueloRuta = original.idsVueloRuta;
        idPlanificacion = original.idPlanificacion;
    }

    public LinkedList<Long> getIdsVueloRuta(){
        return new LinkedList<>(idsVueloRuta);
    }
}