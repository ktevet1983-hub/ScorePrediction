package com.example.scoreprediction.prediction;

import com.example.scoreprediction.FullResponse;
import com.example.scoreprediction.LeagueRequest;
import com.google.gson.Gson;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class PlayerComparator {

	// Shared constants
	private static final double BIG_DELTA = 0.25;
	private static final double GOALS_PER_MATCH_DELTA_BIG = 0.3;
	private static final double GA_PER_MATCH_DELTA_BIG = 0.3;
	private static final double DISCIPLINE_YELLOW_DELTA = 0.3;
	private static final double DISCIPLINE_RED_DELTA = 0.05;
	private static final double SAVE_PERCENT_BIG = 5.0; // percent
	private static final double TACKLES_PER_MATCH_DELTA_BIG = 0.3;
	private static final double INTERCEPTIONS_PER_MATCH_DELTA_BIG = 0.3;
	private static final double KEY_PASSES_PER_MATCH_DELTA_BIG = 0.3;
	private static final double SHOTS_ON_TARGET_PER_MATCH_DELTA_BIG = 0.3;
	private static final double GOALS_PER90_DELTA_BIG = 0.3;
	private static final double ASSISTS_PER90_DELTA_BIG = 0.2;
	private static final long CACHE_TTL_MS = 90_000L;

	private final String apiKey;
	private final String apiHost;
	private final Gson gson = new Gson();

	// TTL cache: player stats keyed by league:season:team:player
	private static final java.util.concurrent.ConcurrentHashMap<String, CacheEntry<Map<String, Object>>> playerStatsCache = new java.util.concurrent.ConcurrentHashMap<>();

	private static class CacheEntry<T> {
		final long timeMs;
		final T payload;
		CacheEntry(long timeMs, T payload) { this.timeMs = timeMs; this.payload = payload; }
	}

	public static class PlayerCompareArgs {
		public int leagueId;
		public int season;
		public int teamId1;
		public int teamId2;
		public int playerId1;
		public int playerId2;
		public PlayerRole role; // optional; if null derive
	}

	public PlayerComparator(String apiKey, String apiHost) {
		this.apiKey = apiKey;
		this.apiHost = apiHost;
	}

	public PlayerComparisonResult compare(PlayerCompareArgs args) {
		PlayerComparisonResult out = new PlayerComparisonResult();
		out.rawDataRefs.used.add("players");
		if (args == null || args.leagueId <= 0 || args.season <= 0 || args.teamId1 <= 0 || args.teamId2 <= 0 || args.playerId1 <= 0 || args.playerId2 <= 0) {
			PlayerComparisonResult res = PlayerComparisonResult.draw(null);
			res.rawDataRefs.used.add("players");
			res.breakdown.add(new PlayerComparisonResult.BreakdownItem("error", 0, 0, "Invalid args"));
			return res;
		}
		try {
			PlayerStats s1 = loadStats(args.leagueId, args.season, args.teamId1, args.playerId1, out);
			PlayerStats s2 = loadStats(args.leagueId, args.season, args.teamId2, args.playerId2, out);
			if (s1 == null || s2 == null) {
				PlayerComparisonResult res = PlayerComparisonResult.draw(null);
				res.rawDataRefs.used.add("players");
				res.breakdown.add(new PlayerComparisonResult.BreakdownItem("error", 0, 0, "Missing player stats (rate limit or unavailable)"));
				return res;
			}
			PlayerRole role = args.role != null ? args.role : deriveRoleFromStats(s1, s2);
			if (role == null) role = deriveRoleFromStats(s1, s2);
			// Ensure same role group
			PlayerRole r1 = s1.role != null ? s1.role : PlayerRole.fromApiPosition(s1.position);
			PlayerRole r2 = s2.role != null ? s2.role : PlayerRole.fromApiPosition(s2.position);
			if (r1 != null && r2 != null && r1 == r2) {
				switch (r1) {
					case GOALKEEPER:
						return compareGoalkeepers(args, s1, s2);
					case DEFENDER:
						return compareDefenders(args, s1, s2);
					case MIDFIELDER:
						return compareMidfielders(args, s1, s2);
					case FORWARD:
						return compareForwards(args, s1, s2);
					default:
						return compareGeneric(args, s1, s2, "ANY");
				}
			}
			// Fallback: generic comparator supports ANY position pairs
			switch (role) {
				case GOALKEEPER:
					return compareGoalkeepers(args, s1, s2);
				case DEFENDER:
					return compareDefenders(args, s1, s2);
				case MIDFIELDER:
					return compareMidfielders(args, s1, s2);
				case FORWARD:
					return compareForwards(args, s1, s2);
				default:
					return compareGeneric(args, s1, s2, "ANY");
			}
		} catch (IllegalArgumentException iae) {
			// rethrow for the web layer to map to 400
			throw iae;
		} catch (Exception e) {
			return PlayerComparisonResult.draw(null);
		}
	}

	private PlayerRole deriveRoleFromStats(PlayerStats s1, PlayerStats s2) {
		PlayerRole r1 = s1.role != null ? s1.role : PlayerRole.fromApiPosition(s1.position);
		PlayerRole r2 = s2.role != null ? s2.role : PlayerRole.fromApiPosition(s2.position);
		if (r1 == null || r2 == null) return null;
		if (r1 == r2) return r1;
		return null;
	}

	private PlayerComparisonResult compareGoalkeepers(PlayerCompareArgs args, PlayerStats s1, PlayerStats s2) {
		int t1 = 0, t2 = 0;
		PlayerComparisonResult out = new PlayerComparisonResult();
		out.positionGroup = PlayerRole.GOALKEEPER.groupCode();
		out.player1 = s1.info;
		out.player2 = s2.info;

		// gk.savePct: higher better
		{
			int p1 = 0, p2 = 0;
			double a = s1.gkSavePct;
			double b = s2.gkSavePct;
			if (a > 0 || b > 0) {
				if (Math.abs(a - b) >= SAVE_PERCENT_BIG) {
					if (a > b) p1 = 2; else p2 = 2;
				} else if (a > b) {
					p1 = 1;
				} else if (b > a) {
					p2 = 1;
				}
			}
			out.breakdown.add(new PlayerComparisonResult.BreakdownItem("gk.savePct", p1, p2, notePct(a, b)));
			t1 += p1; t2 += p2;
		}

		// gk.goalsConcededPerMatch: lower better
		{
			int p1 = 0, p2 = 0;
			double a = s1.goalsConcededPerMatch;
			double b = s2.goalsConcededPerMatch;
			if (a > 0 || b > 0) {
				if (Math.abs(a - b) >= GA_PER_MATCH_DELTA_BIG) {
					if (a < b) p1 = 2; else p2 = 2;
				} else if (a < b) {
					p1 = 1;
				} else if (b < a) {
					p2 = 1;
				}
			}
			out.breakdown.add(new PlayerComparisonResult.BreakdownItem("gk.goalsConcededPerMatch", p1, p2, noteDelta(a, b)));
			t1 += p1; t2 += p2;
		}

		// gk.cleanSheets: more better
		{
			int p1 = 0, p2 = 0;
			int a = s1.cleanSheets;
			int b = s2.cleanSheets;
			if (a > b) p1 = 1;
			else if (b > a) p2 = 1;
			out.breakdown.add(new PlayerComparisonResult.BreakdownItem("gk.cleanSheets", p1, p2, "p1=" + a + ", p2=" + b));
			t1 += p1; t2 += p2;
		}

		// gk.aerial/claims/punches small +1 if available and higher
		{
			int p1 = 0, p2 = 0;
			double a = s1.aerialActionsPerMatch;
			double b = s2.aerialActionsPerMatch;
			if (a > 0 || b > 0) {
				if (a > b) p1 = 1;
				else if (b > a) p2 = 1;
			}
			out.breakdown.add(new PlayerComparisonResult.BreakdownItem("gk.aerialActionsPerMatch", p1, p2, noteDelta(a, b)));
			t1 += p1; t2 += p2;
		}

		// discipline fewer cards: +1
		{
			int p1 = disciplineBetter(s1, s2);
			int p2 = disciplineBetter(s2, s1);
			out.breakdown.add(new PlayerComparisonResult.BreakdownItem("discipline.cards", p1, p2, "lower is better"));
			t1 += p1; t2 += p2;
		}

		out.scorePlayer1 = t1;
		out.scorePlayer2 = t2;
		out.winner = t1 == t2 ? "DRAW" : (t1 > t2 ? "PLAYER1" : "PLAYER2");
		return out;
	}

	private PlayerComparisonResult compareDefenders(PlayerCompareArgs args, PlayerStats s1, PlayerStats s2) {
		int t1 = 0, t2 = 0;
		PlayerComparisonResult out = new PlayerComparisonResult();
		out.positionGroup = PlayerRole.DEFENDER.groupCode();
		out.player1 = s1.info;
		out.player2 = s2.info;

		// tackles per match
		{
			int p1 = cmpDeltaPts(s1.tacklesPerMatch, s2.tacklesPerMatch, TACKLES_PER_MATCH_DELTA_BIG);
			int p2 = cmpDeltaPts(s2.tacklesPerMatch, s1.tacklesPerMatch, TACKLES_PER_MATCH_DELTA_BIG);
			out.breakdown.add(new PlayerComparisonResult.BreakdownItem("def.tacklesPerMatch", p1, p2, noteDelta(s1.tacklesPerMatch, s2.tacklesPerMatch)));
			t1 += p1; t2 += p2;
		}
		// interceptions per match
		{
			int p1 = cmpDeltaPts(s1.interceptionsPerMatch, s2.interceptionsPerMatch, INTERCEPTIONS_PER_MATCH_DELTA_BIG);
			int p2 = cmpDeltaPts(s2.interceptionsPerMatch, s1.interceptionsPerMatch, INTERCEPTIONS_PER_MATCH_DELTA_BIG);
			out.breakdown.add(new PlayerComparisonResult.BreakdownItem("def.interceptionsPerMatch", p1, p2, noteDelta(s1.interceptionsPerMatch, s2.interceptionsPerMatch)));
			t1 += p1; t2 += p2;
		}
		// clearances per match
		{
			int p1 = 0, p2 = 0;
			if (s1.clearancesPerMatch > s2.clearancesPerMatch) p1 = 1;
			else if (s2.clearancesPerMatch > s1.clearancesPerMatch) p2 = 1;
			out.breakdown.add(new PlayerComparisonResult.BreakdownItem("def.clearancesPerMatch", p1, p2, noteDelta(s1.clearancesPerMatch, s2.clearancesPerMatch)));
			t1 += p1; t2 += p2;
		}
		// blocks per match
		{
			int p1 = 0, p2 = 0;
			if (s1.blocksPerMatch > s2.blocksPerMatch) p1 = 1;
			else if (s2.blocksPerMatch > s1.blocksPerMatch) p2 = 1;
			out.breakdown.add(new PlayerComparisonResult.BreakdownItem("def.blocksPerMatch", p1, p2, noteDelta(s1.blocksPerMatch, s2.blocksPerMatch)));
			t1 += p1; t2 += p2;
		}
		// goals conceded per match (lower better)
		{
			int p1 = 0, p2 = 0;
			double a = s1.goalsConcededPerMatch;
			double b = s2.goalsConcededPerMatch;
			if (a > 0 || b > 0) {
				if (Math.abs(a - b) >= GA_PER_MATCH_DELTA_BIG) {
					if (a < b) p1 = 2; else p2 = 2;
				} else if (a < b) {
					p1 = 1;
				} else if (b < a) {
					p2 = 1;
				}
			}
			out.breakdown.add(new PlayerComparisonResult.BreakdownItem("def.goalsConcededPerMatch", p1, p2, noteDelta(a, b)));
			t1 += p1; t2 += p2;
		}
		// passing: key passes per match small +1
		{
			int p1 = cmpSmall(s1.keyPassesPerMatch, s2.keyPassesPerMatch);
			int p2 = cmpSmall(s2.keyPassesPerMatch, s1.keyPassesPerMatch);
			out.breakdown.add(new PlayerComparisonResult.BreakdownItem("def.keyPassesPerMatch", p1, p2, noteDelta(s1.keyPassesPerMatch, s2.keyPassesPerMatch)));
			t1 += p1; t2 += p2;
		}
		// discipline
		{
			int p1 = disciplineBetter(s1, s2);
			int p2 = disciplineBetter(s2, s1);
			out.breakdown.add(new PlayerComparisonResult.BreakdownItem("discipline.cards", p1, p2, "lower is better"));
			t1 += p1; t2 += p2;
		}

		out.scorePlayer1 = t1;
		out.scorePlayer2 = t2;
		out.winner = t1 == t2 ? "DRAW" : (t1 > t2 ? "PLAYER1" : "PLAYER2");
		return out;
	}

	private PlayerComparisonResult compareMidfielders(PlayerCompareArgs args, PlayerStats s1, PlayerStats s2) {
		int t1 = 0, t2 = 0;
		PlayerComparisonResult out = new PlayerComparisonResult();
		out.positionGroup = PlayerRole.MIDFIELDER.groupCode();
		out.player1 = s1.info;
		out.player2 = s2.info;

		// key passes per match
		{
			int p1 = cmpDeltaPts(s1.keyPassesPerMatch, s2.keyPassesPerMatch, KEY_PASSES_PER_MATCH_DELTA_BIG);
			int p2 = cmpDeltaPts(s2.keyPassesPerMatch, s1.keyPassesPerMatch, KEY_PASSES_PER_MATCH_DELTA_BIG);
			out.breakdown.add(new PlayerComparisonResult.BreakdownItem("mid.keyPassesPerMatch", p1, p2, noteDelta(s1.keyPassesPerMatch, s2.keyPassesPerMatch)));
			t1 += p1; t2 += p2;
		}
		// assists per90
		{
			int p1 = cmpDeltaPts(s1.assistsPer90, s2.assistsPer90, ASSISTS_PER90_DELTA_BIG);
			int p2 = cmpDeltaPts(s2.assistsPer90, s1.assistsPer90, ASSISTS_PER90_DELTA_BIG);
			out.breakdown.add(new PlayerComparisonResult.BreakdownItem("mid.assistsPer90", p1, p2, noteDelta(s1.assistsPer90, s2.assistsPer90)));
			t1 += p1; t2 += p2;
		}
		// goals per90
		{
			int p1 = cmpDeltaPts(s1.goalsPer90, s2.goalsPer90, GOALS_PER90_DELTA_BIG);
			int p2 = cmpDeltaPts(s2.goalsPer90, s1.goalsPer90, GOALS_PER90_DELTA_BIG);
			out.breakdown.add(new PlayerComparisonResult.BreakdownItem("mid.goalsPer90", p1, p2, noteDelta(s1.goalsPer90, s2.goalsPer90)));
			t1 += p1; t2 += p2;
		}
		// defensive work: tackles+interceptions per match
		{
			int p1 = 0, p2 = 0;
			double a = s1.tacklesPerMatch + s1.interceptionsPerMatch;
			double b = s2.tacklesPerMatch + s2.interceptionsPerMatch;
			if (Math.abs(a - b) >= 0.4) {
				if (a > b) p1 = 1; else p2 = 1;
			}
			out.breakdown.add(new PlayerComparisonResult.BreakdownItem("mid.defWork(tackles+interceptions)", p1, p2, noteDelta(a, b)));
			t1 += p1; t2 += p2;
		}
		// xG+xA per90 if available
		{
			int p1 = cmpSmall(s1.xgxaPer90, s2.xgxaPer90);
			int p2 = cmpSmall(s2.xgxaPer90, s1.xgxaPer90);
			out.breakdown.add(new PlayerComparisonResult.BreakdownItem("mid.xGxAper90", p1, p2, noteDelta(s1.xgxaPer90, s2.xgxaPer90)));
			t1 += p1; t2 += p2;
		}
		// discipline
		{
			int p1 = disciplineBetter(s1, s2);
			int p2 = disciplineBetter(s2, s1);
			out.breakdown.add(new PlayerComparisonResult.BreakdownItem("discipline.cards", p1, p2, "lower is better"));
			t1 += p1; t2 += p2;
		}

		out.scorePlayer1 = t1;
		out.scorePlayer2 = t2;
		out.winner = t1 == t2 ? "DRAW" : (t1 > t2 ? "PLAYER1" : "PLAYER2");
		return out;
	}

	private PlayerComparisonResult compareForwards(PlayerCompareArgs args, PlayerStats s1, PlayerStats s2) {
		int t1 = 0, t2 = 0;
		PlayerComparisonResult out = new PlayerComparisonResult();
		out.positionGroup = PlayerRole.FORWARD.groupCode();
		out.player1 = s1.info;
		out.player2 = s2.info;

		// goals per90: higher => +2 (if clear), else +1
		{
			int p1 = cmpDeltaPtsWeighted(s1.goalsPer90, s2.goalsPer90, GOALS_PER90_DELTA_BIG, 2);
			int p2 = cmpDeltaPtsWeighted(s2.goalsPer90, s1.goalsPer90, GOALS_PER90_DELTA_BIG, 2);
			out.breakdown.add(new PlayerComparisonResult.BreakdownItem("fwd.goalsPer90", p1, p2, noteDelta(s1.goalsPer90, s2.goalsPer90)));
			t1 += p1; t2 += p2;
		}
		// shots on target per match
		{
			int p1 = cmpDeltaPts(s1.shotsOnTargetPerMatch, s2.shotsOnTargetPerMatch, SHOTS_ON_TARGET_PER_MATCH_DELTA_BIG);
			int p2 = cmpDeltaPts(s2.shotsOnTargetPerMatch, s1.shotsOnTargetPerMatch, SHOTS_ON_TARGET_PER_MATCH_DELTA_BIG);
			out.breakdown.add(new PlayerComparisonResult.BreakdownItem("fwd.shotsOnTargetPerMatch", p1, p2, noteDelta(s1.shotsOnTargetPerMatch, s2.shotsOnTargetPerMatch)));
			t1 += p1; t2 += p2;
		}
		// xG per90 if available
		{
			int p1 = cmpSmall(s1.xgPer90, s2.xgPer90);
			int p2 = cmpSmall(s2.xgPer90, s1.xgPer90);
			out.breakdown.add(new PlayerComparisonResult.BreakdownItem("fwd.xGper90", p1, p2, noteDelta(s1.xgPer90, s2.xgPer90)));
			t1 += p1; t2 += p2;
		}
		// assists per90 small +1
		{
			int p1 = cmpSmall(s1.assistsPer90, s2.assistsPer90);
			int p2 = cmpSmall(s2.assistsPer90, s1.assistsPer90);
			out.breakdown.add(new PlayerComparisonResult.BreakdownItem("fwd.assistsPer90", p1, p2, noteDelta(s1.assistsPer90, s2.assistsPer90)));
			t1 += p1; t2 += p2;
		}
		// big chances missed (lower better) small +1
		{
			int p1 = 0, p2 = 0;
			if (s1.bigChancesMissed >= 0 && s2.bigChancesMissed >= 0) {
				if (s1.bigChancesMissed < s2.bigChancesMissed) p1 = 1;
				else if (s2.bigChancesMissed < s1.bigChancesMissed) p2 = 1;
			}
			out.breakdown.add(new PlayerComparisonResult.BreakdownItem("fwd.bigChancesMissed(lowerBetter)", p1, p2, "p1=" + s1.bigChancesMissed + ", p2=" + s2.bigChancesMissed));
			t1 += p1; t2 += p2;
		}
		// discipline
		{
			int p1 = disciplineBetter(s1, s2);
			int p2 = disciplineBetter(s2, s1);
			out.breakdown.add(new PlayerComparisonResult.BreakdownItem("discipline.cards", p1, p2, "lower is better"));
			t1 += p1; t2 += p2;
		}

		out.scorePlayer1 = t1;
		out.scorePlayer2 = t2;
		out.winner = t1 == t2 ? "DRAW" : (t1 > t2 ? "PLAYER1" : "PLAYER2");
		return out;
	}

	private PlayerComparisonResult compareGeneric(PlayerCompareArgs args, PlayerStats s1, PlayerStats s2, String group) {
		int t1 = 0, t2 = 0;
		PlayerComparisonResult out = new PlayerComparisonResult();
		out.positionGroup = group;
		out.player1 = s1.info;
		out.player2 = s2.info;

		// Attack
		{ int[] p = genericPoints(out, "gen.goalsPer90", s1.goalsPer90, s2.goalsPer90, GOALS_PER90_DELTA_BIG, 1); t1 += p[0]; t2 += p[1]; }
		{ int[] p = genericPoints(out, "gen.assistsPer90", s1.assistsPer90, s2.assistsPer90, ASSISTS_PER90_DELTA_BIG, 1); t1 += p[0]; t2 += p[1]; }
		{ int[] p = genericPoints(out, "gen.shotsOnTargetPerMatch", s1.shotsOnTargetPerMatch, s2.shotsOnTargetPerMatch, SHOTS_ON_TARGET_PER_MATCH_DELTA_BIG, 1); t1 += p[0]; t2 += p[1]; }
		{ int[] p = genericPoints(out, "gen.keyPassesPerMatch", s1.keyPassesPerMatch, s2.keyPassesPerMatch, KEY_PASSES_PER_MATCH_DELTA_BIG, 1); t1 += p[0]; t2 += p[1]; }
		{ int[] p = genericPoints(out, "gen.xGper90", s1.xgPer90, s2.xgPer90, GOALS_PER90_DELTA_BIG, 1); t1 += p[0]; t2 += p[1]; }
		{ int[] p = genericPoints(out, "gen.xAper90", s1.xaPer90, s2.xaPer90, ASSISTS_PER90_DELTA_BIG, 1); t1 += p[0]; t2 += p[1]; }
		{ int[] p = genericPoints(out, "gen.xGxAper90", s1.xgxaPer90, s2.xgxaPer90, GOALS_PER90_DELTA_BIG, 1); t1 += p[0]; t2 += p[1]; }

		// Defense
		{ int[] p = genericPoints(out, "gen.tacklesPerMatch", s1.tacklesPerMatch, s2.tacklesPerMatch, TACKLES_PER_MATCH_DELTA_BIG, 1); t1 += p[0]; t2 += p[1]; }
		{ int[] p = genericPoints(out, "gen.interceptionsPerMatch", s1.interceptionsPerMatch, s2.interceptionsPerMatch, INTERCEPTIONS_PER_MATCH_DELTA_BIG, 1); t1 += p[0]; t2 += p[1]; }
		{ int[] p = genericPoints(out, "gen.blocksPerMatch", s1.blocksPerMatch, s2.blocksPerMatch, BIG_DELTA, 1); t1 += p[0]; t2 += p[1]; }
		{ int[] p = genericPoints(out, "gen.clearancesPerMatch", s1.clearancesPerMatch, s2.clearancesPerMatch, BIG_DELTA, 1); t1 += p[0]; t2 += p[1]; }
		{ int[] p = genericPoints(out, "gen.goalsConcededPerMatch(lowerBetter)", s1.goalsConcededPerMatch, s2.goalsConcededPerMatch, GA_PER_MATCH_DELTA_BIG, -1); t1 += p[0]; t2 += p[1]; }

		// Goalkeeping
		{ int[] p = genericPoints(out, "gen.gk.savePct", s1.gkSavePct, s2.gkSavePct, SAVE_PERCENT_BIG, 1); t1 += p[0]; t2 += p[1]; }
		{ int[] p = genericPoints(out, "gen.cleanSheets", s1.cleanSheets, s2.cleanSheets, 5.0, 1); t1 += p[0]; t2 += p[1]; }

		// Discipline (lower is better)
		{ int[] p = genericPoints(out, "gen.yellowPerGame(lowerBetter)", s1.yellowPerGame, s2.yellowPerGame, 0.05, -1); t1 += p[0]; t2 += p[1]; }
		{ int[] p = genericPoints(out, "gen.redPerGame(lowerBetter)", s1.redPerGame, s2.redPerGame, 0.02, -1); t1 += p[0]; t2 += p[1]; }

		// Finishing (lower better)
		{ int[] p = genericPoints(out, "gen.bigChancesMissed(lowerBetter)", (s1.bigChancesMissed < 0 ? 0 : s1.bigChancesMissed), (s2.bigChancesMissed < 0 ? 0 : s2.bigChancesMissed), 2.0, -1); t1 += p[0]; t2 += p[1]; }

		out.scorePlayer1 = t1;
		out.scorePlayer2 = t2;
		out.winner = t1 == t2 ? "DRAW" : (t1 > t2 ? "PLAYER1" : "PLAYER2");
		return out;
	}

	private int[] genericPoints(PlayerComparisonResult out, String metric, double a, double b, double big, int mode) {
		// mode: 1 => higher better, -1 => lower better
		double va = a, vb = b;
		if (mode < 0) { va = -a; vb = -b; }
		int p1 = 0, p2 = 0;
		if (Math.abs(a - b) < 1e-9) {
			p1 = 1; p2 = 1;
		} else if (va > vb) {
			p1 = 2;
			if (Math.abs(a - b) >= big) p1 += 3;
		} else {
			p2 = 2;
			if (Math.abs(a - b) >= big) p2 += 3;
		}
		out.breakdown.add(new PlayerComparisonResult.BreakdownItem(metric, p1, p2, noteDelta(a, b)));
		return new int[]{ p1, p2 };
	}

	private static class PlayerStats {
		PlayerComparisonResult.PlayerInfo info = new PlayerComparisonResult.PlayerInfo();
		PlayerRole role;
		String position; // detailed position string (API)

		// Time
		int appearances;
		int minutes;

		// Attack
		double goalsPer90;
		double assistsPer90;
		double shotsOnTargetPerMatch;
		double keyPassesPerMatch;
		double xgPer90;
		double xaPer90;
		double xgxaPer90;
		int bigChancesMissed = -1; // -1 unknown

		// Defense
		double tacklesPerMatch;
		double interceptionsPerMatch;
		double blocksPerMatch;
		double clearancesPerMatch;
		double goalsConcededPerMatch; // when on pitch (approx via conceded / apps)
		double aerialActionsPerMatch; // claims/punches approx if available

		// GK
		double gkSavePct; // 0-100
		int cleanSheets;

		// Discipline (per game proxy using totals + appearances)
		double yellowPerGame;
		double redPerGame;
	}

	private PlayerStats loadStats(int leagueId, int season, int teamId, int playerId, PlayerComparisonResult out) {
		String key = "players:" + leagueId + ":" + season + ":" + teamId + ":" + playerId;
		Map<String, Object> obj;
		long now = System.currentTimeMillis();
		CacheEntry<Map<String, Object>> ce = playerStatsCache.get(key);
		if (ce != null && (now - ce.timeMs) < CACHE_TTL_MS) {
			obj = ce.payload;
			out.rawDataRefs.cacheHits.put("players:" + playerId, true);
		} else {
			// Prefer the simplest, most reliable query: id + season.
			// We'll filter by league/team at parse time.
			String urlPrimary = "https://" + apiHost + "/players?id=" + playerId + "&season=" + season;
			String json = httpGet(urlPrimary);
			@SuppressWarnings("unchecked")
			Map<String, Object> parsed = gson.fromJson(json, Map.class);
			// Fallback: if empty response, try without season (provider sometimes omits per-season stats)
			if (parsed == null || parsed.get("response") == null) {
				String urlFallback = "https://" + apiHost + "/players?id=" + playerId;
				json = httpGet(urlFallback);
				parsed = gson.fromJson(json, Map.class);
			}
			obj = parsed;
			playerStatsCache.put(key, new CacheEntry<>(now, obj));
			out.rawDataRefs.cacheHits.put("players:" + playerId, false);
		}
		return parsePlayerStats(obj, leagueId, season, teamId, playerId);
	}

	private PlayerStats parsePlayerStats(Map<String, Object> obj, int leagueId, int season, int teamId, int playerId) {
		if (obj == null) return null;
		@SuppressWarnings("unchecked")
		List<Object> resp = (List<Object>) obj.get("response");
		if (resp == null || resp.isEmpty()) return null;

		Map<String, Object> best = null;
		Map<String, Object> bestStat = null;

		for (Object o : resp) {
			if (!(o instanceof Map)) continue;
			@SuppressWarnings("unchecked")
			Map<String, Object> entry = (Map<String, Object>) o;
			@SuppressWarnings("unchecked")
			List<Object> statistics = (List<Object>) entry.get("statistics");
			if (statistics == null || statistics.isEmpty()) continue;
			for (Object st : statistics) {
				if (!(st instanceof Map)) continue;
				@SuppressWarnings("unchecked")
				Map<String, Object> stat = (Map<String, Object>) st;
				@SuppressWarnings("unchecked")
				Map<String, Object> t = (Map<String, Object>) stat.get("team");
				@SuppressWarnings("unchecked")
				Map<String, Object> l = (Map<String, Object>) stat.get("league");
				int tid = toInt(t == null ? null : t.get("id"));
				int lid = toInt(l == null ? null : l.get("id"));
				int seasonVal = toInt(l == null ? null : l.get("season"));
				if (tid == teamId && lid == leagueId && (seasonVal == 0 || seasonVal == season)) {
					best = entry;
					bestStat = stat;
					break;
				}
			}
			if (best != null) break;
		}

		if (best == null) {
			// fallback: first entry
			Object first = resp.get(0);
			if (first instanceof Map) {
				best = (Map<String, Object>) first;
				@SuppressWarnings("unchecked")
				List<Object> statistics = (List<Object>) best.get("statistics");
				if (statistics != null && !statistics.isEmpty() && statistics.get(0) instanceof Map) {
					bestStat = (Map<String, Object>) statistics.get(0);
				}
			}
		}
		if (best == null || bestStat == null) return null;

		PlayerStats s = new PlayerStats();
		// Player identity
		@SuppressWarnings("unchecked")
		Map<String, Object> player = (Map<String, Object>) best.get("player");
		s.info.id = toInt(player == null ? null : player.get("id"));
		s.info.name = str(player == null ? null : player.get("name"));
		s.info.age = toInt(player == null ? null : player.get("age"));
		s.info.nationality = str(player == null ? null : player.get("nationality"));
		s.info.teamId = teamId;
		s.info.leagueId = leagueId;
		s.info.season = season;

		// Games and position
		@SuppressWarnings("unchecked")
		Map<String, Object> games = (Map<String, Object>) bestStat.get("games");
		int apps = toInt(games == null ? null : (games.containsKey("appearances") ? games.get("appearances") : games.get("appearences")));
		int minutes = toInt(games == null ? null : games.get("minutes"));
		String pos = str(games == null ? null : games.get("position"));
		s.position = pos;
		PlayerRole role = PlayerRole.fromApiPosition(pos);
		s.role = role;
		s.info.position = pos;
		s.info.role = role == null ? null : role.groupCode();
		s.appearances = apps;
		s.minutes = minutes;

		// Goals & assists
		@SuppressWarnings("unchecked")
		Map<String, Object> goals = (Map<String, Object>) bestStat.get("goals");
		int goalsTotal = toInt(goals == null ? null : goals.get("total"));
		int assistsTotal = toInt(goals == null ? null : goals.get("assists"));
		// Shots
		@SuppressWarnings("unchecked")
		Map<String, Object> shots = (Map<String, Object>) bestStat.get("shots");
		int shotsOn = toInt(shots == null ? null : shots.get("on"));
		// Passes
		@SuppressWarnings("unchecked")
		Map<String, Object> passes = (Map<String, Object>) bestStat.get("passes");
		int keyPasses = toInt(passes == null ? null : passes.get("key"));
		// Tackles/Interceptions/Blocks/Clearances
		@SuppressWarnings("unchecked")
		Map<String, Object> tackles = (Map<String, Object>) bestStat.get("tackles");
		int tacklesTotal = toInt(tackles == null ? null : tackles.get("total"));
		int interceptions = toInt(tackles == null ? null : tackles.get("interceptions"));
		int blocks = toInt(tackles == null ? null : tackles.get("blocks"));
		@SuppressWarnings("unchecked")
		Map<String, Object> duels = (Map<String, Object>) bestStat.get("duels");
		@SuppressWarnings("unchecked")
		Map<String, Object> cards = (Map<String, Object>) bestStat.get("cards");
		int yellow = toInt(cards == null ? null : cards.get("yellow"));
		int red = toInt(cards == null ? null : cards.get("red"));
		@SuppressWarnings("unchecked")
		Map<String, Object> goals2 = (Map<String, Object>) bestStat.get("goals");
		int conceded = toInt(goals2 == null ? null : goals2.get("conceded"));
		int saves = toInt(goals2 == null ? null : goals2.get("saves"));
		// Clean sheets (varies: games.cleansheets or clean_sheets)
		int cleanSheets = toInt(games == null ? null : (games.containsKey("cleansheets") ? games.get("cleansheets") : games.get("clean_sheets")));

		// Clearances may appear under "defense" or "tackles" depending on provider; try both
		@SuppressWarnings("unchecked")
		Map<String, Object> defense = (Map<String, Object>) bestStat.get("defense");
		int clearances = toInt(defense == null ? null : defense.get("clearances"));
		if (clearances == 0) {
			clearances = toInt(tackles == null ? null : tackles.get("clearances"));
		}

		// Big chances missed if available
		@SuppressWarnings("unchecked")
		Map<String, Object> bigChances = (Map<String, Object>) bestStat.get("big_chances");
		int missed = -1;
		if (bigChances != null && bigChances.containsKey("missed")) {
			missed = toInt(bigChances.get("missed"));
		} else {
			// sometimes under 'shots' as 'total' minus 'on' is not reliable; keep -1
		}

		// Expected metrics
		@SuppressWarnings("unchecked")
		Map<String, Object> expected = (Map<String, Object>) bestStat.get("expected");
		double xg = toDouble(expected == null ? null : expected.get("goals"));
		double xa = toDouble(expected == null ? null : expected.get("assists"));

		// Compute per-match / per90 safely
		double appsSafe = apps > 0 ? apps : 1.0;
		double minutesSafe = minutes > 0 ? minutes : (apps > 0 ? apps * 90.0 : 90.0);

		s.goalsPer90 = (goalsTotal * 90.0) / minutesSafe;
		s.assistsPer90 = (assistsTotal * 90.0) / minutesSafe;
		s.shotsOnTargetPerMatch = shotsOn / appsSafe;
		s.keyPassesPerMatch = keyPasses / appsSafe;
		s.tacklesPerMatch = tacklesTotal / appsSafe;
		s.interceptionsPerMatch = interceptions / appsSafe;
		s.blocksPerMatch = blocks / appsSafe;
		s.clearancesPerMatch = clearances / appsSafe;
		s.goalsConcededPerMatch = conceded / appsSafe;
		// Approx aerial/punches/claims: not directly provided; keep 0 unless provider adds it
		s.aerialActionsPerMatch = 0.0;
		s.cleanSheets = cleanSheets;

		// GK save percentage approximation
		double faced = saves + conceded;
		s.gkSavePct = faced > 0 ? (saves * 100.0) / faced : 0.0;

		s.yellowPerGame = apps > 0 ? (double) yellow / apps : 0.0;
		s.redPerGame = apps > 0 ? (double) red / apps : 0.0;

		s.bigChancesMissed = missed;

		// Expected per90
		s.xgPer90 = xg > 0 && minutesSafe > 0 ? (xg * 90.0) / minutesSafe : 0.0;
		s.xaPer90 = xa > 0 && minutesSafe > 0 ? (xa * 90.0) / minutesSafe : 0.0;
		s.xgxaPer90 = s.xgPer90 + s.xaPer90;

		return s;
	}

	private int disciplineBetter(PlayerStats a, PlayerStats b) {
		double da = a.yellowPerGame * DISCIPLINE_YELLOW_DELTA + a.redPerGame * DISCIPLINE_RED_DELTA;
		double db = b.yellowPerGame * DISCIPLINE_YELLOW_DELTA + b.redPerGame * DISCIPLINE_RED_DELTA;
		if (Math.abs(da - db) < 1e-9) return 0;
		return da < db ? 1 : 0;
	}

	private int cmpDeltaPts(double a, double b, double delta) {
		if (Math.abs(a - b) >= delta) {
			return a > b ? 2 : 0;
		}
		if (a > b) return 1;
		if (b > a) return 0;
		return 0;
	}

	private int cmpDeltaPtsWeighted(double a, double b, double delta, int maxPts) {
		if (Math.abs(a - b) >= delta) return a > b ? maxPts : 0;
		if (a > b) return 1;
		if (b > a) return 0;
		return 0;
	}

	private int cmpSmall(double a, double b) {
		if (a > b) return 1;
		if (b > a) return 0;
		return 0;
	}

	private String httpGet(String url) {
		try {
			LeagueRequest lr = new LeagueRequest(new FullResponse());
			return lr.setRequset(url, apiKey, apiHost);
		} catch (Exception e) {
			return "{}";
		}
	}

	private static int toInt(Object o) {
		if (o == null) return 0;
		if (o instanceof Number) return ((Number) o).intValue();
		try { return Integer.parseInt(String.valueOf(o)); } catch (Exception e) { return 0; }
	}

	private static double toDouble(Object o) {
		if (o == null) return 0.0;
		if (o instanceof Number) return ((Number) o).doubleValue();
		try { return Double.parseDouble(String.valueOf(o)); } catch (Exception e) { return 0.0; }
	}

	private static String str(Object o) {
		return o == null ? null : String.valueOf(o);
	}

	private static String noteDelta(double a, double b) {
		double d = a - b;
		if (Math.abs(d) < 1e-9) return "equal";
		return d > 0 ? ("+" + fmt(d)) : fmt(d);
	}

	private static String notePct(double a, double b) {
		return "p1=" + fmt(a) + "%, p2=" + fmt(b) + "%";
	}

	private static String fmt(double d) {
		return String.format(Locale.ROOT, "%.2f", d);
	}
}


