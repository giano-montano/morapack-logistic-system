package pe.edu.pucp.inf.pddsbackend.models.entities;

import jakarta.persistence.*;
import lombok.Data;
import pe.edu.pucp.inf.pddsbackend.models.domain.Continente;

@Data
@Entity
@Table()
public class Pais {

    @Id
    @Column(name = "codigo", length = 2)   // ISO Alpha-2
    private String codigo;

    @Column(name = "nombre", nullable = false)
    private String nombre;

    @Enumerated(EnumType.STRING)
    private Continente continente;
    // otros atributos si los necesitas, ejemplo: nombreAlternativo, etc.
    // Constructor sin args, getters, setters
}

