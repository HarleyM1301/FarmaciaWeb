from docx import Document
from docx.enum.text import WD_ALIGN_PARAGRAPH
from docx.oxml import OxmlElement
from docx.oxml.ns import qn
from docx.shared import Inches, Pt, RGBColor

OUTPUT = "evidencias/Reporte_pruebas_FarmaciaWeb.docx"
GREEN = "187C5A"
DARK = "173F3A"
LIGHT = "EAF4F0"


def shade(cell, fill):
    properties = cell._tc.get_or_add_tcPr()
    shading = OxmlElement("w:shd")
    shading.set(qn("w:fill"), fill)
    properties.append(shading)


def add_case(doc, number, title, objective, method, url, body, expected, status):
    doc.add_heading(f"Prueba {number}  {title}", level=2)
    for label, value in [
        ("Objetivo. ", objective),
        ("Solicitud. ", f"{method} {url}"),
    ]:
        p = doc.add_paragraph()
        p.add_run(label).bold = True
        p.add_run(value)
    if body:
        p = doc.add_paragraph()
        p.add_run("Body JSON. ").bold = True
        p.add_run(body)
    p = doc.add_paragraph()
    p.add_run("Resultado esperado. ").bold = True
    p.add_run(expected)
    p = doc.add_paragraph()
    p.add_run("Resultado obtenido. ").bold = True
    p.add_run("Completar con el estado HTTP y mensaje que muestre Postman.")
    p = doc.add_paragraph()
    p.add_run("Estado. ").bold = True
    p.add_run(status)
    p = doc.add_paragraph()
    p.add_run("Evidencia. ").bold = True
    p.add_run(f"Insertar la captura real de Postman correspondiente a la prueba {number}.")


doc = Document()
section = doc.sections[0]
section.top_margin = Inches(0.65)
section.bottom_margin = Inches(0.65)
section.left_margin = Inches(0.8)
section.right_margin = Inches(0.8)
normal = doc.styles["Normal"]
normal.font.name = "Aptos"
normal.font.size = Pt(10)
normal.font.color.rgb = RGBColor.from_string(DARK)
normal.paragraph_format.space_after = Pt(5)
for name, size in (("Heading 1", 16), ("Heading 2", 12)):
    style = doc.styles[name]
    style.font.name = "Aptos Display"
    style.font.size = Pt(size)
    style.font.bold = True
    style.font.color.rgb = RGBColor.from_string(GREEN)

p = doc.add_paragraph(style="Title")
p.alignment = WD_ALIGN_PARAGRAPH.LEFT
r = p.add_run("Plan y reporte de pruebas de FarmaciaWeb")
r.font.name = "Aptos Display"
r.font.size = Pt(24)
r.font.bold = True
r.font.color.rgb = RGBColor.from_string(DARK)
p = doc.add_paragraph()
p.add_run("Propósito. ").bold = True
p.add_run(
    "Documentar cinco comprobaciones de FarmaciaWeb, ejecutarlas con Postman y conservar "
    "capturas verificables. Las pruebas 1 y 2 fueron indicadas como realizadas; sus códigos "
    "HTTP y capturas deben anotarse. Las pruebas 3 a 5 quedan listas para ejecutarse."
)

doc.add_heading("Entorno de prueba", level=1)
t = doc.add_table(rows=0, cols=2)
t.style = "Light Shading Accent 1"
for key, value in [
    ("Aplicación", "FarmaciaWeb"),
    ("Servidor", "Apache Tomcat 10"),
    ("Base de datos", "MySQL, farmacia_db"),
    ("Herramienta", "Postman y Collection Runner"),
    ("URL base", "http://localhost:8081/FarmaciaWeb"),
    ("Fecha", "18/10/2026; ajustar a la fecha real de ejecución"),
]:
    cells = t.add_row().cells
    cells[0].text = key
    cells[1].text = value
    shade(cells[0], LIGHT)
    cells[0].paragraphs[0].runs[0].bold = True

doc.add_heading("Resumen de casos", level=1)
t = doc.add_table(rows=1, cols=4)
t.style = "Table Grid"
for cell, value in zip(t.rows[0].cells, ("N.º", "Comprobación", "Esperado", "Estado")):
    cell.text = value
    shade(cell, GREEN)
    run = cell.paragraphs[0].runs[0]
    run.bold = True
    run.font.color.rgb = RGBColor(255, 255, 255)
summary = [
    ("1", "Diagnóstico y conexión MySQL", "200 y conexión exitosa", "Realizada; falta captura"),
    ("2", "Login con datos vacíos", "400 Bad Request", "Realizada; falta captura"),
    ("3", "Panel sin sesión", "302 hacia login.jsp", "Pendiente"),
    ("4", "Endpoint inexistente", "404 Not Found", "Pendiente"),
    ("5", "Registro de usuario", "201 Created", "Pendiente"),
]
for row in summary:
    cells = t.add_row().cells
    for cell, value in zip(cells, row):
        cell.text = value
        for run in cell.paragraphs[0].runs:
            run.font.size = Pt(8)

doc.add_heading("Casos de prueba", level=1)
add_case(doc, 1, "Verificar el servicio y MySQL",
         "Comprobar que la aplicación responda y abra una conexión con MySQL.",
         "GET", "{{baseUrl}}/conexion", "",
         "200 OK y el texto ‘Conexión exitosa con MySQL’.",
         "Ejecutada según lo informado; anotar código y adjuntar captura.")
add_case(doc, 2, "Validar campos obligatorios del login",
         "Comprobar que el login rechace credenciales vacías.",
         "POST", "{{baseUrl}}/api/auth/login", '{"usuario":"","contrasena":""}',
         "400 Bad Request y mensaje de campos obligatorios.",
         "Ejecutada según lo informado; anotar código y adjuntar captura.")
add_case(doc, 3, "Proteger el panel sin sesión",
         "Verificar que una persona sin sesión no pueda abrir el panel interno.",
         "GET", "{{baseUrl}}/panel.jsp", "",
         "302 Found y Location dirigido a /login.jsp.", "Pendiente de ejecutar.")
add_case(doc, 4, "Responder a una ruta inexistente",
         "Comprobar que la API identifique una ruta no implementada.",
         "POST", "{{baseUrl}}/api/auth/no-existe", "Sin body",
         "404 Not Found y mensaje ‘Endpoint no encontrado’.", "Pendiente de ejecutar.")
add_case(doc, 5, "Registrar un usuario de prueba",
         "Comprobar que la API registre una cuenta nueva con datos válidos.",
         "POST", "{{baseUrl}}/api/auth/registro",
         '{"nombre":"Usuario de prueba Postman","usuario":"qa_farmacia_unico","contrasena":"ClavePrueba123"}',
         "201 Created y success=true. El registro público asigna el rol Usuario.",
         "Pendiente. Usa nombre de usuario único.")

doc.add_page_break()
doc.add_heading("Ejecución automatizada con Postman", level=1)
for step in [
    "Inicia MySQL y Tomcat y confirma que la URL base responda.",
    "En Postman, selecciona Import y carga evidencias/FarmaciaWeb-Pruebas.postman_collection.json.",
    "Abre la colección FarmaciaWeb - Evidencia GA9 AA3 y pulsa Run.",
    "Ejecuta las cinco solicitudes en orden. La colección verifica automáticamente códigos HTTP y mensajes.",
    "La prueba 5 crea un usuario ficticio nuevo en la base local en cada ejecución.",
    "Guarda capturas de cada solicitud y de sus resultados. Conserva también el resumen del Collection Runner.",
    "Completa los resultados obtenidos. No marques una prueba aprobada si la respuesta real no coincide con la esperada.",
]:
    doc.add_paragraph(step, style="List Number")

doc.add_heading("Capturas y video", level=1)
doc.add_paragraph(
    "Inserta las capturas reales de Postman en el PDF de evidencias y agrega la captura del "
    "resumen de Collection Runner. En el video MP4, muestra la colección, ejecuta las "
    "solicitudes, explica qué valida cada caso y comenta los resultados. Añade aquí el enlace "
    "al video después de publicarlo donde indique el instructor."
)
doc.add_paragraph("Enlace al video: __________________________________________________________")
doc.add_paragraph(
    "Nota: /conexion es un endpoint de diagnóstico para la conexión de la aplicación, "
    "no una API REST de negocio."
)
footer = section.footer.paragraphs[0]
footer.alignment = WD_ALIGN_PARAGRAPH.CENTER
footer.add_run("FarmaciaWeb | Evidencia de pruebas de software").font.size = Pt(8)
doc.save(OUTPUT)
print(OUTPUT)
