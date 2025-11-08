package com.example.scoreprediction;

import com.google.gson.Gson;
import com.google.gson.internal.LinkedTreeMap;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Scanner;

public class Winner extends LeagueRequest {


    public Winner(FullResponse fullResponse) {
        super(fullResponse);
    }

    @Override
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

    public void howWillWinForGroupStage(String url) throws Exception {
        String apiToken = "ee76c4ea94da4bd64c294500e1037d8e";
        String apiHost = "v3.football.api-sports.io";
        try {
            Scanner input = new Scanner(System.in);
            int team1 = 0;
            int team2 = 0;
            int booting = 0;
            int num = 0;
            String group;
            System.out.println("");
            System.out.println("");
            System.out.println("Enter group stage (only capital letters) : ");
            group = input.nextLine();
            if (group.equals("A")) {
                num = 0;
            }
            if (group.equals("B")) {
                num = 1;
            }
            if (group.equals("C")) {
                num = 2;
            }
            if (group.equals("D")) {
                num = 3;
            }
            if (group.equals("E")) {
                num = 4;
            }
            if (group.equals("F")) {
                num = 5;
            }
            if (group.equals("G")) {
                num = 6;
            }
            if (group.equals("H")) {
                num = 7;
            }
            System.out.println("");
            System.out.println("Select 2 teams to compare (use the position number of the teams)");
            team1 = Integer.parseInt(input.next()) - 1;
            team2 = Integer.parseInt(input.next()) - 1;
            System.out.println("");
            System.out.println("press any number to continue or 0 to stop");
            booting = input.nextInt();
            while (booting != 0) {
                if (team1 < 0 || team2 < 0 || team1 > 4 || team2 > 4) {
                    LeagueRequest.setLiga(url, apiToken, apiHost);
                    System.out.println("");
                    System.out.println("something wrong");
                    System.out.println("put numbers beetwin 1-4 ");
                    System.out.println("try again (use the position number of the teams) ");
                    team1 = input.nextInt() - 1;
                    team2 = input.nextInt() - 1;
                    if (team1 < 0 || team2 < 0 || team1 > 4 || team2 > 4) {
                        System.out.println("");
                        System.out.println("something wrong");
                        System.out.println("put numbers beetwin 1-4 ");
                        System.out.println("try again (use the position number of the teams) ");
                        team1 = Integer.parseInt(input.next()) - 1;
                        team2 = Integer.parseInt(input.next()) - 1;
                    } else {
                        // START THE ALGORITHM

                        System.out.println("");
                        int totalPointsTeam1 = 0;
                        int totalPointsTeam2 = 0;
                        Gson gson = new Gson();
                        fullResponse = gson.fromJson(setRequset(url, "ee76c4ea94da4bd64c294500e1037d8e", "v3.football.api-sports.io"), FullResponse.class);
                        ArrayList<Object> arr = new ArrayList<>();
                        Collections.addAll(arr, fullResponse.response.get(0).league.standings);
                        ArrayList<LinkedTreeMap<String, Object>> standing = (ArrayList) fullResponse.response.get(0).league.standings[num];
                        System.out.println(num);
                        LinkedTreeMap<String, Object> teamObj1 = (LinkedTreeMap<String, Object>) standing.get(team1).get("team");
                        LinkedTreeMap<String, Object> teamObj2 = (LinkedTreeMap<String, Object>) standing.get(team2).get("team");
                        LinkedTreeMap<String, Object> allOfTeam1 = (LinkedTreeMap<String, Object>) standing.get(team1).get("all");
                        LinkedTreeMap<String, Object> allOfTeam2 = (LinkedTreeMap<String, Object>) standing.get(team2).get("all");
                        LinkedTreeMap<String, Object> goalsOfTeam1 = (LinkedTreeMap<String, Object>) ((LinkedTreeMap<?, ?>) standing.get(team1).get("all")).get("goals");
                        LinkedTreeMap<String, Object> goalsOfTeam2 = (LinkedTreeMap<String, Object>) ((LinkedTreeMap<?, ?>) standing.get(team2).get("all")).get("goals");


                        double playedTeam1 = (double) allOfTeam1.get("played");
                        double playedTeam2 = (double) allOfTeam2.get("played");
                        double winTeam1 = (double) allOfTeam1.get("win");
                        double winTeam2 = (double) allOfTeam2.get("win");
                        double drawTeam1 = (double) allOfTeam1.get("draw");
                        double drawTeam2 = (double) allOfTeam2.get("draw");
                        double loseTeam1 = (double) allOfTeam1.get("lose");
                        double loseTeam2 = (double) allOfTeam2.get("lose");
                        double goalsForTeam1 = (double) goalsOfTeam1.get("for");
                        double goalsForTeam2 = (double) goalsOfTeam2.get("for");
                        double goalsAgainstTeam1 = (double) goalsOfTeam1.get("against");
                        double goalsAgainstTeam2 = (double) goalsOfTeam2.get("against");
                        double pointsTeam1 = (double) standing.get(0).get("points");
                        double pointsTeam2 = (double) standing.get(1).get("points");


                        if (playedTeam1 == playedTeam2) {
                            totalPointsTeam1++;
                            totalPointsTeam2++;
                        }
                        if (playedTeam1 > playedTeam2) {
                            totalPointsTeam2++;
                        }
                        if (playedTeam1 < playedTeam2) {
                            totalPointsTeam1++;
                        }
                        // WIN SECTION
                        if (winTeam1 == winTeam2) {
                            totalPointsTeam1++;
                            totalPointsTeam2++;
                        }
                        if (winTeam1 > winTeam2) {
                            totalPointsTeam1++;
                        }
                        if (winTeam1 < winTeam2) {
                            totalPointsTeam2++;
                        }

                        // DRAW SECTION
                        if (drawTeam1 == drawTeam2) {
                            totalPointsTeam1++;
                            totalPointsTeam2++;
                        }
                        if (drawTeam1 > drawTeam2) {
                            totalPointsTeam2++;
                        }
                        if (drawTeam1 < drawTeam2) {
                            totalPointsTeam1++;
                        }
                        // LOSE SECTION

                        if (loseTeam1 > loseTeam2) {
                            totalPointsTeam2++;
                        }
                        if (loseTeam1 < loseTeam2) {
                            totalPointsTeam1++;
                        }
                        // FOR SECTION
                        if (goalsForTeam1 == goalsForTeam2) {
                            if (goalsForTeam1 > 20 || goalsForTeam2 > 20) {
                                totalPointsTeam1 = totalPointsTeam1 + 3;
                                totalPointsTeam2 = totalPointsTeam2 + 3;
                            } else {
                                totalPointsTeam1 = totalPointsTeam1 + 2;
                                totalPointsTeam2 = totalPointsTeam2 + 2;
                            }
                        }
                        if (goalsForTeam1 > goalsForTeam2) {
                            if (goalsForTeam1 > 20) {
                                totalPointsTeam1 = totalPointsTeam1 + 3;
                            } else {
                                totalPointsTeam1 = totalPointsTeam1 + 2;
                            }
                        }
                        if (goalsForTeam1 < goalsForTeam2) {
                            if (goalsForTeam2 > 20) {
                                totalPointsTeam2 = totalPointsTeam2 + 3;
                            } else {
                                totalPointsTeam2 = totalPointsTeam2 + 2;
                            }
                        }

                        // AGAINST SECTION
                        if (goalsAgainstTeam1 == goalsAgainstTeam2) {
                            if (goalsAgainstTeam1 < 8 || goalsAgainstTeam2 < 8) {
                                totalPointsTeam1 = totalPointsTeam1 + 2;
                                totalPointsTeam2 = totalPointsTeam2 + 2;
                            } else {
                                totalPointsTeam1++;
                                totalPointsTeam2++;
                            }
                        }
                        if (goalsAgainstTeam1 > goalsAgainstTeam2) {
                            if (goalsAgainstTeam2 < 8) {
                                totalPointsTeam2 = totalPointsTeam2 + 2;
                            } else {
                                totalPointsTeam2++;
                            }
                        }
                        if (goalsAgainstTeam1 < goalsAgainstTeam2) {
                            if (goalsAgainstTeam1 < 8) {
                                totalPointsTeam1 = totalPointsTeam1 + 2;
                            } else {
                                totalPointsTeam1++;
                            }
                        }
                        // POINTS SECTION
                        if (pointsTeam1 == pointsTeam2) {
                            totalPointsTeam1 = totalPointsTeam1 + 3;
                            totalPointsTeam2 = totalPointsTeam2 + 3;

                        }
                        if (pointsTeam1 > pointsTeam2) {
                            totalPointsTeam1 = totalPointsTeam1 + 3;
                        }
                        if (pointsTeam1 < pointsTeam2) {
                            totalPointsTeam2 = totalPointsTeam2 + 3;
                        }

                        // LEVEL A EXTRA POINTS

                        if (teamObj1.get("name").equals("Manchester City") &&
                                teamObj1.get("name").equals("Liverpool") &&
                                teamObj1.get("name").equals("Paris Saint Germa") &&
                                teamObj1.get("name").equals("Real Madrid") &&
                                teamObj1.get("name").equals("AC Milan") &&
                                teamObj1.get("name").equals("Bayern Munich") &&
                                teamObj1.get("name").equals("Barcelona") &&
                                teamObj1.get("name").equals("Napoli")) {
                            totalPointsTeam1 = totalPointsTeam1 + 3;

                        } else if (teamObj2.get("name").equals("Manchester City") &&
                                teamObj2.get("name").equals("Liverpool") &&
                                teamObj2.get("name").equals("Paris Saint Germa") &&
                                teamObj2.get("name").equals("Real Madrid") &&
                                teamObj2.get("name").equals("AC Milan") &&
                                teamObj2.get("name").equals("Bayern Munich") &&
                                teamObj2.get("name").equals("Barcelona") &&
                                teamObj2.get("name").equals("Napoli")) {
                            totalPointsTeam2 = totalPointsTeam2 + 3;
                        }

                        // LEVEL B EXTRA POINTS

                        if (teamObj1.get("name").equals("Manchester United") &&
                                teamObj1.get("name").equals("Tottenham") &&
                                teamObj1.get("name").equals("Chelsea") &&
                                teamObj2.get("name").equals("Arsenal") &&
                                teamObj2.get("name").equals("Ajax") &&
                                teamObj2.get("name").equals("Atletico Madrid") &&
                                teamObj2.get("name").equals("Porto") &&
                                teamObj2.get("name").equals("Inter") &&
                                teamObj2.get("name").equals("Marseille") &&
                                teamObj2.get("name").equals("Borussia Dortmund") &&
                                teamObj2.get("name").equals("Benfica") &&
                                teamObj2.get("name").equals("Juventus")) {
                            totalPointsTeam1 = totalPointsTeam1 + 2;

                        } else if (teamObj2.get("name").equals("Manchester United") &&
                                teamObj2.get("name").equals("Tottenham") &&
                                teamObj2.get("name").equals("Chelsea") &&
                                teamObj2.get("name").equals("Arsenal") &&
                                teamObj2.get("name").equals("Ajax") &&
                                teamObj2.get("name").equals("Atletico Madrid") &&
                                teamObj2.get("name").equals("Porto") &&
                                teamObj2.get("name").equals("Inter") &&
                                teamObj2.get("name").equals("Marseille") &&
                                teamObj2.get("name").equals("Borussia Dortmund") &&
                                teamObj2.get("name").equals("Benfica") &&
                                teamObj2.get("name").equals("Juventus")) {
                            totalPointsTeam2 = totalPointsTeam2 + 2;
                        }

                        System.out.println(teamObj1.get("name") + " have : " + totalPointsTeam1 + " points");
                        System.out.println(teamObj2.get("name") + " have : " + totalPointsTeam2 + " points");
                        System.out.println("");
                        System.out.println("");
                        if (totalPointsTeam1 == totalPointsTeam2) {
                            System.out.println("IT WILL BE A DRAW ");
                        } else {
                            System.out.println("I WOULD PUT MY MONEY ON ");
                            System.out.println("");
                            System.out.println(totalPointsTeam1 > totalPointsTeam2 ? teamObj1.get("name") : teamObj2.get("name"));
                        }
                    }

                } else {

                    System.out.println("");
                    System.out.println("");
                    // start the algorithm
                    int totalPointsTeam1 = 0;
                    int totalPointsTeam2 = 0;
                    Gson gson = new Gson();
                    fullResponse = gson.fromJson(setRequset(url, "ee76c4ea94da4bd64c294500e1037d8e", "v3.football.api-sports.io"), FullResponse.class);
                    ArrayList<Object> arr = new ArrayList<>();
                    Collections.addAll(arr, fullResponse.response.get(0).league.standings);
                    ArrayList<LinkedTreeMap<String, Object>> standing = (ArrayList) fullResponse.response.get(0).league.standings[num];

                    LinkedTreeMap<String, Object> teamObj1 = (LinkedTreeMap<String, Object>) standing.get(team1).get("team");
                    LinkedTreeMap<String, Object> teamObj2 = (LinkedTreeMap<String, Object>) standing.get(team2).get("team");
                    LinkedTreeMap<String, Object> allOfTeam1 = (LinkedTreeMap<String, Object>) standing.get(team1).get("all");
                    LinkedTreeMap<String, Object> allOfTeam2 = (LinkedTreeMap<String, Object>) standing.get(team2).get("all");
                    LinkedTreeMap<String, Object> goalsOfTeam1 = (LinkedTreeMap<String, Object>) ((LinkedTreeMap<?, ?>) standing.get(team1).get("all")).get("goals");
                    LinkedTreeMap<String, Object> goalsOfTeam2 = (LinkedTreeMap<String, Object>) ((LinkedTreeMap<?, ?>) standing.get(team2).get("all")).get("goals");


                    double playedTeam1 = (double) allOfTeam1.get("played");
                    double playedTeam2 = (double) allOfTeam2.get("played");
                    double winTeam1 = (double) allOfTeam1.get("win");
                    double winTeam2 = (double) allOfTeam2.get("win");
                    double drawTeam1 = (double) allOfTeam1.get("draw");
                    double drawTeam2 = (double) allOfTeam2.get("draw");
                    double loseTeam1 = (double) allOfTeam1.get("lose");
                    double loseTeam2 = (double) allOfTeam2.get("lose");
                    double goalsForTeam1 = (double) goalsOfTeam1.get("for");
                    double goalsForTeam2 = (double) goalsOfTeam2.get("for");
                    double goalsAgainstTeam1 = (double) goalsOfTeam1.get("against");
                    double goalsAgainstTeam2 = (double) goalsOfTeam2.get("against");
                    double pointsTeam1 = (double) standing.get(0).get("points");
                    double pointsTeam2 = (double) standing.get(1).get("points");


                    if (playedTeam1 == playedTeam2) {
                        totalPointsTeam1++;
                        totalPointsTeam2++;
                    }
                    if (playedTeam1 > playedTeam2) {
                        totalPointsTeam2++;
                    }
                    if (playedTeam1 < playedTeam2) {
                        totalPointsTeam1++;
                    }
                    // WIN SECTION
                    if (winTeam1 == winTeam2) {
                        totalPointsTeam1++;
                        totalPointsTeam2++;
                    }
                    if (winTeam1 > winTeam2) {
                        totalPointsTeam1++;
                    }
                    if (winTeam1 < winTeam2) {
                        totalPointsTeam2++;
                    }

                    // DRAW SECTION
                    if (drawTeam1 == drawTeam2) {
                        totalPointsTeam1++;
                        totalPointsTeam2++;
                    }
                    if (drawTeam1 > drawTeam2) {
                        totalPointsTeam2++;
                    }
                    if (drawTeam1 < drawTeam2) {
                        totalPointsTeam1++;
                    }
                    // LOSE SECTION

                    if (loseTeam1 > loseTeam2) {
                        totalPointsTeam2++;
                    }
                    if (loseTeam1 < loseTeam2) {
                        totalPointsTeam1++;
                    }
                    // FOR SECTION
                    if (goalsForTeam1 == goalsForTeam2) {
                        if (goalsForTeam1 > 20 || goalsForTeam2 > 20) {
                            totalPointsTeam1 = totalPointsTeam1 + 3;
                            totalPointsTeam2 = totalPointsTeam2 + 3;
                        } else {
                            totalPointsTeam1 = totalPointsTeam1 + 2;
                            totalPointsTeam2 = totalPointsTeam2 + 2;
                        }
                    }
                    if (goalsForTeam1 > goalsForTeam2) {
                        if (goalsForTeam1 > 20) {
                            totalPointsTeam1 = totalPointsTeam1 + 3;
                        } else {
                            totalPointsTeam1 = totalPointsTeam1 + 2;
                        }
                    }
                    if (goalsForTeam1 < goalsForTeam2) {
                        if (goalsForTeam2 > 20) {
                            totalPointsTeam2 = totalPointsTeam2 + 3;
                        } else {
                            totalPointsTeam2 = totalPointsTeam2 + 2;
                        }
                    }

                    // AGAINST SECTION
                    if (goalsAgainstTeam1 == goalsAgainstTeam2) {
                        if (goalsAgainstTeam1 < 8 || goalsAgainstTeam2 < 8) {
                            totalPointsTeam1 = totalPointsTeam1 + 2;
                            totalPointsTeam2 = totalPointsTeam2 + 2;
                        } else {
                            totalPointsTeam1++;
                            totalPointsTeam2++;
                        }
                    }
                    if (goalsAgainstTeam1 > goalsAgainstTeam2) {
                        if (goalsAgainstTeam2 < 8) {
                            totalPointsTeam2 = totalPointsTeam2 + 2;
                        } else {
                            totalPointsTeam2++;
                        }
                    }
                    if (goalsAgainstTeam1 < goalsAgainstTeam2) {
                        if (goalsAgainstTeam1 < 8) {
                            totalPointsTeam1 = totalPointsTeam1 + 2;
                        } else {
                            totalPointsTeam1++;
                        }
                    }
                    // POINTS SECTION
                    if (pointsTeam1 == pointsTeam2) {
                        totalPointsTeam1 = totalPointsTeam1 + 3;
                        totalPointsTeam2 = totalPointsTeam2 + 3;

                    }
                    if (pointsTeam1 > pointsTeam2) {
                        totalPointsTeam1 = totalPointsTeam1 + 3;
                    }
                    if (pointsTeam1 < pointsTeam2) {
                        totalPointsTeam2 = totalPointsTeam2 + 3;
                    }

                    // LEVEL A EXTRA POINTS

                    if (teamObj1.get("name").equals("Manchester City") &&
                            teamObj1.get("name").equals("Liverpool") &&
                            teamObj1.get("name").equals("Paris Saint Germa") &&
                            teamObj1.get("name").equals("Real Madrid") &&
                            teamObj1.get("name").equals("AC Milan") &&
                            teamObj1.get("name").equals("Bayern Munich") &&
                            teamObj1.get("name").equals("Barcelona") &&
                            teamObj1.get("name").equals("Napoli")) {
                        totalPointsTeam1 = totalPointsTeam1 + 3;

                    } else if (teamObj2.get("name").equals("Manchester City") &&
                            teamObj2.get("name").equals("Liverpool") &&
                            teamObj2.get("name").equals("Paris Saint Germa") &&
                            teamObj2.get("name").equals("Real Madrid") &&
                            teamObj2.get("name").equals("AC Milan") &&
                            teamObj2.get("name").equals("Bayern Munich") &&
                            teamObj2.get("name").equals("Barcelona") &&
                            teamObj2.get("name").equals("Napoli")) {
                        totalPointsTeam2 = totalPointsTeam2 + 3;
                    }

                    // LEVEL B EXTRA POINTS

                    if (teamObj1.get("name").equals("Manchester United") &&
                            teamObj1.get("name").equals("Tottenham") &&
                            teamObj1.get("name").equals("Chelsea") &&
                            teamObj2.get("name").equals("Arsenal") &&
                            teamObj2.get("name").equals("Ajax") &&
                            teamObj2.get("name").equals("Atletico Madrid") &&
                            teamObj2.get("name").equals("Porto") &&
                            teamObj2.get("name").equals("Inter") &&
                            teamObj2.get("name").equals("Marseille") &&
                            teamObj2.get("name").equals("Borussia Dortmund") &&
                            teamObj2.get("name").equals("Benfica") &&
                            teamObj2.get("name").equals("Juventus")) {
                        totalPointsTeam1 = totalPointsTeam1 + 2;

                    } else if (teamObj2.get("name").equals("Manchester United") &&
                            teamObj2.get("name").equals("Tottenham") &&
                            teamObj2.get("name").equals("Chelsea") &&
                            teamObj2.get("name").equals("Arsenal") &&
                            teamObj2.get("name").equals("Ajax") &&
                            teamObj2.get("name").equals("Atletico Madrid") &&
                            teamObj2.get("name").equals("Porto") &&
                            teamObj2.get("name").equals("Inter") &&
                            teamObj2.get("name").equals("Marseille") &&
                            teamObj2.get("name").equals("Borussia Dortmund") &&
                            teamObj2.get("name").equals("Benfica") &&
                            teamObj2.get("name").equals("Juventus")) {
                        totalPointsTeam2 = totalPointsTeam2 + 2;
                    }

                    System.out.println(teamObj1.get("name") + " have : " + totalPointsTeam1 + " points");
                    System.out.println(teamObj2.get("name") + " have : " + totalPointsTeam2 + " points");
                    System.out.println("");
                    System.out.println("");
                    if (totalPointsTeam1 == totalPointsTeam2) {
                        System.out.println("IT WILL BE A DRAW ");
                    } else {
                        System.out.println("I WOULD PUT MY MONEY ON ");
                        System.out.println("");
                        System.out.println(totalPointsTeam1 > totalPointsTeam2 ? teamObj1.get("name") : teamObj2.get("name"));
                    }
                    System.out.println("After 10 sec we will proceed to the next game");
                    Thread.sleep(10000);
                    System.out.println("");
                    System.out.println("");
                    LeagueRequest.setLiga(url, apiToken, apiHost);
                    System.out.println("");
                    System.out.println("");
                    System.out.println("press any number to continue or 0 to stop");
                    booting = Integer.parseInt(input.next());
                    if (booting == 0) {
                        break;
                    } else {
                        System.out.println("Select 2 teams to compare (use the position number of the teams)");
                        team1 = Integer.parseInt(input.next()) - 1;
                        team2 = Integer.parseInt(input.next()) - 1;
                    }
                }
            }
            System.out.println("bye bye !!!");

        } catch (NumberFormatException e) {
            System.out.println("Please run again put only positive integers");

        }


    }

    public void howWillWin(String url) throws Exception {
        String apiToken = "ee76c4ea94da4bd64c294500e1037d8e";
        String apiHost = "v3.football.api-sports.io";
        try {
            Scanner input = new Scanner(System.in);
            int team1 = 0;
            int team2 = 0;
            int booting = 0;

            System.out.println("");
            System.out.println("Select 2 teams to compare (use the position number of the teams)");
            team1 = Integer.parseInt(input.next()) - 1;
            team2 = Integer.parseInt(input.next()) - 1;
            System.out.println("");
            System.out.println("press any number to continue or 0 to stop");
            booting = Integer.parseInt(input.next());

            while (booting != 0) {
                Gson gson = new Gson();
                fullResponse = gson.fromJson(setRequset(url, apiToken, apiHost), FullResponse.class);

                ArrayList<Object> arr = new ArrayList<>();
                Collections.addAll(arr, fullResponse.response.get(0).league.standings);
                ArrayList<LinkedTreeMap<String, Object>> standing = (ArrayList) fullResponse.response.get(0).league.standings[0];

                int maxIndex = standing.size() - 1;
                if (team1 < 0 || team2 < 0 || team1 > maxIndex || team2 > maxIndex) {
                    LeagueRequest.setLiga(url, apiToken, apiHost);
                    System.out.println("");
                    System.out.println("something wrong");
                    System.out.println("put numbers between 1-" + (maxIndex + 1));
                    System.out.println("try again (use the position number of the teams)");
                    team1 = Integer.parseInt(input.next()) - 1;
                    team2 = Integer.parseInt(input.next()) - 1;
                    continue;
                }

                System.out.println("");
                int totalPointsTeam1 = 0;
                int totalPointsTeam2 = 0;

                LinkedTreeMap<String, Object> teamObj1 = (LinkedTreeMap<String, Object>) standing.get(team1).get("team");
                LinkedTreeMap<String, Object> teamObj2 = (LinkedTreeMap<String, Object>) standing.get(team2).get("team");
                LinkedTreeMap<String, Object> allOfTeam1 = (LinkedTreeMap<String, Object>) standing.get(team1).get("all");
                LinkedTreeMap<String, Object> allOfTeam2 = (LinkedTreeMap<String, Object>) standing.get(team2).get("all");
                LinkedTreeMap<String, Object> goalsOfTeam1 = (LinkedTreeMap<String, Object>) ((LinkedTreeMap<?, ?>) standing.get(team1).get("all")).get("goals");
                LinkedTreeMap<String, Object> goalsOfTeam2 = (LinkedTreeMap<String, Object>) ((LinkedTreeMap<?, ?>) standing.get(team2).get("all")).get("goals");

                double playedTeam1 = (double) allOfTeam1.get("played");
                double playedTeam2 = (double) allOfTeam2.get("played");
                double winTeam1 = (double) allOfTeam1.get("win");
                double winTeam2 = (double) allOfTeam2.get("win");
                double drawTeam1 = (double) allOfTeam1.get("draw");
                double drawTeam2 = (double) allOfTeam2.get("draw");
                double loseTeam1 = (double) allOfTeam1.get("lose");
                double loseTeam2 = (double) allOfTeam2.get("lose");
                double goalsForTeam1 = (double) goalsOfTeam1.get("for");
                double goalsForTeam2 = (double) goalsOfTeam2.get("for");
                double goalsAgainstTeam1 = (double) goalsOfTeam1.get("against");
                double goalsAgainstTeam2 = (double) goalsOfTeam2.get("against");
                double pointsTeam1 = (double) standing.get(team1).get("points");
                double pointsTeam2 = (double) standing.get(team2).get("points");

                if (playedTeam1 == playedTeam2) {
                    totalPointsTeam1++;
                    totalPointsTeam2++;
                }
                if (playedTeam1 > playedTeam2) {
                    totalPointsTeam2++;
                }
                if (playedTeam1 < playedTeam2) {
                    totalPointsTeam1++;
                }
                // WIN SECTION
                if (winTeam1 == winTeam2) {
                    totalPointsTeam1++;
                    totalPointsTeam2++;
                }
                if (winTeam1 > winTeam2) {
                    totalPointsTeam1++;
                }
                if (winTeam1 < winTeam2) {
                    totalPointsTeam2++;
                }

                // DRAW SECTION
                if (drawTeam1 == drawTeam2) {
                    totalPointsTeam1++;
                    totalPointsTeam2++;
                }
                if (drawTeam1 > drawTeam2) {
                    totalPointsTeam2++;
                }
                if (drawTeam1 < drawTeam2) {
                    totalPointsTeam1++;
                }
                // LOSE SECTION

                if (loseTeam1 > loseTeam2) {
                    totalPointsTeam2++;
                }
                if (loseTeam1 < loseTeam2) {
                    totalPointsTeam1++;
                }
                // FOR SECTION
                if (goalsForTeam1 == goalsForTeam2) {
                    if (goalsForTeam1 > 20 || goalsForTeam2 > 20) {
                        totalPointsTeam1 = totalPointsTeam1 + 3;
                        totalPointsTeam2 = totalPointsTeam2 + 3;
                    } else {
                        totalPointsTeam1 = totalPointsTeam1 + 2;
                        totalPointsTeam2 = totalPointsTeam2 + 2;
                    }
                }
                if (goalsForTeam1 > goalsForTeam2) {
                    if (goalsForTeam1 > 20) {
                        totalPointsTeam1 = totalPointsTeam1 + 3;
                    } else {
                        totalPointsTeam1 = totalPointsTeam1 + 2;
                    }
                }
                if (goalsForTeam1 < goalsForTeam2) {
                    if (goalsForTeam2 > 20) {
                        totalPointsTeam2 = totalPointsTeam2 + 3;
                    } else {
                        totalPointsTeam2 = totalPointsTeam2 + 2;
                    }
                }

                // AGAINST SECTION
                if (goalsAgainstTeam1 == goalsAgainstTeam2) {
                    if (goalsAgainstTeam1 < 8 || goalsAgainstTeam2 < 8) {
                        totalPointsTeam1 = totalPointsTeam1 + 2;
                        totalPointsTeam2 = totalPointsTeam2 + 2;
                    } else {
                        totalPointsTeam1++;
                        totalPointsTeam2++;
                    }
                }
                if (goalsAgainstTeam1 > goalsAgainstTeam2) {
                    if (goalsAgainstTeam2 < 8) {
                        totalPointsTeam2 = totalPointsTeam2 + 2;
                    } else {
                        totalPointsTeam2++;
                    }
                }
                if (goalsAgainstTeam1 < goalsAgainstTeam2) {
                    if (goalsAgainstTeam1 < 8) {
                        totalPointsTeam1 = totalPointsTeam1 + 2;
                    } else {
                        totalPointsTeam1++;
                    }
                }
                // POINTS SECTION
                if (pointsTeam1 == pointsTeam2) {
                    totalPointsTeam1 = totalPointsTeam1 + 3;
                    totalPointsTeam2 = totalPointsTeam2 + 3;

                }
                if (pointsTeam1 > pointsTeam2) {
                    totalPointsTeam1 = totalPointsTeam1 + 3;
                }
                if (pointsTeam1 < pointsTeam2) {
                    totalPointsTeam2 = totalPointsTeam2 + 3;
                }

                // LEVEL A EXTRA POINTS
                if (teamObj1.get("name").equals("Manchester City") &&
                        teamObj1.get("name").equals("Liverpool") &&
                        teamObj1.get("name").equals("Paris Saint Germa") &&
                        teamObj1.get("name").equals("Real Madrid") &&
                        teamObj1.get("name").equals("AC Milan") &&
                        teamObj1.get("name").equals("Bayern Munich") &&
                        teamObj1.get("name").equals("Barcelona") &&
                        teamObj1.get("name").equals("Napoli")) {
                    totalPointsTeam1 = totalPointsTeam1 + 3;

                } else if (teamObj2.get("name").equals("Manchester City") &&
                        teamObj2.get("name").equals("Liverpool") &&
                        teamObj2.get("name").equals("Paris Saint Germa") &&
                        teamObj2.get("name").equals("Real Madrid") &&
                        teamObj2.get("name").equals("AC Milan") &&
                        teamObj2.get("name").equals("Bayern Munich") &&
                        teamObj2.get("name").equals("Barcelona") &&
                        teamObj2.get("name").equals("Napoli")) {
                    totalPointsTeam2 = totalPointsTeam2 + 3;
                }

                // LEVEL B EXTRA POINTS
                if (teamObj1.get("name").equals("Manchester United") &&
                        teamObj1.get("name").equals("Tottenham") &&
                        teamObj1.get("name").equals("Chelsea") &&
                        teamObj2.get("name").equals("Arsenal") &&
                        teamObj2.get("name").equals("Ajax") &&
                        teamObj2.get("name").equals("Atletico Madrid") &&
                        teamObj2.get("name").equals("Porto") &&
                        teamObj2.get("name").equals("Inter") &&
                        teamObj2.get("name").equals("Marseille") &&
                        teamObj2.get("name").equals("Borussia Dortmund") &&
                        teamObj2.get("name").equals("Benfica") &&
                        teamObj2.get("name").equals("Juventus")) {
                    totalPointsTeam1 = totalPointsTeam1 + 2;

                } else if (teamObj2.get("name").equals("Manchester United") &&
                        teamObj2.get("name").equals("Tottenham") &&
                        teamObj2.get("name").equals("Chelsea") &&
                        teamObj2.get("name").equals("Arsenal") &&
                        teamObj2.get("name").equals("Ajax") &&
                        teamObj2.get("name").equals("Atletico Madrid") &&
                        teamObj2.get("name").equals("Porto") &&
                        teamObj2.get("name").equals("Inter") &&
                        teamObj2.get("name").equals("Marseille") &&
                        teamObj2.get("name").equals("Borussia Dortmund") &&
                        teamObj2.get("name").equals("Benfica") &&
                        teamObj2.get("name").equals("Juventus")) {
                    totalPointsTeam2 = totalPointsTeam2 + 2;
                }

                System.out.println(teamObj1.get("name") + " have : " + totalPointsTeam1 + " points");
                System.out.println(teamObj2.get("name") + " have : " + totalPointsTeam2 + " points");
                System.out.println("");
                System.out.println("");
                if (totalPointsTeam1 == totalPointsTeam2) {
                    System.out.println("IT WILL BE A DRAW ");
                } else {
                    System.out.println("I WOULD PUT MY MONEY ON ");
                    System.out.println("");
                    System.out.println(totalPointsTeam1 > totalPointsTeam2 ? teamObj1.get("name") : teamObj2.get("name"));
                }

                System.out.println("After 10 sec we will proceed to the next game");
                Thread.sleep(10000);
                System.out.println("");
                System.out.println("");
                LeagueRequest.setLiga(url, apiToken, apiHost);
                System.out.println("");
                System.out.println("");
                System.out.println("press any number to continue or 0 to stop");
                booting = Integer.parseInt(input.next());
                if (booting == 0) {
                    break;
                } else {
                    System.out.println("Select 2 teams to compare (use the position number of the teams)");
                    team1 = Integer.parseInt(input.next()) - 1;
                    team2 = Integer.parseInt(input.next()) - 1;
                }
            }

            System.out.println("bye bye !!!");
        } catch (NumberFormatException e) {
            System.out.println("Please run again put only positive integers");
        }
    }
}


