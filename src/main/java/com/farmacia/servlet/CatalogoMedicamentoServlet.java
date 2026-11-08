package com.farmacia.servlet;

import com.farmacia.dao.CatalogoMedicamentoDAO;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;

/** API de búsqueda para sugerir coincidencias oficiales CUM en la recepción de facturas. */
@WebServlet("/CatalogoMedicamentoServlet")
public class CatalogoMedicamentoServlet extends HttpServlet {
    /** Devuelve coincidencias como JSON para que la recepción de compras las muestre sin recargar la página. */
    @Override protected void doGet(HttpServletRequest req,HttpServletResponse resp)throws ServletException,IOException{
        // Fija UTF-8 para que nombres farmacológicos con tildes lleguen intactos al navegador.
        resp.setCharacterEncoding("UTF-8");resp.setContentType("application/json;charset=UTF-8");
        // Busca un máximo de veinte resultados y forma manualmente una respuesta JSON escapada.
        try{List<Map<String,Object>> rows=new CatalogoMedicamentoDAO().buscar(req.getParameter("q"),20);StringBuilder json=new StringBuilder("[");
            for(Map<String,Object> row:rows){if(json.length()>1)json.append(',');json.append('{').append("\"id\":").append(row.get("id")).append(',');
                campo(json,"producto",row.get("producto"));campo(json,"registro",row.get("registro"));campo(json,"cum",row.get("cum"));campo(json,"descripcion",row.get("descripcion"));campo(json,"estado",row.get("estado"));campo(json,"unidad",row.get("unidad"));campo(json,"atc",row.get("atc"));campo(json,"ium",row.get("ium"));campo(json,"via",row.get("via"));campo(json,"forma",row.get("forma"));campo(json,"componentes",row.get("componentes"));if(json.charAt(json.length()-1)==',')json.setLength(json.length()-1);json.append('}');}
            resp.getWriter().write(json.append(']').toString());
        }catch(SQLException e){
            // Registra el error interno y evita exponer detalles de conexión o SQL al navegador.
            getServletContext().log("Falló la búsqueda del catálogo INVIMA",e);resp.setStatus(500);resp.getWriter().write("{\"error\":\"No se pudo consultar el catálogo\"}");}
    }
    /** Escribe un campo JSON y protege comillas, barras y controles para no romper la respuesta. */
    private static void campo(StringBuilder out,String nombre,Object valor){out.append('"').append(nombre).append("\":");if(valor==null){out.append("null,");return;}out.append('"');String s=valor.toString();for(int i=0;i<s.length();i++){char c=s.charAt(i);switch(c){case '"'->out.append("\\\"");case '\\'->out.append("\\\\");case '\n'->out.append("\\n");case '\r'->out.append("\\r");case '\t'->out.append("\\t");default->{if(c<32)out.append(String.format("\\u%04x",(int)c));else out.append(c);}}}out.append("\",");}
}
