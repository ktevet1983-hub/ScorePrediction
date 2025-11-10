package com.example.scoreprediction;

public final class LeagueUtils {

	public static boolean isChampionsLeague(String leagueId) {
		return "2".equals(leagueId);
	}

	public static boolean isEuropaLeague(String leagueId) {
		return "3".equals(leagueId);
	}

	public static boolean isWorldCup(String leagueId) {
		return "1".equals(leagueId);
	}

	private LeagueUtils() {}
}


