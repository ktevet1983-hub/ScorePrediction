package com.example.scoreprediction.prediction;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class PlayerComparisonResult {

	public static class PlayerInfo {
		public int id;
		public String name;
		public int teamId;
		public int leagueId;
		public int season;
		public String position; // raw API position string
		public String role;     // "GK" | "DEF" | "MID" | "FWD"
		public Integer age;
		public String nationality;
	}

	public static class BreakdownItem {
		public String metric;
		public int p1;
		public int p2;
		public String note;

		public BreakdownItem() {}

		public BreakdownItem(String metric, int p1, int p2, String note) {
			this.metric = metric;
			this.p1 = p1;
			this.p2 = p2;
			this.note = note;
		}
	}

	public static class RawDataRefs {
		public List<String> used = new ArrayList<>();
		public Map<String, Boolean> cacheHits = new HashMap<>();
	}

	public String winner; // "PLAYER1" | "PLAYER2" | "DRAW"
	public int scorePlayer1;
	public int scorePlayer2;
	public String positionGroup; // "GK" | "DEF" | "MID" | "FWD"
	public PlayerInfo player1;
	public PlayerInfo player2;
	public List<BreakdownItem> breakdown = new ArrayList<>();
	public RawDataRefs rawDataRefs = new RawDataRefs();

	public static PlayerComparisonResult draw(String groupCode) {
		PlayerComparisonResult pr = new PlayerComparisonResult();
		pr.winner = "DRAW";
		pr.scorePlayer1 = 0;
		pr.scorePlayer2 = 0;
		pr.positionGroup = groupCode;
		return pr;
	}
}


