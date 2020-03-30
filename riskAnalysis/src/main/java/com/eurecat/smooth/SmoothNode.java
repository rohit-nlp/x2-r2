package com.eurecat.smooth;

import com.fasterxml.jackson.annotation.JsonCreator;
import org.deidentifier.arx.ARXLattice;


import org.json.simple.JSONObject;

import java.util.Objects;

public class SmoothNode {
    private ARXLattice.ARXNode node;
    private double[] risks;
    private String attributeName;
    private Double id;
    private int k;

    public int getK(){
        return this.k;
    }

    public Double getRecommendedLevelRisk() {
        return recommendedLevelRisk;
    }

    public void setRecommendedLevelRisk(Double recommendedLevelRisk) {
        this.recommendedLevelRisk = recommendedLevelRisk;
    }

    private Double recommendedLevelRisk;



    private int recommendedLevel;

    public int getRecommendedLevel() {
        return recommendedLevel;
    }

    public void setRecommendedLevel(int recommendedLevel) {
        this.recommendedLevel = recommendedLevel;
    }

    public int getMaxLevelAttribute() {
        return maxLevelAttribute;
    }

    public void setMaxLevelAttribute(int maxLevelAttribute) {
        this.maxLevelAttribute = maxLevelAttribute;
    }

    private int maxLevelAttribute;

//    public int getAttributeIndex() {
//        return attributeIndex;
//    }

//    public void setAttributeIndex(int attributeIndex) {
//        this.attributeIndex = attributeIndex;
//    }

    public String getAttributeName() {
        return attributeName;
    }

//    public void setAttributeName(String attributeName) {
//        this.attributeName = attributeName;
//    }

    /**
     * @return
     */
    public int getGeneralizationLevel() {
        return this.node.getGeneralization(this.getAttributeName());
    }


    @JsonCreator
//    public SmoothNode(@JsonProperty("hashcode") int hashcode){
//        this.hashCode = hashcode;
//    }

//    public SmoothNode(ARXLattice.ARXNode node,double[] risks){
//        this.node = node;
//        this.risks = risks;
//    }

    public SmoothNode(ARXLattice.ARXNode node, int k) {
        this.node = node;
        this.k = k;
    }


    public SmoothNode(ARXLattice.ARXNode node, double[] risks, String attributeName, int maxLevel, int k) {
        this.node = node;
        this.risks = risks;
//        this.attributeIndex = attributeIndex;
        this.attributeName = attributeName;
        this.maxLevelAttribute = maxLevel;
        this.k = k;
        this.id = id();
    }

    public Double id() {
        String concat = String.valueOf(k);
        for (int i : this.node.getTransformation()) {
            concat = concat + Integer.toString(i);
        }
        return Double.valueOf(concat);
    }

//    @Override
//    public int hashCode(){
//        return Objects.hash(node);
//    }

    public ARXLattice.ARXNode getNode() {
        return node;
    }

    public void setNode(ARXLattice.ARXNode node) {
        this.node = node;
    }

    public double[] getRisks() {
        return risks;
    }

    public void setRisks(double[] risks) {
        this.risks = risks;
    }


    public String toString() {
        String result = "hashCode: " + id + ", attributeName: " + attributeName + ", level: " + this.getGeneralizationLevel();
        return result;
    }


}
