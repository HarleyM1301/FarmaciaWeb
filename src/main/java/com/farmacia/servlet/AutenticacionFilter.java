package com.farmacia.servlet;

import java.io.IOException;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.text.Normalizer;
import java.util.Locale;

/** Protege las pantallas internas y redirige visitantes al login. */
@WebFilter(urlPatterns = {"/panel.jsp", "/medicamentos.jsp", "/ventas.jsp", "/compras.jsp", "/reportes.jsp", "/MedicamentoServlet", "/MovimientoServlet", "/FacturaCompraServlet"})
public class AutenticacionFilter implements Filter {
    @Override
    public void doFilter(jakarta.servlet.ServletRequest solicitud,
                         jakarta.servlet.ServletResponse respuesta,
                         FilterChain cadena) throws IOException, ServletException {
        HttpServletRequest request = (HttpServletRequest) solicitud;
        HttpServletResponse response = (HttpServletResponse) respuesta;
        // Solo permite continuar si hay una sesión autenticada.
        HttpSession sesion = request.getSession(false);
        if (sesion == null || sesion.getAttribute("usuario") == null) {
            response.sendRedirect(request.getContextPath() + "/login.jsp");
            return;
        }

        // El administrador y el farmacéutico gestionan el inventario y las compras.
        String ruta = request.getServletPath();
        String rol = normalizar(String.valueOf(sesion.getAttribute("rol")));
        boolean gestionCompleta = rol.equals("admin") || rol.equals("administrador") || rol.equals("farmaceutico");
        boolean esVenta = ruta.equals("/ventas.jsp")
                || (ruta.equals("/MovimientoServlet") && "venta".equals(request.getParameter("tipo")));
        boolean puedeVender = gestionCompleta || rol.equals("cajero") || rol.equals("vendedor");
        boolean requiereGestion = ruta.equals("/medicamentos.jsp") || ruta.equals("/compras.jsp")
                || ruta.equals("/reportes.jsp") || ruta.equals("/MedicamentoServlet")
                || ruta.equals("/FacturaCompraServlet")
                || (ruta.equals("/MovimientoServlet") && "compra".equals(request.getParameter("tipo")));
        if ((esVenta && !puedeVender) || (requiereGestion && !gestionCompleta)) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN, "Tu perfil no tiene permiso para este módulo.");
            return;
        }
        cadena.doFilter(solicitud, respuesta);
    }

    /** Compara perfiles independientemente de tildes, mayúsculas y espacios. */
    private String normalizar(String valor) {
        return Normalizer.normalize(valor == null ? "" : valor.trim(), Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT);
    }
}
