package com.example.scoreprediction.prediction;

import com.example.scoreprediction.FullResponse;
import com.example.scoreprediction.LeagueRequest;
import com.google.gson.Gson;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class TeamComparator {

    // Implementation constants
    private static final int FORM_WIN = 3;
    private static final int FORM_DRAW = 1;
    private static final int FORM_LOSS = 0;
    private static final double GOALS_PER_MATCH_DELTA_BIG = 0.3;
    private static final double GA_PER_MATCH_DELTA_BIG = 0.3;
    private static final double DISCIPLINE_YELLOW_DELTA = 0.3;
    private static final double DISCIPLINE_RED_DELTA = 0.05;
    private static final int HOME_AWAY_WINPCT_DELTA_BIG = 10;
    private static final double POWER_INDEX_BIG_GAP = 0.15;
    private static final long CACHE_TTL_MS = 90_000L;

    private final String apiKey;
    private final String apiHost;
    private final Gson gson = new Gson();

    // TTL caches
    private static final Map<String, CacheEntry<String>> standingsCache = new HashMap<>();
    private static final Map<String, CacheEntry<Map<String, Object>>> statsCache = new HashMap<>();
    private static final Map<String, CacheEntry<Map<String, Object>>> h2hCache = new HashMap<>();

    private static class CacheEntry<T> {
        final long timeMs;
        final T payload;
        CacheEntry(long timeMs, T payload) { this.timeMs = timeMs; this.payload = payload; }
    }

    public static class CompareArgs {
        public int leagueId;
        public int season;
        public int teamId1;
        public int teamId2;
        public boolean isGroupStage;
    }

    public TeamComparator(String apiKey, String apiHost) {
        this.apiKey = apiKey;
        this.apiHost = apiHost;
    }

    public PredictionResult compareTeams(CompareArgs args) {
        PredictionResult out = new PredictionResult();
        if (args == null || args.teamId1 <= 0 || args.teamId2 <= 0 || args.leagueId <= 0 || args.season <= 0) {
            return PredictionResult.draw();
        }

        // For raw refs
        out.rawDataRefs.used.add("standings");
        out.rawDataRefs.used.add("teams.statistics");

        try {
            // 1) Standings (cached)
            String standingsKey = "standings:" + args.leagueId + ":" + args.season;
            String standingsJson;
            CacheEntry<String> sEntry = standingsCache.get(standingsKey);
            long now = System.currentTimeMillis();
            if (sEntry != null && (now - sEntry.timeMs) < CACHE_TTL_MS) {
                standingsJson = sEntry.payload;
                out.rawDataRefs.cacheHits.put("standings", true);
            } else {
                String url = "https://" + apiHost + "/standings?season=" + args.season + "&league=" + args.leagueId;
                standingsJson = httpGet(url);
                standingsCache.put(standingsKey, new CacheEntry<>(now, standingsJson));
                out.rawDataRefs.cacheHits.put("standings", false);
            }

            @SuppressWarnings("unchecked")
            Map<String, Object> standingsObj = gson.fromJson(standingsJson, Map.class);
            @SuppressWarnings("unchecked")
            List<Object> response = (List<Object>) standingsObj.get("response");
            if (response == null || response.isEmpty()) {
                return PredictionResult.draw();
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> resp0 = (Map<String, Object>) response.get(0);
            @SuppressWarnings("unchecked")
            Map<String, Object> league = (Map<String, Object>) resp0.get("league");
            if (league == null) return PredictionResult.draw();
            List<Object> standingsBlocks = asList(league.get("standings"));
            if (standingsBlocks == null || standingsBlocks.isEmpty()) return PredictionResult.draw();
            // Flatten all standings blocks so we can find any team regardless of group
            List<Map<String, Object>> table = new ArrayList<>();
            for (Object blk : standingsBlocks) {
                if (blk instanceof List) {
                    @SuppressWarnings("unchecked")
                    List<Map<String, Object>> rows = (List<Map<String, Object>>) blk;
                    table.addAll(rows);
                }
            }

            // Find rows for teamId1/teamId2
            Map<String, Object> row1 = null;
            Map<String, Object> row2 = null;
            for (Map<String, Object> row : table) {
                @SuppressWarnings("unchecked")
                Map<String, Object> t = (Map<String, Object>) row.get("team");
                if (t == null) continue;
                int tid = toInt(t.get("id"));
                if (tid == args.teamId1) row1 = row;
                if (tid == args.teamId2) row2 = row;
            }
            if (row1 == null || row2 == null) {
                return PredictionResult.draw();
            }

            // Pull standings-derived fields
            int points1 = toInt(row1.get("points"));
            int points2 = toInt(row2.get("points"));
            int goalsDiff1 = toInt(row1.get("goalsDiff"));
            int goalsDiff2 = toInt(row2.get("goalsDiff"));
            String form1 = str(row1.get("form"));
            String form2 = str(row2.get("form"));

            @SuppressWarnings("unchecked")
            Map<String, Object> all1 = (Map<String, Object>) row1.get("all");
            @SuppressWarnings("unchecked")
            Map<String, Object> all2 = (Map<String, Object>) row2.get("all");
            @SuppressWarnings("unchecked")
            Map<String, Object> home1 = (Map<String, Object>) row1.get("home");
            @SuppressWarnings("unchecked")
            Map<String, Object> away1 = (Map<String, Object>) row1.get("away");
            @SuppressWarnings("unchecked")
            Map<String, Object> home2 = (Map<String, Object>) row2.get("home");
            @SuppressWarnings("unchecked")
            Map<String, Object> away2 = (Map<String, Object>) row2.get("away");
            if (all1 == null || all2 == null) return PredictionResult.draw();
            int played1 = toInt(all1.get("played"));
            int played2 = toInt(all2.get("played"));
            int winsHome1 = home1 == null ? 0 : toInt(home1.get("win"));
            int winsAway1 = away1 == null ? 0 : toInt(away1.get("win"));
            int winsHome2 = home2 == null ? 0 : toInt(home2.get("win"));
            int winsAway2 = away2 == null ? 0 : toInt(away2.get("win"));

            // 2) Team statistics for both teams (cached)
            Stats stats1 = fetchTeamStats(args.leagueId, args.season, args.teamId1, out);
            Stats stats2 = fetchTeamStats(args.leagueId, args.season, args.teamId2, out);
            if (stats1 == null || stats2 == null) {
                // Safely return DRAW on missing
                return PredictionResult.draw();
            }

            // 3) Optional head-to-head last 5 (cached) - only fetch when group stage to minimize calls
            HeadToHead h2h = null;
            if (args.isGroupStage) {
                h2h = fetchHeadToHead(args.teamId1, args.teamId2, out);
            }

            // Scoring
            int t1 = 0, t2 = 0;
            List<PredictionResult.BreakdownItem> breakdown = out.breakdown;

            // Metric: Goal Difference (standings)
            int mGD1 = 0, mGD2 = 0;
            if (goalsDiff1 > goalsDiff2) { mGD1 = 1; }
            else if (goalsDiff2 > goalsDiff1) { mGD2 = 1; }
            breakdown.add(new PredictionResult.BreakdownItem("standings.goalDiff", mGD1, mGD2, noteDelta(goalsDiff1, goalsDiff2)));
            if (args.isGroupStage) {
                // Extra weight +1 to winner of GD
                if (mGD1 > mGD2) mGD1 += 1;
                else if (mGD2 > mGD1) mGD2 += 1;
                breakdown.add(new PredictionResult.BreakdownItem("groupstage.weight.gd", mGD1 > mGD2 ? 1 : 0, mGD2 > mGD1 ? 1 : 0, "GD weighted for groups"));
            }
            t1 += mGD1; t2 += mGD2;

            // Metric: Recent Form score
            int formScore1 = formScore(form1);
            int formScore2 = formScore(form2);
            int mForm1 = 0, mForm2 = 0;
            if (formScore1 > formScore2) mForm1 = 1; else if (formScore2 > formScore1) mForm2 = 1;
            breakdown.add(new PredictionResult.BreakdownItem("standings.form", mForm1, mForm2, "form1=" + formScore1 + ", form2=" + formScore2));
            t1 += mForm1; t2 += mForm2;

            // Metric: Home/Away split win% (approximate overall strengths)
            int totalHomeGames1 = (home1 == null ? 0 : toInt(home1.get("played")));
            int totalAwayGames2 = (away2 == null ? 0 : toInt(away2.get("played")));
            int totalHomeGames2 = (home2 == null ? 0 : toInt(home2.get("played")));
            int totalAwayGames1 = (away1 == null ? 0 : toInt(away1.get("played")));
            int homePct1 = percent(winsHome1, totalHomeGames1);
            int awayPct2 = percent(winsAway2, totalAwayGames2);
            int homePct2 = percent(winsHome2, totalHomeGames2);
            int awayPct1 = percent(winsAway1, totalAwayGames1);
            int haScore1 = 0, haScore2 = 0;
            // Use average of home and away win rates
            int winMix1 = (homePct1 + awayPct1) / 2;
            int winMix2 = (homePct2 + awayPct2) / 2;
            if (Math.abs(winMix1 - winMix2) >= HOME_AWAY_WINPCT_DELTA_BIG) {
                if (winMix1 > winMix2) haScore1 = 1; else haScore2 = 1;
            }
            breakdown.add(new PredictionResult.BreakdownItem("standings.homeAwayWinPct", haScore1, haScore2, "mix1=" + winMix1 + ", mix2=" + winMix2));
            t1 += haScore1; t2 += haScore2;

            // Metric: Goals for per match (team statistics)
            int gfp1 = cmpDelta(stats1.goalsForPerMatch, stats2.goalsForPerMatch, GOALS_PER_MATCH_DELTA_BIG);
            breakdown.add(new PredictionResult.BreakdownItem("stats.goalsForPerMatch", gfp1, 1 - gfp1, "t1=" + fmt(stats1.goalsForPerMatch) + ", t2=" + fmt(stats2.goalsForPerMatch)));
            t1 += gfp1; t2 += (1 - gfp1);

            // Metric: Goals against per match (lower is better)
            int gap1 = cmpDelta(stats2.goalsAgainstPerMatch, stats1.goalsAgainstPerMatch, GA_PER_MATCH_DELTA_BIG); // invert
            breakdown.add(new PredictionResult.BreakdownItem("stats.goalsAgainstPerMatch", gap1, 1 - gap1, "t1=" + fmt(stats1.goalsAgainstPerMatch) + ", t2=" + fmt(stats2.goalsAgainstPerMatch)));
            t1 += gap1; t2 += (1 - gap1);
            if (args.isGroupStage) {
                // Fewer goals conceded weighted +1
                int bonus1 = 0, bonus2 = 0;
                if (stats1.goalsAgainstPerMatch < stats2.goalsAgainstPerMatch) bonus1 = 1;
                else if (stats2.goalsAgainstPerMatch < stats1.goalsAgainstPerMatch) bonus2 = 1;
                breakdown.add(new PredictionResult.BreakdownItem("groupstage.weight.ga", bonus1, bonus2, "GA weighted for groups"));
                t1 += bonus1; t2 += bonus2;
            }

            // Metric: Clean sheets
            int cs1 = Integer.compare(stats1.cleanSheets, stats2.cleanSheets);
            int csScore1 = cs1 > 0 ? 1 : (cs1 < 0 ? 0 : 0);
            breakdown.add(new PredictionResult.BreakdownItem("stats.cleanSheets", csScore1, 1 - csScore1, "t1=" + stats1.cleanSheets + ", t2=" + stats2.cleanSheets));
            t1 += csScore1; t2 += (1 - csScore1);

            // Metric: Failed to score (lower is better)
            int fts1 = Integer.compare(stats2.failedToScore, stats1.failedToScore); // invert
            int ftsScore1 = fts1 > 0 ? 1 : (fts1 < 0 ? 0 : 0);
            breakdown.add(new PredictionResult.BreakdownItem("stats.failedToScore", ftsScore1, 1 - ftsScore1, "t1=" + stats1.failedToScore + ", t2=" + stats2.failedToScore));
            t1 += ftsScore1; t2 += (1 - ftsScore1);

            // Metric: Discipline (yellow/red per game; lower is better)
            int disc1 = discCompare(stats1, stats2);
            breakdown.add(new PredictionResult.BreakdownItem("stats.discipline", disc1, 1 - disc1, "lower cards per game is better"));
            t1 += disc1; t2 += (1 - disc1);

            // Metric: Strength tier via power index (PPG + GD/Match)
            double ppg1 = played1 > 0 ? (double) points1 / played1 : 0d;
            double ppg2 = played2 > 0 ? (double) points2 / played2 : 0d;
            double pi1 = 0.6 * ppg1 + 0.4 * (played1 > 0 ? (double) goalsDiff1 / played1 : 0d);
            double pi2 = 0.6 * ppg2 + 0.4 * (played2 > 0 ? (double) goalsDiff2 / played2 : 0d);
            int tierScore1 = 0, tierScore2 = 0;
            if (Math.abs(pi1 - pi2) >= POWER_INDEX_BIG_GAP) {
                if (pi1 > pi2) tierScore1 = 1; else tierScore2 = 1;
            }
            breakdown.add(new PredictionResult.BreakdownItem("tier.powerIndex", tierScore1, tierScore2, "pi1=" + fmt(pi1) + ", pi2=" + fmt(pi2)));
            t1 += tierScore1; t2 += tierScore2;

            // Metric: Head-to-head (last 5) - only if fetched
            if (h2h != null) {
                int h2hScore1 = 0, h2hScore2 = 0;
                if (h2h.t1Wins > h2h.t2Wins) h2hScore1 = 1;
                else if (h2h.t2Wins > h2h.t1Wins) h2hScore2 = 1;
                breakdown.add(new PredictionResult.BreakdownItem("headtohead.last5", h2hScore1, h2hScore2, "t1W=" + h2h.t1Wins + ", t2W=" + h2h.t2Wins + ", D=" + h2h.draws));
                t1 += h2hScore1; t2 += h2hScore2;

                if (args.isGroupStage) {
                    // Bonus +2 for strong dominance (UEFA tie-break nuance)
                    int bonus1 = 0, bonus2 = 0;
                    if (Math.abs(h2h.t1Wins - h2h.t2Wins) >= 3) {
                        if (h2h.t1Wins > h2h.t2Wins) bonus1 = 2; else bonus2 = 2;
                    }
                    breakdown.add(new PredictionResult.BreakdownItem("groupstage.bonus.h2hDominance", bonus1, bonus2, "UEFA head-to-head dominance"));
                    t1 += bonus1; t2 += bonus2;
                }
            }

            // Tie-breaking for group-stage: form, else DRAW
            if (t1 == t2 && args.isGroupStage) {
                if (formScore1 > formScore2) {
                    breakdown.add(new PredictionResult.BreakdownItem("groupstage.tiebreak.form", 1, 0, "Form tie-breaker"));
                    t1 += 1;
                } else if (formScore2 > formScore1) {
                    breakdown.add(new PredictionResult.BreakdownItem("groupstage.tiebreak.form", 0, 1, "Form tie-breaker"));
                    t2 += 1;
                }
            }

            out.scoreTeam1 = t1;
            out.scoreTeam2 = t2;
            if (t1 == t2) out.winner = PredictionResult.Winner.DRAW;
            else if (t1 > t2) out.winner = PredictionResult.Winner.TEAM1;
            else out.winner = PredictionResult.Winner.TEAM2;
            return out;
        } catch (Exception e) {
            return PredictionResult.draw();
        }
    }

    private Stats fetchTeamStats(int leagueId, int season, int teamId, PredictionResult out) {
        String key = "stats:" + leagueId + ":" + season + ":" + teamId;
        CacheEntry<Map<String, Object>> ce = statsCache.get(key);
        long now = System.currentTimeMillis();
        Map<String, Object> obj;
        if (ce != null && (now - ce.timeMs) < CACHE_TTL_MS) {
            obj = ce.payload;
            out.rawDataRefs.cacheHits.put("teams.statistics:" + (out.rawDataRefs.cacheHits.containsKey("teams.statistics:t1") ? "t2" : "t1"), true);
        } else {
            String url = "https://" + apiHost + "/teams/statistics?league=" + leagueId + "&season=" + season + "&team=" + teamId;
            String json = httpGet(url);
            @SuppressWarnings("unchecked")
            Map<String, Object> parsed = gson.fromJson(json, Map.class);
            obj = parsed;
            statsCache.put(key, new CacheEntry<>(now, obj));
            out.rawDataRefs.cacheHits.put("teams.statistics:" + (out.rawDataRefs.cacheHits.containsKey("teams.statistics:t1") ? "t2" : "t1"), false);
        }
        return parseStats(obj);
    }

    private static class Stats {
        double goalsForPerMatch;
        double goalsAgainstPerMatch;
        int cleanSheets;
        int failedToScore;
        double yellowPerGame;
        double redPerGame;
    }

    private Stats parseStats(Map<String, Object> obj) {
        if (obj == null) return null;
        @SuppressWarnings("unchecked")
        Map<String, Object> response = (Map<String, Object>) obj.get("response");
        if (response == null) return null;
        Stats s = new Stats();
        // goals average
        @SuppressWarnings("unchecked")
        Map<String, Object> goals = (Map<String, Object>) response.get("goals");
        if (goals != null) {
            @SuppressWarnings("unchecked")
            Map<String, Object> gf = (Map<String, Object>) goals.get("for");
            @SuppressWarnings("unchecked")
            Map<String, Object> ga = (Map<String, Object>) goals.get("against");
            s.goalsForPerMatch = parseAverage(gf);
            s.goalsAgainstPerMatch = parseAverage(ga);
        }
        // clean sheets, failed to score
        @SuppressWarnings("unchecked")
        Map<String, Object> cleanSheet = (Map<String, Object>) response.get("clean_sheet");
        if (cleanSheet != null) {
            s.cleanSheets = toInt(cleanSheet.get("total"));
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> failedToScore = (Map<String, Object>) response.get("failed_to_score");
        if (failedToScore != null) {
            s.failedToScore = toInt(failedToScore.get("total"));
        }
        // discipline via cards and fixtures played
        int played = 0;
        @SuppressWarnings("unchecked")
        Map<String, Object> fixtures = (Map<String, Object>) response.get("fixtures");
        if (fixtures != null) {
            Object playedObj = fixtures.get("played");
            if (playedObj instanceof Number) {
                played = ((Number) playedObj).intValue();
            } else if (playedObj instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> playedMap = (Map<String, Object>) playedObj;
                played = toInt(playedMap.get("total"));
            }
        }
        int yellowTotal = sumCardTotals(response, "yellow");
        int redTotal = sumCardTotals(response, "red");
        s.yellowPerGame = played > 0 ? (double) yellowTotal / played : 0d;
        s.redPerGame = played > 0 ? (double) redTotal / played : 0d;
        return s;
    }

    private int sumCardTotals(Map<String, Object> response, String color) {
        @SuppressWarnings("unchecked")
        Map<String, Object> cards = (Map<String, Object>) response.get("cards");
        if (cards == null) return 0;
        @SuppressWarnings("unchecked")
        Map<String, Object> cardColor = (Map<String, Object>) cards.get(color);
        if (cardColor == null) return 0;
        int sum = 0;
        for (Object v : cardColor.values()) {
            if (v instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> m = (Map<String, Object>) v;
                sum += toInt(m.get("total"));
            } else if (v instanceof Number) {
                sum += ((Number) v).intValue();
            }
        }
        return sum;
    }

    private double parseAverage(Map<String, Object> forOrAgainst) {
        if (forOrAgainst == null) return 0d;
        @SuppressWarnings("unchecked")
        Map<String, Object> avg = (Map<String, Object>) forOrAgainst.get("average");
        if (avg != null) {
            Object total = avg.get("total");
            if (total instanceof Number) return ((Number) total).doubleValue();
            try {
                return Double.parseDouble(String.valueOf(total));
            } catch (Exception ignored) {}
        }
        Object total = forOrAgainst.get("total");
        if (total instanceof Number) return ((Number) total).doubleValue();
        try { return Double.parseDouble(String.valueOf(total)); } catch (Exception e) { return 0d; }
    }

    private HeadToHead fetchHeadToHead(int team1, int team2, PredictionResult out) {
        String key = "h2h:" + Math.min(team1, team2) + ":" + Math.max(team1, team2);
        CacheEntry<Map<String, Object>> ce = h2hCache.get(key);
        long now = System.currentTimeMillis();
        Map<String, Object> obj;
        if (ce != null && (now - ce.timeMs) < CACHE_TTL_MS) {
            obj = ce.payload;
            out.rawDataRefs.cacheHits.put("headtohead", true);
        } else {
            String url = "https://" + apiHost + "/fixtures/headtohead?h2h=" + team1 + "-" + team2 + "&last=5";
            String json = httpGet(url);
            @SuppressWarnings("unchecked")
            Map<String, Object> parsed = gson.fromJson(json, Map.class);
            obj = parsed;
            h2hCache.put(key, new CacheEntry<>(now, obj));
            out.rawDataRefs.cacheHits.put("headtohead", false);
        }
        return parseHeadToHead(obj, team1, team2);
    }

    private static class HeadToHead {
        int t1Wins;
        int t2Wins;
        int draws;
    }

    private HeadToHead parseHeadToHead(Map<String, Object> obj, int team1, int team2) {
        if (obj == null) return null;
        @SuppressWarnings("unchecked")
        List<Object> resp = (List<Object>) obj.get("response");
        if (resp == null) return null;
        HeadToHead h = new HeadToHead();
        for (Object o : resp) {
            if (!(o instanceof Map)) continue;
            @SuppressWarnings("unchecked")
            Map<String, Object> fix = (Map<String, Object>) o;
            @SuppressWarnings("unchecked")
            Map<String, Object> teams = (Map<String, Object>) fix.get("teams");
            if (teams == null) continue;
            @SuppressWarnings("unchecked")
            Map<String, Object> home = (Map<String, Object>) teams.get("home");
            @SuppressWarnings("unchecked")
            Map<String, Object> away = (Map<String, Object>) teams.get("away");
            int hid = toInt(home == null ? null : home.get("id"));
            int aid = toInt(away == null ? null : away.get("id"));
            @SuppressWarnings("unchecked")
            Map<String, Object> goals = (Map<String, Object>) fix.get("goals");
            int gh = toInt(goals == null ? null : goals.get("home"));
            int ga = toInt(goals == null ? null : goals.get("away"));
            int res = Integer.compare(gh, ga);
            if (res == 0) { h.draws++; }
            else {
                boolean homeWin = res > 0;
                int winnerTeam = homeWin ? hid : aid;
                if (winnerTeam == team1) h.t1Wins++;
                else if (winnerTeam == team2) h.t2Wins++;
            }
        }
        return h;
    }

    private String httpGet(String url) {
        try {
            LeagueRequest lr = new LeagueRequest(new FullResponse());
            return lr.setRequset(url, apiKey, apiHost);
        } catch (Exception e) {
            return "{}";
        }
    }

    private static String noteDelta(int a, int b) {
        int d = a - b;
        if (d == 0) return "equal";
        return d > 0 ? ("+" + d) : String.valueOf(d);
    }

    private static int cmpDelta(double a, double b, double delta) {
        if (Math.abs(a - b) < delta) return 0;
        return a > b ? 1 : 0;
    }

    private static int discCompare(Stats s1, Stats s2) {
        // Weighted discipline: yellow 0.3, red 0.05 (per game)
        double d1 = s1.yellowPerGame * DISCIPLINE_YELLOW_DELTA + s1.redPerGame * DISCIPLINE_RED_DELTA;
        double d2 = s2.yellowPerGame * DISCIPLINE_YELLOW_DELTA + s2.redPerGame * DISCIPLINE_RED_DELTA;
        if (Math.abs(d1 - d2) < 1e-6) return 0;
        return d1 < d2 ? 1 : 0;
    }

    private static int formScore(String form) {
        if (form == null || form.isBlank()) return 0;
        int score = 0;
        String f = form.trim().toUpperCase(Locale.ROOT);
        for (int i = 0; i < f.length(); i++) {
            char c = f.charAt(i);
            if (c == 'W') score += FORM_WIN;
            else if (c == 'D') score += FORM_DRAW;
            else if (c == 'L') score += FORM_LOSS;
        }
        return score;
    }

    @SuppressWarnings("unchecked")
    private static List<Object> asList(Object o) {
        if (o instanceof List) return (List<Object>) o;
        if (o == null) return null;
        List<Object> l = new ArrayList<>();
        l.add(o);
        return l;
    }

    private static int toInt(Object o) {
        if (o == null) return 0;
        if (o instanceof Number) return ((Number) o).intValue();
        try { return Integer.parseInt(String.valueOf(o)); } catch (Exception e) { return 0; }
    }

    private static String str(Object o) {
        return o == null ? "" : String.valueOf(o);
    }

    private static String fmt(double d) {
        return String.format(Locale.ROOT, "%.2f", d);
    }

    private static int percent(int wins, int played) {
        if (played <= 0) return 0;
        double pct = (double) wins * 100.0 / (double) played;
        return (int) Math.round(pct);
    }
}


