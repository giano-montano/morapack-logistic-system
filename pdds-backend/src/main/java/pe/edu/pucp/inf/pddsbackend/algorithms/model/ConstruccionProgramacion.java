package pe.edu.pucp.inf.pddsbackend.algorithms.model;

import jakarta.validation.constraints.NotNull;
import pe.edu.pucp.inf.pddsbackend.modelos.dominio.Producto;

import java.util.LinkedList;

public record ConstruccionProgramacion(
        @NotNull LinkedList<Long> ruta,
        @NotNull Producto productoEscogido,
        int capacidadRutaParaMasProds
) {
}
