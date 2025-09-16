package pe.edu.pucp.inf.pddsbackend.dto;

import jakarta.validation.constraints.NotNull;
import pe.edu.pucp.inf.pddsbackend.models.entities.Almacen;
import pe.edu.pucp.inf.pddsbackend.models.entities.Pedido;

import java.time.Instant;

public record GuardarPedidoDTO(
        Long idCliente,

        @NotNull
        Long idAlmacenDestino,

        @NotNull
        Integer cantProductos,

        Instant instanteRegistro // no necesariamente es cuando se registra en el sistema

) {
    //podría ir en un mapper separado tal vez
    public Pedido toEntity(){
        // veamos almacén de destino... mejor en el service

        Pedido pedido = Pedido.builder()
        // NOTA: no seteamos almacenDestino aquí
                .cantidadProductosTotal(cantProductos)
                .instanteRegistro(instanteRegistro != null ? instanteRegistro : Instant.now())


                .build();
        return pedido;
    }
}
