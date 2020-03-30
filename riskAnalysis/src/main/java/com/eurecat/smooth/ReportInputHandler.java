package com.eurecat.smooth;


import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.json.simple.JSONObject;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class ReportInputHandler {
//    static String json = "{\n" +
//            "  'Country': 'Spain',\n" +
//            "  'dataPath': '/Users/rohit.kumar/Documents/Projects/SMOOTH/data/sampleData.csv',\n" +
//            "  'ID': 'XXXXXX',\n" +
//            "  'results': {\n" +
//            "  'race': [\n" +
//            "      {\n" +
//            "        'columnNum:': 2,\n" +
//            "        'predictionScore': 27.772227772227772,\n" +
//            "        'examples': [\n" +
//            "          '282-Negro',\n" +
//            "          '841-Blanco',\n" +
//            "          '779-Negro',\n" +
//            "          '111-Negro',\n" +
//            "          '648-Negro'\n" +
//            "        ]\n" +
//            "      }\n" +
//            "    ]\n" +
//            "  }\n" +
//            "}";

    //    public static  void main(String[] args) throws  Exception{
//        ReportInput ri=parseJson(json);
//        JSONObject jo=generateReport(ri);
//        System.out.println(jo.toJSONString());
//
//    }
    private static List<String> protected_class = new ArrayList<String>() {
        {
            add("politicalOpinion");
            add("religiousBeliefs");
            add("healthCard");
            add("philosophicalBeliefs");
            add("taxId");
            add("postalAddress");
            add("name");
            add("phoneNumber");
            add("race");
            add("socialSecurity");
            add("email");

        }
    };

    public static ReportInput parseJson(String inputJson) {


        JsonObject jsonObject = new JsonParser().parse(inputJson).getAsJsonObject();
        String dataPath = jsonObject.get("dataPath").getAsString();
        System.out.println(dataPath);
        String Id = (String) jsonObject.get("ID").getAsString();
        System.out.println(Id);
        String delim = "\",\"";
        if (jsonObject.has("delim")) {
            delim = jsonObject.get("delim").getAsString();
        }

        Set<Integer> protected_col_num = new HashSet<Integer>();
        for (int class_id = 0; class_id < protected_class.size(); class_id++) {
            String class_name = protected_class.get(class_id);
            if (jsonObject.getAsJsonObject("results").has(class_name)) {
                JsonArray arr = jsonObject.getAsJsonObject("results").getAsJsonArray(class_name);
                for (int i = 0; i < arr.size(); i++) {
                    int col_num = arr.get(i).getAsJsonObject().get("columnNum:").getAsInt();
                    System.out.println(col_num);
                    protected_col_num.add(col_num);
                }
            }
        }
        return new ReportInput(dataPath, Id, protected_col_num, delim);
    }

    public static ReportInput parseJson(Input input) {


        String dataPath = "/app-data/"+input.getDataPath(); // for production in google
      // String dataPath = input.getDataPath(); // for local

        System.out.println("data at"+dataPath);
        String Id = input.getID();
        System.out.println(Id);
        String delim = "\",\"";
        Set<Integer> protected_col_num = new HashSet<Integer>();
        for (Results ri : input.getResults()) {
            String type = ri.getType();
            if (protected_class.contains(type)) {
                    protected_col_num.add(ri.getColumnNum());
            } else {
                System.out.println("found un protected class ignoring " + type);
            }
        }


        return new ReportInput(dataPath, Id, protected_col_num, delim);
    }

    public static JSONObject generateReport(ReportInput ri) throws IOException {

        File ifile = new File(ri.getDataPath());
        System.out.println(ri.getDataPath());
        //remove the cols which are protected
        String updatedFile = DataManager.getUpdatedDataPath(ri.getProtected_col(), ifile, ri.getDelim());
        File tempFile = new File(updatedFile);

        //pass the new tempFile to analyze risk
        SmoothIO smoothio = new SmoothIO();
        String dataFolder = tempFile.getParent();
        String dataFile = tempFile.getName().replace(".csv", "");
        smoothio.setFolderPath(dataFolder);
        System.out.println(dataFolder);
        System.out.println(dataFile);
        smoothio.loadDataCsv(dataFile, ',');
        smoothio.createResultWithoutRecomendation();
        return smoothio.getCurrentRisk();


    }

    public static String generateBasicReport(ReportInput ri) throws IOException {

        File ifile = new File(ri.getDataPath());
        System.out.println(ri.getDataPath());
        //remove the cols which are protected
        String updatedFile = DataManager.getUpdatedDataPath(ri.getProtected_col(), ifile, ri.getDelim());
        File tempFile = new File(updatedFile);

        //pass the new tempFile to analyze risk
        SmoothIO smoothio = new SmoothIO();
        String dataFolder = tempFile.getParent();
        String dataFile = tempFile.getName().replace(".csv", "");
        smoothio.setFolderPath(dataFolder);
        System.out.println(dataFolder);
        System.out.println(dataFile);
        smoothio.loadDataCsv(dataFile, ',');
        smoothio.createResultWithoutRecomendation();
        return smoothio.getCurrentRiskLevel();



    }






}

