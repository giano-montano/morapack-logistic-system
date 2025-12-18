package pe.edu.pucp.inf.pddsbackend.modelos.dominio;

import lombok.Getter;
import lombok.Setter;
import pe.edu.pucp.inf.pddsbackend.miscelaneo.Bitacora;
import pe.edu.pucp.inf.pddsbackend.miscelaneo.Hiperparametros;
import static pe.edu.pucp.inf.pddsbackend.algorithms.model.EstadoGlobal.deepCopy;
import java.io.Serializable;
import java.time.Instant;
import java.time.temporal.ChronoUnit;


public class Programacion implements Serializable {
    private char estado;
    @Getter private final Pedido pedido;
    @Setter @Getter private Producto producto;
    @Getter private final Ruta ruta;

    /*
     * Constructor principal. Programación nace como tipo C (Creación)
     */
    public Programacion(Pedido pedido, Producto producto, Ruta ruta) {   
        if(producto.validarPlanificadoNoExistente_C()){
            this.pedido = pedido;
            this.producto = producto;
            this.ruta = ruta;
            this.estado = 'C';
            return;
        }
        
        String error = String.format("ERROR (Programación): El producto no es de tipo C");
        Bitacora.escribir(error);
        throw new IllegalStateException(error);  
    }

    /*
     * Constructor copia profunda usando serialización
     */
    public Programacion(Programacion original) {
        Programacion copia = deepCopy(original);
        this.pedido = copia.pedido;
        this.producto = copia.producto;
        this.ruta = copia.ruta;
        this.estado = copia.estado;
    }

    /*
     * Validar programacion de tipo E (Existentes) [producto tipo D, t_salida < t_actual < t_incancelable || t_actual < t_salida]
     */
    public boolean validarExistente_E(Instant instanteActual) {
        if(this.producto.validarPlanificadoExistente_D()) {
            if(this.ruta.verificarRutaEnIntermedios(instanteActual) || this.ruta.verificarRutaNoEmpieza(instanteActual)) {
                return true;
            }
        }

        return false;
    }

    /*
     * Validar programacion de tipo I (Incancelable) [producto tipo B, t_incancelable < t_actual < t_llegada + HORA_RECOJO]
     */
    public boolean validarIncancelable_I(Instant instanteActual) {
        if(this.producto.validarIncancelable_B()) {
            if(this.ruta.verificarRutaEnUltimoTramo(instanteActual)) {
                return true;
            }
        }

        return false;
    }

    /*
     * Validar programacion de tipo C (Creada) [producto tipo C, t_actual < t_salida]
     */
    public boolean validarCreada_C(Instant instanteActual) {
        if(this.producto.validarPlanificadoNoExistente_C()) {
            if(this.ruta.verificarRutaNoEmpieza(instanteActual)) {
                return true;
            }
        }

        return false;
    }

    /*
     * Validar programacion de tipo T (Terminada) [t_llegada + HORA_RECOJO < t_actual]
     */
    public boolean validarTerminada_T(Instant instanteActual) {
        if(this.ruta.verificarRutaFinalizada(instanteActual)) {
            return true;
        }

        return false;
    }

    /*
     * Transición de tipo C a tipo I.
     */
    public void transCreada_C_Incancelable_I() {
        if(this.estado == 'C'){
            this.estado = 'I';
            this.producto.transPlanificadoNoExistente_C_Incancelable_B();
            return;
        }

        String error = String.format("ERROR (Transición producto): D → A inválido");
        Bitacora.escribir(error);
        throw new IllegalStateException(error);
    }

    /*
     * Transición de tipo C a tipo E.
     */
    public void transCreada_C_Existente_E() {
        if(this.estado == 'C'){
            this.estado = 'E';
            this.producto.transPlanificadoNoExistente_C_PlanificadoExistente_D();
            return;
        }

        String error = String.format("ERROR (Transición producto): C → E inválido");
        Bitacora.escribir(error);
        throw new IllegalStateException(error);
    }

    /*
     * Transición de tipo E a tipo I.
     */
    public void transExistente_E_Incancelable_I() {
        if(this.estado == 'E'){
            this.estado = 'I';
            this.producto.transPlanificadoExistente_D_Incancelable_B();
            return;
        }

        String error = String.format("ERROR (Transición producto): E → I inválido");
        Bitacora.escribir(error);
        throw new IllegalStateException(error);
    }

    /*
     * Transición de tipo I a tipo T.
     */
    public void transIncancelable_I_Terminada_T() {
        if(this.estado == 'I'){
            this.estado = 'T';
            return;
        }

        String error = String.format("ERROR (Transición producto): I → T inválido");
        Bitacora.escribir(error);
        throw new IllegalStateException(error);
    }

    @Override
    public String toString() {
        return "Programacion{" +
                "idPedido=" + this.pedido.getId() +
                ", uuidProducto=" + this.producto.getId() +
                ", idsVueloRuta=" + this.ruta.getUuid() + 
                '}';
    }
}
