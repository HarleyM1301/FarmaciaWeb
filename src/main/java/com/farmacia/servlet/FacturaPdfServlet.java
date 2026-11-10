package com.farmacia.servlet;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.MultipartConfig;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.Part;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Base64;
import javax.imageio.ImageIO;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.text.PDFTextStripper;

/** Extrae texto de factura PDF o renderiza páginas para OCR local en el navegador. */
@WebServlet("/FacturaPdfServlet")
@MultipartConfig(maxFileSize=10L*1024*1024, maxRequestSize=11L*1024*1024)
public class FacturaPdfServlet extends HttpServlet {
    private static final int MAX_PAGINAS_OCR = 8;

    @Override protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        resp.setCharacterEncoding("UTF-8");
        resp.setContentType("application/json;charset=UTF-8");
        try {
            Part parte=req.getPart("archivo");
            if(parte==null||parte.getSize()==0)throw new IllegalArgumentException("Selecciona un archivo PDF.");
            if(parte.getSize()>10L*1024*1024)throw new IllegalArgumentException("El PDF supera el límite de 10 MB.");
            byte[] datos;
            try(var in=parte.getInputStream()){datos=in.readNBytes(10*1024*1024+1);}
            if(datos.length>10*1024*1024)throw new IllegalArgumentException("El PDF supera el límite de 10 MB.");
            try(PDDocument pdf=Loader.loadPDF(datos)){
                if(pdf.isEncrypted())throw new IllegalArgumentException("El PDF está protegido con contraseña.");
                PDFTextStripper extractor=new PDFTextStripper();extractor.setSortByPosition(true);
                String texto=extractor.getText(pdf).trim();
                if(texto.length()>=40){resp.getWriter().write("{\"texto\":\""+escapar(texto)+"\",\"paginas\":[],\"totalPaginas\":"+pdf.getNumberOfPages()+"}");return;}
                int cantidad=Math.min(pdf.getNumberOfPages(),MAX_PAGINAS_OCR);
                PDFRenderer renderer=new PDFRenderer(pdf);renderer.setSubsamplingAllowed(true);
                StringBuilder salida=new StringBuilder("{\"texto\":\"\",\"totalPaginas\":").append(pdf.getNumberOfPages()).append(",\"paginas\":[");
                for(int i=0;i<cantidad;i++){
                    if(i>0)salida.append(',');
                    PDRectangle pagina=pdf.getPage(i).getMediaBox();
                    float ancho=Math.max(1,pagina.getWidth()),alto=Math.max(1,pagina.getHeight());
                    float dpi=(float)Math.min(120,Math.min(1800*72.0/ancho,2400*72.0/alto));
                    BufferedImage imagen=renderer.renderImageWithDPI(i,dpi,ImageType.RGB);
                    ByteArrayOutputStream bytes=new ByteArrayOutputStream();ImageIO.write(imagen,"jpeg",bytes);imagen.flush();
                    salida.append("{\"pagina\":").append(i+1).append(",\"imagen\":\"data:image/jpeg;base64,")
                            .append(Base64.getEncoder().encodeToString(bytes.toByteArray())).append("\"}");
                }
                resp.getWriter().write(salida.append("]}").toString());
            }
        }catch(IllegalArgumentException e){resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);resp.getWriter().write("{\"error\":\""+escapar(e.getMessage())+"\"}");}
        catch(Exception e){getServletContext().log("No se pudo procesar la factura PDF",e);resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);resp.getWriter().write("{\"error\":\"No se pudo leer el PDF. Comprueba que el archivo esté completo y no tenga contraseña.\"}");}
    }

    private static String escapar(String texto){StringBuilder s=new StringBuilder();for(char c:texto.toCharArray()){switch(c){case '"'->s.append("\\\"");case '\\'->s.append("\\\\");case '\n'->s.append("\\n");case '\r'->s.append("\\r");case '\t'->s.append("\\t");default->{if(c<32)s.append(String.format("\\u%04x",(int)c));else s.append(c);}}}return s.toString();}
}
