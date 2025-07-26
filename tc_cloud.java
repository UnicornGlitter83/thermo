//compile with   javac -cp .:json-20230618.jar:mysql-connector-j-9.3.0.jar server.java
//run with       java -cp .:json-20230618.jar:mysql-connector-j-9.3.0.jar server
//URL		 http://18.116.46.202:8000/


import com.sun.net.httpserver.*;
import org.json.JSONObject;

import java.io.*;
import java.net.InetSocketAddress;
import java.sql.*;
import java.util.regex.*;

public class tc_cloud	{
        private	static final int PORT =	8001;
	private static final String DB_URL = "jdbc:mysql://localhost/thermostat";
        private static final String DB_USER = "root";
        private static final String DB_PASS = "password";

        public static void main(String[] args) throws Exception {
                HttpServer server = HttpServer.create(new InetSocketAddress(PORT), 0);
		server.createContext("/", new RootHandler());
		System.out.println("Server started on port " + PORT);
		server.start();
	}

	static class RootHandler implements HttpHandler {
		public void handle(HttpExchange exchange) throws IOException {
			String method = exchange.getRequestMethod();
			String path = exchange.getRequestURI().getPath();
			Connection conn = null;
	
			try {
				conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASS);
				if (method.equals("POST") || method.equals("PUT")) {
					handlePostPut(exchange, conn, path);
				} else if (method.equals("GET")) {
					handleGet(exchange, conn, path);
				} else if (method.equals("DELETE")) {
					handleDelete(exchange, conn, path);
				} else {
					sendResponse(exchange, 405, "{\"error\":\"Method not Allowed\"}");
				}
		
			} catch (SQLException e) {
				sendResponse(exchange, 500, "{\"error\":\"Database error: " + e.getMessage() + "\"}");
				e.printStackTrace();
			} finally {
				if (conn != null) try { conn.close(); } catch (Exception e) {}
			}
		}
	}

	private static void handlePostPut(HttpExchange exchange, Connection conn, String path) throws IOException, SQLException {
		String body = new BufferedReader(new InputStreamReader(exchange.getRequestBody()))
			.lines().reduce("", (acc, line) -> acc + line);
		
		if (body.isEmpty()) {
			sendResponse(exchange, 400, "{\"error\":\"Payload Required\"}");
			return;
		}

		JSONObject json;
		try {
			json = new JSONObject(body);
		} catch (Exception e) {
			sendResponse(exchange, 400, "{\"error\":\"Invalid JSON\"}");
			return;
		}
		
		String idStr = path.replaceFirst("/thermostat/?", "");
		boolean hasId = !idStr.isEmpty() && idStr.matches("\\d+");

		if (exchange.getRequestMethod().equals("PUT") && hasId) {
			int id = Integer.parseInt(idStr);
			PreparedStatement stmt = conn.prepareStatement(
				"UPDATE thermostat_data SET temp = ?, status = ? WHERE id = ?");
			stmt.setFloat(1, (float) json.getDouble("temp"));
			stmt.setString(2, json.getString("status"));
			stmt.setInt(3, id);

			int rows = stmt.executeUpdate();
			if (rows > 0) {
				sendResponse(exchange, 200, "{\"status\":\"Updated\", \"id\":" + id + "}");
			} else {
				sendResponse(exchange, 404, "{\"error\":\"ID not found\"}");
			}
		} else if (exchange.getRequestMethod().equals("POST")) {

			PreparedStatement stmt = conn.prepareStatement(
				"Insert into thermostat_data (temp, status) VALUES (?, ?)",
				Statement.RETURN_GENERATED_KEYS);
			stmt.setFloat(1, (float) json.getDouble("temp"));
			stmt.setString(2, json.getString("status"));

			int rows = stmt.executeUpdate();
			if(rows > 0) {
				ResultSet rs = stmt.getGeneratedKeys();
				int newID = rs.next() ? rs.getInt(1) : -1;
				sendResponse(exchange, 201, "{\"id\":" + newID + "}");
			} else {
				sendResponse(exchange, 500, "{\"error\":\"Insert failed\"}");
			}
		} else {
			sendResponse(exchange, 400, "{\"error\":\"Invalid method or missing ID\"}");
		}
	}
	
	
	private static void handleGet(HttpExchange exchange, Connection conn, String path) throws IOException, SQLException {
		if (path.equals("/thermostat") || path.equals("/thermostat/")) {
			PreparedStatement stmt = conn.prepareStatement ("Select id, temp, status, ts from thermostat_data");
			ResultSet rs = stmt.executeQuery();

			StringBuilder json = new StringBuilder("[");
			while (rs.next()) {
				if(json.length() > 1) json.append(",");
				json.append("{");
				json.append("\"id\":").append(rs.getInt("id")).append(",");
				json.append("\"temp\":").append(rs.getFloat("temp")).append(",");
				json.append("\"status\":\"").append(rs.getString("status")).append("\",");
				json.append("\"ts\":\"").append(rs.getTimestamp("ts")).append("\"");
				json.append("}");
			}
			json.append("]");

			sendResponse(exchange, 200, json.toString());
			return;
		}

		String idStr = path.replaceFirst("/thermostat/", "");
		if (!idStr.matches("\\d+")) {
			sendResponse(exchange, 400, "{\"error\":\"Invalid id\"}");
			return;
		}
		
		PreparedStatement stmt = conn.prepareStatement("Select id, temp, status, ts from thermostat_data where id = ?");
		stmt.setInt(1, Integer.parseInt(idStr));
		ResultSet rs = stmt.executeQuery();
		
		if (rs.next()) {
			JSONObject json = new JSONObject();
			json.put("id", rs.getInt("id"));
			json.put("temp", rs.getFloat("temp"));
			json.put("status", rs.getString("status"));
			json.put("ts", rs.getTimestamp("ts").toString());

			sendResponse(exchange, 200, json.toString());
		} else {
			sendResponse(exchange, 404, "{\"error\":\"Not Found\"}");
		}
	}
	
	private static void handleDelete(HttpExchange exchange, Connection conn, String path) throws IOException, SQLException {
		String idStr = path.replaceFirst("/thermostat/", "");
		
		if (!idStr.matches("\\d+")) {
			sendResponse(exchange, 400, "{\"error\":\"Invalid id\"}");
			return;
		}
		
		int id = Integer.parseInt(idStr);
		PreparedStatement stmt = conn.prepareStatement("DELETE FROM thermostat_data WHERE id = ?");
		stmt.setInt(1, id);
		int rows = stmt.executeUpdate();

		if (rows > 0) {
			sendResponse(exchange, 200, "{\"status\":\"Deleted\"}");
		} else {
			sendResponse(exchange, 404, "{\"error\":\"Not Found\"}");
		}		

	}

	private static void sendResponse(HttpExchange exchange, int statusCode, String response) throws IOException {
		byte[] bytes = response.getBytes();
		exchange.getResponseHeaders().add("Content-Type", "application/json");
		exchange.sendResponseHeaders(statusCode, bytes.length);
		OutputStream os = exchange.getResponseBody();
		os.write(bytes);
		os.close();
	}
}

