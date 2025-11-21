package pe.edu.pucp.inf.pddsbackend.modelos.entidades;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "producto")
@Builder
public class ProductoEntidad
{
    @Id
    private UUID uuid; // asignado manualmente por el algoritmo

    private Instant fechaPlanificacion; // cuando el algoritmo lo crea
    private Instant fechaExistencia; // cuando spawnea en un vuelo

    private Boolean existe = false;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private AlmacenEntidad almacenInfinitoOrigen;

    @OneToMany(fetch = FetchType.LAZY)
    private List<VueloEntidad> vuelosRuta;

    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    private AlmacenEntidad almacenActual;

    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    private AlmacenEntidad vueloActual;

    // boolean entregado=false; // Ni bien llegue a su aeropuerto de destino!
    // boolean planificado=false; // Referido a si ha sido planificado ya en medio
    // de la planifación en curso (algoritmo).

}
