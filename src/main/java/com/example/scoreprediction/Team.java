package com.example.scoreprediction;

import java.util.List;

public class Team {
    Integer id;
    String name;
	String logo; // Team logo URL from API

	// Squad list as returned by players/squads?team={id}
	List<SquadPlayer> players;

	// Minimal player representation inside a team's squad
	public static class SquadPlayer {
		Integer id; // Player ID
		String name; // Player display name
		Integer age; // Player age
		Integer number; // Squad/jersey number
		String position; // Position in squad (e.g., "Defender")
		String photo; // Player photo URL
	}

}

