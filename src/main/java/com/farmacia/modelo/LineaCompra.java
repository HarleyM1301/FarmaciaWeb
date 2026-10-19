package com.farmacia.modelo;

import java.sql.Date;

/** Una línea recibida en una factura; cada fila representa un producto y su lote. */
public class LineaCompra {
    private int productoId;
    private String nombreNuevo;
    private double precioVenta;
    private String lote;
    private Date vencimiento;
    private int cantidad;
    private double costo;

    public int getProductoId() { return productoId; }
    public void setProductoId(int productoId) { this.productoId = productoId; }
    public String getNombreNuevo() { return nombreNuevo; }
    public void setNombreNuevo(String nombreNuevo) { this.nombreNuevo = nombreNuevo; }
    public double getPrecioVenta() { return precioVenta; }
    public void setPrecioVenta(double precioVenta) { this.precioVenta = precioVenta; }
    public String getLote() { return lote; }
    public void setLote(String lote) { this.lote = lote; }
    public Date getVencimiento() { return vencimiento; }
    public void setVencimiento(Date vencimiento) { this.vencimiento = vencimiento; }
    public int getCantidad() { return cantidad; }
    public void setCantidad(int cantidad) { this.cantidad = cantidad; }
    public double getCosto() { return costo; }
    public void setCosto(double costo) { this.costo = costo; }
}
