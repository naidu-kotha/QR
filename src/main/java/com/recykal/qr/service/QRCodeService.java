package com.recykal.qr.service;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.result.InsertManyResult;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.bson.Document;
import org.hashids.Hashids;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import javax.imageio.ImageIO;
import javax.servlet.http.HttpServletResponse;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@Service
public class QRCodeService {
    private Hashids hashids;

    @Value("${spring.qrcode.salt-key}")
    private String QR_SALT_KEY;

    @Value("${spring.mongo.uri}")
    private String uri;

    public String encodeHashId(Long id) {
        try {
            Hashids hashids = getHashObject();
            return hashids.encode(id);
        } catch (Exception ex) {
            System.out.println(ex.getMessage());
            return ex.getMessage();
        }
    }

    public Long decodeHashId(String id) {
        try {
            Hashids hashids = getHashObject();
            long[] qrCode = hashids.decode(id);
            //log.info("QR code {} ", qrCode);
            return (qrCode != null && qrCode.length > 0) ? qrCode[0] : null;
        } catch (Exception ex) {
            System.out.println(ex.getMessage());
            return null;
        }
    }

    private Hashids getHashObject() {
        if (hashids == null) {
            this.hashids = new Hashids(QR_SALT_KEY, 10, "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ");
            return this.hashids;
        }

        return this.hashids;
    }

    public void generateQRCodeCsv(Long numTimesToInsert) throws IOException {
        HttpServletResponse response = ((ServletRequestAttributes) RequestContextHolder.currentRequestAttributes())
                .getResponse();
        response.setContentType("text/csv");
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setHeader(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=users.csv");

        try (CSVPrinter qrCsv = new CSVPrinter(response.getWriter(), CSVFormat.DEFAULT);
             MongoClient mongoClient = MongoClients.create(uri)) {

            List<String> labels = Arrays.asList("id", "encoded_id");
            qrCsv.printRecord(labels);

            MongoDatabase database = mongoClient.getDatabase("QR");
            MongoCollection<Document> collection = database.getCollection("qr_codes");

            List<Document> qrDocument = new ArrayList<>();

            for (int i = 0; i < numTimesToInsert; i++) {
                long standardNumber = 100000L + i;
                String qrCodeData = encodeHashId(standardNumber);
                Document document = new Document()
                        .append("id", standardNumber)
                        .append("title", "Ski Bloopers")
                        .append("mohan", "coder");

                List<Object> qrObject = new ArrayList<>();
                qrDocument.add(document);
                qrObject.add(standardNumber);
                qrObject.add(qrCodeData);

                qrCsv.printRecord(qrObject);
            }

            collection.insertMany(qrDocument);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }



    @Async()
    public byte[] generateQRCodeBytes(String qrString) throws WriterException, IOException {
        int width = 200;
        int height = 200;

        QRCodeWriter qrCodeWriter = new QRCodeWriter();
        Map<EncodeHintType, Object> hints = new HashMap<>();
        hints.put(EncodeHintType.CHARACTER_SET, "UTF-8");

        BitMatrix bitMatrix = qrCodeWriter.encode(qrString, BarcodeFormat.QR_CODE, width, height, hints);
        BufferedImage qrImage = MatrixToImageWriter.toBufferedImage(bitMatrix);

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(qrImage, "PNG", baos);

        return baos.toByteArray();
    }

    @Transactional
    @GetMapping(value = "/zip", produces = "application/zip")
    public ResponseEntity<byte[]> downloadQRCodes(
            @RequestParam(defaultValue = "1") Long count) throws IOException, WriterException {

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ZipOutputStream zipOut = new ZipOutputStream(baos);

        MongoClient mongoClient = MongoClients.create(uri);
        MongoDatabase database = mongoClient.getDatabase("QR");
        MongoCollection<Document> collection = database.getCollection("qr_codes");

        List<Document> qrDocument = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            long standardNumber = 100000L + i;
            String qrString = encodeHashId(standardNumber);

            Document document = new Document()
                    .append("id", standardNumber)
                    .append("title", "Ski Bloopers")
                    .append("mohan", "coder");

            qrDocument.add(document);

            byte[] qrCodeBytes = generateQRCodeBytes(qrString);
            String qrCodeFileName = "qrcode" + (1 + i) + ".png";
            ZipEntry zipEntry = new ZipEntry(qrCodeFileName);
            zipOut.putNextEntry(zipEntry);
            zipOut.write(qrCodeBytes);
            zipOut.closeEntry();
        }

        InsertManyResult result = collection.insertMany(qrDocument);
        System.out.println("Success! Inserted " + result.getInsertedIds().size() + " documents.");
        zipOut.close();

        byte[] zipBytes = baos.toByteArray();

        return ResponseEntity.ok()
                .header("Content-Disposition", "attachment; filename=\"qrcodes.zip\"")
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .contentLength(zipBytes.length)
                .body(zipBytes);
    }


    @Transactional
    @GetMapping(value = "/threadzip", produces = "application/zip")
    public ResponseEntity<byte[]> downloadQRCodesByThread(@RequestParam(defaultValue = "1") Long count)
            throws IOException, InterruptedException {

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ZipOutputStream zipOut = new ZipOutputStream(baos);

        MongoClient mongoClient = MongoClients.create(uri);
        MongoDatabase database = mongoClient.getDatabase("QR");
        MongoCollection<Document> collection = database.getCollection("qr_codes");

        int threads = Runtime.getRuntime().availableProcessors(); // Number of available threads

        List<Thread> threadList = new ArrayList<>();
        List<Document> qrDocument = new ArrayList<>();

        for (int i = 0; i < count; i++) {
            long standardNumber = 100000L + i;
            String qrString = encodeHashId(standardNumber);
            Document document = new Document()
                    .append("id", standardNumber)
                    .append("title", "Ski Bloopers")
                    .append("mohan", "coder");

            qrDocument.add(document);

            int finalI = i;

            Thread thread = new Thread(() -> {
                try {
                    byte[] qrCodeBytes = generateQRCodeBytes(qrString);
                    String qrCodeFileName = "qrcode" + (1 + finalI) + ".png";
                    ZipEntry zipEntry = new ZipEntry(qrCodeFileName);

                    synchronized (zipOut) {
                        zipOut.putNextEntry(zipEntry);
                        zipOut.write(qrCodeBytes);
                        zipOut.closeEntry();
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            });

            threadList.add(thread);
            thread.start();

            if (threadList.size() >= threads) {
                waitForThreads(threadList);
            }
        }

        waitForThreads(threadList); // Wait for remaining threads to finish
        InsertManyResult result = collection.insertMany(qrDocument);
        System.out.println("Success! Inserted " + result.getInsertedIds().size() + " documents.");
        zipOut.close();

        byte[] zipBytes = baos.toByteArray();

        return ResponseEntity.ok()
                .header("Content-Disposition", "attachment; filename=\"qrcodes.zip\"")
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .contentLength(zipBytes.length)
                .body(zipBytes);
    }

    private void waitForThreads(List<Thread> threadList) throws InterruptedException {
        for (Thread thread : threadList) {
            thread.join();
        }
        threadList.clear();
    }

}
