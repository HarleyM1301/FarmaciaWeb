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
@WebFilter(urlPatterns = {"/panel.jsp", "/medicamentos.jsp", "/ventas.jsp", "/compras.jsp", "/compra-detalle.jsp", "/reportes.jsp", "/trazabilidad.jsp", "/usuarios.jsp", "/UsuarioServlet", "/VentaServlet", "/MedicamentoServlet", "/MovimientoServlet", "/CompraServlet", "/FacturaCompraServlet", "/CatalogoInvimaServlet", "/CatalogoMedicamentoServlet", "/catalogo-invima.jsp"})
public class AutenticacionFilter implements Filter {
    @Override
    public void doFilter(jakarta.servlet.ServletRequest solicitud,
                         jakarta.servlet.ServletResponse respuesta,
                         FilterChain cadena) throws IOException, ServletException {
        // Convierte la solicitud genérica de Servlet en una petición HTTP para leer sesión y ruta.
        HttpServletRequest request = (HttpServletRequest) solicitud;
        // Convierte la respuesta genérica para enviar redirecciones y códigos de estado HTTP.
        HttpServletResponse response = (HttpServletResponse) respuesta;
        // Solo permite continuar si hay una sesión autenticada.
        HttpSession sesion = request.getSession(false);
        // Redirige a quien no tenga una sesión con usuario autenticado.
        if (sesion == null || sesion.getAttribute("usuario") == null) {
            response.sendRedirect(request.getContextPath() + "/login.jsp");
            return;
        }

        // Normaliza el rol y define qué módulos puede usar cada tipo de cuenta.
        // Lee la ruta solicitada para aplicar los permisos propios de cada módulo.
        String ruta = request.getServletPath();
        // Convierte el rol a minúsculas y quita tildes para comparar etiquetas equivalentes.
        String rol = normalizar(String.valueOf(sesion.getAttribute("rol")));
        // Marca los perfiles que pueden acceder a compras, reportes y administración.
        boolean gestionCompleta = rol.equals("admin") || rol.equals("administrador") || rol.equals("farmaceutico");
        // Separa el permiso de administrar cuentas del acceso general de Farmacéutico.
        boolean esAdministrador = rol.equals("admin") || rol.equals("administrador");
        // Permite que perfiles de caja consulten el inventario para realizar ventas.
        boolean puedeVerInventario = gestionCompleta || rol.equals("usuario") || rol.equals("cajero") || rol.equals("vendedor");
        // Identifica el formulario de ventas y el envío de una operación de tipo venta.
        boolean esVenta = ruta.equals("/ventas.jsp")
                || ruta.equals("/VentaServlet")
                || (ruta.equals("/MovimientoServlet") && "venta".equals(request.getParameter("tipo")));
        // Determina qué perfiles pueden registrar una venta.
        boolean puedeVender = gestionCompleta || rol.equals("usuario") || rol.equals("cajero") || rol.equals("vendedor");
        // Señala rutas de compra y administración que requieren permisos elevados.
        boolean requiereGestion = ruta.equals("/compras.jsp")
                || ruta.equals("/reportes.jsp") || ruta.equals("/trazabilidad.jsp") || ruta.equals("/MedicamentoServlet")
                || ruta.equals("/FacturaCompraServlet")
                || ruta.equals("/CompraServlet") || ruta.equals("/compra-detalle.jsp")
                || (ruta.equals("/MovimientoServlet") && "compra".equals(request.getParameter("tipo")));
        // Identifica la página de catálogo que requiere permiso para ver inventario.
        boolean requiereInventario = ruta.equals("/medicamentos.jsp");
        // Protege tanto la pantalla como las acciones POST de edición y borrado de usuarios.
        boolean requiereAdministracionUsuarios = ruta.equals("/usuarios.jsp") || ruta.equals("/UsuarioServlet");
        boolean requiereCatalogoAdmin = ruta.equals("/CatalogoInvimaServlet") || ruta.equals("/catalogo-invima.jsp");
        boolean requiereCatalogoBusqueda = ruta.equals("/CatalogoMedicamentoServlet");
        // Deniega con HTTP 403 cuando el rol no tiene autorización para la ruta.
        if ((esVenta && !puedeVender) || (requiereGestion && !gestionCompleta)
                || (requiereAdministracionUsuarios && !esAdministrador)
                || (requiereCatalogoAdmin && !esAdministrador)
                || (requiereCatalogoBusqueda && !gestionCompleta)
                || (requiereInventario && !puedeVerInventario)) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN, "Tu perfil no tiene permiso para este módulo.");
            return;
        }
        // Continúa la cadena de filtros y entrega la solicitud al servlet o JSP autorizado.
        cadena.doFilter(solicitud, respuesta);
    }

    /** Compara perfiles independientemente de tildes, mayúsculas y espacios. */
    private String normalizar(String valor) {
        // Separa marcas diacríticas para quitar tildes de perfiles escritos en español.
        return Normalizer.normalize(valor == null ? "" : valor.trim(), Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT);
    }
}
