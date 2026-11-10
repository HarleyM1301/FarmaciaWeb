package com.farmacia.servlet;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.MultipartConfig;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Base64;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/** Extrae XML DIAN o PDF adjunto desde ZIP sin escribir archivos en el servidor. */
@WebServlet("/FacturaZipServlet")
@MultipartConfig(maxFileSize=10L*1024*1024, maxRequestSize=11L*1024*1024)
public class FacturaZipServlet extends HttpServlet {
    private static final int MAX_EXPANSION=20*1024*1024;

    @Override protected void doPost(HttpServletRequest req,HttpServletResponse resp)throws ServletException,IOException{
        resp.setCharacterEncoding("UTF-8");resp.setContentType("application/json;charset=UTF-8");
        try{
            var parte=req.getPart("archivo");if(parte==null||parte.getSize()==0)throw new IllegalArgumentException("Selecciona un archivo ZIP.");
            byte[] xml=null,pdf=null;int expandido=0;
            try(ZipInputStream zip=new ZipInputStream(parte.getInputStream())){
                ZipEntry entrada;
                while((entrada=zip.getNextEntry())!=null){
                    if(entrada.isDirectory())continue;
                    String nombre=entrada.getName().toLowerCase(java.util.Locale.ROOT);
                    byte[] contenido=leerLimitado(zip,MAX_EXPANSION-expandido);
                    expandido+=contenido.length;
                    if(expandido>MAX_EXPANSION)throw new IllegalArgumentException("El contenido descomprimido supera el límite de 20 MB.");
                    if(nombre.endsWith(".xml")&&xml==null)xml=contenido;
                    if(nombre.endsWith(".pdf")&&pdf==null)pdf=contenido;
                    if(xml!=null&&pdf!=null)break;
                }
            }
            if(xml!=null){if(xml.length>10*1024*1024)throw new IllegalArgumentException("El XML de la factura supera el límite de 10 MB.");resp.getWriter().write("{\"tipo\":\"xml\",\"contenido\":\""+escapar(new String(xml,java.nio.charset.StandardCharsets.UTF_8))+"\"}");}
            else if(pdf!=null){if(pdf.length>10*1024*1024)throw new IllegalArgumentException("El PDF de la factura supera el límite de 10 MB.");resp.getWriter().write("{\"tipo\":\"pdf\",\"contenido\":\""+Base64.getEncoder().encodeToString(pdf)+"\"}");}
            else throw new IllegalArgumentException("El ZIP no contiene un XML ni un PDF de factura.");
        }catch(IllegalArgumentException e){resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);resp.getWriter().write("{\"error\":\""+escapar(e.getMessage())+"\"}");}
    }

    private static byte[] leerLimitado(ZipInputStream in,int limite)throws IOException{
        if(limite<=0)throw new IllegalArgumentException("El ZIP supera el límite de 20 MB descomprimidos.");
        ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] buffer=new byte[8192];int leidos;
        while((leidos=in.read(buffer))!=-1){if(out.size()+leidos>limite)throw new IllegalArgumentException("El ZIP supera el límite de 20 MB descomprimidos.");out.write(buffer,0,leidos);}
        return out.toByteArray();
    }
    private static String escapar(String texto){StringBuilder s=new StringBuilder();for(char c:texto.toCharArray()){switch(c){case '"'->s.append("\\\"");case '\\'->s.append("\\\\");case '\n'->s.append("\\n");case '\r'->s.append("\\r");case '\t'->s.append("\\t");default->{if(c<32)s.append(String.format("\\u%04x",(int)c));else s.append(c);}}}return s.toString();}
}
