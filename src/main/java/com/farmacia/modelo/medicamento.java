package com.farmacia.modelo;

/** Representa una presentación y lote de medicamento almacenado en inventario. */
public class medicamento {
    // Identificador de la fila en la tabla medicamentos.
    private int idMedicamento;
    // Nombre comercial que se muestra en inventario.
    private String nombre;
    // Número de registro sanitario INVIMA del medicamento; no corresponde al lote.
    private String registroInvima;
    // Presentación oficial CUM asociada a esta ficha del inventario, si fue seleccionada.
    private Long catalogoMedicamentoId;
    // Grupo farmacológico guardado en la columna categoria del catálogo.
    private String categoria;
    // Código del lote que permite distinguir existencias y vencimientos.
    private String lote;
    // Fecha de vencimiento en formato ISO AAAA-MM-DD.
    private String fechaVencimiento;
    // Unidades disponibles para venta.
    private int stock;
    // Precio de venta por unidad.
    private double precio;
    // Unidad mínima que controla el inventario (por ejemplo, tableta o sobre).
    private String unidadBase = "unidad";
    // Cantidad de unidades mínimas que contiene una presentación facturada.
    private int unidadesPorPresentacion = 1;
    // Unidades base contenidas en un blíster; cero indica que no está configurado.
    private int unidadesPorBlister;

    // Devuelve la clave primaria del producto guardado.
    public int getIdMedicamento() { return idMedicamento; }
    // Guarda la clave primaria asignada al producto.
    public void setIdMedicamento(int idMedicamento) { this.idMedicamento = idMedicamento; }
    // Devuelve el nombre comercial del medicamento.
    public String getNombre() { return nombre; }
    // Guarda el nombre que se mostrará en el catálogo.
    public void setNombre(String nombre) { this.nombre = nombre; }
    // Devuelve el número de registro sanitario asociado al producto.
    public String getRegistroInvima() { return registroInvima; }
    // Guarda el número INVIMA, independiente del código de lote recibido.
    public void setRegistroInvima(String registroInvima) { this.registroInvima = registroInvima; }
    // Devuelve el identificador de la presentación oficial asociada.
    public Long getCatalogoMedicamentoId() { return catalogoMedicamentoId; }
    // Asocia o desvincula esta ficha con una presentación del catálogo INVIMA.
    public void setCatalogoMedicamentoId(Long catalogoMedicamentoId) { this.catalogoMedicamentoId = catalogoMedicamentoId; }
    // Devuelve el grupo farmacológico asignado al medicamento.
    public String getCategoria() { return categoria; }
    // Guarda el grupo farmacológico para organizar el inventario.
    public void setCategoria(String categoria) { this.categoria = categoria; }

    // Devuelve el lote que distingue estas existencias de otros lotes.
    public String getLote() { return lote; }
    // Guarda el lote asociado al medicamento.
    public void setLote(String lote) { this.lote = lote; }

    // Devuelve la fecha de vencimiento en formato ISO.
    public String getFechaVencimiento() { return fechaVencimiento; }
    // Guarda la fecha de vencimiento del lote.
    public void setFechaVencimiento(String fechaVencimiento) { this.fechaVencimiento = fechaVencimiento; }

    // Devuelve la cantidad disponible en unidades base.
    public int getStock() { return stock; }
    // Actualiza la cantidad disponible del producto.
    public void setStock(int stock) { this.stock = stock; }

    // Devuelve el precio al que se vende una unidad base.
    public double getPrecio() { return precio; }
    // Guarda el precio de venta del producto.
    public void setPrecio(double precio) { this.precio = precio; }
    // Devuelve la unidad mínima de control del inventario.
    public String getUnidadBase() { return unidadBase; }
    // Guarda la unidad que se suma o descuenta de las existencias.
    public void setUnidadBase(String unidadBase) { this.unidadBase = unidadBase; }
    // Devuelve el factor de conversión de caja o paquete a unidad base.
    public int getUnidadesPorPresentacion() { return unidadesPorPresentacion; }
    // Guarda el número de unidades base contenidas en cada presentación.
    public void setUnidadesPorPresentacion(int unidadesPorPresentacion) { this.unidadesPorPresentacion = unidadesPorPresentacion; }
    // Devuelve el factor de conversión para vender un blíster.
    public int getUnidadesPorBlister() { return unidadesPorBlister; }
    // Define el factor del blíster o cero cuando el producto no usa esa presentación.
    public void setUnidadesPorBlister(int unidadesPorBlister) { this.unidadesPorBlister = unidadesPorBlister; }

    /** Describe las existencias como cajas completas, blísteres y unidades sueltas. */
    public String getStockDesglosado() {
        int disponibles = Math.max(0, stock);
        int factorCaja = Math.max(1, unidadesPorPresentacion);
        int factorBlister = unidadesPorBlister > 0 && unidadesPorBlister <= factorCaja
                && factorCaja % unidadesPorBlister == 0 ? unidadesPorBlister : 0;
        if (factorCaja == 1) return disponibles + " " + pluralUnidad(unidadBase, disponibles);

        int cajas = disponibles / factorCaja;
        int restante = disponibles % factorCaja;
        java.util.List<String> partes = new java.util.ArrayList<>();
        if (cajas > 0) partes.add(cajas + " " + (cajas == 1 ? "caja" : "cajas"));
        if (factorBlister > 0) {
            int blisters = restante / factorBlister;
            restante %= factorBlister;
            if (blisters > 0) partes.add(blisters + " " + (blisters == 1 ? "blíster" : "blísteres"));
        }
        if (restante > 0 || partes.isEmpty()) partes.add(restante + " " + pluralUnidad(unidadBase, restante));
        return String.join(" + ", partes);
    }

    /** Devuelve el nombre de la unidad base en singular o plural para presentar el stock. */
    private String pluralUnidad(String unidad, int cantidad) {
        String base = unidad == null || unidad.isBlank() ? "unidad" : unidad;
        if (cantidad == 1) return base.equals("capsula") ? "cápsula" : base;
        return switch (base) {
            case "unidad" -> "unidades";
            case "caja" -> "cajas";
            case "paquete" -> "paquetes";
            case "tableta" -> "tabletas";
            case "capsula" -> "cápsulas";
            case "sobre" -> "sobres";
            case "ml" -> "ml";
            case "gota" -> "gotas";
            case "ampolla" -> "ampollas";
            case "frasco" -> "frascos";
            default -> base + "s";
        };
    }
}
