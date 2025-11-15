package com.example.scoreprediction;

import com.google.gson.Gson;
import com.google.gson.internal.LinkedTreeMap;
import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import com.example.scoreprediction.prediction.PredictionResult;
import com.example.scoreprediction.prediction.TeamComparator;
import com.example.scoreprediction.prediction.PlayerComparator;
import com.example.scoreprediction.prediction.PlayerComparisonResult;

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
    // Simple short-lived cache for /players aggregation
    private static final java.util.concurrent.ConcurrentHashMap<String, CachedItem> playersCache = new java.util.concurrent.ConcurrentHashMap<>();
    // Short-lived cache for squads to avoid repeated fetches when re-selecting same team
    private static final java.util.concurrent.ConcurrentHashMap<String, CachedItem> squadCache = new java.util.concurrent.ConcurrentHashMap<>();
    static class CachedItem {
        final long timeMs;
        final String body;
        CachedItem(long timeMs, String body) { this.timeMs = timeMs; this.body = body; }
    }

    public static void main(String[] args) throws Exception {
        apiKey = System.getenv("API_FOOTBALL_KEY");
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("API_FOOTBALL_KEY is not set");
        }

        HttpServer server = HttpServer.create(new InetSocketAddress(8080), 0);
        server.createContext("/standings", new StandingsHandler());
        server.createContext("/predict", new PredictHandler());
        server.createContext("/predict/compare", new ComparePredictHandler());
		server.createContext("/predict/comparePlayers", new ComparePlayersHandler());
		// New alias for acceptance criteria
		server.createContext("/predict/playerCompare", new ComparePlayersHandler());
        server.createContext("/squad", new SquadHandler());
        server.createContext("/profiles", new ProfilesHandler());
        server.createContext("/playerProfile", new PlayerProfileHandler());
        server.createContext("/playerStats", new PlayerStatsHandler());
        server.createContext("/players", new PlayersHandler());
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
                // Log the raw JSON (truncated) to confirm structure
                try {
                    String preview = json == null ? "null" : (json.length() > 2000 ? json.substring(0, 2000) + "..." : json);
                    System.out.println("Standings raw JSON preview: " + preview);
                } catch (Exception ignored) {}

                sendJson(exchange, 200, json);
            } catch (Exception e) {
                String message = e.getMessage() == null ? "Internal Server Error" : e.getMessage();
                sendJson(exchange, 500, "{\"error\":\"" + escape(message) + "\"}");
            }
        }
    }

	// GET /predict/comparePlayers?league={id}&season={year}&team1={teamId1}&player1={playerId1}&team2={teamId2}&player2={playerId2}
	static class ComparePlayersHandler implements HttpHandler {
		@Override
		public void handle(HttpExchange exchange) throws IOException {
			addCorsAndContentType(exchange.getResponseHeaders());
			exchange.getResponseHeaders().set("Cache-Control", "no-store");

			if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
				sendJson(exchange, 405, "{\"error\":\"Method Not Allowed\"}");
				return;
			}

			try {
				Map<String, String> q = parseQueryParams(exchange.getRequestURI());
				String leagueParam = q.get("league");
				String seasonParam = q.get("season");
				String team1Param = q.get("team1");
				String player1Param = q.get("player1");
				String team2Param = q.get("team2");
				String player2Param = q.get("player2");

				if (leagueParam == null || seasonParam == null || team1Param == null || player1Param == null || team2Param == null || player2Param == null
						|| leagueParam.isBlank() || seasonParam.isBlank() || team1Param.isBlank() || player1Param.isBlank() || team2Param.isBlank() || player2Param.isBlank()) {
					sendJson(exchange, 400, "{\"error\":\"Missing required query params: league, season, team1, player1, team2, player2\"}");
					return;
				}

				int leagueId, season, team1, team2, player1, player2;
				try {
					leagueId = Integer.parseInt(leagueParam);
					season = Integer.parseInt(seasonParam);
					team1 = Integer.parseInt(team1Param);
					player1 = Integer.parseInt(player1Param);
					team2 = Integer.parseInt(team2Param);
					player2 = Integer.parseInt(player2Param);
				} catch (NumberFormatException nfe) {
					sendJson(exchange, 400, "{\"error\":\"league, season, team1, player1, team2, player2 must be integers\"}");
					return;
				}
				if (leagueId <= 0 || season <= 0 || team1 <= 0 || team2 <= 0 || player1 <= 0 || player2 <= 0) {
					sendJson(exchange, 400, "{\"error\":\"All parameters must be positive integers\"}");
					return;
				}

				PlayerComparator comparator = new PlayerComparator(apiKey, API_HOST);
				PlayerComparator.PlayerCompareArgs args = new PlayerComparator.PlayerCompareArgs();
				args.leagueId = leagueId;
				args.season = season;
				args.teamId1 = team1;
				args.teamId2 = team2;
				args.playerId1 = player1;
				args.playerId2 = player2;
				args.role = null; // derive from API

				PlayerComparisonResult result;
				try {
					result = comparator.compare(args);
				} catch (IllegalArgumentException bad) {
					String msg = bad.getMessage() == null ? "Bad Request" : bad.getMessage();
					sendJson(exchange, 400, "{\"error\":\"" + escape(msg) + "\"}");
					return;
				}
				Gson gson = new Gson();
				String json = gson.toJson(result);
				sendJson(exchange, 200, json);
			} catch (Exception e) {
				String message = e.getMessage() == null ? "Internal Server Error" : e.getMessage();
				sendJson(exchange, 500, "{\"error\":\"" + escape(message) + "\"}");
			}
		}
	}

    // GET /predict/compare?league=xxx&season=xxx&team1=xxx&team2=xxx&groupstage={true|false}
    static class ComparePredictHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            addCorsAndContentType(exchange.getResponseHeaders());
            exchange.getResponseHeaders().set("Cache-Control", "no-store");

            if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendJson(exchange, 405, "{\"error\":\"Method Not Allowed\"}");
                return;
            }

            try {
                Map<String, String> q = parseQueryParams(exchange.getRequestURI());
                String leagueParam = q.get("league");
                String seasonParam = q.get("season");
                String team1Param = q.get("team1");
                String team2Param = q.get("team2");
                String groupStageParam = q.get("groupstage");

                if (leagueParam == null || seasonParam == null || team1Param == null || team2Param == null
                        || leagueParam.isBlank() || seasonParam.isBlank() || team1Param.isBlank() || team2Param.isBlank()) {
                    sendJson(exchange, 400, "{\"error\":\"Missing required query params: league, season, team1, team2\"}");
                    return;
                }

                int leagueId, season, team1, team2;
                try {
                    leagueId = Integer.parseInt(leagueParam);
                    season = Integer.parseInt(seasonParam);
                    team1 = Integer.parseInt(team1Param);
                    team2 = Integer.parseInt(team2Param);
                } catch (NumberFormatException nfe) {
                    sendJson(exchange, 400, "{\"error\":\"league, season, team1, team2 must be integers\"}");
                    return;
                }
                boolean isGroupStage = groupStageParam != null && ("true".equalsIgnoreCase(groupStageParam) || "1".equals(groupStageParam));

                TeamComparator comparator = new TeamComparator(apiKey, API_HOST);
                TeamComparator.CompareArgs args = new TeamComparator.CompareArgs();
                args.leagueId = leagueId;
                args.season = season;
                args.teamId1 = team1;
                args.teamId2 = team2;
                args.isGroupStage = isGroupStage;

                PredictionResult result = comparator.compareTeams(args);
                Gson gson = new Gson();
                String json = gson.toJson(result);
                sendJson(exchange, 200, json);
            } catch (Exception e) {
                String message = e.getMessage() == null ? "Internal Server Error" : e.getMessage();
                sendJson(exchange, 500, "{\"error\":\"" + escape(message) + "\"}");
            }
        }
    }

    static class PlayersHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            addCorsAndContentType(exchange.getResponseHeaders());
            exchange.getResponseHeaders().set("Cache-Control", "public, max-age=90");

            if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendJson(exchange, 405, "{\"error\":\"Method Not Allowed\"}");
                return;
            }

            try {
                Map<String, String> queryParams = parseQueryParams(exchange.getRequestURI());
                String teamParam = queryParams.get("team");
                String seasonParam = queryParams.get("season");

                if (teamParam == null || teamParam.isBlank() || seasonParam == null || seasonParam.isBlank()) {
                    sendJson(exchange, 400, "{\"error\":\"Missing required query params: team, season\"}");
                    return;
                }

                // Validate numeric inputs
                try {
                    Integer.parseInt(teamParam);
                    Integer.parseInt(seasonParam);
                } catch (NumberFormatException nfe) {
                    sendJson(exchange, 400, "{\"error\":\"team and season must be integers\"}");
                    return;
                }

                // Short in-memory cache to reduce repeated aggregations
                String cacheKey = teamParam + ":" + seasonParam;
                CachedItem cached = playersCache.get(cacheKey);
                long now = System.currentTimeMillis();
                if (cached != null && (now - cached.timeMs) < 90_000L && cached.body != null) {
                    sendJson(exchange, 200, cached.body);
                    return;
                }

                // Aggregate all pages from /players endpoint
                com.google.gson.Gson gson = new com.google.gson.Gson();
                java.util.List<Object> allPlayers = new java.util.ArrayList<>();
                int page = 1;
                int total = 1;
                do {
                    String url = "https://" + API_HOST + "/players?team=" + encode(teamParam) + "&season=" + encode(seasonParam) + "&page=" + page;
                    System.out.println("Players request => url=" + url);
                    LeagueRequest leagueRequest = new LeagueRequest(new FullResponse());
                    String json = leagueRequest.setRequset(url, apiKey, API_HOST);

                    @SuppressWarnings("unchecked")
                    java.util.Map<String, Object> obj = gson.fromJson(json, java.util.Map.class);
                    if (obj == null) obj = new java.util.HashMap<>();
                    @SuppressWarnings("unchecked")
                    java.util.Map<String, Object> paging = (java.util.Map<String, Object>) obj.get("paging");
                    if (paging != null) {
                        Object curObj = paging.get("current");
                        Object totObj = paging.get("total");
                        int cur = (curObj instanceof Number) ? ((Number) curObj).intValue() : page;
                        total = (totObj instanceof Number) ? ((Number) totObj).intValue() : 1;
                        page = cur; // ensure consistency
                    } else {
                        total = 1;
                    }
                    @SuppressWarnings("unchecked")
                    java.util.List<Object> respList = (java.util.List<Object>) obj.get("response");
                    if (respList != null) {
                        allPlayers.addAll(respList);
                    }
                    page++;
                } while (page <= total);

                java.util.Map<String, Object> out = new java.util.HashMap<>();
                out.put("results", allPlayers.size());
                out.put("paging", java.util.Map.of("current", 1, "total", 1));
                out.put("response", allPlayers);

                String outJson = gson.toJson(out);
                playersCache.put(cacheKey, new CachedItem(now, outJson));
                sendJson(exchange, 200, outJson);
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

                boolean championsLeague = LeagueUtils.isChampionsLeague(league);
                boolean europaLeague = LeagueUtils.isEuropaLeague(league);
                boolean worldCup = LeagueUtils.isWorldCup(league);
                int seasonInt;
                try {
                    seasonInt = Integer.parseInt(season);
                } catch (NumberFormatException nfe) {
                    sendJson(exchange, 400, "{\"error\":\"season must be an integer\"}");
                    return;
                }

                // For Champions League and Europa League before 2024, group is required
                if (championsLeague && seasonInt < 2024) {
                    if (group == null || group.isBlank()) {
                        sendJson(exchange, 400, "{\"error\":\"group is required for Champions League before 2024\"}");
                        return;
                    }
                } else if (europaLeague && seasonInt < 2024) {
                    if (group == null || group.isBlank()) {
                        sendJson(exchange, 400, "{\"error\":\"group is required for Europa League before 2024\"}");
                        return;
                    }
                } else if (worldCup) {
                    if (group == null || group.isBlank()) {
                        sendJson(exchange, 400, "{\"error\":\"group is required for World Cup\"}");
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
                if (worldCup || (championsLeague && seasonInt < 2024) || (europaLeague && seasonInt < 2024)) {
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

                @SuppressWarnings("unchecked")
                java.util.Map<String, Object> teamObj1 = (java.util.Map<String, Object>) standing.get(team1Idx).get("team");
                @SuppressWarnings("unchecked")
                java.util.Map<String, Object> teamObj2 = (java.util.Map<String, Object>) standing.get(team2Idx).get("team");
                String teamName1 = String.valueOf(teamObj1.get("name"));
                String teamName2 = String.valueOf(teamObj2.get("name"));
                int teamId1 = (teamObj1.get("id") instanceof Number) ? ((Number) teamObj1.get("id")).intValue() : 0;
                int teamId2 = (teamObj2.get("id") instanceof Number) ? ((Number) teamObj2.get("id")).intValue() : 0;

                boolean isGroupStage = worldCup || (championsLeague && seasonInt < 2024) || (europaLeague && seasonInt < 2024);

                TeamComparator comparator = new TeamComparator(apiKey, API_HOST);
                TeamComparator.CompareArgs args = new TeamComparator.CompareArgs();
                args.leagueId = Integer.parseInt(league);
                args.season = seasonInt;
                args.teamId1 = teamId1;
                args.teamId2 = teamId2;
                args.isGroupStage = isGroupStage;

                PredictionResult result = comparator.compareTeams(args);
                // Build backward-compatible response for existing UI, while returning full new fields
                java.util.Map<String, Object> out = new java.util.HashMap<>();
                out.put("winner", result.winner == null ? null : result.winner.name());
                out.put("scoreTeam1", result.scoreTeam1);
                out.put("scoreTeam2", result.scoreTeam2);
                out.put("breakdown", result.breakdown);
                out.put("rawDataRefs", result.rawDataRefs);
                // compatibility fields expected by app.js
                out.put("league", league);
                out.put("season", season);
                out.put("team1Index", team1IndexOneBased);
                out.put("team2Index", team2IndexOneBased);
                out.put("team1Name", teamName1);
                out.put("team2Name", teamName2);
                out.put("score1", result.scoreTeam1);
                out.put("score2", result.scoreTeam2);
                String outcome = "draw";
                String winnerName = null;
                if (result.winner == PredictionResult.Winner.TEAM1) { outcome = "team1"; winnerName = teamName1; }
                else if (result.winner == PredictionResult.Winner.TEAM2) { outcome = "team2"; winnerName = teamName2; }
                out.put("result", outcome);
                out.put("winnerName", winnerName);
                Gson outGson = new Gson();
                String json = outGson.toJson(out);
                sendJson(exchange, 200, json);
            } catch (Exception e) {
                String message = e.getMessage() == null ? "Internal Server Error" : e.getMessage();
                sendJson(exchange, 500, "{\"error\":\"" + escape(message) + "\"}");
            }
        }
    }

    static class SquadHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            addCorsAndContentType(exchange.getResponseHeaders());

            if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendJson(exchange, 405, "{\"error\":\"Method Not Allowed\"}");
                return;
            }

            try {
                Map<String, String> queryParams = parseQueryParams(exchange.getRequestURI());
                String teamParam = queryParams.get("team");
                String season = queryParams.get("season"); // optional, squads endpoint is current-season oriented

                if (teamParam == null || teamParam.isBlank()) {
                    sendJson(exchange, 400, "{\"error\":\"Missing required query param: team\"}");
                    return;
                }

                // Validate team is integer-like to avoid accidental misuse
                try {
                    Integer.parseInt(teamParam);
                } catch (NumberFormatException nfe) {
                    sendJson(exchange, 400, "{\"error\":\"team must be an integer\"}");
                    return;
                }

                // API-Football squads endpoint (current season): https://v3.football.api-sports.io/players/squads?team={TEAM_ID}
                // Season is not supported on this endpoint; we ignore if provided, but keep behavior consistent.
                String url = "https://" + API_HOST + "/players/squads?team=" + encode(teamParam);

                // Log the outgoing URL and season hint for quick manual verification
                System.out.println("Squad request => url=" + url + (season == null ? "" : (", season=" + season)));

                // 90-second cache to dedupe repeated squad requests (e.g., when reselecting same team)
                String cacheKey = "squad:" + teamParam;
                long now = System.currentTimeMillis();
                CachedItem cached = squadCache.get(cacheKey);
                String json;
                if (cached != null && (now - cached.timeMs) < 90_000L && cached.body != null) {
                    json = cached.body;
                } else {
                    LeagueRequest leagueRequest = new LeagueRequest(new FullResponse());
                    json = leagueRequest.setRequset(url, apiKey, API_HOST);
                    squadCache.put(cacheKey, new CachedItem(now, json));
                }

                // Basic sampling/logging of response size
                System.out.println("Squad response length=" + (json == null ? 0 : json.length()));

                sendJson(exchange, 200, json);
            } catch (Exception e) {
                String message = e.getMessage() == null ? "Internal Server Error" : e.getMessage();
                sendJson(exchange, 500, "{\"error\":\"" + escape(message) + "\"}");
            }
        }
    }

    static class ProfilesHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            addCorsAndContentType(exchange.getResponseHeaders());

            if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendJson(exchange, 405, "{\"error\":\"Method Not Allowed\"}");
                return;
            }

            try {
                Map<String, String> queryParams = parseQueryParams(exchange.getRequestURI());
                String playerParam = queryParams.get("player");

                if (playerParam == null || playerParam.isBlank()) {
                    sendJson(exchange, 400, "{\"error\":\"Missing required query param: player\"}");
                    return;
                }

                // Validate player id is integer-like
                try {
                    Integer.parseInt(playerParam);
                } catch (NumberFormatException nfe) {
                    sendJson(exchange, 400, "{\"error\":\"player must be an integer\"}");
                    return;
                }

                // API-Football player profiles endpoint: https://" + API_HOST + "/players/profiles?player={PLAYER_ID}
                String url = "https://" + API_HOST + "/players/profiles?player=" + encode(playerParam);
                System.out.println("Profiles request => url=" + url);

                LeagueRequest leagueRequest = new LeagueRequest(new FullResponse());
                String json = leagueRequest.setRequset(url, apiKey, API_HOST);

                System.out.println("Profiles response length=" + (json == null ? 0 : json.length()));
                sendJson(exchange, 200, json);
            } catch (Exception e) {
                String message = e.getMessage() == null ? "Internal Server Error" : e.getMessage();
                sendJson(exchange, 500, "{\"error\":\"" + escape(message) + "\"}");
            }
        }
    }

    // GET /playerProfile?player={ID}
    static class PlayerProfileHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            addCorsAndContentType(exchange.getResponseHeaders());
            exchange.getResponseHeaders().set("Cache-Control", "public, max-age=90");

            if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendJson(exchange, 405, "{\"error\":\"Method Not Allowed\"}");
                return;
            }

            try {
                Map<String, String> queryParams = parseQueryParams(exchange.getRequestURI());
                String playerParam = queryParams.get("player");

                if (playerParam == null || playerParam.isBlank()) {
                    sendJson(exchange, 400, "{\"error\":\"Missing required query param: player\"}");
                    return;
                }
                try {
                    Integer.parseInt(playerParam);
                } catch (NumberFormatException nfe) {
                    sendJson(exchange, 400, "{\"error\":\"player must be an integer\"}");
                    return;
                }

                String url = "https://" + API_HOST + "/players/profiles?player=" + encode(playerParam);
                System.out.println("PlayerProfile request => url=" + url);

                LeagueRequest leagueRequest = new LeagueRequest(new FullResponse());
                String json = leagueRequest.setRequset(url, apiKey, API_HOST);

                System.out.println("PlayerProfile response length=" + (json == null ? 0 : json.length()));
                sendJson(exchange, 200, json);
            } catch (Exception e) {
                String message = e.getMessage() == null ? "Internal Server Error" : e.getMessage();
                sendJson(exchange, 500, "{\"error\":\"" + escape(message) + "\"}");
            }
        }
    }

    // GET /playerStats?player={ID}&season={YEAR}
    static class PlayerStatsHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            addCorsAndContentType(exchange.getResponseHeaders());
            exchange.getResponseHeaders().set("Cache-Control", "public, max-age=90");

            if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendJson(exchange, 405, "{\"error\":\"Method Not Allowed\"}");
                return;
            }

            try {
                Map<String, String> queryParams = parseQueryParams(exchange.getRequestURI());
                String playerParam = queryParams.get("player");
                String seasonParam = queryParams.get("season");

                if (playerParam == null || playerParam.isBlank() || seasonParam == null || seasonParam.isBlank()) {
                    sendJson(exchange, 400, "{\"error\":\"Missing required query params: player, season\"}");
                    return;
                }
                try {
                    Integer.parseInt(playerParam);
                    Integer.parseInt(seasonParam);
                } catch (NumberFormatException nfe) {
                    sendJson(exchange, 400, "{\"error\":\"player and season must be integers\"}");
                    return;
                }

                String url = "https://" + API_HOST + "/players?id=" + encode(playerParam) + "&season=" + encode(seasonParam);
                System.out.println("PlayerStats request => url=" + url);

                LeagueRequest leagueRequest = new LeagueRequest(new FullResponse());
                String json = leagueRequest.setRequset(url, apiKey, API_HOST);

                System.out.println("PlayerStats response length=" + (json == null ? 0 : json.length()));
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
                // Redirect root to landing page
                exchange.getResponseHeaders().set("Location", "/landing.html");
                exchange.getResponseHeaders().set("Cache-Control", "no-store");
                exchange.sendResponseHeaders(302, -1);
                return;
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
				// Add served-from header and caching
				String servedFrom = fsPath.toAbsolutePath().toString();
				exchange.getResponseHeaders().set("X-Served-From", servedFrom);
				// Log important static files
				String pLower = path.toLowerCase();
				if (pLower.endsWith("/player.html") || pLower.endsWith("player.html") || pLower.endsWith("/styles.css") || pLower.endsWith("styles.css")) {
					System.out.println("Static serve (fs): " + path + " -> " + servedFrom);
				}
				if (path.toLowerCase().endsWith(".html") || path.toLowerCase().endsWith(".htm")) {
					exchange.getResponseHeaders().set("Cache-Control", "no-store");
				} else {
					exchange.getResponseHeaders().set("Cache-Control", "public, max-age=90");
				}
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
				// Add served-from header and caching
				String servedFrom = "classpath:" + resourcePath;
				exchange.getResponseHeaders().set("X-Served-From", servedFrom);
				// Log important static files
				String pLower = path.toLowerCase();
				if (pLower.endsWith("/player.html") || pLower.endsWith("player.html") || pLower.endsWith("/styles.css") || pLower.endsWith("styles.css")) {
					System.out.println("Static serve (cp): " + path + " -> " + servedFrom);
				}
				if (path.toLowerCase().endsWith(".html") || path.toLowerCase().endsWith(".htm")) {
					exchange.getResponseHeaders().set("Cache-Control", "no-store");
				} else {
					exchange.getResponseHeaders().set("Cache-Control", "public, max-age=90");
				}
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

    // (removed unused toDouble helper)
}


