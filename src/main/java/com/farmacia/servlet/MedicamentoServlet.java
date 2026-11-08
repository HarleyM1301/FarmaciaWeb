package com.farmacia.servlet;

import com.farmacia.dao.MedicamentoDAO;
import com.farmacia.modelo.medicamento;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;

@WebServlet("/MedicamentoServlet")
/** Procesa altas, modificaciones y eliminaciones del inventario. */
public class MedicamentoServlet extends HttpServlet {

    /** Valida el formulario y guarda el cambio solicitado por POST. */
    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        try {
            // Identifica si el formulario solicita guardar o eliminar.
            String accion = request.getParameter("accion");
            // Crea el acceso a operaciones del catálogo y stock.
            MedicamentoDAO dao = new MedicamentoDAO();
            // Guarda la dirección a la que se redirigen los resultados del formulario.
            String destino = request.getContextPath() + "/medicamentos.jsp";

            if ("eliminar".equals(accion)) {
                // Convierte el ID enviado por el botón de eliminar a entero.
                int id = Integer.parseInt(request.getParameter("id"));
                // Distingue un borrado correcto, un producto protegido por historial y un fallo técnico.
                MedicamentoDAO.ResultadoEliminacion resultado = dao.eliminar(id);
                String estado = switch (resultado) {
                    case ELIMINADO -> "?deleted=1";
                    case CON_HISTORIAL -> "?error=historial";
                    case ERROR -> "?error=1";
                };
                response.sendRedirect(destino + estado);
                // Evita que el flujo de eliminación continúe como si fuera un guardado.
                return;
            }

            // Comprueba y normaliza la entrada antes de guardarla.
            // Lee los campos de identidad y vencimiento enviados por el formulario.
            String nombre = request.getParameter("nombre");
            // Lee el grupo terapéutico que se usará para resumir las existencias.
            String categoria = request.getParameter("categoria");
            // Lee por separado el registro sanitario INVIMA y el código del lote.
            String registroInvima = request.getParameter("registroInvima");
            String lote = request.getParameter("lote");
            String fecha = request.getParameter("fecha");
            // Convierte los valores numéricos del formulario a sus tipos de dominio.
            int stock = Integer.parseInt(request.getParameter("stock"));
            double precio = Double.parseDouble(request.getParameter("precio"));
            String unidadBase = request.getParameter("unidadBase");
            int unidadesPorPresentacion = Integer.parseInt(request.getParameter("unidadesPorPresentacion"));
            String blisterIngresado = request.getParameter("unidadesPorBlister");
            int unidadesPorBlister = blisterIngresado == null || blisterIngresado.isBlank() ? 0 : Integer.parseInt(blisterIngresado);
            // Valida campos obligatorios, cantidades no negativas y precios finitos.
            if (nombre == null || nombre.isBlank()
                    || lote != null && lote.trim().length() > 30
                    || categoria == null || categoria.isBlank() || categoria.trim().length() > 50
                    || registroInvima != null && registroInvima.trim().length() > 60
                    || stock < 0
                    || unidadesPorPresentacion < 1 || unidadesPorBlister < 0 || unidadesPorBlister > unidadesPorPresentacion
                    || unidadesPorBlister > 0 && unidadesPorPresentacion % unidadesPorBlister != 0
                    || !Double.isFinite(precio) || precio < 0) {
                // Regresa al catálogo con un indicador para mostrar el error de validación.
                response.sendRedirect(destino + "?error=1");
                // Detiene el guardado cuando los campos no son válidos.
                return;
            }

            // Crea el objeto de medicamento que se enviará al DAO.
            medicamento m = new medicamento();
            // Copia al modelo los datos validados del formulario.
            m.setNombre(nombre);
            m.setCategoria(categoria.trim());
            m.setRegistroInvima(registroInvima == null ? "" : registroInvima.trim());
            m.setLote(lote == null || lote.isBlank() ? null : lote.trim());
            m.setFechaVencimiento(fecha == null || fecha.isBlank() ? null : fecha);
            m.setStock(stock);
            m.setPrecio(precio);
            m.setUnidadBase(unidadBase);
            m.setUnidadesPorPresentacion(unidadesPorPresentacion);
            m.setUnidadesPorBlister(unidadesPorBlister);

            // Lee el ID oculto para distinguir una edición de un alta nueva.
            String id = request.getParameter("id");
            // Inserta si no hay ID; en caso contrario, actualiza la ficha existente.
            boolean guardado = id == null || id.isBlank()
                    ? dao.insertar(m)
                    : actualizarConId(dao, m, id);

            // Muestra el resultado del guardado al volver al catálogo.
            response.sendRedirect(destino + (guardado ? "?success=1" : "?error=1"));
        } catch (Exception e) {
            // Deja el detalle técnico en los registros de Tomcat.
            getServletContext().log("No se pudo guardar el medicamento", e);
            // Vuelve al catálogo con un mensaje de error genérico.
            response.sendRedirect(request.getContextPath() + "/medicamentos.jsp?error=1");
        }
    }

    // Completa la edición con el ID oculto que envía el formulario modal.
    private boolean actualizarConId(MedicamentoDAO dao, medicamento m, String id) {
        // Convierte y asigna al modelo el ID de la ficha que se va a editar.
        m.setIdMedicamento(Integer.parseInt(id));
        // Solicita al DAO persistir las modificaciones de esa ficha.
        return dao.actualizar(m);
    }
}
