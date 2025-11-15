package com.example.scoreprediction.compare;

import java.util.ArrayList;
import java.util.List;

public class PlayerScore {
	public int playerId;
	public String name;
	public int season;

	public double totalPositive;
	public double totalNegative;
	public double finalScore;

	public List<CompetitionBreakdown> perCompetition = new ArrayList<>();

	public static class CompetitionBreakdown {
		public String leagueName;
		public Integer leagueId;
		public Double positive;
		public Double negative;
	}
}


