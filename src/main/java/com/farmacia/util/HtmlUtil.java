package com.farmacia.util;

/** Funciones pequeñas para imprimir datos del usuario sin interpretar HTML. */
public final class HtmlUtil {
    private HtmlUtil() { }

    /** Convierte caracteres especiales en entidades HTML seguras. */
    public static String escapar(Object valor) {
        // Representa un dato ausente como texto vacío en la página.
        if (valor == null) return "";
        // Escapa caracteres que el navegador podría interpretar como HTML.
        // Sustituye los caracteres especiales para que el navegador los muestre como texto.
        return valor.toString().replace("&", "&amp;")
                .replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&#39;");
    }
}
