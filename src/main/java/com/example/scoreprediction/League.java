package com.example.scoreprediction;

public class League {


    Integer id;
    Integer season;
    String name;
    String country;
    String logo; // League logo URL
    String flag; // Country flag URL associated with the league
    Object[] standings;



    public void setId(Integer id) {
        this.id = id;
    }

    public void setSeason(Integer season) {
        this.season = season;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setCountry(String country) {
        this.country = country;
    }

    public void setLogo(String logo) {
        this.logo = logo;
    }

    public void setFlag(String flag) {
        this.flag = flag;
    }

    public void setStandings(Object[] standings) {
        this.standings = standings;
    }

    public Integer getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getCountry() {
        return country;
    }

    public String getLogo() {
        return logo;
    }

    public String getFlag() {
        return flag;
    }

    public Object[] getStandings() {
        return standings;
    }

    public Integer getSeason() {
        return season;
    }


}


