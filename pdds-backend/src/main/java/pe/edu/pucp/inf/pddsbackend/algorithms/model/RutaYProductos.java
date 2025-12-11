package pe.edu.pucp.inf.pddsbackend.algorithms.model;
import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.LinkedList;

import pe.edu.pucp.inf.pddsbackend.modelos.dominio.Producto;
import pe.edu.pucp.inf.pddsbackend.modelos.dominio.Vuelo;


public record RutaYProductos(
        @NotNull List<Producto> produtosElegidos,
        @NotNull LinkedList<Vuelo> rutaElegida) {
}