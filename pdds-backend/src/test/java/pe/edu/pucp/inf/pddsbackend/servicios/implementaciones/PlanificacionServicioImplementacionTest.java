package pe.edu.pucp.inf.pddsbackend.servicios.implementaciones;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import pe.edu.pucp.inf.pddsbackend.miscelaneo.Bitacora;
import pe.edu.pucp.inf.pddsbackend.modelos.dominio.Planificacion;
import pe.edu.pucp.inf.pddsbackend.repositorios.PlanificacionRepositorio;

@ExtendWith(MockitoExtension.class)
class PlanificacionServicioImplementacionTest
{

    @Mock
    private PlanificacionRepositorio planificacionRepositorio;

    private PlanificacionServicioImplementacion servicio;

    @BeforeEach
    void setUp()
    {
        servicio = new PlanificacionServicioImplementacion(planificacionRepositorio);
    }


    private int capacidad = 20;
    private List<Integer> inventario;
    private TreeMap<Integer, Integer> cambios;
    private Boolean esInfinito = false;

    private Integer DELTAAA;

    @Test
    void planificarTest()
    {
        Bitacora.escribir("ÑAJAJAJJAJ");
        cambios = new TreeMap<>();

        cambios.put(1, 2);
        cambios.put(3, +5);
        cambios.put(4, 1);

        this.inventario = new ArrayList<>();
        inventario.add(1);
        inventario.add(1);
        inventario.add(1);
        inventario.add(1);
        inventario.add(1);
        inventario.add(1);
        inventario.add(1);
        inventario.add(1);
        inventario.add(1);
        inventario.add(1);


        int instanteActual = 1;
        Bitacora.escribir(cambios.toString());
        /******/
        Boolean instanteActualExiste, instanteEsMayor;
        int posicion, maxDelta, minDelta, nNumeros, listaNumeros[], sumasParciales[];

        listaNumeros = new int[this.cambios.size()+5];
        sumasParciales = new int[this.cambios.size()+5];

        if(this.esInfinito == true)
        {
            DELTAAA = Integer.MAX_VALUE;
        }

        nNumeros = 0;
        posicion = 0;
        listaNumeros[nNumeros] = this.inventario.size();
        sumasParciales[nNumeros] = this.inventario.size();	
        instanteEsMayor = true;
        instanteActualExiste = this.cambios.containsKey(instanteActual);

        for(Map.Entry<Integer, Integer> entry : this.cambios.entrySet())
        {
            int instante = entry.getKey();
            int cambio = entry.getValue();

            nNumeros++;

            if(instanteActualExiste == true && instante == instanteActual)
            {
                posicion = nNumeros;
            }

            if(instanteActualExiste == false && instante > instanteActual)
            {
                instanteActualExiste = true;
                listaNumeros[nNumeros] = 0;
                sumasParciales[nNumeros] = sumasParciales[nNumeros - 1];
                posicion = nNumeros;
                nNumeros++;
                instanteEsMayor = false;
            }

            listaNumeros[nNumeros] = cambio;
            sumasParciales[nNumeros] = sumasParciales[nNumeros - 1] + cambio;
        }

        
        if(instanteEsMayor == true && instanteActualExiste == false)
        {
            nNumeros++;
            listaNumeros[nNumeros] = 0;
            sumasParciales[nNumeros] = sumasParciales[nNumeros - 1];
            posicion = nNumeros;
        }


        Bitacora.escribir("pos %d", posicion);
        maxDelta = Integer.MAX_VALUE; 
        minDelta = Integer.MIN_VALUE;

        for (int indice = posicion; indice <= nNumeros; indice++) {
            minDelta = Math.max(minDelta, -sumasParciales[indice]);                
            maxDelta = Math.min(maxDelta, this.capacidad - sumasParciales[indice]);
        }

        maxDelta = (maxDelta <= 0)? 0 : maxDelta;
        


        Bitacora.escribir("Lista numeros: %s",Arrays.toString(listaNumeros));
        Bitacora.escribir("Sumas parcialles : %s",Arrays.toString(sumasParciales));
        Bitacora.escribir("delta : %d",maxDelta );


        /*
        Bitacora.escribir("XXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXX");
        Bitacora.escribir("planificarTest ejecutando");
        Bitacora.escribir("XXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXX");

        Planificacion planificacion = crearPlanificacionDummy();

        assertDoesNotThrow(() -> {
            servicio.planificar(planificacion);
        });

        Bitacora.escribir("XXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXX");
        */
    }

    private Planificacion crearPlanificacionDummy()
    {
        return Planificacion.builder()
                .id(UUID.randomUUID())
                .instanteActual(Instant.parse("2025-01-01T10:00:00Z"))
                .build();
    }
}
