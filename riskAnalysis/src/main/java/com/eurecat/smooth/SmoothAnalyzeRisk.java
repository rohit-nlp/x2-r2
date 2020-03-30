package com.eurecat.smooth;

import akka.NotUsed;
import akka.actor.ActorSystem;
import akka.http.javadsl.ConnectHttp;
import akka.http.javadsl.Http;
import akka.http.javadsl.ServerBinding;
import akka.http.javadsl.marshallers.jackson.Jackson;
import akka.http.javadsl.model.HttpRequest;
import akka.http.javadsl.model.HttpResponse;
import akka.http.javadsl.model.StatusCodes;
import akka.http.javadsl.server.AllDirectives;
import akka.http.javadsl.server.Route;
import akka.stream.ActorMaterializer;
import akka.stream.javadsl.Flow;
import org.json.simple.JSONObject;

import java.io.IOException;
import java.util.concurrent.CompletionStage;

public class SmoothAnalyzeRisk extends AllDirectives {

    public static void main(String[] args) throws Exception {


        ActorSystem system = ActorSystem.create("routes");

        String host="0.0.0.0";
        int port=5004;
        final Http http = Http.get(system);
        final ActorMaterializer materializer = ActorMaterializer.create(system);

        SmoothAnalyzeRisk sar=new SmoothAnalyzeRisk();
        final Flow<HttpRequest, HttpResponse, NotUsed> routeFlow = sar.appRoute().flow(system, materializer);
        final CompletionStage<ServerBinding> binding = http.bindAndHandle(routeFlow,
                ConnectHttp.toHost(host, port), materializer);

        System.out.println("Server online at http://"+host+":"+port+"/");

//        binding
//                .thenCompose(ServerBinding::unbind) // trigger unbinding from the port
//                .thenAccept(unbound -> system.terminate()); // and shutdown when done

    }


    /**
     * creates the http routes for the api
     *
     * @return
     */
    private Route appRoute() throws IOException {

        Route resultRoute =
                parameter("input", input ->
                parameterOptional("hashcode", optHash -> {
                    Double hash = Double.valueOf(optHash.orElse("-1"));
//                    System.out.println("appRoute ------------------");
                    SmoothIO smoothio = null;
                    try {
                        smoothio = new SmoothIO();
                        smoothio.setFolderPath("data/");
                        smoothio.loadDataCsv("adult");
                        smoothio.addHierarchiesFromFolder();

                        //todo createResult as soon as we have data
                        smoothio.createResult();
                        return complete(StatusCodes.OK,smoothio.handleJSONRequest(hash).toJSONString());
                    } catch (IOException e) {
                        e.printStackTrace();
                        JSONObject obj = new JSONObject();
                        obj.put("error",e.getMessage());
                        return complete(StatusCodes.NOT_ACCEPTABLE,obj.toJSONString());
                    }

                })
        );
        Route reportRoute =
                entity(Jackson.unmarshaller(Input.class), input -> {
                    // parameter("input", input -> {
                    ReportInputHandler rih = new ReportInputHandler();
                    ReportInput ri = ReportInputHandler.parseJson(input);
                    try {
                        String riskLevel = ReportInputHandler.generateBasicReport(ri);
                        JSONObject obj = new JSONObject();
                        obj.put("ID", input.getID());
                        obj.put("dataPath", input.getDataPath());
                        obj.put("riskResult", riskLevel);
                        return complete(StatusCodes.OK,obj.toJSONString());
                    } catch (IOException e) {
                        e.printStackTrace();
                        JSONObject obj = new JSONObject();
                        obj.put("error", e.getMessage());
                        return complete(StatusCodes.NOT_ACCEPTABLE, obj.toJSONString());
                    }

                } );
        Route reportRouteAdvanced =
                entity(Jackson.unmarshaller(Input.class), input -> {
                    // parameter("input", input -> {
                    ReportInputHandler rih = new ReportInputHandler();
                    ReportInput ri = ReportInputHandler.parseJson(input);
                    try {
                        JSONObject jo = ReportInputHandler.generateReport(ri);
                        jo.put("ID", input.getID());
                        jo.put("dataPath", input.getDataPath());
                        return complete(StatusCodes.OK, jo.toJSONString());
                    } catch (IOException e) {
                        e.printStackTrace();
                        JSONObject obj = new JSONObject();
                        obj.put("error", e.getMessage());
                        return complete(StatusCodes.NOT_ACCEPTABLE, obj.toJSONString());
                    }

                } );
        return
                concat(
                        get(() -> concat(
                                path("resultforUI", () -> resultRoute),
                                path("analyzeRisk", () -> reportRoute))
                        )
                ,
                        post(() -> concat(
                                path("resultforUI", () -> resultRoute),
                                path("analyzeRisk", () -> reportRoute),
                                path("analyzeRiskAdvanced", () -> reportRouteAdvanced))
                        )
                );

    }
}
