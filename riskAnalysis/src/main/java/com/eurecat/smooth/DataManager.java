package com.eurecat.smooth;

import java.io.*;
import java.util.Date;
import java.util.HashSet;
import java.util.Set;
import java.util.Scanner;
import com.univocity.parsers.csv.CsvParserSettings;

public class DataManager {
    public static String getUpdatedDataPath(Set<Integer> colsToRemove, File originalFile,String delim) throws IOException {

        Scanner myReader = null;
        OutputStreamWriter out = null;
        File newFile = File.createTempFile("updated_" + new Date().getTime(), ".csv");

        try {

            OutputStream fout= new FileOutputStream(newFile);
            OutputStream bout= new BufferedOutputStream(fout);
             out
                    = new OutputStreamWriter(bout, "8859_1");
            FileWriter fw = new FileWriter(newFile);


            myReader = new Scanner(originalFile,"ISO-8859-1");


            while (myReader.hasNextLine()) {
                String data = myReader.nextLine();
                String[] newData = data.split(delim);
                int addedColCount=0;
                for (int i = 0; i < newData.length; i++) {
                    if (!colsToRemove.contains(i)) {

                        if (addedColCount!=0) {
                            out.write(delim);
                        }
                        else{
                            if(colsToRemove.contains(0)){//if first col was removed
                                out.write("\"");
                            }
                        }
                        if(newData[i].isEmpty()){
                            out.write("not-given");
                        }else {
                            out.write(newData[i]);
                        }
                        addedColCount++;
                    }

                }

                if(colsToRemove.contains(newData.length-1)){
                    //last col is removed so add an extra closing quote
                    out.write("\"");
                }
                out.write("\n");

                out.flush();


            }


        } finally {
            try {
                if (myReader != null) {
                    myReader.close();
                }
            } catch (Exception ex) {
                System.out.println("Error in closing the Scanner" + ex);
            }

            try {
                if (out != null)
                    out.flush();
                    out.close();
            } catch (Exception ex) {
                System.out.println("Error in closing the OutputStreamWriter" + ex);
            }
        }
        return newFile.getAbsolutePath();
    }

//    public static void main(String[] args) throws Exception {
//
//        HashSet<Integer> cols = new HashSet<Integer>();
//        cols.add(1);
//        cols.add(2);
//        cols.add(5);
//        File data = new File("/Users/rohit.kumar/Documents/Projects/SMOOTH/data/SpanishData.csv");
//        System.out.println(DataManager.getUpdatedDataPath(cols, data,"\",\""));
//    }
}
