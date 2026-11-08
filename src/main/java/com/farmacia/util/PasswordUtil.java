
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

        // Rechaza valores vacíos antes de generar el hash de la contraseña.
        if (contrasena == null || contrasena.isEmpty()) {
            throw new IllegalArgumentException(
                "La contraseña no puede estar vacía"
            );
        }

        // Genera el hash con una sal aleatoria.
        // Devuelve una cadena irreversible que sirve para comprobar futuras contraseñas.
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
            // Sin la clave ingresada o el hash almacenado no se puede autenticar.
            return false;
        }

        try {
            // Compara la clave ingresada con el hash sin recuperar la clave original.
            return BCrypt.checkpw(contrasena, hashGuardado);
        } catch (IllegalArgumentException e) {
            // Un hash mal formado se considera una credencial incorrecta.
            return false;
        }
    }
}
