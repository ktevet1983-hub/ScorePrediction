package com.example.scoreprediction.compare;

import com.example.scoreprediction.FullResponse;
import com.example.scoreprediction.LeagueRequest;
import com.google.gson.Gson;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class PlayerComparisonService {

	private static final long CACHE_TTL_MS = 90_000L;

	private final String apiKey;
	private final String apiHost;
	private final Gson gson = new Gson();

	private static class CacheEntry<T> {
		final long timeMs;
		final T payload;
		CacheEntry(long timeMs, T payload) { this.timeMs = timeMs; this.payload = payload; }
	}

	// Cache computed scores to enforce 1 API call per player/season for the window
	private static final ConcurrentHashMap<String, CacheEntry<PlayerScore>> scoreCache = new ConcurrentHashMap<>();

	public PlayerComparisonService(String apiKey, String apiHost) {
		this.apiKey = apiKey;
		this.apiHost = apiHost;
	}

	public PlayerScore computeScore(int playerId, int season) {
		if (playerId <= 0 || season <= 0) {
			PlayerScore ps = new PlayerScore();
			ps.playerId = Math.max(playerId, 0);
			ps.season = Math.max(season, 0);
			ps.name = null;
			ps.totalPositive = 0;
			ps.totalNegative = 0;
			ps.finalScore = 0;
			return ps;
		}
		String key = playerId + ":" + season;
		long now = System.currentTimeMillis();
		CacheEntry<PlayerScore> ce = scoreCache.get(key);
		if (ce != null && (now - ce.timeMs) < CACHE_TTL_MS && ce.payload != null) {
			return ce.payload;
		}

		// Fetch API once
		String url = "https://" + apiHost + "/players?id=" + playerId + "&season=" + season;
		String json = httpGet(url);
		@SuppressWarnings("unchecked")
		Map<String, Object> root = gson.fromJson(json, Map.class);

		PlayerScore out = new PlayerScore();
		out.playerId = playerId;
		out.season = season;
		out.name = null;

		double totalPos = 0.0;
		double totalNeg = 0.0;
		List<PlayerScore.CompetitionBreakdown> breakdown = new ArrayList<>();

		if (root != null) {
			@SuppressWarnings("unchecked")
			List<Object> response = (List<Object>) root.get("response");
			if (response != null && !response.isEmpty()) {
				Object item0 = response.get(0);
				if (item0 instanceof Map) {
					@SuppressWarnings("unchecked")
					Map<String, Object> r0 = (Map<String, Object>) item0;
					@SuppressWarnings("unchecked")
					Map<String, Object> player = (Map<String, Object>) r0.get("player");
					if (player != null) {
						Object nm = player.get("name");
						out.name = nm == null ? null : String.valueOf(nm);
					}
					@SuppressWarnings("unchecked")
					List<Object> statistics = (List<Object>) r0.get("statistics");
					if (statistics != null) {
						for (Object st : statistics) {
							if (!(st instanceof Map)) continue;
							@SuppressWarnings("unchecked")
							Map<String, Object> stat = (Map<String, Object>) st;
							// league info for breakdown
							@SuppressWarnings("unchecked")
							Map<String, Object> league = (Map<String, Object>) stat.get("league");
							String leagueName = league != null && league.get("name") != null ? String.valueOf(league.get("name")) : null;
							Integer leagueId = league != null && league.get("id") instanceof Number ? ((Number) league.get("id")).intValue() : null;

							double pos = 0.0;
							double neg = 0.0;

							// goals
							@SuppressWarnings("unchecked")
							Map<String, Object> goals = (Map<String, Object>) stat.get("goals");
							pos += num(goals == null ? null : goals.get("total"));
							pos += num(goals == null ? null : goals.get("assists"));
							pos += num(goals == null ? null : goals.get("saves"));
							neg += num(goals == null ? null : goals.get("conceded"));

							// duels: won positive; lost negative (lost may be missing => total - won)
							@SuppressWarnings("unchecked")
							Map<String, Object> duels = (Map<String, Object>) stat.get("duels");
							double duelsWon = num(duels == null ? null : duels.get("won"));
							double duelsLost = num(duels == null ? null : duels.get("lost"));
							double duelsTotal = num(duels == null ? null : duels.get("total"));
							if (duelsLost <= 0 && duelsTotal > 0 && duelsWon >= 0) {
								double calcLost = duelsTotal - duelsWon;
								if (calcLost > 0) duelsLost = calcLost;
							}
							pos += duelsWon;
							neg += Math.max(0, duelsLost);

							// dribbles.success
							@SuppressWarnings("unchecked")
							Map<String, Object> dribbles = (Map<String, Object>) stat.get("dribbles");
							pos += num(dribbles == null ? null : dribbles.get("success"));

							// passes.key
							@SuppressWarnings("unchecked")
							Map<String, Object> passes = (Map<String, Object>) stat.get("passes");
							pos += num(passes == null ? null : passes.get("key"));

							// penalty.saved (positive) / penalty.missed (negative)
							@SuppressWarnings("unchecked")
							Map<String, Object> penalty = (Map<String, Object>) stat.get("penalty");
							pos += num(penalty == null ? null : penalty.get("saved"));
							neg += num(penalty == null ? null : penalty.get("missed"));

							// fouls: drawn positive, committed negative
							@SuppressWarnings("unchecked")
							Map<String, Object> fouls = (Map<String, Object>) stat.get("fouls");
							pos += num(fouls == null ? null : fouls.get("drawn"));
							neg += num(fouls == null ? null : fouls.get("committed"));

							// tackles: interceptions + blocks positive
							@SuppressWarnings("unchecked")
							Map<String, Object> tackles = (Map<String, Object>) stat.get("tackles");
							pos += num(tackles == null ? null : tackles.get("interceptions"));
							pos += num(tackles == null ? null : tackles.get("blocks"));

							// cards: yellow + red negative
							@SuppressWarnings("unchecked")
							Map<String, Object> cards = (Map<String, Object>) stat.get("cards");
							neg += num(cards == null ? null : cards.get("yellow"));
							neg += num(cards == null ? null : cards.get("red"));

							PlayerScore.CompetitionBreakdown cb = new PlayerScore.CompetitionBreakdown();
							cb.leagueName = leagueName;
							cb.leagueId = leagueId;
							cb.positive = pos;
							cb.negative = neg;
							breakdown.add(cb);

							totalPos += pos;
							totalNeg += neg;
						}
					}
				}
			}
		}

		out.totalPositive = totalPos;
		out.totalNegative = totalNeg;
		out.finalScore = totalPos - totalNeg;
		out.perCompetition = breakdown;

		scoreCache.put(key, new CacheEntry<>(now, out));
		return out;
	}

	public PlayerComparisonResult comparePlayers(int p1, int p2, int season) {
		PlayerComparisonResult res = new PlayerComparisonResult();
		PlayerScore s1 = computeScore(p1, season);
		PlayerScore s2 = computeScore(p2, season);
		res.player1 = s1;
		res.player2 = s2;
		double a = s1 == null ? 0.0 : s1.finalScore;
		double b = s2 == null ? 0.0 : s2.finalScore;
		if (Math.abs(a - b) < 1e-9) res.winner = "DRAW";
		else res.winner = a > b ? "PLAYER1" : "PLAYER2";
		return res;
	}

	private String httpGet(String url) {
		try {
			LeagueRequest lr = new LeagueRequest(new FullResponse());
			return lr.setRequset(url, apiKey, apiHost);
		} catch (Exception e) {
			return "{}";
		}
	}

	private static double num(Object o) {
		if (o == null) return 0.0;
		if (o instanceof Number) return ((Number) o).doubleValue();
		try {
			String s = String.valueOf(o).trim();
			if (s.endsWith("%")) s = s.substring(0, s.length() - 1);
			return Double.parseDouble(s);
		} catch (Exception e) {
			return 0.0;
		}
	}
}


