package com.farmacia.servlet;

import com.farmacia.dao.CatalogoMedicamentoDAO;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.MultipartConfig;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.sql.SQLException;

/** Pantalla y acción de carga del conjunto oficial INVIMA de medicamentos humanos. */
@WebServlet("/CatalogoInvimaServlet")
@MultipartConfig(maxFileSize=80L*1024*1024, maxRequestSize=80L*1024*1024)
public class CatalogoInvimaServlet extends HttpServlet {
    /** Muestra cuántas presentaciones CUM hay cargadas y renderiza el formulario de actualización. */
    @Override protected void doGet(HttpServletRequest req,HttpServletResponse resp)throws ServletException,IOException{
        try{req.setAttribute("cantidadCatalogo",new CatalogoMedicamentoDAO().contar());}
        catch(SQLException e){getServletContext().log("No se pudo consultar el catálogo INVIMA",e);req.setAttribute("errorCatalogo",true);}
        req.getRequestDispatcher("/catalogo-invima.jsp").forward(req,resp);
    }
    /** Recibe un CSV, valida su extensión y delega la importación transaccional al DAO. */
    @Override protected void doPost(HttpServletRequest req,HttpServletResponse resp)throws ServletException,IOException{
        req.setCharacterEncoding("UTF-8");
        try{
            // Obtiene el archivo multipart adjunto en el formulario de administración.
            var part=req.getPart("archivoCatalogo");
            String nombre=part==null?"":part.getSubmittedFileName();
            // Acepta únicamente CSV, que es el formato del conjunto abierto de INVIMA.
            if(nombre==null||!nombre.toLowerCase(java.util.Locale.ROOT).endsWith(".csv"))throw new IllegalArgumentException("Debes cargar el archivo CSV oficial descargado de Datos Abiertos.");
            // Carga el contenido directamente desde la petición sin copiarlo a una carpeta pública.
            int filas;try(var in=part.getInputStream()){filas=new CatalogoMedicamentoDAO().importar(in);}
            // Redirige con el número procesado para presentar el resultado en la pantalla.
            resp.sendRedirect(req.getContextPath()+"/CatalogoInvimaServlet?importados="+filas);
        }catch(IllegalArgumentException|SQLException e){getServletContext().log("No se pudo importar el catálogo INVIMA",e);resp.sendRedirect(req.getContextPath()+"/CatalogoInvimaServlet?error=1");}
    }
}
