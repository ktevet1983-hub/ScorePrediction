package com.example.scoreprediction.compare;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Scores a single player's performance based on API-Football players endpoint "statistics" array.
 *
 * STRICTLY implements the provided rules:
 * - Sum positives and negatives per statistics object (league)
 * - Do not divide or average anything
 * - Apply league importance multipliers per object
 * - After summing all weighted object scores, apply DUELS bonus (+2 if total duels.won > 15)
 *
 * Inputs are handled defensively; nulls and unexpected types are treated as zero or ignored.
 */
public final class PlayerStatisticsScorer {

	private PlayerStatisticsScorer() {}

	public static ScoreResult scoreFromStatistics(List<?> statisticsArray) {
		ScoreResult result = new ScoreResult();
		List<LeagueDetail> details = new ArrayList<>();
		result.detailsPerLeague = details;

		double totalScore = 0.0;
		int totalDuelsWon = 0;

		if (statisticsArray != null) {
			for (Object obj : statisticsArray) {
				if (!(obj instanceof Map)) continue;
				@SuppressWarnings("unchecked")
				Map<String, Object> stat = (Map<String, Object>) obj;

				// league info
				Map<String, Object> league = asMap(stat.get("league"));
				Integer leagueId = asIntObj(league == null ? null : league.get("id"));
				String leagueName = league == null ? null : asString(league.get("name"));

				// games context
				Map<String, Object> games = asMap(stat.get("games"));
				int minutes = asInt(games == null ? null : games.get("minutes"));
				String position = games == null ? null : asString(games.get("position"));
				double ratingValue = parseRating(games == null ? null : games.get("rating"));

				// components
				Map<String, Object> goals = asMap(stat.get("goals"));
				Map<String, Object> passes = asMap(stat.get("passes"));
				Map<String, Object> tackles = asMap(stat.get("tackles"));
				Map<String, Object> dribbles = asMap(stat.get("dribbles"));
				Map<String, Object> fouls = asMap(stat.get("fouls"));
				Map<String, Object> cards = asMap(stat.get("cards"));
				Map<String, Object> penalty = asMap(stat.get("penalty"));
				Map<String, Object> duels = asMap(stat.get("duels"));

				// Positive points
				double positive = 0.0;
				positive += asInt(goals == null ? null : goals.get("total")); // goals.total
				positive += asInt(goals == null ? null : goals.get("assists")); // assists
				positive += asInt(passes == null ? null : passes.get("key")); // passes.key
				positive += asInt(tackles == null ? null : tackles.get("interceptions")); // tackles.interceptions
				positive += asInt(dribbles == null ? null : dribbles.get("success")); // dribbles.success
				positive += asInt(fouls == null ? null : fouls.get("drawn")); // fouls.drawn

				// Clean sheet bonus (goals.conceded == 0)
				int conceded = asInt(goals == null ? null : goals.get("conceded"));
				if (conceded == 0) {
					RoleGroup role = RoleGroup.fromPosition(position);
					if (role == RoleGroup.GK || role == RoleGroup.DEF) {
						positive += 3.0;
					} else if (role == RoleGroup.MID || role == RoleGroup.ATT) {
						positive += 1.0;
					}
				}

				// Special rating rule (only if minutes >= 500)
				if (minutes >= 500 && !Double.isNaN(ratingValue)) {
					if (ratingValue >= 7.0 && ratingValue < 8.0) positive += 1.0;
					else if (ratingValue >= 8.0 && ratingValue < 9.0) positive += 2.0;
					else if (ratingValue >= 9.0 && ratingValue <= 10.0) positive += 3.0;
				}

				// Penalty object scoring
				if (penalty != null && !penalty.isEmpty()) {
					positive += asInt(penalty.get("won"));     // won = +1
					// committed = -1
					// missed = -1
					// saved = +2
					// scored = +1
					int saved = asInt(penalty.get("saved"));
					int scored = asInt(penalty.get("scored"));
					positive += (saved * 2.0);
					positive += scored;
					// negatives applied below with other negatives
					// We'll carry committed and missed into negative accumulator
				}

				// Negative points
				double negative = 0.0;
				negative += asInt(fouls == null ? null : fouls.get("committed")); // fouls.committed
				negative += asInt(cards == null ? null : cards.get("yellow")); // -1 per yellow
				negative += asInt(cards == null ? null : cards.get("red")) * 3.0; // -3 per red
				if (conceded > 0) {
					negative += conceded; // -1 per conceded goal (only if > 0)
				}
				if (penalty != null && !penalty.isEmpty()) {
					negative += asInt(penalty.get("commited")); // -1 per committed penalty
					negative += asInt(penalty.get("missed"));   // -1 per missed penalty
				}

				// Duels: do NOT contribute to pos/neg. Only for bonus later.
				totalDuelsWon += asInt(duels == null ? null : duels.get("won"));

				// Object score and weighting by league importance
				double baseScore = positive - negative;
				int multiplier = leagueMultiplier(leagueId);
				double weightedScore = baseScore * multiplier;

				LeagueDetail ld = new LeagueDetail();
				ld.leagueId = leagueId;
				ld.leagueName = leagueName;
				ld.positivePoints = positive;
				ld.negativePoints = negative;
				ld.baseScore = baseScore;
				ld.multiplier = multiplier;
				ld.weightedScore = weightedScore;
				details.add(ld);

				totalScore += weightedScore;
			}
		}

		boolean duelsBonus = totalDuelsWon > 15;
		if (duelsBonus) totalScore += 2.0;

		result.totalScore = totalScore;
		result.totalDuelsWon = totalDuelsWon;
		result.duelsBonusApplied = duelsBonus;
		return result;
	}

	private static int leagueMultiplier(Integer leagueId) {
		if (leagueId == null) return 1;
		// If id = 1 or 3 → x3
		if (leagueId == 1 || leagueId == 3) return 3;
		// If id in {39, 140, 135, 61, 78, 3, 94} → x2 (3 already handled above)
		switch (leagueId) {
			case 39:
			case 140:
			case 135:
			case 61:
			case 78:
			case 94:
				return 2;
			default:
				return 1;
		}
	}

	private static Map<String, Object> asMap(Object o) {
		if (o instanceof Map) {
			@SuppressWarnings("unchecked")
			Map<String, Object> m = (Map<String, Object>) o;
			return m;
		}
		return null;
	}

	private static String asString(Object o) {
		return o == null ? null : String.valueOf(o);
	}

	private static int asInt(Object o) {
		if (o == null) return 0;
		if (o instanceof Number) return ((Number) o).intValue();
		try {
			String s = String.valueOf(o).trim();
			if (s.isEmpty()) return 0;
			if (s.endsWith("%")) s = s.substring(0, s.length() - 1);
			return (int) Math.floor(Double.parseDouble(s));
		} catch (Exception e) {
			return 0;
		}
	}

	private static Integer asIntObj(Object o) {
		if (o == null) return null;
		if (o instanceof Number) return ((Number) o).intValue();
		try {
			String s = String.valueOf(o).trim();
			if (s.isEmpty()) return null;
			if (s.endsWith("%")) s = s.substring(0, s.length() - 1);
			return (int) Math.floor(Double.parseDouble(s));
		} catch (Exception e) {
			return null;
		}
	}

	private static double parseRating(Object rating) {
		if (rating == null) return Double.NaN;
		if (rating instanceof Number) {
			return ((Number) rating).doubleValue();
		}
		try {
			String s = String.valueOf(rating).trim();
			if (s.isEmpty()) return Double.NaN;
			return Double.parseDouble(s);
		} catch (Exception e) {
			return Double.NaN;
		}
	}

	private enum RoleGroup {
		GK, DEF, MID, ATT, UNKNOWN;
		static RoleGroup fromPosition(String posRaw) {
			if (posRaw == null) return UNKNOWN;
			String p = posRaw.trim().toUpperCase();
			if (p.isEmpty()) return UNKNOWN;
			// Common API-Football values: "G", "D", "M", "F"
			// Also accept full words.
			if (p.startsWith("G") || p.contains("KEEP")) return GK;
			if (p.startsWith("D") || p.contains("DEF")) return DEF;
			if (p.startsWith("M") || p.contains("MID")) return MID;
			if (p.startsWith("F") || p.contains("ATT") || p.contains("FORW")) return ATT;
			return UNKNOWN;
		}
	}

	/**
	 * Final scoring result.
	 */
	public static class ScoreResult {
		public double totalScore;
		public List<LeagueDetail> detailsPerLeague;
		public int totalDuelsWon;
		public boolean duelsBonusApplied;
	}

	/**
	 * Per-league scoring breakdown for a single statistics object.
	 */
	public static class LeagueDetail {
		public Integer leagueId;
		public String leagueName;
		public double positivePoints;
		public double negativePoints;
		public double baseScore;     // positivePoints - negativePoints
		public int multiplier;       // importance multiplier
		public double weightedScore; // baseScore * multiplier
	}
}


