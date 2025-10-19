package pe.edu.pucp.inf.pddsbackend.miscelaneo;

import java.io.*;
import java.util.*;

import lombok.NoArgsConstructor;
import pe.edu.pucp.inf.pddsbackend.algoritmo.Estado;
import pe.edu.pucp.inf.pddsbackend.modelos.dominio.Continente;

@NoArgsConstructor
public class Lectora
{

    public void leerArchivo(Estado estadoInicial, String rutaArchivo)
            throws IOException
    {
        String linea;
        BufferedReader bufferedReader;
        Continente continente;

        bufferedReader = this.abrirArchivo(rutaArchivo);

        while ((linea = bufferedReader.readLine()) != null)
        {
            linea = linea.trim();
            if (linea.isEmpty())
            {
                continue;
            }
            ;

            if (!Character.isDigit(linea.charAt(0)))
            {
                continente = Continente.valueOf(
                        linea.trim().replaceAll("\\s+", "_").toUpperCase());
                continue;
            }

            String[] partes = linea.split("\\s+");
            String id = partes[1];
            String ciudad = partes[2];
            String pais = partes[3];
            long utc = Long.parseLong(partes[5]);
            long capacidad = Long.parseLong(partes[6]);

            System.out.println(ciudad);
            System.out.println(pais);
            System.out.println(utc);
            System.out.println(capacidad);
            // almacenes.add(new Almacen(id, capacidad, utc, ciudad, pais,
            // continenteActual));
        }
    }

    private BufferedReader abrirArchivo(String rutaArchivo)
    {
        try
        {
            FileReader fileReader = new FileReader(rutaArchivo);
            BufferedReader bufferedReader = new BufferedReader(fileReader);

            return bufferedReader;
        }
        catch (IOException e)
        {
            throw new UncheckedIOException(e);
        }
    }
}
