package p;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.Period;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class CsvToJsonAlumnos {

    public static void main(String[] args) {
        // Puedes pasar rutas por argumentos:
        // args[0] = entrada CSV, args[1] = salida JSON, args[2] = log errores (opcional)
        Path inputCsv =  Path.of("./alumnos.csv");
        Path outputJson =  Path.of("alumnos.json");
        Path errorLog =  Path.of("errores.log");

        try {
            List<Map<String, Object>> alumnos = leerYTransformar(inputCsv, errorLog);
            escribirJson(alumnos, outputJson);
        } catch (IOException e) {
            System.err.println("Error de E/S: " + e.getMessage());
        }
    }

    private static List<Map<String, Object>> leerYTransformar(Path inputCsv, Path errorLog) throws IOException {
        List<Map<String, Object>> salida = new ArrayList<>();
        List<String> errores = new ArrayList<>();

        if (!Files.exists(inputCsv)) {
            throw new IOException("No existe el fichero: " + inputCsv.toAbsolutePath());
        }

        try (BufferedReader br = Files.newBufferedReader(inputCsv,Charset.forName("windows-1252") )) {
            String header = br.readLine();
            if (header == null) {
                return salida;
            }

            // Esperado: id;nombre;apellidos;fecha_nacimiento;nota_media
            String line;
            int lineNumber = 1;

            while ((line = br.readLine()) != null) {
                lineNumber++;
                if (line.trim().isEmpty()) continue;

                try {
                    Map<String, Object> obj = parseAndTransform(line);
                    salida.add(obj);
                } catch (RuntimeException ex) {
                    errores.add("Línea " + lineNumber + ": " + ex.getMessage() + " | Contenido: " + line);
                }
            }
        }

        if (!errores.isEmpty()) {
            Files.write(errorLog, errores, StandardCharsets.UTF_8);
        }

        return salida;
    }

    private static Map<String, Object> parseAndTransform(String csvLine) {
        // CSV con separador ';'
        String[] parts = csvLine.split(";", -1);
        if (parts.length != 5) {
            throw new IllegalArgumentException("Número de columnas incorrecto (esperadas 5, recibidas " + parts.length + ")");
        }

        String idStr = parts[0].trim();
        String nombre = parts[1].trim();
        String apellidos = parts[2].trim();
        String fechaStr = parts[3].trim();
        String notaStr = parts[4].trim();

        int id = parseIntStrict(idStr, "id");
        if (nombre.isEmpty()) throw new IllegalArgumentException("nombre vacío");
        if (apellidos.isEmpty()) throw new IllegalArgumentException("apellidos vacío");

        LocalDate fechaNac = parseDateStrict(fechaStr, "fecha_nacimiento"); // YYYY-MM-DD
        int edad = Period.between(fechaNac, LocalDate.now()).getYears();

        double nota = parseDoubleStrict(notaStr, "nota_media");
        String calificacion = aCalificacion(nota);

        // LinkedHashMap para mantener orden estable en el JSON
        Map<String, Object> obj = new LinkedHashMap<>();
        obj.put("id", id);
        obj.put("nombreCompleto", nombre + " " + apellidos);
        obj.put("edad", edad);
        obj.put("calificacion", calificacion);
        return obj;
    }

    private static int parseIntStrict(String s, String field) {
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(field + " no es entero: " + s);
        }
    }

    private static double parseDoubleStrict(String s, String field) {
        // Acepta "7.5" o "7,5"
        String normalized = s.replace(',', '.').trim();
        try {
            return Double.parseDouble(normalized);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(field + " no es número: " + s);
        }
    }

    private static LocalDate parseDateStrict(String s, String field) {
        try {
            return LocalDate.parse(s); // ISO-8601: YYYY-MM-DD
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException(field + " inválida (esperado YYYY-MM-DD): " + s);
        }
    }

    private static String aCalificacion(double nota) {
        if (nota < 5.0) return "INSUFICIENTE";
        if (nota < 7.0) return "APROBADO";
        if (nota < 9.0) return "NOTABLE";
        return "SOBRESALIENTE";
    }

    private static void escribirJson(List<Map<String, Object>> alumnos, Path outputJson) throws IOException {
        // JSON manual simple (sin librerías externas)
        String json = toJsonArray(alumnos);

        try (BufferedWriter bw = Files.newBufferedWriter(outputJson, StandardCharsets.UTF_8)) {
            bw.write(json);
        }
    }

    private static String toJsonArray(List<Map<String, Object>> items) {
        StringBuilder sb = new StringBuilder();
        sb.append("[\n");
        for (int i = 0; i < items.size(); i++) {
            sb.append("  ");
            sb.append(toJsonObject(items.get(i), 2));
            if (i < items.size() - 1) sb.append(",");
            sb.append("\n");
        }
        sb.append("]\n");
        return sb.toString();
    }

    private static String toJsonObject(Map<String, Object> obj, int indent) {
        StringBuilder sb = new StringBuilder();
        sb.append("{\n");

        int count = 0;
        for (Map.Entry<String, Object> e : obj.entrySet()) {
            count++;
            sb.append(" ".repeat(indent + 2));
            sb.append("\"").append(escapeJson(e.getKey())).append("\": ");
            sb.append(toJsonValue(e.getValue()));
            if (count < obj.size()) sb.append(",");
            sb.append("\n");
        }

        sb.append(" ".repeat(indent));
        sb.append("}");
        return sb.toString();
    }

    private static String toJsonValue(Object v) {
        if (v == null) return "null";
        if (v instanceof Number || v instanceof Boolean) return v.toString();
        return "\"" + escapeJson(String.valueOf(v)) + "\"";
    }

    private static String escapeJson(String s) {
        // Escapes mínimos para JSON
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"': out.append("\\\""); break;
                case '\\': out.append("\\\\"); break;
                case '\b': out.append("\\b"); break;
                case '\f': out.append("\\f"); break;
                case '\n': out.append("\\n"); break;
                case '\r': out.append("\\r"); break;
                case '\t': out.append("\\t"); break;
                default:
                    if (c < 0x20) {
                        out.append(String.format(Locale.ROOT, "\\u%04x", (int) c));
                    } else {
                        out.append(c);
                    }
            }
        }
        return out.toString();
    }
}
