<%@page contentType="text/html" pageEncoding="UTF-8"%>
<!DOCTYPE html>
<html>
<head>
    <meta charset="UTF-8">
    <title>Registro de Usuario</title>
</head>
<body>

<h2>Registro de Usuarios</h2>

<form action="${pageContext.request.contextPath}/RegistroUsuarioServlet" method="post">

    <label>nombres:</label><br>
    <input type="text" name="nombres" required><br><br>

    <label>apellidos:</label><br>
    <input type="text" name="apellidos" required><br><br>

    <label>documento:</label><br>
    <input type="text" name="documento" required><br><br>

    <label>correo:</label><br>
    <input type="email" name="correo" required><br><br>

    <label>usuario:</label><br>
    <input type="text" name="usuario" required><br><br>

    <label>contraseña:</label><br>
    <input type="password" name="clave" required><br><br>

    <label>teléfono:</label><br>
    <input type="text" name="telefono"><br><br>

    <label>dirección:</label><br>
    <input type="text" name="direccion"><br><br>

    <label>rol:</label><br>
    <select name="rol">
        <option value="Administrador">Administrador</option>
        <option value="Vendedor">Vendedor</option>
   <option value="Usuario">Usuario</option>
    </select>

    <br><br>

    <input type="submit" value="Registrar">

</form>

</body>
</html>