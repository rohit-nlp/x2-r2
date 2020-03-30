package com.eurecat.smooth;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

import java.util.ArrayList;

public class Recommendation {
    private int level;
    private String name;
    private int maxLevel;
    private ArrayList<Double> ids;
    private JSONArray riskJSONArray;
    private double lowestRisk;

    public Recommendation(String name, int maxLevel) {
        this.maxLevel = maxLevel;
        this.name = name;
        this.riskJSONArray = new JSONArray();
        this.ids = new ArrayList<>();
    }

    public void setLowestRisk(double risk){
        this.lowestRisk = risk;
    }

    public double getLowestRisk(){
        return this.lowestRisk;
    }


    public int getLevel() {
        return level;
    }

    public void setLevel(int level) {
        this.level = level;
    }

    public void addRiskJson(JSONObject riskjson){
        riskJSONArray.add(riskjson);
    }

    public void addHashCode(Double hash){
        this.ids.add(hash);
    }

    public ArrayList<Double> getHashCodes(){
        return ids;
    }

    public JSONObject getJSONObject(){
        JSONObject obj = new JSONObject();
        obj.put("name", name);
        obj.put("recommendedLevel", level);
        obj.put("maxLevel", maxLevel);

        JSONArray values = new JSONArray();
        for(int i =0; i < ids.size(); i++){
            JSONObject v = new JSONObject();
            v.put("hashcode", ids.get(i));
            v.put("risk", riskJSONArray.get(i));
            values.add(v);
        }

        obj.put("values", values);

        return obj;

    }



}
