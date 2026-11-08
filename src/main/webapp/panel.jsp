<%@ page contentType="text/html;charset=UTF-8" pageEncoding="UTF-8" language="java" %>
<%@ page import="com.farmacia.dao.MedicamentoDAO,com.farmacia.modelo.medicamento,com.farmacia.dao.MovimientoDAO,com.farmacia.util.HtmlUtil" %>
<%@ page import="java.util.List,java.util.Map,java.util.ArrayList,java.util.Comparator,java.util.Locale,java.util.Date,java.util.TreeMap" %>
<%@ page import="java.math.BigDecimal,java.time.LocalDate,java.time.ZoneId,java.time.temporal.ChronoUnit,java.time.format.DateTimeFormatter" %>
<%-- Dashboard: resume datos reales del catálogo, ventas, compras y alertas. --%>
<%
request.setAttribute("activePage", "panel");
List<medicamento> medicamentosPanel = new MedicamentoDAO().listar();
List<Map<String,Object>> ventasPanel = new ArrayList<>();
List<Map<String,Object>> comprasPanel = new ArrayList<>();
boolean errorMovimientosPanel = false;
try {
    MovimientoDAO movimientos = new MovimientoDAO();
    ventasPanel = movimientos.listar(true);
    comprasPanel = movimientos.listar(false);
} catch (java.sql.SQLException e) {
    application.log("No se pudieron cargar los movimientos del dashboard", e);
    errorMovimientosPanel = true;
}

LocalDate hoyPanel = LocalDate.now();
LocalDate inicioMesPanel = hoyPanel.withDayOfMonth(1);
BigDecimal ventasMesPanel = BigDecimal.ZERO;
BigDecimal comprasMesPanel = BigDecimal.ZERO;
BigDecimal[] ventasSeisMesesPanel = new BigDecimal[6];
for (int i = 0; i < ventasSeisMesesPanel.length; i++) ventasSeisMesesPanel[i] = BigDecimal.ZERO;
for (Map<String,Object> venta : ventasPanel) {
    Object fechaObj = venta.get("fecha");
    if (fechaObj instanceof Date) {
        LocalDate fecha = ((Date) fechaObj).toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
        BigDecimal total = venta.get("total") instanceof BigDecimal ? (BigDecimal) venta.get("total") : BigDecimal.ZERO;
        if (fecha.getYear() == hoyPanel.getYear() && fecha.getMonth() == hoyPanel.getMonth()) ventasMesPanel = ventasMesPanel.add(total);
        long mesesAtras = ChronoUnit.MONTHS.between(fecha.withDayOfMonth(1), inicioMesPanel);
        if (mesesAtras >= 0 && mesesAtras < ventasSeisMesesPanel.length) {
            int indice = ventasSeisMesesPanel.length - 1 - (int) mesesAtras;
            ventasSeisMesesPanel[indice] = ventasSeisMesesPanel[indice].add(total);
        }
    }
}
for (Map<String,Object> compra : comprasPanel) {
    Object fechaObj = compra.get("fecha");
    if (fechaObj instanceof Date) {
        LocalDate fecha = ((Date) fechaObj).toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
        if (fecha.getYear() == hoyPanel.getYear() && fecha.getMonth() == hoyPanel.getMonth() && compra.get("total") instanceof BigDecimal)
            comprasMesPanel = comprasMesPanel.add((BigDecimal) compra.get("total"));
    }
}
int stockTotalPanel = 0, alertasVencimientoPanel = 0;
Map<String, int[]> existenciasPorGrupoPanel = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
for (medicamento med : medicamentosPanel) {
    stockTotalPanel += med.getStock();
    String grupo = med.getCategoria() == null || med.getCategoria().isBlank() ? "Sin clasificar" : med.getCategoria().trim();
    int[] resumenGrupo = existenciasPorGrupoPanel.computeIfAbsent(grupo, ignorado -> new int[2]);
    resumenGrupo[0] += med.getStock();
    resumenGrupo[1]++;
    try {
        if (med.getFechaVencimiento() != null && !med.getFechaVencimiento().isBlank()
                && !LocalDate.parse(med.getFechaVencimiento()).isAfter(hoyPanel.plusDays(90))) alertasVencimientoPanel++;
    } catch (java.time.format.DateTimeParseException ignorado) { /* Una fecha inválida no debe detener el resumen. */ }
}
List<medicamento> stockBajoPanel = new ArrayList<>();
for (medicamento med : medicamentosPanel) if (med.getStock() <= 10) stockBajoPanel.add(med);
stockBajoPanel.sort(Comparator.comparingInt(medicamento::getStock));
BigDecimal maxVentasGraficaPanel = BigDecimal.ONE;
for (BigDecimal valor : ventasSeisMesesPanel) if (valor.compareTo(maxVentasGraficaPanel) > 0) maxVentasGraficaPanel = valor;
DateTimeFormatter mesFormatoPanel = DateTimeFormatter.ofPattern("MMM", Locale.forLanguageTag("es-CO"));
DateTimeFormatter fechaFormatoPanel = DateTimeFormatter.ofPattern("dd/MM/yyyy");
%>
<!DOCTYPE html>
<html lang="es">
<head>
    <meta charset="UTF-8"><meta name="viewport" content="width=device-width, initial-scale=1">
    <title>Dashboard | Farmacia</title>
    <link rel="stylesheet" href="${pageContext.request.contextPath}/assets/css/app.css">
</head>
<body><div class="shell">
    <%@ include file="/WEB-INF/partials/topbar.jspf" %>
    <%@ include file="/WEB-INF/partials/sidebar.jspf" %>
    <main class="main dashboard-main">
        <div class="page-heading dashboard-heading">
            <div><h1>Dashboard</h1><p>Bienvenido, <%= HtmlUtil.escapar(session.getAttribute("nombre")) %>. Este es el resumen de tu farmacia.</p></div>
            <% if (gestionCompletaMenu) { %><a class="btn" href="${pageContext.request.contextPath}/medicamentos.jsp">＋ Agregar medicamento</a><% } %>
        </div>
        <% if (errorMovimientosPanel) { %><div class="notice error">No se pudieron cargar ventas y compras. Revisa la conexión con MySQL.</div><% } %>

        <%-- Indicadores principales: las ventas y compras corresponden al mes calendario actual. --%>
        <section class="grid dashboard-kpis" aria-label="Indicadores del mes">
            <article class="card stat dashboard-stat"><span class="stat-icon">💊</span><div><div class="stat-label">Productos registrados</div><div class="stat-value"><%= medicamentosPanel.size() %></div><small>En el catálogo</small></div></article>
            <article class="card stat dashboard-stat"><span class="stat-icon green">▤</span><div><div class="stat-label">Existencias totales</div><div class="stat-value"><%= stockTotalPanel %></div><small>Unidades base · detalle por grupo abajo</small></div></article>
            <article class="card stat dashboard-stat"><span class="stat-icon amber">🛒</span><div><div class="stat-label">Ventas del mes</div><div class="stat-value money-value">$ <%= String.format(Locale.forLanguageTag("es-CO"), "%,.0f", ventasMesPanel.doubleValue()) %></div><small><%= ventasPanel.size() %> ventas en el historial</small></div></article>
            <article class="card stat dashboard-stat"><span class="stat-icon purple">▣</span><div><div class="stat-label">Compras del mes</div><div class="stat-value money-value">$ <%= String.format(Locale.forLanguageTag("es-CO"), "%,.0f", comprasMesPanel.doubleValue()) %></div><small><%= comprasPanel.size() %> compras en el historial</small></div></article>
        </section>

        <%-- Desglosa las unidades y los productos por grupo farmacológico. --%>
        <section class="card dashboard-groups">
            <div class="dashboard-card-heading"><div><h2>Existencias por grupo farmacológico</h2><p class="muted">Unidades base registradas por categoría terapéutica</p></div><a class="back-link" href="${pageContext.request.contextPath}/medicamentos.jsp">Clasificar medicamentos →</a></div>
            <div class="table-wrap"><table class="dashboard-table"><thead><tr><th>Grupo farmacológico</th><th>Productos o lotes</th><th>Existencias</th></tr></thead><tbody>
            <% for (Map.Entry<String, int[]> grupo : existenciasPorGrupoPanel.entrySet()) { %>
                <tr><td><%= HtmlUtil.escapar(grupo.getKey()) %></td><td><%= grupo.getValue()[1] %></td><td><strong><%= grupo.getValue()[0] %></strong> unidades base</td></tr>
            <% } if (existenciasPorGrupoPanel.isEmpty()) { %><tr><td colspan="3" class="empty">Aún no hay medicamentos para agrupar.</td></tr><% } %>
            </tbody></table></div>
        </section>

        <%-- Gráfico de seis meses, accesos rápidos y alertas basados en las existencias actuales. --%>
        <section class="grid dashboard-middle">
            <article class="card dashboard-chart-card">
                <div class="dashboard-card-heading"><div><h2>Ventas de los últimos 6 meses</h2><p class="muted">Valores registrados por mes</p></div><span class="dashboard-period">Últimos 6 meses</span></div>
                <div class="dashboard-bars" role="img" aria-label="Gráfico de ventas mensuales">
                    <% for (int i = 0; i < ventasSeisMesesPanel.length; i++) { LocalDate mes = inicioMesPanel.minusMonths(5 - i); BigDecimal valor = ventasSeisMesesPanel[i]; int alto = Math.max(4, valor.multiply(BigDecimal.valueOf(100)).divide(maxVentasGraficaPanel, 0, java.math.RoundingMode.HALF_UP).intValue()); %>
                    <div class="dashboard-bar-column"><span class="dashboard-bar-value">$<%= String.format(Locale.forLanguageTag("es-CO"), "%,.0f", valor.doubleValue()) %></span><div class="dashboard-bar-track"><span class="dashboard-bar" style="height:<%= alto %>%"></span></div><span class="dashboard-bar-label"><%= mes.format(mesFormatoPanel) %></span></div>
                    <% } %>
                </div>
            </article>
            <article class="card dashboard-actions-card">
                <h2>Acciones rápidas</h2>
                <div class="dashboard-actions">
                    <% if (gestionCompletaMenu) { %><a href="${pageContext.request.contextPath}/medicamentos.jsp"><span>＋</span>Nuevo medicamento <b>›</b></a><a href="${pageContext.request.contextPath}/compras.jsp"><span>▣</span>Registrar compra <b>›</b></a><a href="${pageContext.request.contextPath}/reportes.jsp"><span>▥</span>Ver reportes <b>›</b></a><% } %>
                    <% if (puedeVenderMenu) { %><a href="${pageContext.request.contextPath}/ventas.jsp"><span>🛒</span>Nueva venta <b>›</b></a><% } %>
                </div>
            </article>
        </section>

        <%-- Paneles inferiores: movimientos recientes y productos que requieren revisión. --%>
        <section class="grid dashboard-bottom">
            <article class="card dashboard-table-card">
                <div class="dashboard-card-heading"><h2>Ventas recientes</h2><a class="back-link" href="${pageContext.request.contextPath}/ventas.jsp">Ver todas →</a></div>
                <div class="table-wrap"><table class="dashboard-table"><thead><tr><th>Venta</th><th>Cliente</th><th>Total</th><th>Fecha</th></tr></thead><tbody>
                <% int filasVentasPanel = 0; for (Map<String,Object> venta : ventasPanel) { if (filasVentasPanel++ >= 5) break; Object fechaObj = venta.get("fecha"); String fechaTexto = fechaObj instanceof Date ? fechaFormatoPanel.format(((Date)fechaObj).toInstant().atZone(ZoneId.systemDefault()).toLocalDate()) : "—"; BigDecimal total = venta.get("total") instanceof BigDecimal ? (BigDecimal)venta.get("total") : BigDecimal.ZERO; %>
                    <tr><td>VTA-<%= venta.get("id") %></td><td><%= HtmlUtil.escapar(venta.get("tercero")) %></td><td>$ <%= String.format(Locale.forLanguageTag("es-CO"), "%,.0f", total.doubleValue()) %></td><td><%= fechaTexto %></td></tr>
                <% } if (filasVentasPanel == 0) { %><tr><td colspan="4" class="empty">Aún no hay ventas registradas.</td></tr><% } %>
                </tbody></table></div>
            </article>
            <article class="card dashboard-table-card">
                <div class="dashboard-card-heading"><h2>Productos con stock bajo</h2><a class="back-link" href="${pageContext.request.contextPath}/medicamentos.jsp">Ver inventario →</a></div>
                <div class="table-wrap"><table class="dashboard-table"><thead><tr><th>Medicamento</th><th>Lote</th><th>Stock</th><th>Estado</th></tr></thead><tbody>
                <% int filasStockPanel = 0; for (medicamento med : stockBajoPanel) { if (filasStockPanel++ >= 5) break; %>
                    <tr><td><%= HtmlUtil.escapar(med.getNombre()) %></td><td><%= HtmlUtil.escapar(med.getLote()) %></td><td><%= HtmlUtil.escapar(med.getStockDesglosado()) %></td><td><span class="badge <%= med.getStock() == 0 ? "danger" : "warn" %>"><%= med.getStock() == 0 ? "Agotado" : "Bajo" %></span></td></tr>
                <% } if (filasStockPanel == 0) { %><tr><td colspan="4" class="empty">No hay productos con stock bajo.</td></tr><% } %>
                </tbody></table></div>
                <p class="dashboard-note">Se consideran bajos los productos con 10 unidades base o menos.</p>
            </article>
        </section>

        <% if (gestionCompletaMenu) { %><section class="card dashboard-alerts"><div class="dashboard-card-heading"><h2>Alertas de vencimiento</h2><a class="back-link" href="${pageContext.request.contextPath}/trazabilidad.jsp">Ver movimientos →</a></div>
            <% boolean hayAlertasPanel = false; for (medicamento med : medicamentosPanel) { try { if (med.getFechaVencimiento() == null || med.getFechaVencimiento().isBlank()) continue; LocalDate vencimiento = LocalDate.parse(med.getFechaVencimiento()); long dias = ChronoUnit.DAYS.between(hoyPanel, vencimiento); if (dias <= 90) { hayAlertasPanel = true; %>
                <div class="progress-row"><strong><%= HtmlUtil.escapar(med.getNombre()) %></strong><span class="muted">Lote <%= HtmlUtil.escapar(med.getLote()) %></span><span class="badge <%= dias < 0 ? "danger" : "warn" %>"><%= dias < 0 ? "Vencido hace " + Math.abs(dias) + " días" : "Vence en " + dias + " días" %></span></div>
            <% } } catch (java.time.format.DateTimeParseException ignorado) { } } if (!hayAlertasPanel) { %><p class="empty">No hay lotes vencidos ni próximos a vencer en 90 días.</p><% } %>
        </section><% } %>
    </main>
</div></body></html>
