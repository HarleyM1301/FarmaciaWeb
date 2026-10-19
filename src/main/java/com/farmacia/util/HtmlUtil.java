package com.farmacia.util;

/** Funciones pequeñas para imprimir datos del usuario sin interpretar HTML. */
public final class HtmlUtil {
    private HtmlUtil() { }

    /** Convierte caracteres especiales en entidades HTML seguras. */
    public static String escapar(Object valor) {
        if (valor == null) return "";
        return valor.toString().replace("&", "&amp;")
                .replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&#39;");
    }
}
