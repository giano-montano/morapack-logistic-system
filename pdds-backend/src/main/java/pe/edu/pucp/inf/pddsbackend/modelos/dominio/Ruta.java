package pe.edu.pucp.inf.pddsbackend.modelos.dominio;

import lombok.AllArgsConstructor;

import java.util.LinkedList;
import java.util.UUID;

@AllArgsConstructor
public class Ruta {
    UUID uuid;
    LinkedList<Vuelo> vuelosRuta;
    Double puntaje;

    // copia
    public Ruta(Ruta ruta) {
        this.uuid = ruta.uuid;
        this.vuelosRuta = new LinkedList<>(vuelosRuta); // OJO! NO ESTÁ HACIENDO DEEP COPY A LOS VUELOS EN SÍ
        this.puntaje = ruta.puntaje;
    }

}
