package pe.edu.pucp.inf.pddsbackend.websocket.dto;

/**
 * DTO para notificar cambios en la capacidad de un almacén via WebSocket Se
 * envía cuando: - Sale un vuelo (reduce capacidad - solo si NO es infinito) -
 * Llega un vuelo (aumenta capacidad - solo si NO es infinito) - Cliente retira
 * productos (reduce capacidad - solo si NO es infinito)
 *
 * ⚠️ NO se envía para almacenes infinitos ya que su capacidad no cambia
 * realmente
 */
public class CambioCapacidadAlmacenDTO
{
    private Long id;
    private Integer capacidadOcupada;
    private Integer capacidadMaxima;

    public CambioCapacidadAlmacenDTO()
    {
    }

    public CambioCapacidadAlmacenDTO(Long id, Integer capacidadOcupada, Integer capacidadMaxima)
    {
        this.id = id;
        this.capacidadOcupada = capacidadOcupada;
        this.capacidadMaxima = capacidadMaxima;
    }

    // Getters y Setters
    public Long getId()
    {
        return id;
    }

    public void setId(Long id)
    {
        this.id = id;
    }

    public Integer getInventario()
    {
        return capacidadOcupada;
    }

    public void setCapacidadOcupada(Integer capacidadOcupada)
    {
        this.capacidadOcupada = capacidadOcupada;
    }

    public Integer getCapacidadMaxima()
    {
        return capacidadMaxima;
    }

    public void setCapacidadMaxima(Integer capacidadMaxima)
    {
        this.capacidadMaxima = capacidadMaxima;
    }
}
