package p;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.Period;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class ConversonCsvToJson {

	public static void main(String[] args) {
		Path inputCsv = Path.of("./alumnos.csv");
		Path outputJSON = Path.of("./alumnos.json");
		Path errorLog = Path.of("./errores.log");
		try {
			List<Map<String, Object>> alumnos = leerYTransformar(inputCsv, errorLog);
			escribirJson(alumnos, outputJSON);
		} catch (Exception e) {
			System.err.println("Error E/S " + e.getMessage());
		}

	}

	private static void escribirJson(List<Map<String, Object>> alumnos, Path outputJSON) throws IOException {
		String json = toJsonArray(alumnos);
		try (BufferedWriter bw = Files.newBufferedWriter(outputJSON, StandardCharsets.UTF_8)) {
			bw.write(json);
		}
	}

	private static String toJsonArray(List<Map<String, Object>> alumnos) {
		StringBuilder sb = new StringBuilder();
		sb.append("[\n");
		for (int i = 0; i < alumnos.size(); i++) {
			sb.append("   ");
			sb.append(toJsonObject(alumnos.get(i), 2));
			if(i< alumnos.size()-1)
				sb.append(",\n");
		}

		sb.append("\n]");
		return sb.toString();
	}

	private static String toJsonObject(Map<String, Object> map, int i) {
		StringBuilder sb = new StringBuilder();
		sb.append("{\n");
		int contador = 0;
		for (Map.Entry<String, Object> elemento : map.entrySet()) {
			contador++;
			sb.append("   ".repeat(4));
			sb.append("\"" + elemento.getKey() + "\": " + convertValue( elemento.getValue()));
			
			if (contador < map.size())
				sb.append(",");
			sb.append("\n");
		}
		sb.append("   }");
		return sb.toString();
	}

	private static String convertValue(Object value) {
		if(value  instanceof Number || value instanceof Boolean) return value.toString();
		return "\""+value.toString()+"\"";
	}

	private static List<Map<String, Object>> leerYTransformar(Path inputCsv, Path errorLog) throws IOException {
		List<Map<String, Object>> salida = new ArrayList<>();
		List<String> errores = new ArrayList<>();
		if (!Files.exists(inputCsv)) {
			throw new IOException("No existe el fichero:" + inputCsv.toAbsolutePath());
		}

		try (BufferedReader br = Files.newBufferedReader(inputCsv, Charset.forName("windows-1252"))) {
			String cabecera = br.readLine();
			if (cabecera == null) {
				return salida;
			}
			int numColumnas = cabecera.split(";", -1).length;
			String cuerpo;
			int numLinea = 1;
			while ((cuerpo = br.readLine()) != null) {
				numLinea++;
				if (cuerpo.trim().isEmpty())
					continue;
				try {
					Map<String, Object> obj = parseAndTransformData(cuerpo, numColumnas);
					salida.add(obj);
				} catch (Exception e) {
					errores.add("Linea " + numLinea + " :" + e.getMessage() + " || contenido :" + cuerpo);
				}
			}
		}

		if (!errores.isEmpty()) {
			Files.write(errorLog, errores, StandardCharsets.UTF_8);
		}

		return salida;

	}

	private static Map<String, Object> parseAndTransformData(String cuerpo, int numColumnas) throws Exception {
		String[] partesCuerpo = cuerpo.split(";", -1);
		if (partesCuerpo.length != numColumnas) {
			throw new Exception("Número de columnas no coincode con la cabecera");
		}

		String idStr = partesCuerpo[0].trim();
		String nombre = partesCuerpo[1].trim();
		String apellidos = partesCuerpo[2].trim();
		String fechaStr = partesCuerpo[3].trim();
		String notaStr = partesCuerpo[4].trim();

		if (nombre.isEmpty())
			throw new Exception("Nombre vacío");
		if (apellidos.isEmpty())
			throw new Exception("Apellidos vacíos");

		int id = parseInt(idStr, "id");
		LocalDate fecha = parseDate(fechaStr, "fechaNacimiento");
		double nota = parseDouble(notaStr, "nota");

		int edad = Period.between(fecha, LocalDate.now()).getYears();
		String calificacion = parseCalificaciones(nota);

		Map<String, Object> obj = new LinkedHashMap<String, Object>();
		obj.put("id", id);
		obj.put("nombreCompleto", nombre + " " + apellidos);
		obj.put("edad", edad);
		obj.put("calificacion", calificacion);

		return obj;
	}

	private static String parseCalificaciones(double nota) {
		if (nota < 5)
			return "SUSPENSO";
		if (nota < 7)
			return "APROBADO";
		if (nota < 9)
			return "NOTABLE";
		return "SOBRESALIENTE";
	}

	private static int parseInt(String s, String campo) throws Exception {
		try {
			return Integer.parseInt(s);
		} catch (Exception e) {
			throw new Exception(campo + " no es un entero" + s);
		}
	}

	private static double parseDouble(String s, String campo) throws Exception {
		String normalizarDatos = s.replace(",", ".");
		try {
			return Double.parseDouble(normalizarDatos);
		} catch (Exception e) {
			throw new Exception(campo + " no es un numero decimal" + s);
		}
	}

	private static LocalDate parseDate(String s, String campo) throws Exception {
		try {
			return LocalDate.parse(s); // YYYY-MM-DD
		} catch (Exception e) {
			throw new Exception(campo + " no es una fecha con formato correcto yyyy-mm-dd" + s);
		}
	}

}
