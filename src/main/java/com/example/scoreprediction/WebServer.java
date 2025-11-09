package com.example.scoreprediction;

import com.google.gson.Gson;
import com.google.gson.internal.LinkedTreeMap;
import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public class WebServer {

    private static final String API_HOST = "v3.football.api-sports.io";
    private static String apiKey;

    public static void main(String[] args) throws Exception {
        apiKey = System.getenv("API_FOOTBALL_KEY");
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("API_FOOTBALL_KEY is not set");
        }

        HttpServer server = HttpServer.create(new InetSocketAddress(8080), 0);
        server.createContext("/standings", new StandingsHandler());
        server.createContext("/predict", new PredictHandler());
        server.createContext("/", new StaticFileHandler());
        server.setExecutor(null);
        server.start();
        System.out.println("Server started on http://localhost:8080");
    }

    static class StandingsHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            addCorsAndContentType(exchange.getResponseHeaders());

            if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendJson(exchange, 405, "{\"error\":\"Method Not Allowed\"}");
                return;
            }

            try {
                Map<String, String> queryParams = parseQueryParams(exchange.getRequestURI());
                String league = queryParams.get("league");
                String season = queryParams.get("season");

                if (league == null || league.isBlank() || season == null || season.isBlank()) {
                    sendJson(exchange, 400, "{\"error\":\"Missing required query params: league, season\"}");
                    return;
                }

                String url = "https://" + API_HOST + "/standings?season=" + encode(season) + "&league=" + encode(league);

                FullResponse fullResponse = new FullResponse();
                LeagueRequest leagueRequest = new LeagueRequest(fullResponse);
                String json = leagueRequest.setRequset(url, apiKey, API_HOST);

                sendJson(exchange, 200, json);
            } catch (Exception e) {
                String message = e.getMessage() == null ? "Internal Server Error" : e.getMessage();
                sendJson(exchange, 500, "{\"error\":\"" + escape(message) + "\"}");
            }
        }
    }

    static class PredictHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            addCorsAndContentType(exchange.getResponseHeaders());

            if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendJson(exchange, 405, "{\"error\":\"Method Not Allowed\"}");
                return;
            }

            try {
                Map<String, String> queryParams = parseQueryParams(exchange.getRequestURI());
                String league = queryParams.get("league");
                String season = queryParams.get("season");
                String team1Param = queryParams.get("team1");
                String team2Param = queryParams.get("team2");
                String group = queryParams.get("group");

                if (league == null || league.isBlank() ||
                        season == null || season.isBlank() ||
                        team1Param == null || team1Param.isBlank() ||
                        team2Param == null || team2Param.isBlank()) {
                    sendJson(exchange, 400, "{\"error\":\"Missing required query params: league, season, team1, team2\"}");
                    return;
                }

                boolean championsLeague = "2".equals(league);
                int seasonInt;
                try {
                    seasonInt = Integer.parseInt(season);
                } catch (NumberFormatException nfe) {
                    sendJson(exchange, 400, "{\"error\":\"season must be an integer\"}");
                    return;
                }

                // For Champions League before 2024, group is required
                if (championsLeague && seasonInt < 2024) {
                    if (group == null || group.isBlank()) {
                        sendJson(exchange, 400, "{\"error\":\"group is required for Champions League before 2024\"}");
                        return;
                    }
                }

                int team1IndexOneBased;
                int team2IndexOneBased;
                try {
                    team1IndexOneBased = Integer.parseInt(team1Param);
                    team2IndexOneBased = Integer.parseInt(team2Param);
                } catch (NumberFormatException nfe) {
                    sendJson(exchange, 400, "{\"error\":\"team1 and team2 must be integers\"}");
                    return;
                }

                if (team1IndexOneBased <= 0 || team2IndexOneBased <= 0) {
                    sendJson(exchange, 400, "{\"error\":\"team1 and team2 must be positive (1-based)\"}");
                    return;
                }

                String url = "https://" + API_HOST + "/standings?season=" + encode(season) + "&league=" + encode(league);

                LeagueRequest leagueRequest = new LeagueRequest(new FullResponse());
                String body = leagueRequest.setRequset(url, apiKey, API_HOST);

                Gson gson = new Gson();
                FullResponse fullResponse = gson.fromJson(body, FullResponse.class);
                ResponseObject responseObject = fullResponse.getResponse() != null && !fullResponse.getResponse().isEmpty()
                        ? fullResponse.getResponse().get(0) : null;
                if (responseObject == null || responseObject.getLeague() == null || responseObject.getLeague().getStandings() == null) {
                    sendJson(exchange, 502, "{\"error\":\"Unexpected API response\"}");
                    return;
                }

                // Log predict parameters
                System.out.println("Predict params => league=" + league + ", season=" + season + ", group=" + (group == null ? "null" : group) + ", teamA=" + team1Param + ", teamB=" + team2Param);

                // Determine which standings block to use
                Object[] standingsBlocks = responseObject.getLeague().getStandings();
                if (standingsBlocks.length == 0) {
                    sendJson(exchange, 502, "{\"error\":\"Unexpected standings format\"}");
                    return;
                }

                ArrayList<LinkedTreeMap<String, Object>> standing;
                if (championsLeague && seasonInt < 2024) {
                    // Map group letter to index 0..7
                    int groupIndex = -1;
                    if (group != null) {
                        String g = group.trim().toUpperCase();
                        if (g.length() == 1) {
                            char c = g.charAt(0);
                            if (c >= 'A' && c <= 'H') {
                                groupIndex = c - 'A';
                            }
                        }
                    }
                    if (groupIndex < 0 || groupIndex >= standingsBlocks.length) {
                        sendJson(exchange, 400, "{\"error\":\"Invalid group. Expected A-H\"}");
                        return;
                    }
                    if (!(standingsBlocks[groupIndex] instanceof ArrayList)) {
                        sendJson(exchange, 502, "{\"error\":\"Unexpected standings format for selected group\"}");
                        return;
                    }
                    @SuppressWarnings("unchecked")
                    ArrayList<LinkedTreeMap<String, Object>> s = (ArrayList<LinkedTreeMap<String, Object>>) standingsBlocks[groupIndex];
                    standing = s;
                } else {
                    if (!(standingsBlocks[0] instanceof ArrayList)) {
                        sendJson(exchange, 502, "{\"error\":\"Unexpected standings format\"}");
                        return;
                    }
                    @SuppressWarnings("unchecked")
                    ArrayList<LinkedTreeMap<String, Object>> s = (ArrayList<LinkedTreeMap<String, Object>>) standingsBlocks[0];
                    standing = s;
                }

                int maxIndex = standing.size();
                int team1Idx = team1IndexOneBased - 1;
                int team2Idx = team2IndexOneBased - 1;
                if (team1Idx < 0 || team2Idx < 0 || team1Idx >= maxIndex || team2Idx >= maxIndex) {
                    sendJson(exchange, 400, "{\"error\":\"team1 and team2 must be between 1 and " + maxIndex + "\"}");
                    return;
                }

                LinkedTreeMap<String, Object> teamObj1 = (LinkedTreeMap<String, Object>) standing.get(team1Idx).get("team");
                LinkedTreeMap<String, Object> teamObj2 = (LinkedTreeMap<String, Object>) standing.get(team2Idx).get("team");
                LinkedTreeMap<String, Object> allOfTeam1 = (LinkedTreeMap<String, Object>) standing.get(team1Idx).get("all");
                LinkedTreeMap<String, Object> allOfTeam2 = (LinkedTreeMap<String, Object>) standing.get(team2Idx).get("all");
                LinkedTreeMap<String, Object> goalsOfTeam1 = (LinkedTreeMap<String, Object>) ((LinkedTreeMap<?, ?>) standing.get(team1Idx).get("all")).get("goals");
                LinkedTreeMap<String, Object> goalsOfTeam2 = (LinkedTreeMap<String, Object>) ((LinkedTreeMap<?, ?>) standing.get(team2Idx).get("all")).get("goals");

                double playedTeam1 = toDouble(allOfTeam1.get("played"));
                double playedTeam2 = toDouble(allOfTeam2.get("played"));
                double winTeam1 = toDouble(allOfTeam1.get("win"));
                double winTeam2 = toDouble(allOfTeam2.get("win"));
                double drawTeam1 = toDouble(allOfTeam1.get("draw"));
                double drawTeam2 = toDouble(allOfTeam2.get("draw"));
                double loseTeam1 = toDouble(allOfTeam1.get("lose"));
                double loseTeam2 = toDouble(allOfTeam2.get("lose"));
                double goalsForTeam1 = toDouble(goalsOfTeam1.get("for"));
                double goalsForTeam2 = toDouble(goalsOfTeam2.get("for"));
                double goalsAgainstTeam1 = toDouble(goalsOfTeam1.get("against"));
                double goalsAgainstTeam2 = toDouble(goalsOfTeam2.get("against"));
                double pointsTeam1 = toDouble(standing.get(team1Idx).get("points"));
                double pointsTeam2 = toDouble(standing.get(team2Idx).get("points"));

                int totalPointsTeam1 = 0;
                int totalPointsTeam2 = 0;

                if (playedTeam1 == playedTeam2) { totalPointsTeam1++; totalPointsTeam2++; }
                if (playedTeam1 > playedTeam2) { totalPointsTeam2++; }
                if (playedTeam1 < playedTeam2) { totalPointsTeam1++; }

                if (winTeam1 == winTeam2) { totalPointsTeam1++; totalPointsTeam2++; }
                if (winTeam1 > winTeam2) { totalPointsTeam1++; }
                if (winTeam1 < winTeam2) { totalPointsTeam2++; }

                if (drawTeam1 == drawTeam2) { totalPointsTeam1++; totalPointsTeam2++; }
                if (drawTeam1 > drawTeam2) { totalPointsTeam2++; }
                if (drawTeam1 < drawTeam2) { totalPointsTeam1++; }

                if (loseTeam1 > loseTeam2) { totalPointsTeam2++; }
                if (loseTeam1 < loseTeam2) { totalPointsTeam1++; }

                if (goalsForTeam1 == goalsForTeam2) {
                    if (goalsForTeam1 > 20 || goalsForTeam2 > 20) { totalPointsTeam1 += 3; totalPointsTeam2 += 3; }
                    else { totalPointsTeam1 += 2; totalPointsTeam2 += 2; }
                }
                if (goalsForTeam1 > goalsForTeam2) { totalPointsTeam1 += (goalsForTeam1 > 20 ? 3 : 2); }
                if (goalsForTeam1 < goalsForTeam2) { totalPointsTeam2 += (goalsForTeam2 > 20 ? 3 : 2); }

                if (goalsAgainstTeam1 == goalsAgainstTeam2) {
                    if (goalsAgainstTeam1 < 8 || goalsAgainstTeam2 < 8) { totalPointsTeam1 += 2; totalPointsTeam2 += 2; }
                    else { totalPointsTeam1++; totalPointsTeam2++; }
                }
                if (goalsAgainstTeam1 > goalsAgainstTeam2) { totalPointsTeam2 += (goalsAgainstTeam2 < 8 ? 2 : 1); }
                if (goalsAgainstTeam1 < goalsAgainstTeam2) { totalPointsTeam1 += (goalsAgainstTeam1 < 8 ? 2 : 1); }

                if (pointsTeam1 == pointsTeam2) { totalPointsTeam1 += 3; totalPointsTeam2 += 3; }
                if (pointsTeam1 > pointsTeam2) { totalPointsTeam1 += 3; }
                if (pointsTeam1 < pointsTeam2) { totalPointsTeam2 += 3; }

                String teamName1 = String.valueOf(teamObj1.get("name"));
                String teamName2 = String.valueOf(teamObj2.get("name"));

                String outcome;
                String winnerName = null;
                if (totalPointsTeam1 == totalPointsTeam2) {
                    outcome = "draw";
                } else if (totalPointsTeam1 > totalPointsTeam2) {
                    outcome = "team1";
                    winnerName = teamName1;
                } else {
                    outcome = "team2";
                    winnerName = teamName2;
                }

                String json = "{"
                        + "\"league\":\"" + escape(league) + "\","
                        + "\"season\":\"" + escape(season) + "\","
                        + "\"team1Index\":" + team1IndexOneBased + ","
                        + "\"team2Index\":" + team2IndexOneBased + ","
                        + "\"team1Name\":\"" + escape(teamName1) + "\","
                        + "\"team2Name\":\"" + escape(teamName2) + "\","
                        + "\"score1\":" + totalPointsTeam1 + ","
                        + "\"score2\":" + totalPointsTeam2 + ","
                        + "\"result\":\"" + outcome + "\","
                        + "\"winnerName\":" + (winnerName == null ? "null" : "\"" + escape(winnerName) + "\"")
                        + "}";

                sendJson(exchange, 200, json);
            } catch (Exception e) {
                String message = e.getMessage() == null ? "Internal Server Error" : e.getMessage();
                sendJson(exchange, 500, "{\"error\":\"" + escape(message) + "\"}");
            }
        }
    }

    static class StaticFileHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                byte[] bytes = "{\"error\":\"Method Not Allowed\"}".getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(405, bytes.length);
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(bytes);
                }
                return;
            }

            String path = exchange.getRequestURI().getPath();
            if (path == null || path.isBlank() || "/".equals(path)) {
                path = "/index.html";
            }
            // prevent simple path traversal
            if (path.contains("..")) {
                byte[] bytes = "{\"error\":\"Bad Request\"}".getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(400, bytes.length);
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(bytes);
                }
                return;
            }

            String resourcePath = "public" + path;
            if (resourcePath.startsWith("/")) {
                resourcePath = resourcePath.substring(1);
            }

			// Prefer filesystem during development to reflect latest changes,
			// fall back to classpath resource if not found.
			java.nio.file.Path fsPath = java.nio.file.Paths.get("src", "main", "resources", "public", path.startsWith("/") ? path.substring(1) : path);
			if (java.nio.file.Files.exists(fsPath)) {
				byte[] body = java.nio.file.Files.readAllBytes(fsPath);
				String contentType = guessContentType(path);
				exchange.getResponseHeaders().set("Content-Type", contentType);
				exchange.getResponseHeaders().set("Cache-Control", "no-store, no-cache, must-revalidate");
				try {
					java.nio.file.attribute.FileTime lm = java.nio.file.Files.getLastModifiedTime(fsPath);
					exchange.getResponseHeaders().set("Last-Modified", String.valueOf(lm.toMillis()));
				} catch (Exception ignored) {}
				exchange.sendResponseHeaders(200, body.length);
				try (OutputStream os = exchange.getResponseBody()) {
					os.write(body);
				}
				return;
			}

			try (InputStream is = WebServer.class.getClassLoader().getResourceAsStream(resourcePath)) {
				if (is == null) {
					byte[] bytes = "Not Found".getBytes(StandardCharsets.UTF_8);
					exchange.getResponseHeaders().set("Content-Type", "text/plain; charset=utf-8");
					exchange.sendResponseHeaders(404, bytes.length);
					try (OutputStream os = exchange.getResponseBody()) {
						os.write(bytes);
					}
					return;
				}
				byte[] body = is.readAllBytes();
				String contentType = guessContentType(path);
				exchange.getResponseHeaders().set("Content-Type", contentType);
				exchange.getResponseHeaders().set("Cache-Control", "no-store, no-cache, must-revalidate");
				exchange.sendResponseHeaders(200, body.length);
				try (OutputStream os = exchange.getResponseBody()) {
					os.write(body);
				}
			}
        }

        private String guessContentType(String path) {
            String p = path.toLowerCase();
            if (p.endsWith(".html") || p.endsWith(".htm")) return "text/html; charset=utf-8";
            if (p.endsWith(".css")) return "text/css; charset=utf-8";
            if (p.endsWith(".js")) return "application/javascript; charset=utf-8";
            if (p.endsWith(".svg")) return "image/svg+xml";
            if (p.endsWith(".png")) return "image/png";
            if (p.endsWith(".jpg") || p.endsWith(".jpeg")) return "image/jpeg";
            if (p.endsWith(".ico")) return "image/x-icon";
            return "application/octet-stream";
        }
    }

    private static void addCorsAndContentType(Headers headers) {
        headers.set("Content-Type", "application/json");
        headers.set("Access-Control-Allow-Origin", "*");
    }

    private static void sendJson(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    private static Map<String, String> parseQueryParams(URI uri) {
        Map<String, String> map = new HashMap<>();
        String query = uri.getRawQuery();
        if (query == null || query.isEmpty()) {
            return map;
        }
        String[] pairs = query.split("&");
        for (String pair : pairs) {
            int idx = pair.indexOf('=');
            if (idx > -1) {
                String key = decode(pair.substring(0, idx));
                String value = decode(pair.substring(idx + 1));
                map.put(key, value);
            } else {
                String key = decode(pair);
                map.put(key, "");
            }
        }
        return map;
    }

    private static String decode(String value) {
        return URLDecoder.decode(value, StandardCharsets.UTF_8);
    }

    private static String encode(String value) {
        return value.replace(" ", "%20");
    }

    private static String escape(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private static double toDouble(Object o) {
        if (o == null) return 0d;
        if (o instanceof Number) return ((Number) o).doubleValue();
        try {
            return Double.parseDouble(o.toString());
        } catch (Exception e) {
            return 0d;
        }
    }
}


