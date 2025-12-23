package pe.edu.pucp.inf.pddsbackend.modelos.dominio;

import lombok.Getter;
import lombok.Setter;
import pe.edu.pucp.inf.pddsbackend.miscelaneo.Bitacora;

import java.io.Serializable;
import java.time.Instant;
import java.util.LinkedList;
import java.util.UUID;

import static pe.edu.pucp.inf.pddsbackend.algorithms.model.EstadoGlobal.deepCopy;

public class Producto implements Serializable {
    @Getter private boolean existente = false;
    private boolean planificado = false;
    private boolean incancelable = false;
    @Getter private final UUID id;
    @Getter private final Almacen almacenOrigen;

    /*
     * Constructor principal. Producto nace como tipo C (planificado no existente)
     */
    public Producto(Almacen almacenOrigen) {
        this.existente = false; 
        this.planificado = true;
        this.incancelable = false;

        this.id = UUID.randomUUID();
        this.almacenOrigen = almacenOrigen;

//        if(!this.almacenOrigen.isInfinito()){
            String error = String.format("ERROR NO CONSIDERADO (Producto): El almacen origen debe tener inventario infinito");
            Bitacora.escribir(error);
//            throw new IllegalStateException(error);
//        }
    }

    /*
     * Constructor copia profunda usando serialización
     */
    public Producto(Producto value) {
        Producto copia = deepCopy(value);
        this.id = copia.id;
        this.existente = copia.existente;
        this.planificado = copia.planificado;
        this.incancelable = copia.incancelable;
        this.almacenOrigen = copia.almacenOrigen;
    }

    /*
     * Validar producto de tipo A (No planificado) [existente = true, planificado = false, incancelable = false]
     */
    public boolean validarNoPlanificado_A() {
        if(this.existente && !this.planificado && !this.incancelable) return true;
        return false;
    }

    /*
     * Validar producto de tipo B (incancelable) [existente = true, planificado = true, incancelable = true]
     */
    public boolean validarIncancelable_B(){
        if(this.existente && this.planificado && this.incancelable) return true;
        return false;
    }

    /*
     * Validar producto de tipo D (Planificado existente) [existente = true, planificado = true, incancelable = false] 
     */
    public boolean validarPlanificadoExistente_D(){
        if(this.existente && this.planificado && !this.incancelable) return true;
        return false;
    }

    /*
     * Validar producto de tipo C (Planificado no existente) [existente = false, planificado = true, incancelable = false]
     */
    public boolean validarPlanificadoNoExistente_C(){
        if(!this.existente && this.planificado && !this.incancelable) return true;
        return false;
    }

    /*
     * Transición de tipo A a tipo D
     */
    public void transNoPlanificado_A_PlanificadoExistente_D(){
        if(validarNoPlanificado_A()){
            this.existente = true;
            this.planificado = true;
            this.incancelable = false;
            return;
        }

        String error = String.format("ERROR (Transición producto): A → D inválido");
        Bitacora.escribir(error);
        throw new IllegalStateException(error);
    }

    public void transNoPlanificado_A_PlanificadoExistente_Dv2(){
//        if(validarNoPlanificado_A()){
            this.existente = true;
            this.planificado = true;
            this.incancelable = false;
            return;
//        }
//
//        String error = String.format("ERROR (Transición producto): A → D inválido");
//        Bitacora.escribir(error);
//        throw new IllegalStateException(error);
    }

    /*
     * Transición de tipo D a tipo A
     */
    public void transPlanificadoExistente_D_NoPlanificado_A(){
        if(validarPlanificadoExistente_D()){
            this.existente = true;
            this.planificado = false;
            this.incancelable = false;
            return;
        }

        String error = String.format("ERROR (Transición producto): D → A inválido");
        Bitacora.escribir(error);
        throw new IllegalStateException(error);
    }
    public void transPlanificadoExistente_D_NoPlanificado_Av2(){
//        if(validarPlanificadoExistente_D()){
            this.existente = true;
            this.planificado = false;
            this.incancelable = false;
            return;
//        }
//
//        String error = String.format("ERROR (Transición producto): D → A inválido");
//        Bitacora.escribir(error);
//        throw new IllegalStateException(error);
    }

    /*
     * Transición de tipo C a tipo B
     */
    public void transPlanificadoNoExistente_C_Incancelable_B(){
        if(validarPlanificadoNoExistente_C()){
            this.existente = true;
            this.planificado = true;
            this.incancelable = true;
            return;
        }

        String error = String.format("ERROR (Transición producto): C → B inválido");
        Bitacora.escribir(error);
        throw new IllegalStateException(error);
    }
    public void transPlanificadoNoExistente_C_Incancelable_Bv2(){
            this.existente = true;
            this.planificado = true;
            this.incancelable = true;
            return;
    }

    /*
     * Transición de tipo C a tipo D
     */
    public void transPlanificadoNoExistente_C_PlanificadoExistente_D(){
        if(validarPlanificadoNoExistente_C()){
            this.existente = true;
            this.planificado = true;
            this.incancelable = false;
            return;
        }

        String error = String.format("ERROR (Transición producto): C → D inválido");
        Bitacora.escribir(error);
        throw new IllegalStateException(error);
    }
    public void transPlanificadoNoExistente_C_PlanificadoExistente_Dv2(){
            this.existente = true;
            this.planificado = true;
            this.incancelable = false;
            return;

    }

    /*
     * Transición de tipo D a tipo B
     */
    public void transPlanificadoExistente_D_Incancelable_B(){
        if(validarPlanificadoExistente_D()){
            this.existente = true;
            this.planificado = true;
            this.incancelable = true;
            return;
        }

        String error = String.format("ERROR (Transición producto): D → B inválido");
        Bitacora.escribir(error);
        throw new IllegalStateException(error);
    }
    public void transPlanificadoExistente_D_Incancelable_Bv2(){
//        if(validarPlanificadoExistente_D()){
            this.existente = true;
            this.planificado = true;
            this.incancelable = true;
//            return;
//        }
//
//        String error = String.format("ERROR (Transición producto): D → B inválido");
//        Bitacora.escribir(error);
//        throw new IllegalStateException(error);
    }

    /*
     * Obtener continente del producto
     */
    public Continente obtenerContinente() {
        return this.almacenOrigen.getContinente(); 
    }

    /*
     * Comparar productos por UUID
     */
    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        Producto producto = (Producto) obj;
        return this.id.equals(producto.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }

    @Override
    public String toString(){
        String tipo;
        if (validarNoPlanificado_A()) tipo = "A";
        else if (validarIncancelable_B()) tipo = "B";
        else if (validarPlanificadoNoExistente_C()) tipo = "C";
        else if (validarPlanificadoExistente_D()) tipo = "D";
        else tipo = "?";
        
        return String.format("Producto[UUID=%s, tipo=%s, almacenOrigen=%s (%s)]",
                id.toString().substring(0, 8),
                tipo,
                almacenOrigen.getId(),
                almacenOrigen.getNombreCiudad());
    }
}
