package com.eurecat.smooth;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public class Input {

    private final String country;

    public String getCountry() {
        return country;
    }

    public String getDataPath() {
        return dataPath;
    }

    public String getID() {
        return ID;
    }

    public Results[] getResults() {
        return results;
    }
    private final int success;
    public int getSuccess() {
        return success;
    }
    private final String dataPath;
    private final String ID;
    private final Results[] results;
    @JsonCreator
    Input(@JsonProperty("Country") String country, @JsonProperty("dataPath") String dataPath, @JsonProperty("ID") String id, @JsonProperty("datasetResult") Results[] results,@JsonProperty("success") int success) {
        this.country = country;
        this.dataPath = dataPath;
        ID = id;
        this.results = results;
        this.success=success;
    }

}
