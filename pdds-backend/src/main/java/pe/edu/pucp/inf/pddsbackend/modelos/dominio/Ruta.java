package pe.edu.pucp.inf.pddsbackend.modelos.dominio;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.LinkedList;
import java.util.UUID;

@AllArgsConstructor
@Getter
public class Ruta {
    private UUID uuid;
    private LinkedList<Vuelo> vuelosRuta;
    private Double puntaje;

    public Ruta(LinkedList<Vuelo> vs) {
        vuelosRuta = vs;
        uuid = UUID.randomUUID();
        puntaje = 0.0;
    }

    // copia
    public Ruta(Ruta ruta) {
        this.uuid = ruta.uuid;
        this.vuelosRuta = new LinkedList<>(vuelosRuta); // OJO! NO ESTÁ HACIENDO DEEP COPY A LOS VUELOS EN SÍ
        this.puntaje = ruta.puntaje;
    }

}
