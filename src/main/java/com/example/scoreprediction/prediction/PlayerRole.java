package com.example.scoreprediction.prediction;

public enum PlayerRole {
	GOALKEEPER("GK"),
	DEFENDER("DEF"),
	MIDFIELDER("MID"),
	FORWARD("FWD");

	private final String groupCode;

	PlayerRole(String groupCode) {
		this.groupCode = groupCode;
	}

	public String groupCode() {
		return groupCode;
	}

	public static PlayerRole fromApiPosition(String pos) {
		if (pos == null) return null;
		String p = pos.trim().toUpperCase();
		if (p.isEmpty()) return null;
		// Common API-Football position strings and abbreviations
		if (p.equals("G") || p.equals("GK") || p.contains("GOALKEEPER")) return GOALKEEPER;
		if (p.equals("D") || p.equals("DF") || p.startsWith("DC") || p.startsWith("DR") || p.startsWith("DL") || p.contains("DEF")) return DEFENDER;
		if (p.equals("M") || p.equals("MF") || p.contains("MID") || p.equals("AM") || p.equals("DM") || p.startsWith("MC")) return MIDFIELDER;
		if (p.equals("F") || p.equals("FW") || p.equals("ST") || p.equals("LW") || p.equals("RW") || p.contains("FORWARD") || p.contains("ATTACK")) return FORWARD;
		// Fallbacks for variants
		if (p.contains("WING")) return FORWARD;
		if (p.contains("STRIK")) return FORWARD;
		if (p.contains("CENTER BACK") || p.contains("CENTRE BACK")) return DEFENDER;
		if (p.contains("FULL BACK") || p.contains("BACK")) return DEFENDER;
		if (p.contains("MIDF")) return MIDFIELDER;
		return null;
	}
}


