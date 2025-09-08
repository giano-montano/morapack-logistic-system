package pe.edu.pucp.inf.pddsbackend.utils;

import org.springframework.stereotype.Component;
import pe.edu.pucp.inf.pddsbackend.algorithms.model.VueloForAlgorithm;
import pe.edu.pucp.inf.pddsbackend.algorithms.model.grasp.RutaADestino;

import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

@Component
public class PrettyPrinter {

    public static String printMap(Map map){
        StringBuilder str=new StringBuilder();
        for(Object entry : map.entrySet() ){
            Entry casted= (Entry) entry;
            str.append(casted.getKey()).append(":---------------------\n ").append(casted.getValue().toString()+"\n");
        }
        return str.toString();
    }

    public static String printListOfLists(List<List<Object>> list){
        StringBuilder str=new StringBuilder();
        for ( List<Object> listElement : list) {

            for(Object ob : listElement) {
                str.append(ob.toString()).append("\n");
            }
        }
        return str.toString();
    }

    public static String printList(List list){
        StringBuilder str=new StringBuilder();
            for(Object ob : list) {
                str.append(ob.toString()).append("\n");
            }
        return str.toString();
    }


}
