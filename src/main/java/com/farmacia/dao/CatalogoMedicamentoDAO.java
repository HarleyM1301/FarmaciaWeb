package com.farmacia.dao;

import com.farmacia.util.Conexion;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.text.Normalizer;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Acceso al catálogo oficial CUM/INVIMA y carga del CSV publicado por Datos Abiertos. */
public class CatalogoMedicamentoDAO {
    /** Cuenta las presentaciones oficiales que la página de administración puede consultar. */
    public long contar() throws SQLException {
        try (Connection c = Conexion.getConexion(); PreparedStatement p = c.prepareStatement("SELECT COUNT(*) FROM catalogo_medicamento_invima"); ResultSet r = p.executeQuery()) {
            if (!r.next()) return 0;
            return r.getLong(1);
        }
    }

    /** Importa en transacción el CSV oficial (encabezados API del dataset i7cb-raxc). */
    public int importar(InputStream archivo) throws SQLException, IOException {
        if (archivo == null) throw new IllegalArgumentException("Selecciona el archivo CSV del catálogo INVIMA.");
        try (Connection c = Conexion.getConexion()) {
            if (c == null) throw new SQLException("No se pudo conectar con MySQL.");
            boolean auto = c.getAutoCommit(); c.setAutoCommit(false);
            int filas = 0;
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(archivo, StandardCharsets.UTF_8))) {
                // Usa una tabla temporal para separar la lectura del CSV de la actualización definitiva.
                try (PreparedStatement create = c.prepareStatement("CREATE TEMPORARY TABLE farmacia_cum_import_stage (codigo_cum VARCHAR(110),expediente VARCHAR(40),producto VARCHAR(255),titular VARCHAR(255),registro_sanitario VARCHAR(100),fecha_expedicion DATE,fecha_vencimiento_registro DATE,estado_registro VARCHAR(40),expediente_cum VARCHAR(40),consecutivo_cum VARCHAR(30),cantidad_cum VARCHAR(30),descripcion_comercial TEXT,estado_cum VARCHAR(40),fecha_activo DATE,fecha_inactivo DATE,muestra_medica VARCHAR(20),unidad VARCHAR(20),atc VARCHAR(20),ium VARCHAR(120),via_administracion VARCHAR(100),forma_farmaceutica VARCHAR(150),principio_activo VARCHAR(500),cantidad VARCHAR(50),unidad_medida VARCHAR(40),unidad_referencia VARCHAR(100),descripcion_atc VARCHAR(500)) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci")) { create.execute(); }
                // Lee encabezados para ubicar las columnas aunque el archivo cambie su orden.
                List<String> header = leerCsv(reader);
                if (header == null || header.isEmpty()) throw new IllegalArgumentException("El CSV está vacío.");
                Map<String,Integer> col = new HashMap<>();
                for (int i=0;i<header.size();i++) col.put(normalizar(header.get(i)), i);
                // Rechaza archivos ajenos antes de procesar cientos de miles de registros.
                if (!col.containsKey("producto") || !col.containsKey("expedientecum")) throw new IllegalArgumentException("El archivo no parece ser el catálogo oficial CUM de INVIMA.");
                // Prepara una inserción por lotes que reduce viajes individuales a MySQL.
                String stageSql="INSERT INTO farmacia_cum_import_stage (codigo_cum,expediente,producto,titular,registro_sanitario,fecha_expedicion,fecha_vencimiento_registro,estado_registro,expediente_cum,consecutivo_cum,cantidad_cum,descripcion_comercial,estado_cum,fecha_activo,fecha_inactivo,muestra_medica,unidad,atc,ium,via_administracion,forma_farmaceutica,principio_activo,cantidad,unidad_medida,unidad_referencia,descripcion_atc) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)";
                try(PreparedStatement p=c.prepareStatement(stageSql)) {
                List<String> row;
                while ((row=leerCsv(reader)) != null) {
                    // Ignora líneas vacías que algunos exportadores agregan al final del CSV.
                    if (row.stream().allMatch(String::isBlank)) continue;
                    String expCum=valor(row,col,"expedientecum"), consecutivo=valor(row,col,"consecutivocum"), cantidad=valor(row,col,"cantidadcum");
                    String key=(expCum+"-"+consecutivo+"-"+cantidad).replaceAll("[- ]+$", "");
                    // Omite registros sin identificador CUM o sin nombre comercial.
                    if (key.equals("--") || key.isBlank() || valor(row,col,"producto").isBlank()) continue;
                    int i=1;p.setString(i++,key);
                    p.setString(i++, recortar(valor(row,col,"expediente"),40));p.setString(i++, recortar(valor(row,col,"producto"),255));p.setString(i++, recortar(valor(row,col,"titular"),255));p.setString(i++,recortar(valor(row,col,"registrosanitario"),100));
                    fecha(p,i++,valor(row,col,"fechaexpedicion"));fecha(p,i++,valor(row,col,"fechavencimiento"));p.setString(i++,recortar(valor(row,col,"estadoregistro"),40));
                    p.setString(i++, recortar(expCum,40));p.setString(i++,recortar(consecutivo,30));p.setString(i++,recortar(cantidad,30));p.setString(i++,valor(row,col,"descripcioncomercial"));p.setString(i++,recortar(valor(row,col,"estadocum"),40));
                    fecha(p,i++,valor(row,col,"fechaactivo"));fecha(p,i++,valor(row,col,"fechainactivo"));p.setString(i++,recortar(valor(row,col,"muestramedica"),20));p.setString(i++,recortar(valor(row,col,"unidad"),20));p.setString(i++,recortar(valor(row,col,"atc"),20));
                    p.setString(i++,recortar(valor(row,col,"ium"),120));p.setString(i++,recortar(valor(row,col,"viaadministracion"),100));p.setString(i++,recortar(valor(row,col,"formafarmaceutica"),150));p.setString(i++,recortar(valor(row,col,"principioactivo"),500));p.setString(i++,recortar(valor(row,col,"cantidad"),50));p.setString(i++,recortar(valor(row,col,"unidadmedida"),40));p.setString(i++,recortar(valor(row,col,"unidadreferencia"),100));p.setString(i,recortar(valor(row,col,"descripcionatc"),500));
                    p.addBatch(); filas++;
                    // Vacía el lote cada 500 filas para limitar memoria y tamaño del paquete SQL.
                    if (filas % 500 == 0) p.executeBatch();
                }
                p.executeBatch();
                }
                if (filas == 0) throw new IllegalArgumentException("El CSV no contiene filas CUM válidas.");
                // Consolida filas repetidas del mismo código CUM en una sola presentación maestra.
                String upsert="INSERT INTO catalogo_medicamento_invima (expediente,producto,titular,registro_sanitario,fecha_expedicion,fecha_vencimiento_registro,estado_registro,expediente_cum,consecutivo_cum,cantidad_cum,descripcion_comercial,estado_cum,fecha_activo,fecha_inactivo,muestra_medica,unidad,atc,codigo_cum,ium,via_administracion,forma_farmaceutica) "
                        +"SELECT codigo_cum,MAX(producto),MAX(titular),MAX(registro_sanitario),MAX(fecha_expedicion),MAX(fecha_vencimiento_registro),MAX(estado_registro),MAX(expediente_cum),MAX(consecutivo_cum),MAX(cantidad_cum),MAX(descripcion_comercial),MAX(estado_cum),MAX(fecha_activo),MAX(fecha_inactivo),MAX(muestra_medica),MAX(unidad),MAX(atc),codigo_cum,MAX(ium),MAX(via_administracion),MAX(forma_farmaceutica) FROM farmacia_cum_import_stage GROUP BY codigo_cum "
                        +"ON DUPLICATE KEY UPDATE expediente=VALUES(expediente),producto=VALUES(producto),titular=VALUES(titular),registro_sanitario=VALUES(registro_sanitario),fecha_expedicion=VALUES(fecha_expedicion),fecha_vencimiento_registro=VALUES(fecha_vencimiento_registro),estado_registro=VALUES(estado_registro),expediente_cum=VALUES(expediente_cum),consecutivo_cum=VALUES(consecutivo_cum),cantidad_cum=VALUES(cantidad_cum),descripcion_comercial=VALUES(descripcion_comercial),estado_cum=VALUES(estado_cum),fecha_activo=VALUES(fecha_activo),fecha_inactivo=VALUES(fecha_inactivo),muestra_medica=VALUES(muestra_medica),unidad=VALUES(unidad),atc=VALUES(atc),ium=VALUES(ium),via_administracion=VALUES(via_administracion),forma_farmaceutica=VALUES(forma_farmaceutica)";
                try(PreparedStatement p=c.prepareStatement(upsert)){p.executeUpdate();}
                // Conserva cada principio activo por separado para medicamentos combinados.
                String componentes="INSERT INTO catalogo_medicamento_componente_invima (id_catalogo,firma_componente,principio_activo,cantidad,unidad_medida,unidad_referencia,atc,descripcion_atc) "
                        +"SELECT m.id_catalogo,SHA2(CONCAT_WS(CHAR(31),COALESCE(s.principio_activo,''),COALESCE(s.cantidad,''),COALESCE(s.unidad_medida,''),COALESCE(s.unidad_referencia,''),COALESCE(s.atc,'')),256),s.principio_activo,s.cantidad,s.unidad_medida,s.unidad_referencia,s.atc,s.descripcion_atc FROM farmacia_cum_import_stage s JOIN catalogo_medicamento_invima m ON m.codigo_cum=s.codigo_cum WHERE s.principio_activo IS NOT NULL AND s.principio_activo<>'' GROUP BY m.id_catalogo,s.principio_activo,s.cantidad,s.unidad_medida,s.unidad_referencia,s.atc,s.descripcion_atc "
                        +"ON DUPLICATE KEY UPDATE principio_activo=VALUES(principio_activo),cantidad=VALUES(cantidad),unidad_medida=VALUES(unidad_medida),unidad_referencia=VALUES(unidad_referencia),atc=VALUES(atc),descripcion_atc=VALUES(descripcion_atc)";
                try(PreparedStatement p=c.prepareStatement(componentes)){p.executeUpdate();}
                // Confirma juntas las presentaciones y sus componentes para evitar importaciones parciales.
                c.commit(); return filas;
            } catch (Exception e) {
                // Deshace la importación completa cuando falla la lectura o una escritura SQL.
                c.rollback();
                if (e instanceof SQLException se) throw se;
                if (e instanceof IOException io) throw io;
                if (e instanceof RuntimeException re) throw re;
                throw new SQLException("Falló la importación del catálogo INVIMA.",e);
            } finally { c.setAutoCommit(auto); }
        }
    }

    /** Busca presentaciones oficiales por nombre, marca, registro sanitario o CUM. */
    public List<Map<String,Object>> buscar(String texto, int limite) throws SQLException {
        // Exige una búsqueda mínima para evitar descargar una lista completa del catálogo.
        String q=texto==null?"":texto.trim();
        List<Map<String,Object>> out=new ArrayList<>(); if(q.length()<2) return out;
        String sql="SELECT m.id_catalogo,m.producto,m.registro_sanitario,m.codigo_cum,m.descripcion_comercial,m.estado_registro,m.estado_cum,m.unidad,m.atc,m.ium,m.via_administracion,m.forma_farmaceutica "
                +"FROM catalogo_medicamento_invima m WHERE (m.estado_registro IS NULL OR LOWER(m.estado_registro) LIKE '%vigent%') "
                +"AND (m.producto LIKE ? OR m.descripcion_comercial LIKE ? OR m.registro_sanitario LIKE ? OR m.codigo_cum LIKE ? OR EXISTS (SELECT 1 FROM catalogo_medicamento_componente_invima x WHERE x.id_catalogo=m.id_catalogo AND x.principio_activo LIKE ?)) "
                +"ORDER BY CASE WHEN m.producto LIKE ? THEN 0 ELSE 1 END, m.producto LIMIT ?";
        String like="%"+q+"%", starts=q+"%";
        try(Connection c=Conexion.getConexion();PreparedStatement p=c.prepareStatement(sql)){
            p.setString(1,like);p.setString(2,like);p.setString(3,like);p.setString(4,like);p.setString(5,like);p.setString(6,starts);p.setInt(7,Math.max(1,Math.min(limite,30)));
            try(ResultSet r=p.executeQuery()){while(r.next()){
                Map<String,Object> m=new HashMap<>();m.put("id",r.getLong("id_catalogo"));m.put("producto",r.getString("producto"));m.put("registro",r.getString("registro_sanitario"));m.put("cum",r.getString("codigo_cum"));m.put("descripcion",r.getString("descripcion_comercial"));m.put("estado",r.getString("estado_cum"));m.put("unidad",r.getString("unidad"));m.put("atc",r.getString("atc"));m.put("ium",r.getString("ium"));m.put("via",r.getString("via_administracion"));m.put("forma",r.getString("forma_farmaceutica"));m.put("componentes","");out.add(m);
            }}
            // Recupera ingredientes de los resultados en una consulta agrupada, no una por producto.
            if(!out.isEmpty()){
                String marks=String.join(",",java.util.Collections.nCopies(out.size(),"?"));
                String detailSql="SELECT id_catalogo,GROUP_CONCAT(DISTINCT CONCAT_WS(' ',principio_activo,CONCAT(cantidad,' ',unidad_medida),CONCAT('por',unidad_referencia)) SEPARATOR ' + ') AS componentes FROM catalogo_medicamento_componente_invima WHERE id_catalogo IN ("+marks+") GROUP BY id_catalogo";
                Map<Long,String> componentes=new HashMap<>();
                try(PreparedStatement d=c.prepareStatement(detailSql)){for(int i=0;i<out.size();i++)d.setLong(i+1,(Long)out.get(i).get("id"));try(ResultSet r=d.executeQuery()){while(r.next())componentes.put(r.getLong("id_catalogo"),r.getString("componentes"));}}
                for(Map<String,Object> m:out)m.put("componentes",componentes.getOrDefault((Long)m.get("id"),""));
            }
        }
        return out;
    }

    /** Busca la presentación oficial asociada a un código leído en el empaque. */
    public Map<String,Object> buscarPorCodigoBarras(String codigo) throws SQLException {
        String limpio = codigo == null ? "" : codigo.replaceAll("\\s", "");
        if (!limpio.matches("[0-9]{8,14}")) return null;
        String sql = "SELECT m.id_catalogo,m.producto,m.registro_sanitario,m.codigo_cum,m.forma_farmaceutica,m.atc "
                + "FROM catalogo_medicamento_codigo_barras b JOIN catalogo_medicamento_invima m ON m.id_catalogo=b.id_catalogo "
                + "WHERE b.codigo=? AND (m.estado_registro IS NULL OR LOWER(m.estado_registro) LIKE '%vigent%') LIMIT 1";
        try (Connection c=Conexion.getConexion()) {
            if(c==null)throw new SQLException("No hay conexión con MySQL.");
            try(PreparedStatement p=c.prepareStatement(sql)){
                p.setString(1,limpio);
                try (ResultSet r=p.executeQuery()) {
                    if (!r.next()) return null;
                    Map<String,Object> fila=new HashMap<>();
                    fila.put("id",r.getLong("id_catalogo")); fila.put("producto",r.getString("producto"));
                    fila.put("registro",r.getString("registro_sanitario")); fila.put("cum",r.getString("codigo_cum"));
                    fila.put("forma",r.getString("forma_farmaceutica")); fila.put("atc",r.getString("atc"));
                    return fila;
                }
            }
        }
    }

    /** Devuelve el contenido de una columna o una cadena vacía si falta en el archivo. */
    private static String valor(List<String> row,Map<String,Integer> col,String key){Integer i=col.get(key);return i==null||i>=row.size()?"":row.get(i).trim();}
    /** Canonicaliza encabezados para comparar palabras con tildes, espacios o mayúsculas distintas. */
    private static String normalizar(String s){return Normalizer.normalize(s==null?"":s.replace("\uFEFF",""),Normalizer.Form.NFD).replaceAll("\\p{M}","").toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]","");}
    /** Convierte cadenas vacías a NULL para conservar la semántica de columnas opcionales. */
    private static String nullSiVacio(String s){return s==null||s.isBlank()?null:s;}
    /** Limita textos al máximo admitido por la columna destino sin cortar valores cortos. */
    private static String recortar(String s,int max){return s==null?null:s.length()>max?s.substring(0,max):s;}
    /** Convierte los formatos de fecha del CSV a DATE y guarda NULL si no son reconocibles. */
    private static void fecha(PreparedStatement p,int i,String v)throws SQLException{if(v==null||v.isBlank()){p.setNull(i,java.sql.Types.DATE);return;} for(DateTimeFormatter f:List.of(DateTimeFormatter.ISO_LOCAL_DATE,DateTimeFormatter.ofPattern("MM/dd/uuuu"),DateTimeFormatter.ofPattern("dd/MM/uuuu"))){try{p.setDate(i,Date.valueOf(LocalDate.parse(v.trim(),f)));return;}catch(DateTimeParseException ignored){}}p.setNull(i,java.sql.Types.DATE);}

    /** Parser CSV con comillas escapadas y celdas multilínea. */
    private static List<String> leerCsv(BufferedReader r)throws IOException{
        // Acumula caracteres respetando delimitadores y saltos de línea dentro de celdas entre comillas.
        List<String> fields=new ArrayList<>();StringBuilder cell=new StringBuilder();boolean quoted=false,started=false;int ch;
        while((ch=r.read())!=-1){char c=(char)ch;started=true;if(quoted){if(c=='"'){r.mark(1);int next=r.read();if(next=='"')cell.append('"');else{quoted=false;if(next!=-1)r.reset();}}else cell.append(c);}else if(c=='"'&&cell.length()==0){quoted=true;}else if(c==','){fields.add(cell.toString());cell.setLength(0);}else if(c=='\n'){fields.add(cell.toString());return fields;}else if(c!='\r')cell.append(c);}
        if(!started)return null;fields.add(cell.toString());return fields;
    }
}
