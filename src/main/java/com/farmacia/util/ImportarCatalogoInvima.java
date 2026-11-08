package com.farmacia.util;

import com.farmacia.dao.CatalogoMedicamentoDAO;
import java.sql.Connection;
import java.sql.Statement;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;

/** Utilidad local para actualizar el catálogo sin subir el CSV desde el navegador. */
public final class ImportarCatalogoInvima {
    // Impide crear instancias: esta clase solo ofrece comandos ejecutables desde consola.
    private ImportarCatalogoInvima() { }
    /** Ejecuta la importación, aplica una migración SQL o consulta el estado del catálogo. */
    public static void main(String[] args) throws Exception {
        // Permite aplicar scripts versionados al mismo MySQL que utiliza la aplicación.
        if(args.length==2&&"--migracion".equals(args[0])){aplicarMigracion(Path.of(args[1]));return;}
        // Comprueba los argumentos antes de abrir archivos o conexiones.
        if(args.length!=1)throw new IllegalArgumentException("Uso: ImportarCatalogoInvima <archivo.csv|--verificar> o --migracion <archivo.sql>");
        // Reutiliza el DAO de la aplicación para que la carga de consola tenga las mismas reglas.
        CatalogoMedicamentoDAO dao=new CatalogoMedicamentoDAO();
        // Importa el CSV si se indicó una ruta; --verificar solo informa los conteos existentes.
        if(!"--verificar".equals(args[0])){int filas;try(var input=Files.newInputStream(Path.of(args[0]))){filas=dao.importar(input);}System.out.println("Filas CUM importadas o actualizadas: "+filas);}
        // Presenta los conteos y una búsqueda de ejemplo para comprobar que el catálogo responde.
        System.out.println("Presentaciones guardadas en catálogo: "+dao.contar());
        try(var c=Conexion.getConexion();var p=c.prepareStatement("SELECT COUNT(*) FROM producto");var r=p.executeQuery()){
            if(r.next())System.out.println("Productos de inventario conservados: "+r.getLong(1));
        }
        try(var c=Conexion.getConexion();var p=c.prepareStatement("SELECT COUNT(*) FROM catalogo_medicamento_componente_invima");var r=p.executeQuery()){
            if(r.next())System.out.println("Componentes de principio activo guardados: "+r.getLong(1));
        }
        var muestra=dao.buscar("acetaminofen",5);
        System.out.println("Coincidencias de ejemplo para acetaminofen: "+muestra.size());
        if(!muestra.isEmpty())System.out.println("Componentes en la primera coincidencia: "+muestra.get(0).get("componentes"));
    }
    /** Ejecuta cada sentencia del archivo SQL de migración usando la conexión configurada del proyecto. */
    private static void aplicarMigracion(Path ruta)throws Exception{
        // Lee el script en UTF-8 y elimina comentarios SQL antes de separar las sentencias.
        String script=Files.readString(ruta,StandardCharsets.UTF_8).replaceAll("(?m)^\\s*--.*$","");
        try(Connection c=Conexion.getConexion();Statement s=c.createStatement()){
            // Ejecuta en orden las instrucciones no vacías del archivo.
            for(String sql:script.split(";")){String instruccion=sql.trim();if(!instruccion.isEmpty())s.execute(instruccion);}
        }
        System.out.println("Migración SQL aplicada: "+ruta.getFileName());
    }
}
