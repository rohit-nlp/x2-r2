package com.eurecat.smooth;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public class Results {

    private final String type;
    private final int columnNum;
    private final double predictionScore;
    private final String[] examples;



    public int getColumnNum() {
        return columnNum;
    }

    public double getPredictionScore() {
        return predictionScore;
    }

    public String[] getExamples() {
        return examples;
    }


    public String getType() {
        return type;
    }


    @JsonCreator
    Results(@JsonProperty("type:") String type, @JsonProperty("columnNum:") int columns, @JsonProperty("predictionScore") double predictionScore, @JsonProperty("examples") String[] examples) {
        this.type = type;
        this.columnNum = columns;
        this.predictionScore = predictionScore;
        this.examples = examples;

    }
}
