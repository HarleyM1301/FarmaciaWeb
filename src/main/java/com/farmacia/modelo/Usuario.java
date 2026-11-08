package com.farmacia.modelo;

/** Datos de usuario usados por el inicio de sesión y el registro. */
public class Usuario {

    // Identificador único asignado por la base de datos.
    private int idUsuario;
    // Nombre completo que se muestra en la interfaz.
    private String nombre;
    // Nombre de acceso que se utiliza al iniciar sesión.
    private String usuario;
    // Hash de la contraseña guardado para verificar el acceso.
    private String contrasena;
    // Rol que determina los módulos disponibles para esta cuenta.
    private String rol;

    // Constructor vacío requerido por herramientas que crean el objeto por etapas.
    public Usuario() {
    }

    // Construye un usuario con todos sus datos, incluido el identificador existente.
    public Usuario(int idUsuario, String nombre, String usuario, String contrasena, String rol) {
        this.idUsuario = idUsuario;
        this.nombre = nombre;
        this.usuario = usuario;
        this.contrasena = contrasena;
        this.rol = rol;
    }

    // Construye un usuario nuevo antes de que la base de datos asigne su identificador.
    public Usuario(String nombre, String usuario, String contrasena, String rol) {
        this.nombre = nombre;
        this.usuario = usuario;
        this.contrasena = contrasena;
        this.rol = rol;
    }

    // Devuelve el identificador asignado por la base de datos.
    public int getIdUsuario() {
        return idUsuario;
    }

    // Guarda el identificador de la cuenta existente.
    public void setIdUsuario(int idUsuario) {
        this.idUsuario = idUsuario;
    }

    // Devuelve el nombre completo usado en la interfaz.
    public String getNombre() {
        return nombre;
    }

    // Actualiza el nombre completo del usuario.
    public void setNombre(String nombre) {
        this.nombre = nombre;
    }

    // Devuelve el nombre usado para iniciar sesión.
    public String getUsuario() {
        return usuario;
    }

    // Guarda el nombre de acceso de la cuenta.
    public void setUsuario(String usuario) {
        this.usuario = usuario;
    }

    // Devuelve el hash que se utiliza para verificar la contraseña.
    public String getContrasena() {
        return contrasena;
    }

    // Guarda el hash de contraseña recibido del proceso de registro o migración.
    public void setContrasena(String contrasena) {
        this.contrasena = contrasena;
    }

    // Devuelve el perfil que determina los permisos de la cuenta.
    public String getRol() {
        return rol;
    }

    // Guarda el perfil que el filtro de autenticación evaluará.
    public void setRol(String rol) {
        this.rol = rol;
    }
}
