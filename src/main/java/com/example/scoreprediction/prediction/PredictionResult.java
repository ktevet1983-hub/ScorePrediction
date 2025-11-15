package com.example.scoreprediction.prediction;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class PredictionResult {

    public enum Winner {
        TEAM1, TEAM2, DRAW
    }

    public static class BreakdownItem {
        public String metric;
        public int t1;
        public int t2;
        public String note;

        public BreakdownItem() {}

        public BreakdownItem(String metric, int t1, int t2, String note) {
            this.metric = metric;
            this.t1 = t1;
            this.t2 = t2;
            this.note = note;
        }
    }

    public static class RawDataRefs {
        public List<String> used = new ArrayList<>();
        public Map<String, Boolean> cacheHits = new HashMap<>();
    }

    public Winner winner;
    public int scoreTeam1;
    public int scoreTeam2;
    public List<BreakdownItem> breakdown = new ArrayList<>();
    public RawDataRefs rawDataRefs = new RawDataRefs();

    public PredictionResult() {}

    public static PredictionResult draw() {
        PredictionResult pr = new PredictionResult();
        pr.winner = Winner.DRAW;
        pr.scoreTeam1 = 0;
        pr.scoreTeam2 = 0;
        return pr;
    }
}


