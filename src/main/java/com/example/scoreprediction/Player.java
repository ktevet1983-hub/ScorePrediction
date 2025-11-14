package com.example.scoreprediction;

import java.util.List;
import java.util.Map;

public class Player {

    int age; // Player age in years
    int id; // API-Football unique player identifier
    boolean injured; // Whether the player is currently injured
    String firstname; // Player given name
    String lastname; // Player family/surname
    String name; // Common/short display name
    String nationality; // Primary nationality
    String weight; // Weight string as returned by API (e.g., "83 kg")
    Map birth; // Existing birth map (kept for backward-compatibility)

    // --- Extended core profile fields (players/profiles) ---
    String height; // Height string as returned by API (e.g., "187 cm")
    String photo; // URL to the player's photo from API

    // Typed birth info alongside legacy Map above (do not remove the Map)
    BirthInfo birthInfo; // Structured birth information (date/place/country)

    // Position/number at a high level (may vary per team/season in statistics)
    String primaryPosition; // Primary playing position (e.g., "Attacker")
    Integer currentNumber; // Current or most recent jersey number when available

    // --- Statistics per team/league/season (players/profiles.statistics[]) ---
    List<StatisticEntry> statistics; // Season statistics entries grouped by team and league

    // --- Team history/links (players/teams) ---
    List<PlayerTeam> teams; // Teams the player has been associated with and seasons played

    // ---------------------- Nested Types ----------------------

    // Birth information as provided in players/profiles.player.birth
    public static class BirthInfo {
        String date; // Birth date in ISO string format (YYYY-MM-DD)
        String place; // City or place of birth
        String country; // Country of birth
    }

    // Minimal team info as embedded within API responses
    public static class TeamInfo {
        Integer id; // Team ID
        String name; // Team name
        String logo; // Team logo URL
    }

    // Minimal league info as embedded within API responses
    public static class LeagueInfo {
        Integer id; // League ID
        String name; // League name
        String country; // League country
        String logo; // League logo URL
        String flag; // Country flag URL for the league (if provided)
        Integer season; // Season year associated with the statistics
    }

    // One statistics entry corresponds to a specific team + league + season
    public static class StatisticEntry {
        Team team; // Team context for these stats
        League league; // League context for these stats
        Integer season; // Season year for this statistics entry
        Games games; // Appearances, minutes, role
        Substitutes substitutes; // Substitution data
        Shots shots; // Shot totals
        Goals goals; // Goals, assists, saves
        Passes passes; // Passing stats
        Tackles tackles; // Defensive actions
        Duels duels; // Duels contested and won
        Dribbles dribbles; // Dribbling attempts/success
        Fouls fouls; // Fouls drawn/committed
        Cards cards; // Discipline cards
        Penalty penalty; // Penalty-related stats
    }

    public static class Games {
        Integer appearences; // Number of appearances
        Integer lineups; // Times in starting lineup
        Integer minutes; // Minutes played
        Integer number; // Jersey number during these games
        String position; // Position played during these games
        Double rating; // Match rating (as numeric value when parsable)
        Boolean captain; // Whether the player served as captain
    }

    public static class Substitutes {
        Integer in; // Times subbed on
        Integer out; // Times subbed off
        Integer bench; // Times named on bench
    }

    public static class Shots {
        Integer total; // Total shots
        Integer on; // Shots on target
    }

    public static class Goals {
        Integer total; // Goals scored
        Integer conceded; // Goals conceded (for GK/DF contexts)
        Integer assists; // Assists provided
        Integer saves; // Saves made (for goalkeepers)
    }

    public static class Passes {
        Integer total; // Total passes
        Integer key; // Key passes
        String accuracy; // Pass accuracy percentage as string (e.g., "80%")
    }

    public static class Tackles {
        Integer total; // Total tackles
        Integer blocks; // Blocks
        Integer interceptions; // Interceptions
    }

    public static class Duels {
        Integer total; // Total duels
        Integer won; // Duels won
    }

    public static class Dribbles {
        Integer attempts; // Dribble attempts
        Integer success; // Successful dribbles
        Integer past; // Times dribbled past opponents
    }

    public static class Fouls {
        Integer drawn; // Fouls drawn
        Integer committed; // Fouls committed
    }

    public static class Cards {
        Integer yellow; // Yellow cards
        Integer yellowred; // Second yellow leading to red
        Integer red; // Red cards
    }

    public static class Penalty {
        Integer won; // Penalties won
        Integer commited; // Penalties committed
        Integer scored; // Penalties scored
        Integer missed; // Penalties missed
        Integer saved; // Penalties saved (goalkeeper)
    }

    // Membership/association with teams as returned by players/teams
    public static class PlayerTeam {
        Team team; // Team details
        League league; // Associated league context when applicable
        List<Integer> seasons; // Seasons in which the player featured for the team
        Integer number; // Jersey number used with this team (if provided)
        String position; // Position with this team (if provided)
        Boolean captain; // Captain status with this team (if provided)
    }



}


