
package com.farmacia.util;

import org.mindrot.jbcrypt.BCrypt;

/**
 * Clase para proteger las contraseñas de los usuarios.
 * Utiliza BCrypt para guardar hashes en lugar de texto legible.
 */
public class PasswordUtil {

    /**
     * Convierte una contraseña en un hash BCrypt.
     */
    public static String encriptar(String contrasena) {

        if (contrasena == null || contrasena.isEmpty()) {
            throw new IllegalArgumentException(
                "La contraseña no puede estar vacía"
            );
        }

        // Genera el hash con una sal aleatoria.
        return BCrypt.hashpw(
            contrasena,
            BCrypt.gensalt(12)
        );
    }

    /**
     * Verifica si la contraseña ingresada coincide con el hash.
     */
    public static boolean verificar(
            String contrasena,
            String hashGuardado) {

        if (contrasena == null || hashGuardado == null) {
            return false;
        }

        try {
            return BCrypt.checkpw(contrasena, hashGuardado);
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
}
