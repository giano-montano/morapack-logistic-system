package pe.edu.pucp.inf.pddsbackend.modelos.dominio;

import lombok.Getter;
import lombok.Setter;
import pe.edu.pucp.inf.pddsbackend.miscelaneo.Bitacora;
import pe.edu.pucp.inf.pddsbackend.miscelaneo.Hiperparametros;
import static pe.edu.pucp.inf.pddsbackend.algorithms.model.EstadoGlobal.deepCopy;
import java.io.Serializable;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import org.springframework.security.access.method.P;


public class Programacion implements Serializable {
    @Getter private char estado;
    @Getter private final Pedido pedido;
    @Setter @Getter private Producto producto;
    @Getter private final Ruta ruta;

    /*
     * Constructor principal. Programación nace como tipo C (Creación)
     * Los productos que se programan son tipo C (Planificado no existente) o tipo A (No planificado)
     */
    public Programacion(Pedido pedido, Producto producto, Ruta ruta) {   
        this.pedido = pedido;
        this.producto = producto;
        this.ruta = ruta;

        if(producto.validarPlanificadoNoExistente_C()){    
            this.estado = 'C';
            return;
        }
        
        if(producto.validarNoPlanificado_A()){    
            this.estado = 'E'; // que imvecil xd 
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
     * Validar programacion de tipo E (Existentes) [producto tipo D, t_salida < t_actual <= t_incancelable || t_actual < t_salida]
     * Agregado <= en t_incancelable
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
        return this.estado == 'I';
        /*/
        if(this.producto.validarIncancelable_B()) {
            if(this.ruta.verificarRutaEnUltimoTramo(instanteActual)) {
                return true;
            }
        }

        return false;*/
    }

    /*
     * Validar programacion de tipo C (Creada) [producto tipo C, t_actual < t_salida]
     */
    public boolean validarCreada_C(Instant instanteActual) {
        /*
        if(this.producto.validarPlanificadoNoExistente_C()) {
            if(this.ruta.verificarRutaNoEmpieza(instanteActual)) {
                return true;
            }
        }

        return false;*/
        return this.estado == 'C';
    }

    /*
     * Validar programacion de tipo T (Terminada) [t_llegada + HORA_RECOJO < t_actual]
     */
    public boolean validarTerminada_T(Instant instanteActual) {
        /*/
        if(this.ruta.verificarRutaFinalizada(instanteActual)) {
            return true;
        }

        return false;*/
        return this.estado == 'T';
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

        String error = String.format("ERROR (Transición prog): C → I inválido");
        Bitacora.escribir(error);
        throw new IllegalStateException(error);
    }

    public void transCreada_C_Incancelable_Iv2() {
            this.estado = 'I';
            this.producto.transPlanificadoNoExistente_C_Incancelable_Bv2();
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

        String error = String.format("ERROR (Transición prog): C → E inválido");
        Bitacora.escribir(error);
        throw new IllegalStateException(error);
    }
    public void transCreada_C_Existente_Ev2() {
            this.estado = 'E';
            this.producto.transPlanificadoNoExistente_C_PlanificadoExistente_Dv2();
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
    public void transExistente_E_Incancelable_Iv2() {
//        if(this.estado == 'E'){
            this.estado = 'I';
//            this.producto.transPlanificadoExistente_D_Incancelable_B();
            return;
//        }
//
//        String error = String.format("ERROR (Transición producto): E → I inválido");
//        Bitacora.escribir(error);
//        throw new IllegalStateException(error);
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

    public void transIncancelable_I_Terminada_Tv2() {
//        if(this.estado == 'I'){
            this.estado = 'T';
//            return;
//        }
//
//        String error = String.format("ERROR (Transición producto): I → T inválido");
//        Bitacora.escribir(error);
//        throw new IllegalStateException(error);
    }

    /*
     * Comparar programacion por UUID
     */
    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        Programacion programacion = (Programacion) obj;
        return this.pedido.equals(programacion.pedido) && this.producto.equals(programacion.producto);
    }

    @Override
    public int hashCode() {
        return 31 * pedido.hashCode() + producto.hashCode();
    }

    @Override
    public String toString() {
        String tipoEstado;
        switch (estado) {
            case 'I': tipoEstado = "Incancelable"; break;
            case 'C': tipoEstado = "Creacion"; break;
            case 'E': tipoEstado = "Existente"; break;
            case 'T': tipoEstado = "Terminada"; break;
            default: tipoEstado = "Desconocido";
        }
        
        return String.format("Programacion[pedido=%d, producto=%s, ruta=%s, estado=%s (%c)]",
                pedido.getId(),
                producto.getId().toString().substring(0, 8),
                ruta.toString()/*ruta.getUuid().toString().substring(0, 8)*/,
                tipoEstado,
                estado);
    }

    public String toStringConRutaDetallada() {
        String tipoEstado;
        switch (estado) {
            case 'I': tipoEstado = "Incancelable"; break;
            case 'C': tipoEstado = "Creacion"; break;
            case 'E': tipoEstado = "Existente"; break;
            case 'T': tipoEstado = "Terminada"; break;
            default: tipoEstado = "Desconocido";
        }

        return String.format("Programacion[pedido=%d, producto=%s, ruta=%s, estado=%s (%c)]",
                pedido.getId(),
                producto.getId().toString().substring(0, 8),
                ruta.toStringDetallado()/*ruta.getUuid().toString().substring(0, 8)*/,
                tipoEstado,
                estado);
    }
}
