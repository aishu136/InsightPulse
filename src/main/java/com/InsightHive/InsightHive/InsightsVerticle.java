package com.InsightHive.InsightHive;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.AsyncResult;
import io.vertx.core.Promise;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.core.json.JsonObject;
import io.vertx.core.json.JsonArray;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpServer;
import io.vertx.ext.web.client.WebClient;
import io.vertx.ext.web.client.WebClientOptions;
import software.amazon.awssdk.auth.credentials.EnvironmentVariableCredentialsProvider;
// import software.amazon.awssdk.services.bedrock.BedrockClient;
import io.vertx.core.AsyncResult;
import io.vertx.core.http.HttpServer;
import software.amazon.awssdk.regions.Region;
// import software.amazon.awssdk.*;
// import software.amazon.awssdk.services.bedrock.model.InvokeModelRequest;
// import software.amazon.awssdk.services.bedrock.model.InvokeModelResponse;

import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;
import software.amazon.awssdk.services.bedrockruntime.model.InvokeModelRequest;
import software.amazon.awssdk.services.bedrockruntime.model.InvokeModelResponse;
import software.amazon.awssdk.core.SdkBytes;

import software.amazon.awssdk.core.SdkBytes;

import org.neo4j.driver.*;

public class InsightsVerticle extends AbstractVerticle {

    private Driver driver;
    private WebClient webClient;


    //  private final BedrockClient bedrockClient = BedrockClient.builder()
    //         .region(Region.AP_SOUTH_1)
    //         .credentialsProvider(EnvironmentVariableCredentialsProvider.create())
    //         .build();

    @Override
    public void start(Promise<Void> startPromise) {

        // Neo4j driver setup
        driver = GraphDatabase.driver("bolt://localhost:7687", 
                    AuthTokens.basic("neo4j", "neo4j@2025"));

        webClient = WebClient.create(vertx);

        Router router = Router.router(vertx);
        router.get("/trending").handler(this::getTrending);
        router.get("/segments").handler(this::getSegments);
        router.get("/heatmap").handler(this::getHeatmap);
        router.post("/ai-insight").handler(this::aiInsight);

  

vertx.createHttpServer()
     .requestHandler(router)
     .listen(8081)
     .onSuccess(server -> {
         startPromise.complete();
         System.out.println("HTTP server started on port " + server.actualPort());
     })
     .onFailure(err -> {
         startPromise.fail(err);
     });

    }

    private void getTrending(RoutingContext ctx) {
        try (Session session = driver.session()) {
            String query = "MATCH (p:Product)<-[:OF_PRODUCT]-(:OrderItem)<-[:HAS_ITEM]-(o:Order) " +
                           "WHERE date(o.orderDate) >= date() - duration({days:30}) " +
                           "RETURN p.productId AS productId, p.name AS name, p.category AS category, count(DISTINCT o) AS orders " +
                           "ORDER BY orders DESC LIMIT 10";

            JsonArray rows = new JsonArray();
            session.run(query).list().forEach(r -> {
                rows.add(new JsonObject()
                        .put("productId", r.get("productId").asString())
                        .put("name", r.get("name").asString())
                        .put("category", r.get("category").asString())
                        .put("orders", r.get("orders").asInt()));
            });

            ctx.json(rows);
        } catch (Exception e) {
            ctx.fail(e);
        }
    }

    private void getSegments(RoutingContext ctx) {
        try (Session session = driver.session()) {
            String query = "MATCH (c:Customer) RETURN c.customerId AS id, c.cluster AS cluster";
            JsonArray rows = new JsonArray();

            session.run(query).list().forEach(r -> {
                rows.add(new JsonObject()
                        .put("customerId", r.get("id").asString())
                        .put("cluster", r.get("cluster").isNull() ? -1 : r.get("cluster").asInt()));
            });

            ctx.json(rows);
        } catch (Exception e) {
            ctx.fail(e);
        }
    }

    private void getHeatmap(RoutingContext ctx) {
        try (Session session = driver.session()) {
            String query = "MATCH (oi:OrderItem)-[:OF_PRODUCT]->(p:Product) " +
                           "RETURN p.category AS category, oi.region AS region, sum(oi.quantity * oi.price) AS revenue";

            JsonArray rows = new JsonArray();
            session.run(query).list().forEach(r -> {
                rows.add(new JsonObject()
                        .put("category", r.get("category").asString())
                        .put("region", r.get("region").asString())
                        .put("revenue", r.get("revenue").asDouble()));
            });

            ctx.json(rows);
        } catch (Exception e) {
            ctx.fail(e);
        }
    }


// private void aiInsight(RoutingContext ctx) {
//     ctx.request().bodyHandler(buffer -> {
//         JsonObject body = buffer.toJsonObject();
//         String prompt = body.getString("context", "Provide a short summary of trends.");

//         // ✅ Gemini expects 'contents' not 'prompt'
//         JsonObject payload = new JsonObject()
//                 .put("contents", new JsonArray()
//                         .add(new JsonObject()
//                                 .put("parts", new JsonArray()
//                                         .add(new JsonObject().put("text", prompt)))));

//         String apiKey = System.getenv("GOOGLE_API_KEY");
//         if (apiKey == null || apiKey.isEmpty()) {
//             ctx.fail(new RuntimeException("Missing GOOGLE_API_KEY environment variable"));
//             return;
//         }

//         // ✅ Create secure WebClient with SSL enabled
//         WebClient secureClient = WebClient.create(vertx, new WebClientOptions().setSsl(true).setTrustAll(true));

//         secureClient
//                 .post(443, "generativelanguage.googleapis.com", "/v1beta/models/gemini-1.5-flash:generateContent")
//                 .putHeader("Content-Type", "application/json")
//                 .putHeader("x-goog-api-key", apiKey)
//                 .sendBuffer(Buffer.buffer(payload.encode()))
//                 .onSuccess(response -> {
//                     if (response.statusCode() == 200) {
//                         JsonObject resp = response.bodyAsJsonObject();

//                         String summary = "No summary";
//                         try {
//                             summary = resp.getJsonArray("candidates")
//                                           .getJsonObject(0)
//                                           .getJsonObject("content")
//                                           .getJsonArray("parts")
//                                           .getJsonObject(0)
//                                           .getString("text");
//                         } catch (Exception e) {
//                             e.printStackTrace();
//                         }

//                         ctx.json(new JsonObject().put("summary", summary));
//                     } else {
//                         ctx.fail(new RuntimeException(
//                                 "Gemini call failed with status " + response.statusCode() + ": " + response.bodyAsString()));
//                     }
//                 })
//                 .onFailure(err -> {
//                     err.printStackTrace();
//                     ctx.fail(err);
//                 });
//     });
// }

private void aiInsight(RoutingContext ctx) {
    ctx.request().bodyHandler(buffer -> {
        JsonObject body = buffer.toJsonObject();
        String prompt = body.getString("context", "Provide a short summary of trends.");

        try (BedrockRuntimeClient bedrockClient = BedrockRuntimeClient.builder()
                .region(software.amazon.awssdk.regions.Region.AP_SOUTH_1)
                .credentialsProvider(EnvironmentVariableCredentialsProvider.create())
                .build()) {

            // Use correct field for Titan Text Lite
            String jsonBody = new JsonObject()
                    .put("inputText", prompt)
                    .encode();

            InvokeModelRequest request = InvokeModelRequest.builder()
                    .modelId("amazon.titan-text-lite-v1")
                    .body(SdkBytes.fromUtf8String(jsonBody))
                    .build();

            InvokeModelResponse response = bedrockClient.invokeModel(request);

            String responseStr = response.body().asUtf8String();
            System.out.println("Raw Bedrock response: " + responseStr); // Log the full response

            String summary = "";
            try {
                JsonObject jsonResponse = new JsonObject(responseStr);

                // Try multiple possible paths
                if (jsonResponse.containsKey("results")) {
                    JsonArray results = jsonResponse.getJsonArray("results");
                    if (!results.isEmpty()) {
                        JsonObject firstResult = results.getJsonObject(0);
                        if (firstResult.containsKey("output")) {
                            JsonArray output = firstResult.getJsonArray("output");
                            if (!output.isEmpty()) {
                                JsonObject firstOutput = output.getJsonObject(0);
                                if (firstOutput.containsKey("content")) {
                                    JsonArray content = firstOutput.getJsonArray("content");
                                    if (!content.isEmpty()) {
                                        summary = content.getJsonObject(0).getString("text", "");
                                    }
                                }
                            }
                        }
                        // Fallback: some versions return outputText directly
                        if (summary.isEmpty() && firstResult.containsKey("outputText")) {
                            summary = firstResult.getString("outputText", "");
                        }
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }

            ctx.json(new JsonObject().put("summary", summary));

        } catch (Exception e) {
            e.printStackTrace();
            ctx.fail(new RuntimeException("Bedrock call failed", e));
        }
    });
}







    @Override
    public void stop() throws Exception {
        if (driver != null) driver.close();
    }
}

