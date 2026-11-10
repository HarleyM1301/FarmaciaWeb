package com.farmacia.modelo;

import java.sql.Date;

/** Una línea recibida en una factura; cada fila representa un producto y su lote. */
public class LineaCompra {
    // Identifica el medicamento existente; queda en cero si se crea uno nuevo.
    private int productoId;
    // Guarda el nombre que se registrará cuando la factura traiga un producto nuevo.
    private String nombreNuevo;
    // Grupo farmacológico del producto nuevo creado desde la recepción.
    private String categoria;
    // Define el precio al que se venderá cada unidad mínima del inventario.
    private double precioVenta;
    // Indica si la recepción debe reemplazar el precio público del producto existente.
    private boolean actualizarPrecioVenta;
    // Guarda el código del lote identificado durante la recepción técnica.
    private String lote;
    // Guarda la fecha de vencimiento identificada para este lote.
    private Date vencimiento;
    // Cantidad recibida según la unidad de presentación escrita en la factura.
    private int cantidad;
    // Costo facturado por cada caja o paquete recibido.
    private double costo;
    // Unidad mínima que se controlará y descontará del inventario.
    private String unidadBase = "unidad";
    // Factor usado para convertir cajas o paquetes en unidades mínimas.
    private int unidadesPorPresentacion = 1;
    // Unidades base contenidas en un blíster para productos nuevos.
    private int unidadesPorBlister;
    private Long catalogoMedicamentoId;
    private String registroInvima;
    private String codigoBarras;

    // Devuelve el ID del producto existente o cero si se va a crear uno.
    public int getProductoId() { return productoId; }
    // Guarda el ID del producto elegido en el catálogo.
    public void setProductoId(int productoId) { this.productoId = productoId; }
    // Devuelve el nombre capturado para un producto que aún no existe.
    public String getNombreNuevo() { return nombreNuevo; }
    // Guarda el nombre que se usará al crear el producto nuevo.
    public void setNombreNuevo(String nombreNuevo) { this.nombreNuevo = nombreNuevo; }
    // Devuelve el grupo farmacológico del producto nuevo.
    public String getCategoria() { return categoria; }
    // Guarda la categoría capturada en la factura.
    public void setCategoria(String categoria) { this.categoria = categoria; }
    // Devuelve el precio de venta de la unidad mínima.
    public double getPrecioVenta() { return precioVenta; }
    // Guarda el precio de venta para la ficha nueva.
    public void setPrecioVenta(double precioVenta) { this.precioVenta = precioVenta; }
    // Informa si el precio público se actualiza al confirmar esta compra.
    public boolean isActualizarPrecioVenta() { return actualizarPrecioVenta; }
    // Define si esta línea solicita una actualización explícita del precio público.
    public void setActualizarPrecioVenta(boolean actualizarPrecioVenta) { this.actualizarPrecioVenta = actualizarPrecioVenta; }
    // Devuelve el código de lote indicado en la factura.
    public String getLote() { return lote; }
    // Guarda el código de lote de esta línea de recepción.
    public void setLote(String lote) { this.lote = lote; }
    // Devuelve la fecha de vencimiento del lote.
    public Date getVencimiento() { return vencimiento; }
    // Guarda el vencimiento que se asignará al producto-lote.
    public void setVencimiento(Date vencimiento) { this.vencimiento = vencimiento; }
    // Devuelve la cantidad convertida a unidades base.
    public int getCantidad() { return cantidad; }
    // Guarda la cantidad que se sumará a las existencias.
    public void setCantidad(int cantidad) { this.cantidad = cantidad; }
    // Devuelve el costo de una unidad mínima, no el costo del paquete completo.
    public double getCosto() { return costo; }
    // Guarda el costo unitario convertido desde la presentación facturada.
    public void setCosto(double costo) { this.costo = costo; }
    // Devuelve la unidad mínima controlada por el inventario.
    public String getUnidadBase() { return unidadBase; }
    // Guarda la unidad base usada para sumar y descontar stock.
    public void setUnidadBase(String unidadBase) { this.unidadBase = unidadBase; }
    // Devuelve cuántas unidades base trae cada caja o paquete.
    public int getUnidadesPorPresentacion() { return unidadesPorPresentacion; }
    // Guarda el factor que permite convertir presentaciones a unidades base.
    public void setUnidadesPorPresentacion(int unidadesPorPresentacion) { this.unidadesPorPresentacion = unidadesPorPresentacion; }
    // Devuelve las unidades que contiene un blíster.
    public int getUnidadesPorBlister() { return unidadesPorBlister; }
    // Guarda las unidades por blíster al crear un producto desde una compra.
    public void setUnidadesPorBlister(int unidadesPorBlister) { this.unidadesPorBlister = unidadesPorBlister; }
    // Devuelve el CUM oficial asociado al producto nuevo, si se confirmó una coincidencia.
    public Long getCatalogoMedicamentoId() { return catalogoMedicamentoId; }
    // Guarda el identificador CUM que enlaza la ficha de inventario al catálogo maestro.
    public void setCatalogoMedicamentoId(Long catalogoMedicamentoId) { this.catalogoMedicamentoId = catalogoMedicamentoId; }
    // Devuelve el número del registro sanitario INVIMA elegido en la recepción.
    public String getRegistroInvima() { return registroInvima; }
    // Guarda el registro sanitario que se asignará al producto creado desde la factura.
    public void setRegistroInvima(String registroInvima) { this.registroInvima = registroInvima; }
    public String getCodigoBarras() { return codigoBarras; }
    public void setCodigoBarras(String codigoBarras) { this.codigoBarras = codigoBarras; }
}
