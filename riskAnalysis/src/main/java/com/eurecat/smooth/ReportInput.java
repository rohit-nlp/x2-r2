package com.eurecat.smooth;

import java.util.Set;

public class ReportInput {
    private String dataPath;
    private String id;
    private Set<Integer> protected_col;
    private String delim=",";

    public  ReportInput(String dataPath,String id,Set<Integer> protected_col,String delim){
        this.dataPath=dataPath;
        this.id=id;
        this.protected_col=protected_col;
        this.delim=delim;
    }

    public String getDataPath() {
        return dataPath;
    }

    public String getId() {
        return id;
    }

    public Set<Integer> getProtected_col() {
        return protected_col;
    }

    public String getDelim(){
        return delim;
    }

}
