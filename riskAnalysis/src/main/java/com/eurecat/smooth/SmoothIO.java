package com.eurecat.smooth;


import cern.colt.Swapper;
import com.google.common.collect.HashMultiset;
import com.google.common.collect.Multiset;
import org.deidentifier.arx.*;
import org.deidentifier.arx.AttributeType.Hierarchy;
import org.deidentifier.arx.criteria.KAnonymity;
import org.deidentifier.arx.io.CSVHierarchyInput;
import org.deidentifier.arx.metric.Metric;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static java.lang.Boolean.FALSE;
import static java.lang.Boolean.TRUE;

public class SmoothIO {
    private String path;
    private Data data;
    private String dataName;
    private ARXResult currentResult;
    private ARXPopulationModel populationmodel;
    private ArrayList<ArrayList<SmoothNode>> smoothNodes;
    private ArrayList<ArrayList<SmoothNode>> recommendations;
    private ArrayList<SmoothNode> appliedRecommendations;
    private SmoothNode currentNode;
    //    private SmoothNode rowRemovalRecommendation;
//    private ARXResult rowRemovalResult;
    private double rowRemovalRecommendationHashcode;
    private JSONObject rowRemovalJSON;
    private int currentK;
    private int maxNumRowsJSON = 100;
    private ArrayList<Double> previousRowNodesHashcodes;


    public SmoothIO() throws IOException {

    }

    /**
     * This methods load the data from as csv, but does not handle the hierarchy
     *
     * @param filename file name of the data csv
     * @return a Data object
     * @throws IOException
     */
    public void loadDataCsv(final String filename) throws IOException {
       loadDataCsv(filename,';');
    }
    public void loadDataCsv(final String filename,char delimiter) throws IOException {
        this.dataName = filename;
        this.data = Data.create(path +File.separator+ filename + ".csv", StandardCharsets.ISO_8859_1, delimiter,'"');
        for (int i = 0; i < data.getHandle().getNumColumns(); i++) {
            data.getDefinition().setAttributeType(data.getHandle().getAttributeName(i), AttributeType.QUASI_IDENTIFYING_ATTRIBUTE);
        }
    }



    /**
     * This method automatically adds the hierarchies to the data if the hierarchies
     * have the format: path/dataName_hierarchy_attributeName.csv
     *
     * @throws IOException
     */
    public void addHierarchiesFromFolder() throws IOException {
        if (path == null || dataName == null) {
            System.out.println("something is null which should not be");
        }
        // Read generalization hierarchies
        FilenameFilter hierarchyFilter = new FilenameFilter() {
            @Override
            public boolean accept(File dir, String name) {
                if (name.matches(dataName + "_hierarchy_(.)+.csv")) {
                    return true;
                } else {
                    return false;
                }
            }
        };

        // Create definition
        File testDir = new File("data/");
        File[] genHierFiles = testDir.listFiles(hierarchyFilter);
        Pattern pattern = Pattern.compile("_hierarchy_(.*?).csv");
        for (File file : genHierFiles) {
            Matcher matcher = pattern.matcher(file.getName());
            if (matcher.find()) {
                CSVHierarchyInput hier = new CSVHierarchyInput(file, StandardCharsets.UTF_8, ';');
                String attributeName = matcher.group(1);
                this.data.getDefinition().setAttributeType(attributeName, Hierarchy.create(hier.getHierarchy()));
            }
        }


    }


    /**
     * Set the path of the folder containing the data and hierarchy files, assumes special naming for future methods
     *
     * @param path
     */
    public void setFolderPath(String path) {
        this.path = path;
    }

    /**
     * Creates an ARXResult object which is required for the risk analysis
     * This is the initial result and should not be called again
     *
     * @throws IOException
     */
    public void createResult() throws IOException {
        previousRowNodesHashcodes = new ArrayList<>();
        this.currentK = 1;

        makeRowRemovalRecommendation();

        populationmodel = ARXPopulationModel.create(data.getHandle().getNumRows());
        ARXAnonymizer anonymizer = new ARXAnonymizer();
        ARXConfiguration config = ARXConfiguration.create();
        config.addPrivacyModel(new KAnonymity(1));
        config.setQualityModel(Metric.createLossMetric());
        currentResult = anonymizer.anonymize(data, config);
        updateCurrentNode(null);
        //todo is this necessary ?
        makeRecommendations();
    }
    public void createResultWithoutRecomendation() throws IOException {
        previousRowNodesHashcodes = new ArrayList<>();
        this.currentK = 1;
        populationmodel = ARXPopulationModel.create(data.getHandle().getNumRows());
        ARXAnonymizer anonymizer = new ARXAnonymizer();
        ARXConfiguration config = ARXConfiguration.create();
        config.addPrivacyModel(new KAnonymity(1));
        config.setQualityModel(Metric.createLossMetric());
        currentResult = anonymizer.anonymize(data, config);
        updateCurrentNode(null);

    }

    private void makeRecommendations() {
        makeAttributeRecommendations();
        makeRowRemovalRecommendation();
    }


    /**
     * Sets the minimum and the maximum generalization level of an attribute.
     * So we force it to be generalized at a specific level
     *
     * @param attributeName
     * @param level
     */
    private void setGeneralizationLevel(String attributeName, int level) {
        data.getDefinition().setMinimumGeneralization(attributeName, level);
        data.getDefinition().setMaximumGeneralization(attributeName, level);
    }


    /**
     * updates the generalization levels of the attributes (fixes them for the anonymization process)
     * based on the current recommendation applied
     */
    private void updateFixedGeneralizationLevels() {
        if (currentResult == null) {
            return;
        }
        final List<String> qis = new ArrayList<String>(currentResult.getOutput(currentResult.getLattice().getBottom()).getDefinition().getQuasiIdentifyingAttributes());
        for (String att : qis) {
            setGeneralizationLevel(att, currentNode.getNode().getGeneralization(att));
        }
    }


    /**
     * creates and returns the JSON object with the rows to remove.
     * The hashcode allows us to apply this recommendation from the frontend.
     *
     * @return
     */
    private JSONObject getRowRemovalJSON(ARXResult rowRemovalResult, ARXLattice.ARXNode node) {
        JSONObject obj = new JSONObject();
        int rowsRemovedInNewResult = rowRemovalResult.getOutput(node).getStatistics().getEquivalenceClassStatistics().getNumberOfSuppressedRecords();

        String text = "";
        int numberRowsToRemove = 0;
        if (previousRowNodesHashcodes.size() == 0) {
            text = "Remove " + rowsRemovedInNewResult + " rows to achieve " + (this.currentK + 1) + "-Anonymity";
            obj.put("restoreHashcode", null);
            obj.put("numberRowsToRemove", rowsRemovedInNewResult);
        } else {
            int totalRowsRemoved = currentResult.getOutput(currentNode.getNode()).getStatistics().getEquivalenceClassStatistics().getNumberOfSuppressedRecords();
//            System.out.println("trans at rowRemJson: " +Arrays.toString(currentNode.getNode().getTransformation()));
            int rowsRemovedCurrentResult = currentResult.getOutput(currentNode.getNode()).getStatistics().getEquivalenceClassStatistics().getNumberOfSuppressedRecords();
            numberRowsToRemove = rowsRemovedInNewResult - rowsRemovedCurrentResult;
//            prevRowNode = previousRowNodes.get(previousRowNodes.size() - 1);
            text = totalRowsRemoved + " rows have been removed to achieve " + this.currentK + "-Anonymity. Remove " + numberRowsToRemove + " more rows to achieve " + (this.currentK + 1) +
                    "-Anonymity, or restore the last removed rows to return to " + (this.currentK - 1) + "-Anonymity";
            obj.put("restoreHashcode", previousRowNodesHashcodes.get(previousRowNodesHashcodes.size() - 1));
            obj.put("numberRowsToRemove", numberRowsToRemove);
        }
        obj.put("text", text);
        obj.put("hashcode", rowRemovalRecommendationHashcode);
        obj.put("columns", this.getHeaderJSONArray());
        obj.put("data", this.getRowsToRemoveJSONArray(rowRemovalResult, node));
        obj.put("risk", getRiskJSON(rowRemovalResult, node));

        return obj;
    }

    /**
     * Returns an array with the records to remove to achieve one level higher of k-anonymity.
     * Rerunning the algorithm with the higher k-anon is done before this method.
     *
     * @return
     */
    private JSONArray getRowsToRemoveJSONArray(ARXResult rowRemovalResult, ARXLattice.ARXNode node) {
        DataHandle rowRemovalHandle = rowRemovalResult.getOutput(node);
//        DataHandle currentHandle = currentResult.getOutput(currentNode.getNode());
        DataHandle handle = data.getHandle();

        int numRows = handle.getNumRows();
        if (numRows > this.maxNumRowsJSON) {
            numRows = this.maxNumRowsJSON;
        }
        JSONArray jarray = new JSONArray();
//        Iterator<String[]> iter = currentHandle.iterator();
        Iterator<String[]> iter = data.getHandle().iterator();
        String[] header = iter.next();
        int counter = 0;
        int row = 0;
        while (counter < numRows) {
            String[] record = iter.next();
            if (rowRemovalHandle.isOutlier(row++)) {
                JSONObject obj = new JSONObject();
                obj.put("key", String.valueOf(counter++));
                for (int col = 0; col < record.length; col++) {
                    obj.put(header[col], record[col]);
                }
                jarray.add(obj);
            }
            if (row == handle.getNumRows()) {
                break;
            }
        }
        return jarray;
    }

    /**
     * Anonymizes the data using k-anonymity.
     * This method is used to compute which rows to remove to achieve one level higher of k-anon.
     * the resulting ARX object is not directly saved as global variable
     *
     * @param k
     * @return
     */
    private ARXResult runKAnonymity(int k) {
//        System.out.println("hashcode at k " + currentK + ": " + currentResult.hashCode());
//        System.out.println("begin runK: "+ currentResult.getOutput(currentNode.getNode()).getStatistics().getEquivalenceClassStatistics().getNumberOfOutlyingTuples());
//        System.out.println("anom again");
        ARXAnonymizer anonymizer2 = new ARXAnonymizer();
        ARXConfiguration config2 = ARXConfiguration.create();
        config2.addPrivacyModel(new KAnonymity(k));
        config2.setQualityModel(Metric.createLossMetric());
        data.getHandle().release();
        ARXResult result2 = null;
        try {
            result2 = anonymizer2.anonymize(data, config2);
        } catch (IOException e) {
            e.printStackTrace();
        }
        if (result2 == null) {
            System.out.println("new result is null");
        } else {
            result2.getLattice().getBottom().expand();
        }
//        System.out.println("end runK: "+ currentResult.getOutput(currentNode.getNode()).getStatistics().getEquivalenceClassStatistics().getNumberOfOutlyingTuples());
//        System.out.println("hashcode at k " + currentK + ": " + currentResult.hashCode());
        return result2;


    }


    /**
     * This method returns the indices of the rows to remove.
     * This could be used if on the front-end we can easily link the indices to the existing data.
     *
     * @param result2
     * @return
     * @throws IOException
     * @deprecated
     */
    private int[] getRowsToRemove(ARXResult result2) throws IOException {


        ARXLattice.ARXNode bottomNode = result2.getLattice().getBottom();
        DataHandle bottomHandle = result2.getOutput(bottomNode);
        int numRowsSuppressed = bottomHandle.getStatistics().getEquivalenceClassStatistics().getNumberOfSuppressedRecords();
        int[] rowsSuppressed = new int[numRowsSuppressed];
        int counter = 0;
        for (int row = 0; row < bottomHandle.getNumRows(); row++) {
            if (bottomHandle.isOutlier(row)) {
                rowsSuppressed[counter++] = row;
            }
        }


        return rowsSuppressed;
    }


    /**
     * This methods explore the lattice and returns the nodes generalizing the same attribute
     * in SmoothNode objects which also store the attribute name, index and generalization
     *
     * @param attributeIndex the index of the attribute we want to generalize
     * @return an ArrayList of nodes
     */
    private ArrayList<SmoothNode> getSuccessorsGeneralizingSameAttribute(int attributeIndex, ARXLattice.ARXNode node) {
//        System.out.println("getSuccessorsGeneralizingSameAttribute");
        if (node == null) {
            node = currentResult.getLattice().getBottom();
        }
        ArrayList<SmoothNode> nodes = new ArrayList<>();
        getSuccessorsGeneralizingSameAttribute(node, attributeIndex, nodes);
//        for (SmoothNode n : nodes){
//            System.out.println(currentResult.getOutput(n.getNode()).getStatistics().getEquivalenceClassStatistics().getNumberOfOutlyingTuples());
//        }
        return nodes;
    }


    /**
     * private recursive method for getting the nodes generalizing a same attribute
     *
     * @param node
     * @param attributeIndex
     * @param nodes
     */
    private void getSuccessorsGeneralizingSameAttribute(ARXLattice.ARXNode node, int attributeIndex, ArrayList<SmoothNode> nodes) {
//        System.out.println("getSuccessorsGeneralizingSameAttribute(node, idx, nodes) ----------------");
        DataHandle temp_handle = currentResult.getOutput(node);
//        if (temp_handle == null) {
//            System.out.println("temp handle == null");
//        }
        node.expand();
        final List<String> qis = new ArrayList<String>(temp_handle.getDefinition().getQuasiIdentifyingAttributes());
        int maxLevelOfAttribute = temp_handle.getDefinition().getHierarchy(qis.get(attributeIndex))[0].length - 1;
        int currentGeneralization = node.getGeneralization(qis.get(attributeIndex));


        ARXLattice.ARXNode[] successors = node.getSuccessors();
        for (ARXLattice.ARXNode successor : successors) {
            if (successor.getGeneralization(qis.get(attributeIndex)) == currentGeneralization + 1) {
//                System.out.println("succesor: " + currentResult.getOutput(successor));
//                System.out.println("new smooth node, idx: " + attributeIndex + "arxnode: " + Arrays.toString(successor.getTransformation()));
                nodes.add(new SmoothNode(successor,
                        getPJMArisks(currentResult.getOutput(successor)),
                        qis.get(attributeIndex),
                        maxLevelOfAttribute, currentK));
                getSuccessorsGeneralizingSameAttribute(successor, attributeIndex, nodes);
            }
        }


    }


    /**
     * Get the Prosecutor, Journalist and Marketer risks of a certain node
     *
     * @param handle: the data handle of the node for which you want to get the risks
     * @return
     */
    private double[] getPJMArisks(DataHandle handle) {
        double[] risks = new double[4];
        risks[0] = handle.getRiskEstimator(populationmodel).getSampleBasedReidentificationRisk().getEstimatedProsecutorRisk();
        risks[1] = handle.getRiskEstimator(populationmodel).getSampleBasedReidentificationRisk().getEstimatedJournalistRisk();
        risks[2] = handle.getRiskEstimator(populationmodel).getSampleBasedReidentificationRisk().getEstimatedMarketerRisk();
        risks[3] = handle.getRiskEstimator(populationmodel).getSampleBasedReidentificationRisk().getAverageRisk();
        return risks;
    }


    /**
     * Return a JSONObject containing the different risks
     *
     * @return
     */
    public JSONObject getRiskJSON(ARXResult result, ARXLattice.ARXNode node) {
//        System.out.println("getRiskJSON");
//        System.out.println(result.getOutput(node).getStatistics().getEquivalenceClassStatistics().getNumberOfOutlyingTuples());
//        for (PrivacyCriterion pc : result.getConfiguration().getPrivacyModels()){
//            System.out.println(pc.toString());
//        }

        JSONObject obj = new JSONObject();
        DataHandle handle = result.getOutput(node);
//        obj.put("utilityLoss", node.getNode().getHighestScore());
        obj.put("gauges", getGaugesJSONObject(handle, node));
        obj.put("riskDistribution", getRiskHistogramValuesJSON(handle));
        return obj;
    }
    public String getRiskLevel(ARXResult result, ARXLattice.ARXNode node) {


        DataHandle handle = result.getOutput(node);
        double risk=handle.getRiskEstimator(this.populationmodel).getSampleBasedReidentificationRisk().getAverageRisk() * 100;
        String riskLevel;
        if(risk<=30.0){
            riskLevel="Low";
        }else if(risk>30.0 & risk<=50.0){
            riskLevel="Medium";
        }else{
            riskLevel="High";
        }


        return riskLevel;
    }

    /**
     * returns a JSONObject with the risks for the gauges (0-100)
     *
     * @return
     */
    public JSONObject getPJMARiskJSONObject(DataHandle handle) {
        JSONObject risk = new JSONObject();
        risk.put("Prosecutor", handle.getRiskEstimator(this.populationmodel).getSampleBasedReidentificationRisk().getEstimatedProsecutorRisk() * 100);
        risk.put("Journalist", handle.getRiskEstimator(this.populationmodel).getSampleBasedReidentificationRisk().getEstimatedJournalistRisk() * 100);
        risk.put("Marketer", handle.getRiskEstimator(this.populationmodel).getSampleBasedReidentificationRisk().getEstimatedMarketerRisk() * 100);
        risk.put("Average", handle.getRiskEstimator(this.populationmodel).getSampleBasedReidentificationRisk().getAverageRisk() * 100);
        return risk;
    }

    public JSONObject getGaugesJSONObject(DataHandle handle, ARXLattice.ARXNode node) {
        JSONObject risk = new JSONObject();
//        risk.put("Prosecutor", handle.getRiskEstimator(this.populationmodel).getSampleBasedReidentificationRisk().getEstimatedProsecutorRisk() * 100);
//        risk.put("Journalist", handle.getRiskEstimator(this.populationmodel).getSampleBasedReidentificationRisk().getEstimatedJournalistRisk() * 100);
//        risk.put("Marketer", handle.getRiskEstimator(this.populationmodel).getSampleBasedReidentificationRisk().getEstimatedMarketerRisk() * 100);
        risk.put("Average", handle.getRiskEstimator(this.populationmodel).getSampleBasedReidentificationRisk().getAverageRisk() * 100);
        risk.put("utilityLoss", node.getHighestScore());
//        System.out.println("UtilityLoss: " + node.getAttributeName() +": "  + node.getGeneralizationLevel()  + ": " + node.getNode().getHighestScore());
        return risk;
    }

    /**
     * returns a JSONArray with the objects cumulativeRisk and atRisk
     *
     * @return
     */
    public JSONObject getRiskHistogramValuesJSON(DataHandle handle) {
        JSONArray cumulativeRisks = new JSONArray();
        double[] thresholdsLow = handle.getRiskEstimator(this.populationmodel).getSampleBasedRiskDistribution().getAvailableLowerRiskThresholds();
        double[] thresholdsHigh = handle.getRiskEstimator(this.populationmodel).getSampleBasedRiskDistribution().getAvailableUpperRiskThresholds();
        double[] recordsAtCumulativeRisk = handle.getRiskEstimator(this.populationmodel).getSampleBasedRiskDistribution().getFractionOfRecordsForCumulativeRiskThresholds();
        double[] recordsAtRisk = handle.getRiskEstimator(this.populationmodel).getSampleBasedRiskDistribution().getFractionOfRecordsForRiskThresholds();
        for (int i = 0; i < recordsAtCumulativeRisk.length; i++) {
            JSONObject obj = new JSONObject();
            obj.put("id", i + 1);
            obj.put("range", "[" + thresholdsLow[i] + ";" + thresholdsHigh[i] + "]");
            obj.put("value", recordsAtCumulativeRisk[i]);
            cumulativeRisks.add(obj);
        }
        JSONArray atRisks = new JSONArray();
        for (int i = 0; i < recordsAtRisk.length; i++) {
            JSONObject obj = new JSONObject();
            obj.put("id", i + 1);
            obj.put("range", "[" + thresholdsLow[i] + ";" + thresholdsHigh[i] + "]");
            obj.put("value", recordsAtRisk[i]);
            atRisks.add(obj);
        }

        JSONObject obj = new JSONObject();
        obj.put("cumulative risk", cumulativeRisks);
        obj.put("at risk", atRisks);

        return obj;
    }


    /**
     * get all the risk nodes per attribute (generalized all the way from bottom to top)
     * passing node == null will start the process from the bottom node
     *
     * @return
     */
    public ArrayList<ArrayList<SmoothNode>> getAllVerticalGeneralizations(ARXLattice.ARXNode node) {
        ArrayList<ArrayList<SmoothNode>> riskNodes = new ArrayList<>();
        final List<String> qis = new ArrayList<String>(currentResult.getOutput(node).getDefinition().getQuasiIdentifyingAttributes());
//        final List<String> qis = new ArrayList<String>(data.getDefinition().getQuasiIdentifyingAttributes());
        for (int i = 0; i < qis.size(); i++) {
            riskNodes.add(getSuccessorsGeneralizingSameAttribute(i, node));
        }
        return riskNodes;
    }

    /**
     * This methods applies the current recommendation to remove the rows.
     * It also handles changing the current results to a k-anon of one level higher
     */
    private void applyRowRemovalRecommendation() {
        previousRowNodesHashcodes.add(rowRemovalRecommendationHashcode);
        currentResult = runKAnonymity(this.currentK + 1);
        this.currentK += 1;
        if (currentNode != null) {
            updateCurrentNode(new SmoothNode(getNodeWithGeneralizationsInCurrentResult(null, currentNode.getNode().getTransformation()), currentK));
        }
        //todo this had to be changed if i allow to jump several K's back
        makeRecommendations();
    }

    private void undoRowRemovalRecommendation() {
        previousRowNodesHashcodes.remove(previousRowNodesHashcodes.size() - 1);
        currentResult = runKAnonymity(this.currentK - 1);
        currentK -= 1;
        //todo change this to keep the generalized attributes
        if (appliedRecommendations.size() == 0) {
            updateCurrentNode(null);
        } else {
            updateCurrentNode(appliedRecommendations.get(appliedRecommendations.size() - 1));
        }
//        updateCurrentNode();
//        updateFixedGeneralizationLevels();
    }


    /**
     * This is the main method called by the API to get the JSONs for the interface,
     * it handles the logic as to whether we simply get the results (initially) or we
     * apply or remove a recommendation
     *
     * @param hashcode
     * @return
     */
    public JSONObject handleJSONRequest(Double hashcode) {
//        System.out.println("hashcode: " + hashcode);
//        System.out.println("handleJSONRequest");
        //todo make this method 2 separate ones
        //todo reload dataHandle or so when removing recommendation
//        System.out.println("handleJSONRequest -----------------");
        //initial case
        if (hashcode.equals(rowRemovalRecommendationHashcode)) {
//            System.out.println("(hashcode.equals(rowRemovalRecommendation.id()))");
            applyRowRemovalRecommendation();
        } else {
            if (previousRowNodesHashcodes != null) {
//                System.out.println("previousRowNodesHashcodes != null and size: " + previousRowNodesHashcodes.size());
                for (Double hash : previousRowNodesHashcodes) {
                    if (hashcode.equals(hash)) {
//                        System.out.println("previous row node");
//                        for (SmoothNode n : appliedRecommendations) {
////                            System.out.println(n.id());
//                        }
                        undoRowRemovalRecommendation();
                        ArrayList<SmoothNode> newAppliedRecommendations = new ArrayList<>();
                        for (SmoothNode n : appliedRecommendations) {

                            newAppliedRecommendations.add(new SmoothNode(getNodeWithGeneralizationsInCurrentResult(null, n.getNode().getTransformation()),
                                    getPJMArisks(currentResult.getOutput(n.getNode())),
                                    n.getAttributeName(),
                                    n.getMaxLevelAttribute(),
                                    currentK));
                        }
                        appliedRecommendations = newAppliedRecommendations;
//                        System.out.println("==============");
//                        for (SmoothNode n : appliedRecommendations) {
////                            System.out.println(n.id());
//                        }
                        makeRecommendations();
                        return getJSONForPlatform();
                    }
                }
            }
            if (!(hashcode.equals(-1d))) {
                if (currentNode == null) {
//                    System.out.println("first recommendation applied");
                    applyRecommendation(hashcode);
                } else {
                    if (hashcode.equals(currentNode.id())) {
//                        System.out.println((hashcode.equals(currentNode.id())));
//                        System.out.println("we are removing the last applied rec");
//                        System.out.println("gens before removing: " + Arrays.toString(currentNode.getNode().getTransformation()));
                        appliedRecommendations.remove(appliedRecommendations.size() - 1);
//                    currentNode = appliedRecommendations.get(appliedRecommendations.size()-1);
//                        System.out.println("gens after removing: " + Arrays.toString(currentNode.getNode().getTransformation()));
                        if (appliedRecommendations.size() == 0) {
//                            System.out.println("we have remove the only applied recommendation");
                            updateCurrentNode(null);
                        } else {
                            currentNode = appliedRecommendations.get(appliedRecommendations.size() - 1);
//                            System.out.println("updateFixedGeneralizationLevels");
//                            updateFixedGeneralizationLevels();

                        }
                    } else {
                        SmoothNode temp = getRecommendationToRemove(hashcode);
                        if (temp != null) {
//                            System.out.println("we remove one of the previous recommendations we have applied");
                            appliedRecommendations.remove(temp);
                            updateCurrentNode(null);
                        } else {
//                            System.out.println("we are not removing one the recommendations already applied");
                            SmoothNode temp2 = getSmoothNodeByHashFromSmoothNodes(hashcode);
                            if (temp2 != null) {
//                                System.out.println("the node is amongst the ones sent to the user");
                                if (currentNode.getNode().getGeneralization(temp2.getAttributeName()) == 0) {
//                                    System.out.println("We are generalizing a new attribute");
                                    applyRecommendation(hashcode);
                                } else {
//                                    System.out.println("this attribute has already been generalized");
//                                    System.out.println("I THINK YOU SHOULD NOT SEE THIS");
                                    updateCurrentNode(temp2);

                                }
                            } else {
//                                System.out.println("the node is not amongst the ones being sent to the user");
                                applyRecommendation(hashcode);
                            }
                        }

                    }


                }
            } else { //hashcode == -1 -> we just want the result
//                System.out.println("We just get the JSON");
                return getJSONForPlatform();
            }
        }

//        System.out.println("before makeAttributeRecommendations()");
        makeRecommendations();
        return getJSONForPlatform();
    }


    private void makeRowRemovalRecommendation() {
        ARXResult nextKResult = runKAnonymity(currentK + 1);
        rowRemovalRecommendationHashcode = Double.valueOf((currentK + 1) + "000000000");
//        previousRowNodesHashcodes.add(Double.valueOf((currentK + 1) + "000000000"));
        int[] trans = null;
        if (currentNode != null) {
            trans = currentNode.getNode().getTransformation();
        }
        ARXLattice.ARXNode rowRemovalRecommendation = getNodeWithGeneralizationsResult(nextKResult, null, trans);
        rowRemovalJSON = getRowRemovalJSON(nextKResult, rowRemovalRecommendation);
        data.getHandle().release();
    }


    /**
     * method that checks whether the given hashcode corresponds to an recommendation that has been applied
     *
     * @param hash
     * @returnx
     */
    private SmoothNode getRecommendationToRemove(Double hash) {
//        System.out.println("getRecommendationToRemove");
//        System.out.println("appliedRecommendations.size(): "+ appliedRecommendations.size());
        for (SmoothNode node : this.appliedRecommendations) {
            System.out.println(node.id() + " == " + hash + " ? : " + (node.id().equals(hash)));
            if (node.id().equals(hash)) {
//                System.out.println("(node.id().equals(hash))");
                return node; // we need to remove the current node
            }
        }
        return null;
    }

    /**
     * Creates and returns the JSON object that is used in the front end.
     * It does not do any computation.
     *
     * @return
     */
    private JSONObject getJSONForPlatform() {
//        System.out.println("getJSONForPlatform");
        JSONObject obj = new JSONObject();
        obj.put("currentState", getCurrentJSONObject());
        obj.put("appliedRecommendations", getAppliedRecommendationsJSON());
        obj.put("recommendations", getRecommendationsJSONArraySorted());
        obj.put("rowRecommendations", rowRemovalJSON);
//        obj.put("appliedRowRemovalRecommendations", getAppliedRowRemovalRecommendationsJSON());
        return obj;
    }

    /**
     * Creates and returns the JSON containing the data at its current state and risks.
     *
     * @return
     */
    private JSONObject getCurrentJSONObject() {
        JSONObject obj = new JSONObject();
        obj.put("columns", this.getHeaderJSONArray());
        obj.put("data", this.getDataJSONArray());
        obj.put("risk", getRiskJSON(currentResult, currentNode.getNode()));
        obj.put("numberRows", this.currentResult.getOutput(this.currentNode.getNode()).getStatistics().getEquivalenceClassStatistics().getNumberOfRecordsIncludingSuppressedRecords());

        return obj;
    }
    public JSONObject getCurrentRisk() {
        JSONObject obj = new JSONObject();
        obj.put("risk", getRiskJSON(currentResult, currentNode.getNode()));
        obj.put("numberRows", this.currentResult.getOutput(this.currentNode.getNode()).getStatistics().getEquivalenceClassStatistics().getNumberOfRecordsIncludingSuppressedRecords());

        return obj;
    }
    public String getCurrentRiskLevel() {

        return getRiskLevel(currentResult, currentNode.getNode());

    }
    /**
     * returns a JSONArray where each JSONObject is a record
     * it assumes that the data has a header
     *
     * @return
     */
    public JSONArray getDataJSONArray() {
        DataHandle currentHandle = currentResult.getOutput(currentNode.getNode());
        int numberRows = currentHandle.getNumRows();
        if (numberRows > this.maxNumRowsJSON) {
            numberRows = this.maxNumRowsJSON;
        }
        JSONArray jarray = new JSONArray();
        Iterator<String[]> iter = currentHandle.iterator();
        String[] header = iter.next();
        for (int i = 0; i < numberRows; i++) {
            String[] row = iter.next();
            if (!currentHandle.isOutlier(i)) {
                JSONObject obj = new JSONObject();
                obj.put("key", String.valueOf(i));
                for (int col = 0; col < row.length; col++) {
                    obj.put(header[col], row[col]);
                }
                jarray.add(obj);
            }
        }
        return jarray;
    }


    /**
     * returns the columns of the data for the dashboard API
     *
     * @return
     */
    public JSONArray getHeaderJSONArray() {
        JSONArray jarray = new JSONArray();
        DataHandle handle = null;
        if (currentResult == null) {
            handle = data.getHandle();
        } else {
            handle = currentResult.getOutput(currentNode.getNode());
        }

        Iterator<String[]> iter = handle.iterator();
        for (String h : iter.next()) {
            JSONObject obj = new JSONObject();
            obj.put("title", h);
            obj.put("dataIndex", h);
            obj.put("sortDirection", "descend");
            jarray.add(obj);

        }
        return jarray;
    }


    /**
     * returns a JSONArray with the applied transformations as Strings for the smooth API
     *
     * @return
     */
    private JSONArray getAppliedRecommendationsJSON() {
        JSONArray jarray = new JSONArray();
        for (SmoothNode node : this.appliedRecommendations) {
            JSONObject obj = new JSONObject();
            obj.put("hashcode", node.id());
            obj.put("text", node.getAttributeName() + " has been generalized to level " + node.getGeneralizationLevel() + "/" + node.getMaxLevelAttribute());
            jarray.add(obj);
//            jarray.add(node.getAttributeName() + " has been generalized to level " + node.getGeneralizationLevel() + "/" + node.getMaxLevelAttribute());
        }

        return jarray;
    }


    /**
     * Applies a new recommendation.
     * Whether the recommendation has not been applied yet should be checked before calling this method
     *
     * @param hash
     */
    private void applyRecommendation(Double hash) {
        SmoothNode node = getSmoothNodeByHashFromSmoothNodes(hash);
        this.appliedRecommendations.add(node);
        updateCurrentNode(node);
    }


    /**
     * Find the SmoothNode of the hash in the smoothnodes
     *
     * @param hash
     * @return
     */
    private SmoothNode getSmoothNodeByHashFromSmoothNodes(Double hash) {
//        System.out.println("hash to find: " + hash);
        for (ArrayList<SmoothNode> list : this.smoothNodes) {
            for (SmoothNode node : list) {
//                System.out.println(hash + " == "  + node.id() + " ? : " + (node.id().equals(hash)));
                if (node.id().equals(hash)) {
//                    System.out.println("we return the node");
                    return node;
                }
            }

        }
        System.out.println(hash);
        System.out.println("NODE NOT FOUND BY HASH");
        return null;
    }

    /**
     * makeAttributeRecommendations using the average risk
     */
    public void makeAttributeRecommendations() {
        this.makeAttributeRecommendations("a");
    }


    /**
     * Makes the recommendations as to which attributes to generalize to which level.
     * It stores them in the global variable "recommendations" which will be sorted before being exported as JSON
     *
     * @param pointOfView "p": prosecutor, "m": marketer, "j": journalist, "a": average
     */
    public void makeAttributeRecommendations(String pointOfView) {
        recommendations = new ArrayList<ArrayList<SmoothNode>>();
//        System.out.println("make recommendations");
//        recommendations = new ArrayList<>();
        if (appliedRecommendations == null) {
            appliedRecommendations = new ArrayList<SmoothNode>();
        }

        if (appliedRecommendations.size() == 0) {
//            System.out.println("appliedRecommendations.size() == 0");
            smoothNodes = getAllVerticalGeneralizations(currentResult.getLattice().getBottom());
        } else {
//            System.out.println("appliedRecommendations size: " + appliedRecommendations.size());
            int[] goalTrans = appliedRecommendations.get(appliedRecommendations.size() - 1).getNode().getTransformation();
//            System.out.println("goalTrans: " +Arrays.toString(goalTrans));
            ARXLattice.ARXNode tempNode = getNodeWithGeneralizationsInCurrentResult(currentResult.getLattice().getBottom(), goalTrans);
//            System.out.println("tempNodeTrans: " +Arrays.toString(tempNode.getTransformation()));
            smoothNodes = getAllVerticalGeneralizations(tempNode);
        }

        int povIndex = 3; // average
        if (pointOfView.equals("p")) {
            povIndex = 0;
        } else {
            if (pointOfView.equals("j")) {
                povIndex = 1;
            } else {
                if (pointOfView.equals("m")) {
                    povIndex = 2;
                }
            }
        }


//        int numberAttributes = currentResult.getOutput().getDefinition().getQuasiIdentifyingAttributes().size();

        // each list are the nodes generalizing the same attribute
        for (ArrayList<SmoothNode> list : smoothNodes) {
            if (list.size() > 0) {
//                System.out.println(list.get(0).getAttributeName());
                boolean doThisList = FALSE;
                if (currentNode == null) {
//                    System.out.println("currentNode == null");
//                    System.out.println(" (currentNode == null)");
                    doThisList = TRUE;
                } else {
//                    System.out.println("Current attribute: " + list.get(0).getAttributeName() + ": " + currentNode.getNode().getGeneralization(list.get(0).getAttributeName()));
                    if (currentNode.getNode().getGeneralization(list.get(0).getAttributeName()) == 0) { // this attribute has not yet been generalized
//                        System.out.println("doThisList = TRUE");
//                        System.out.println(list.get(0).getAttributeName());
//                        System.out.println("(currentNode.getNode().getGeneralization(list.get(0).getAttributeName()) == 0)");
                        doThisList = TRUE;
                    } else {
//                        System.out.println("Do no do list");
                    }
                }
                if (doThisList) {
//                    System.out.println("doList");
//                    Recommendation rec = new Recommendation(list.get(0).getAttributeName(), list.get(0).getMaxLevelAttribute());
//                    System.out.println("rec: " + rec);
                    double recommendedLevelRisk = 9999;
                    int recommendedLevel = 1;
                    int level = 0;
                    ArrayList<SmoothNode> oneAttributeList = new ArrayList<>();
                    for (SmoothNode node : list) {
                        oneAttributeList.add(node);
//                        rec.addRiskJson(getRiskJSON(node));
//                        System.out.println("Id in make recs: " + node.id());
//                        rec.addHashCode(node.id());
                        if (node.getRisks()[povIndex] < recommendedLevelRisk) {
                            recommendedLevelRisk = node.getRisks()[povIndex];
                            recommendedLevel = level;
//                            rec.setLowestRisk(recommendedLevelRisk);

                        }
                        level++;
                    }
                    for (SmoothNode node : list) {
//                        System.out.println(node.getAttributeName());
                        node.setRecommendedLevel(recommendedLevel + 1);
                        node.setRecommendedLevelRisk(recommendedLevelRisk);
                    }
                    recommendations.add(oneAttributeList);
                }

            }
        }
//                    System.out.println("Number of recomenndations: " + recommendations.size());

//
//        for(ArrayList<SmoothNode> list: smoothNodes){
//            for (SmoothNode node : list){
//                System.out.println(node.getAttributeName() + ": " + node.getRecommendedLevelRisk());
//            }
//        }
//        System.out.println("recommendations size: " + this.recommendations.size());
    }

    private ARXLattice.ARXNode getNodeWithGeneralizationsInCurrentResult(ARXLattice.ARXNode node, int[] goal) {
//        System.out.println("getNodeWithGeneralizationsInCurrentResult");

        if (node == null) {
//            System.out.println("(node == null)");
            node = currentResult.getLattice().getBottom();
        }
        if (goal == null) {
//            System.out.println("goal == null");
            goal = node.getTransformation();
        }
//        System.out.println("nodeTrans: " + Arrays.toString(node.getTransformation()));
        if (isSameTransformation(node.getTransformation(), goal)) {
//            System.out.println("isSameTrans");
            return node;
        }
        node.expand();
        ARXLattice.ARXNode[] successors = node.getSuccessors();
        boolean notFound = TRUE;
        int s = 0;
        while (notFound) {
            ARXLattice.ARXNode succ = successors[s++];
//            System.out.println("succTrans: " + Arrays.toString(succ.getTransformation()));
            if (stepCloserToRightNode(node.getTransformation(), succ.getTransformation(), goal)) {
//                System.out.println("stepClose");
                if (isSameTransformation(succ.getTransformation(), goal)) {
//                    System.out.println("isSameTrans");
                    return succ;
                }
                node = succ;
                notFound = FALSE;
                return getNodeWithGeneralizationsInCurrentResult(node, goal);
            }
        }

        return node;
    }

    private ARXLattice.ARXNode getNodeWithGeneralizationsResult(ARXResult result, ARXLattice.ARXNode node, int[] goal) {
//        System.out.println("getNodeWithGeneralizationsResult");

        if (node == null) {
//            System.out.println("(node == null)");
            node = result.getLattice().getBottom();
        }
        if (goal == null) {
//            System.out.println("goal === null");
            goal = node.getTransformation();
        }
//        System.out.println("nodeTrans: " + Arrays.toString(node.getTransformation()));
        if (isSameTransformation(node.getTransformation(), goal)) {
//            System.out.println("isSameTrans");
            return node;
        }
        node.expand();
        ARXLattice.ARXNode[] successors = node.getSuccessors();
        boolean notFound = TRUE;
        int s = 0;
        while (notFound) {
            ARXLattice.ARXNode succ = successors[s++];
//            System.out.println("succTrans: " + Arrays.toString(succ.getTransformation()));
            if (stepCloserToRightNode(node.getTransformation(), succ.getTransformation(), goal)) {
//                System.out.println("stepClose");
                if (isSameTransformation(succ.getTransformation(), goal)) {
//                    System.out.println("isSameTrans");
                    return succ;
                }
                node = succ;
                notFound = FALSE;
                return getNodeWithGeneralizationsResult(result, node, goal);
            }
        }

        return node;
    }

    private boolean isSameTransformation(int[] trans1, int[] trans2) {
        for (int i = 0; i < trans1.length; i++) {
            if (trans1[i] != trans2[i]) {
                return FALSE;
            }
        }
        return TRUE;
    }

    private boolean stepCloserToRightNode(int[] initTrans, int[] succTrans, int[] goal) {
        for (int i = 0; i < initTrans.length; i++) {
            if (initTrans[i] < succTrans[i]) {
                if (succTrans[i] <= goal[i]) {
                    return TRUE;
                }
            }
        }
        return FALSE;
    }


    /**
     * Sorts the recommendations and returns
     * the JSONArray of the recommendations sorted,
     * such that first element has lowest risk
     *
     * @return
     */
    public JSONArray getRecommendationsJSONArraySorted() {
//        System.out.println("getRecommendationsJSONArraySorted");
//        System.out.println(this.recommendations.size());

        JSONArray jarray = new JSONArray();
        ArrayList<ArrayList<SmoothNode>> sortedList = new ArrayList<>();
        for (ArrayList<SmoothNode> nodeList : this.recommendations) {
            if (nodeList.size() > 0) {
                boolean placed = FALSE;
                if (sortedList.size() == 0) {
                    sortedList.add(nodeList);
                } else {
                    int sortedListSize = sortedList.size();
                    for (int i = 0; i < sortedListSize; i++) {
                        Double risk1 = nodeList.get(0).getRecommendedLevelRisk();
//                        System.out.println(risk1 == null);
                        Double risk2 = sortedList.get(i).get(0).getRecommendedLevelRisk();
//                        System.out.println(risk2 == null);
                        if (risk1 < risk2) {
                            sortedList.add(i, nodeList);
                            placed = TRUE;
                            break;
                        }
                    }
                    if (!placed) {
                        sortedList.add(nodeList);
                    }
                }
            }
        }
//        this.recommendations = sortedList;

        for (ArrayList<SmoothNode> nodeList : sortedList) {
            if (nodeList.size() > 0) {
                jarray.add(getRecommendationJSON(nodeList));
            }
        }
        return jarray;
    }

    private JSONObject getRecommendationJSON(ArrayList<SmoothNode> nodeList) {
        JSONObject obj = new JSONObject();
        obj.put("maxLevel", nodeList.get(0).getMaxLevelAttribute());
        obj.put("recommendedLevel", nodeList.get(0).getRecommendedLevel());
        obj.put("name", nodeList.get(0).getAttributeName());
        JSONArray values = new JSONArray();
        for (SmoothNode node : nodeList) {
            JSONObject nodeObj = new JSONObject();
            nodeObj.put("hashcode", node.id());
            nodeObj.put("risk", getRiskJSON(currentResult, node.getNode()));
            values.add(nodeObj);
        }
        obj.put("values", values);
        return obj;
    }


    /**
     * finds the node in the graphs which holds all the generalizations applied atm
     * also calls the method to fix the generalization levels for the next anonymization
     * IMPORTANT: at this point I am assuming that a new recommendation can only differ by 1 attribute
     */
    private void updateCurrentNode(SmoothNode newRec) {
//        System.out.println("updateCurrentNode");

        ARXLattice.ARXNode node;
        if (newRec == null) {
//            System.out.println("(newRec == null)");
            //todo badly written
            currentNode = new SmoothNode(currentResult.getLattice().getBottom(), currentK);
            node = currentNode.getNode();
            if (appliedRecommendations != null) {
                for (SmoothNode n : appliedRecommendations) {
                    node = getNodeWithGeneralizationsInCurrentResult(node, n.getNode().getTransformation());
                }
//                currentNode = new SmoothNode(node, getPJMArisks(currentResult.getOutput(node)), newRec.getAttributeName(), newRec.getMaxLevelAttribute(), currentK);
                currentNode = new SmoothNode(node,  currentK);
            }

        } else {
//            System.out.println("newRec trans: " + Arrays.toString(newRec.getNode().getTransformation()));
            node = getNodeWithGeneralizationsInCurrentResult(null, newRec.getNode().getTransformation());
//            System.out.println("node trans: " + Arrays.toString(node.getTransformation()));
            currentNode = new SmoothNode(node, getPJMArisks(currentResult.getOutput(node)), newRec.getAttributeName(), newRec.getMaxLevelAttribute(), currentK);
        }
//        System.out.println("id of updated currentNode: " +currentNode.id());
    }




    private ArrayList<Integer>[] getRecordsPerEC() {
        int n = 20;
        ArrayList<Integer>[] recordsPerEC = new ArrayList[n];
        for (int i = 0; i < recordsPerEC.length; i++) {
            recordsPerEC[i] = new ArrayList<>();
        }
        DataHandle handle = currentResult.getOutput(currentResult.getLattice().getBottom());
        int[] indices = new int[handle.getDefinition().getQuasiIdentifyingAttributes().size()];
        int index = 0;
        for (String attribute : handle.getDefinition().getQuasiIdentifyingAttributes()) {
            indices[index++] = handle.getColumnIndexOf(attribute);
        }

        Swapper swapper = new Swapper() {
            @Override
            public void swap(int arg0, int arg1) {
                RowSet.create(data).swap(arg0, arg1);
            }
        };
        handle.sort(swapper, true, indices);

        String[] groups = new String[handle.getNumRows()];
        int groupIdx = 0;
        groups[0] = String.valueOf(0);

        for (int row = 1; row < handle.getNumRows(); row++) {
            boolean newClass = false;
            for (int column : indices) {
                if (!handle.getValue(row, column).equals(handle.getValue(row - 1, column))) {
                    newClass = true;
                    break;
                }
            }

            groupIdx += newClass ? 1 : 0;
            groups[row] = String.valueOf(groupIdx);
        }

        List asList = Arrays.asList(groups);
        Set<String> mySet = new HashSet<String>(asList);
        for (String s : mySet) {
            int count = Collections.frequency(asList, s);
            if (count <= n) {
                recordsPerEC[count - 1].add(Integer.valueOf(s));
            }

        }
        return recordsPerEC;
    }

    private void testHashMultiSet() {
        int n = 20;
        ArrayList<String>[] recordsPerEC = new ArrayList[n];
        for (int i = 0; i < recordsPerEC.length; i++) {
            recordsPerEC[i] = new ArrayList<>();
        }
        HashMultiset<String> multiset = HashMultiset.create();
        DataHandle handle = currentResult.getOutput(currentResult.getLattice().getBottom());
        Iterator<String[]> iter = handle.iterator();
        for (String h : iter.next()) {
            multiset.add(h);
        }
        for (Multiset.Entry<String> entry : multiset.entrySet()) {
            int count = entry.getCount();
//            System.out.println(count);
//            System.out.println(entry.getElement());
            if (count <= n) {
                recordsPerEC[count - 1].add(entry.getElement());
            }
        }
//        System.out.println("Number records in smallest EC: " + recordsPerEC[0].size());
//        Iterator iterMulti = multiset.iterator();
//        for (String i : iter.next()){
//
//        }
    }
}

