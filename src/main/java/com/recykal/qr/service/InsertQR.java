package com.recykal.qr.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.bson.Document;
import org.bson.types.ObjectId;

import com.mongodb.MongoException;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.result.InsertManyResult;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class InsertQR {

    @Value("${spring.mongo.uri}")
    private String uri;

    public Map<Object, Object> insertData(Long qrCount) {

        try (MongoClient mongoClient = MongoClients.create(uri)) {

            MongoDatabase database = mongoClient.getDatabase("QR");
            MongoCollection<Document> collection = database.getCollection("qr_codes");

            long startTime = System.currentTimeMillis();
            long numTimesToInsert = qrCount;

            Map<Object, Object> response = new HashMap<>();

            try {
                List<Document> documentsToInsert = new ArrayList<>();
                for (int i = 0; i < numTimesToInsert; i++) {
                    Document document = new Document()
                            .append("_id", new ObjectId())
                            .append("title", "Ski Bloopers")
                            .append("name", "qr-code");

                    documentsToInsert.add(document);
                }
                InsertManyResult result = collection.insertMany(documentsToInsert);
                System.out.println("Success! Inserted " + result.getInsertedIds().size() + " documents.");

                long endTime = System.currentTimeMillis();
                long elapsedTime = endTime - startTime;

                Map<Object, Object> qrCodesList=new HashMap<>();
                qrCodesList.put("message", "successfully created");
                qrCodesList.put("time",elapsedTime);

                response.put("message", "Data inserted successfully " + numTimesToInsert + " times.");
                response.put("time", elapsedTime + " milliseconds.");
            } catch (MongoException me) {
                System.err.println("Unable to insert due to an error: " + me);
                response.put("error", me.getMessage());
            }
            return response;
        }
    }
}