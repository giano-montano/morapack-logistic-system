package pe.edu.pucp.inf.pddsbackend.models.entities;


import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jdk.jfr.Enabled;
import lombok.Data;
import org.springframework.context.annotation.Primary;

@Data
@Enabled
@Entity
public class Ciudad {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY) // Lo hace incremental
    Long id;

    String codigoCiudadEn4letras;
}
