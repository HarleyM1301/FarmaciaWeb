package com.farmacia.modelo;

/** Representa una presentación y lote de medicamento almacenado en inventario. */
public class medicamento {
    // Identificador de la fila en la tabla medicamentos.
    private int idMedicamento;
    // Nombre comercial que se muestra en inventario.
    private String nombre;
    // Código del lote que permite distinguir existencias y vencimientos.
    private String lote;
    // Fecha de vencimiento en formato ISO AAAA-MM-DD.
    private String fechaVencimiento;
    // Unidades disponibles para venta.
    private int stock;
    // Precio de venta por unidad.
    private double precio;

    // Accesores para leer y modificar los datos del medicamento.
    public int getIdMedicamento() { return idMedicamento; }
    public void setIdMedicamento(int idMedicamento) { this.idMedicamento = idMedicamento; }
// Devuelve el nombre del medicamento.
public String getNombre() {
    return nombre;
}
    public void setNombre(String nombre) { this.nombre = nombre; }

    public String getLote() { return lote; }
    public void setLote(String lote) { this.lote = lote; }

    public String getFechaVencimiento() { return fechaVencimiento; }
    public void setFechaVencimiento(String fechaVencimiento) { this.fechaVencimiento = fechaVencimiento; }

    public int getStock() { return stock; }
    public void setStock(int stock) { this.stock = stock; }

    public double getPrecio() { return precio; }
    public void setPrecio(double precio) { this.precio = precio; }
}
