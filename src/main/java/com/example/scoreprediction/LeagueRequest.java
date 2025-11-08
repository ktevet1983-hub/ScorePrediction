package com.example.scoreprediction;

import com.google.gson.Gson;
import com.google.gson.internal.LinkedTreeMap;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Collections;

public class LeagueRequest {

    static FullResponse fullResponse;

    public LeagueRequest(FullResponse fullResponse) {
        this.fullResponse = fullResponse;
    }

    public String setRequset(String url, String token, String host) throws Exception {
        Gson gson = new Gson();
        HttpRequest getRequest = HttpRequest.newBuilder()
                .uri(new URI(url))
                .header("x-rapidapi-key", token)
                .header("x-rapidapi-host", host)
                .build();
        HttpClient httpClient = HttpClient.newHttpClient();
        HttpResponse<String> getresponse = httpClient.send(getRequest, HttpResponse.BodyHandlers.ofString());

        return getresponse.body();
    }

    public static void setLiga(String url, String token, String host) throws Exception {
        Gson gson = new Gson();
        HttpRequest getRequest = HttpRequest.newBuilder()
                .uri(new URI(url))
                .header("x-rapidapi-key", token)
                .header("x-rapidapi-host", host)
                .build();
        HttpClient httpClient = HttpClient.newHttpClient();
        HttpResponse<String> getresponse = httpClient.send(getRequest, HttpResponse.BodyHandlers.ofString());
        fullResponse = gson.fromJson(getresponse.body(), FullResponse.class);
        System.out.println("");
        System.out.println("");
        System.out.println("id: " + fullResponse.getResponse().get(0).getLeague().getId());
        System.out.println(fullResponse.getResponse().get(0).getLeague().getSeason());
        System.out.println(fullResponse.getResponse().get(0).getLeague().getCountry());
        System.out.println(fullResponse.getResponse().get(0).getLeague().getName());
        if (fullResponse.getResponse().get(0).getLeague().getId() != 1 && fullResponse.getResponse().get(0).getLeague().getId() != 2
                && fullResponse.getResponse().get(0).getLeague().getId() != 3){
            System.out.println("");
            System.out.println("rank    team             PL    W     L     D    SC      AG    GD    PO");
            System.out.println("-----------------------------------------------------------------------");
        }
        ArrayList<Object> arr = new ArrayList<>();
        Collections.addAll(arr, fullResponse.getResponse().get(0).getLeague().getStandings());

        for (int i = 0; i < fullResponse.getResponse().get(0).getLeague().getStandings().length; i++) {
            if (fullResponse.getResponse().get(0).getLeague().getId() == 1 || fullResponse.getResponse().get(0).getLeague().getId() == 2
                    || fullResponse.getResponse().get(0).getLeague().getId() == 3) {
                ArrayList<LinkedTreeMap<String, Object>> array = (ArrayList) fullResponse.getResponse().get(0).getLeague().getStandings()[i];
                String group = null;
                for (LinkedTreeMap<String, Object> groupObj : array) {
                    group = (String) groupObj.get("group");
                }
                System.out.println("");
                System.out.println(group);
                System.out.println("");
                System.out.println("rank    team             PL    W     L     D    SC      AG    GD    PO");
                System.out.println("-----------------------------------------------------------------------");
            }
            ArrayList<LinkedTreeMap<String, Object>> standing = (ArrayList) fullResponse.getResponse().get(0).getLeague().getStandings()[i];
            for (LinkedTreeMap<String, Object> team : standing) {

                LinkedTreeMap<String, Object> teamObj = (LinkedTreeMap<String, Object>) team.get("team");
                LinkedTreeMap<String, Object> allObj = (LinkedTreeMap<String, Object>) team.get("all");
                LinkedTreeMap<String, Object> goalsObj = (LinkedTreeMap<String, Object>) allObj.get("goals");

                String intRank = new DecimalFormat("#").format(team.get("rank"));
                String intpoints = new DecimalFormat("#").format(team.get("points"));
                String intgoalsDiff = new DecimalFormat("#").format(team.get("goalsDiff"));
                String intPlayed = new DecimalFormat("#").format(allObj.get("played"));
                String intWin = new DecimalFormat("#").format(allObj.get("win"));
                String intLose = new DecimalFormat("#").format(allObj.get("lose"));
                String intDraw = new DecimalFormat("#").format(allObj.get("draw"));
                String intFor = new DecimalFormat("#").format(goalsObj.get("for"));
                String intAgainst = new DecimalFormat("#").format(goalsObj.get("against"));
                String teamName = String.valueOf(teamObj.get("name"));
                if (teamName.length() > 17) {
                    teamName = teamName.substring(0, 17);
                }

                System.out.print(" " + intRank);
                System.out.print("    " + teamName);

                if (teamName.length() == 3) {
                    if (intRank.length() == 2) {
                        System.out.printf("%16s", intPlayed);
                        System.out.printf("%6s", intWin);
                        System.out.printf("%6s", intLose);
                        System.out.printf("%6s", intDraw);
                        System.out.printf("%6s", intFor);
                        System.out.printf("%8s", intAgainst);
                        System.out.printf("%6s", intgoalsDiff);
                        System.out.printf("%6s", intpoints);
                        System.out.println();
                    } else { // else of rank

                        System.out.printf("%17s", intPlayed);
                        System.out.printf("%6s", intWin);
                        System.out.printf("%6s", intLose);
                        System.out.printf("%6s", intDraw);
                        System.out.printf("%6s", intFor);
                        System.out.printf("%8s", intAgainst);
                        System.out.printf("%6s", intgoalsDiff);
                        System.out.printf("%6s", intpoints);
                        System.out.println();
                    }
                }

                if (teamName.length() == 4) {
                    if (intRank.length() == 2) {
                        System.out.printf("%15s", intPlayed);
                        System.out.printf("%6s", intWin);
                        System.out.printf("%6s", intLose);
                        System.out.printf("%6s", intDraw);
                        System.out.printf("%6s", intFor);
                        System.out.printf("%8s", intAgainst);
                        System.out.printf("%6s", intgoalsDiff);
                        System.out.printf("%6s", intpoints);
                        System.out.println();
                    } else { // else of rank

                        System.out.printf("%16s", intPlayed);
                        System.out.printf("%6s", intWin);
                        System.out.printf("%6s", intLose);
                        System.out.printf("%6s", intDraw);
                        System.out.printf("%6s", intFor);
                        System.out.printf("%8s", intAgainst);
                        System.out.printf("%6s", intgoalsDiff);
                        System.out.printf("%6s", intpoints);
                        System.out.println();
                    }
                }

                if (teamName.length() == 5) {
                    if (intRank.length() == 2) {
                        System.out.printf("%14s", intPlayed);
                        System.out.printf("%6s", intWin);
                        System.out.printf("%6s", intLose);
                        System.out.printf("%6s", intDraw);
                        System.out.printf("%6s", intFor);
                        System.out.printf("%8s", intAgainst);
                        System.out.printf("%6s", intgoalsDiff);
                        System.out.printf("%6s", intpoints);
                        System.out.println();
                    } else { // else of rank

                        System.out.printf("%15s", intPlayed);
                        System.out.printf("%6s", intWin);
                        System.out.printf("%6s", intLose);
                        System.out.printf("%6s", intDraw);
                        System.out.printf("%6s", intFor);
                        System.out.printf("%8s", intAgainst);
                        System.out.printf("%6s", intgoalsDiff);
                        System.out.printf("%6s", intpoints);
                        System.out.println();
                    }
                }


                if (teamName.length() == 6) {
                    if (intRank.length() == 2) {
                        System.out.printf("%13s", intPlayed);
                        System.out.printf("%6s", intWin);
                        System.out.printf("%6s", intLose);
                        System.out.printf("%6s", intDraw);
                        System.out.printf("%6s", intFor);
                        System.out.printf("%8s", intAgainst);
                        System.out.printf("%6s", intgoalsDiff);
                        System.out.printf("%6s", intpoints);
                        System.out.println();
                    } else { // else of rank

                        System.out.printf("%14s", intPlayed);
                        System.out.printf("%6s", intWin);
                        System.out.printf("%6s", intLose);
                        System.out.printf("%6s", intDraw);
                        System.out.printf("%6s", intFor);
                        System.out.printf("%8s", intAgainst);
                        System.out.printf("%6s", intgoalsDiff);
                        System.out.printf("%6s", intpoints);
                        System.out.println();
                    }

                }

                if (teamName.length() == 7) {
                    if (intRank.length() == 2) {
                        System.out.printf("%12s", intPlayed);
                        System.out.printf("%6s", intWin);
                        System.out.printf("%6s", intLose);
                        System.out.printf("%6s", intDraw);
                        System.out.printf("%6s", intFor);
                        System.out.printf("%8s", intAgainst);
                        System.out.printf("%6s", intgoalsDiff);
                        System.out.printf("%6s", intpoints);
                        System.out.println();
                    } else { // else of rank

                        System.out.printf("%13s", intPlayed);
                        System.out.printf("%6s", intWin);
                        System.out.printf("%6s", intLose);
                        System.out.printf("%6s", intDraw);
                        System.out.printf("%6s", intFor);
                        System.out.printf("%8s", intAgainst);
                        System.out.printf("%6s", intgoalsDiff);
                        System.out.printf("%6s", intpoints);
                        System.out.println();
                    }

                }

                if (teamName.length() == 8) {
                    if (intRank.length() == 2) {
                        System.out.printf("%11s", intPlayed);
                        System.out.printf("%6s", intWin);
                        System.out.printf("%6s", intLose);
                        System.out.printf("%6s", intDraw);
                        System.out.printf("%6s", intFor);
                        System.out.printf("%8s", intAgainst);
                        System.out.printf("%6s", intgoalsDiff);
                        System.out.printf("%6s", intpoints);
                        System.out.println();
                    } else { // else of rank

                        System.out.printf("%12s", intPlayed);
                        System.out.printf("%6s", intWin);
                        System.out.printf("%6s", intLose);
                        System.out.printf("%6s", intDraw);
                        System.out.printf("%6s", intFor);
                        System.out.printf("%8s", intAgainst);
                        System.out.printf("%6s", intgoalsDiff);
                        System.out.printf("%6s", intpoints);
                        System.out.println();
                    }

                }

                if (teamName.length() == 9) {
                    if (intRank.length() == 2) {
                        System.out.printf("%10s", intPlayed);
                        System.out.printf("%6s", intWin);
                        System.out.printf("%6s", intLose);
                        System.out.printf("%6s", intDraw);
                        System.out.printf("%6s", intFor);
                        System.out.printf("%8s", intAgainst);
                        System.out.printf("%6s", intgoalsDiff);
                        System.out.printf("%6s", intpoints);
                        System.out.println();
                    } else { // else of rank

                        System.out.printf("%11s", intPlayed);
                        System.out.printf("%6s", intWin);
                        System.out.printf("%6s", intLose);
                        System.out.printf("%6s", intDraw);
                        System.out.printf("%6s", intFor);
                        System.out.printf("%8s", intAgainst);
                        System.out.printf("%6s", intgoalsDiff);
                        System.out.printf("%6s", intpoints);
                        System.out.println();
                    }

                }

                if (teamName.length() == 10) {
                    if (intRank.length() == 2) {
                        System.out.printf("%9s", intPlayed);
                        System.out.printf("%6s", intWin);
                        System.out.printf("%6s", intLose);
                        System.out.printf("%6s", intDraw);
                        System.out.printf("%6s", intFor);
                        System.out.printf("%8s", intAgainst);
                        System.out.printf("%6s", intgoalsDiff);
                        System.out.printf("%6s", intpoints);
                        System.out.println();
                    } else { // else of rank

                        System.out.printf("%10s", intPlayed);
                        System.out.printf("%6s", intWin);
                        System.out.printf("%6s", intLose);
                        System.out.printf("%6s", intDraw);
                        System.out.printf("%6s", intFor);
                        System.out.printf("%8s", intAgainst);
                        System.out.printf("%6s", intgoalsDiff);
                        System.out.printf("%6s", intpoints);
                        System.out.println();
                    }

                }

                if (teamName.length() == 11) {
                    if (intRank.length() == 2) {
                        System.out.printf("%8s", intPlayed);
                        System.out.printf("%6s", intWin);
                        System.out.printf("%6s", intLose);
                        System.out.printf("%6s", intDraw);
                        System.out.printf("%6s", intFor);
                        System.out.printf("%8s", intAgainst);
                        System.out.printf("%6s", intgoalsDiff);
                        System.out.printf("%6s", intpoints);
                        System.out.println();
                    } else { // else of rank

                        System.out.printf("%9s", intPlayed);
                        System.out.printf("%6s", intWin);
                        System.out.printf("%6s", intLose);
                        System.out.printf("%6s", intDraw);
                        System.out.printf("%6s", intFor);
                        System.out.printf("%8s", intAgainst);
                        System.out.printf("%6s", intgoalsDiff);
                        System.out.printf("%6s", intpoints);
                        System.out.println();
                    }


                }


                if (teamName.length() == 12) {
                    if (intRank.length() == 2) {
                        System.out.printf("%7s", intPlayed);
                        System.out.printf("%6s", intWin);
                        System.out.printf("%6s", intLose);
                        System.out.printf("%6s", intDraw);
                        System.out.printf("%6s", intFor);
                        System.out.printf("%8s", intAgainst);
                        System.out.printf("%6s", intgoalsDiff);
                        System.out.printf("%6s", intpoints);
                        System.out.println();
                    } else { // else of rank

                        System.out.printf("%8s", intPlayed);
                        System.out.printf("%6s", intWin);
                        System.out.printf("%6s", intLose);
                        System.out.printf("%6s", intDraw);
                        System.out.printf("%6s", intFor);
                        System.out.printf("%8s", intAgainst);
                        System.out.printf("%6s", intgoalsDiff);
                        System.out.printf("%6s", intpoints);
                        System.out.println();
                    }

                }

                if (teamName.length() == 13) {
                    if (intRank.length() == 2) {
                        System.out.printf("%6s", intPlayed);
                        System.out.printf("%6s", intWin);
                        System.out.printf("%6s", intLose);
                        System.out.printf("%6s", intDraw);
                        System.out.printf("%6s", intFor);
                        System.out.printf("%8s", intAgainst);
                        System.out.printf("%6s", intgoalsDiff);
                        System.out.printf("%6s", intpoints);
                        System.out.println();
                    } else { // else of rank

                        System.out.printf("%7s", intPlayed);
                        System.out.printf("%6s", intWin);
                        System.out.printf("%6s", intLose);
                        System.out.printf("%6s", intDraw);
                        System.out.printf("%6s", intFor);
                        System.out.printf("%8s", intAgainst);
                        System.out.printf("%6s", intgoalsDiff);
                        System.out.printf("%6s", intpoints);
                        System.out.println();
                    }

                }

                if (teamName.length() == 14) {
                    if (intRank.length() == 2) {
                        System.out.printf("%5s", intPlayed);
                        System.out.printf("%6s", intWin);
                        System.out.printf("%6s", intLose);
                        System.out.printf("%6s", intDraw);
                        System.out.printf("%6s", intFor);
                        System.out.printf("%8s", intAgainst);
                        System.out.printf("%6s", intgoalsDiff);
                        System.out.printf("%6s", intpoints);
                        System.out.println();
                    } else { // else of rank

                        System.out.printf("%6s", intPlayed);
                        System.out.printf("%6s", intWin);
                        System.out.printf("%6s", intLose);
                        System.out.printf("%6s", intDraw);
                        System.out.printf("%6s", intFor);
                        System.out.printf("%8s", intAgainst);
                        System.out.printf("%6s", intgoalsDiff);
                        System.out.printf("%6s", intpoints);
                        System.out.println();
                    }

                }

                if (teamName.length() == 15) {
                    if (intRank.length() == 2) {
                        System.out.printf("%4s", intPlayed);
                        System.out.printf("%6s", intWin);
                        System.out.printf("%6s", intLose);
                        System.out.printf("%6s", intDraw);
                        System.out.printf("%6s", intFor);
                        System.out.printf("%8s", intAgainst);
                        System.out.printf("%6s", intgoalsDiff);
                        System.out.printf("%6s", intpoints);
                        System.out.println();
                    } else { // else of rank

                        System.out.printf("%5s", intPlayed);
                        System.out.printf("%6s", intWin);
                        System.out.printf("%6s", intLose);
                        System.out.printf("%6s", intDraw);
                        System.out.printf("%6s", intFor);
                        System.out.printf("%8s", intAgainst);
                        System.out.printf("%6s", intgoalsDiff);
                        System.out.printf("%6s", intpoints);
                        System.out.println();
                    }

                }

                if (teamName.length() == 16) {
                    if (intRank.length() == 2) {
                        System.out.printf("%3s", intPlayed);
                        System.out.printf("%6s", intWin);
                        System.out.printf("%6s", intLose);
                        System.out.printf("%6s", intDraw);
                        System.out.printf("%6s", intFor);
                        System.out.printf("%8s", intAgainst);
                        System.out.printf("%6s", intgoalsDiff);
                        System.out.printf("%6s", intpoints);
                        System.out.println();
                    } else { // else of rank

                        System.out.printf("%4s", intPlayed);
                        System.out.printf("%6s", intWin);
                        System.out.printf("%6s", intLose);
                        System.out.printf("%6s", intDraw);
                        System.out.printf("%6s", intFor);
                        System.out.printf("%8s", intAgainst);
                        System.out.printf("%6s", intgoalsDiff);
                        System.out.printf("%6s", intpoints);
                        System.out.println();
                    }

                }

                if (teamName.length() == 17) {
                    if (intRank.length() == 2) { // if of rank
                        System.out.printf("%2s", intPlayed);
                        System.out.printf("%6s", intWin);
                        System.out.printf("%6s", intLose);
                        System.out.printf("%6s", intDraw);
                        System.out.printf("%6s", intFor);
                        System.out.printf("%8s", intAgainst);
                        System.out.printf("%6s", intgoalsDiff);
                        System.out.printf("%6s", intpoints);
                        System.out.println();
                    } else { // else of rank

                        System.out.printf("%3s", intPlayed);
                        System.out.printf("%6s", intWin);
                        System.out.printf("%6s", intLose);
                        System.out.printf("%6s", intDraw);
                        System.out.printf("%6s", intFor);
                        System.out.printf("%8s", intAgainst);
                        System.out.printf("%6s", intgoalsDiff);
                        System.out.printf("%6s", intpoints);
                        System.out.println();
                    }

                }

            }

        }


    }
}




