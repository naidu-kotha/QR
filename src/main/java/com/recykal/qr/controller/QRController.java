package com.recykal.qr.controller;

import com.google.zxing.WriterException;
import com.recykal.qr.service.QRCodeService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import com.recykal.qr.service.InsertQR;

import java.io.IOException;
import java.util.Map;

@RestController
public class QRController {

    @Autowired
    private InsertQR insertQR;

    @Autowired
    private QRCodeService qrCodeService;

    @PostMapping("/insertqr/{qrCount}")
    public Map<Object,Object> insertData(@PathVariable Long qrCount ) {
        return insertQR.insertData(qrCount);
    }

    @GetMapping("/encrypt/{id}")
    public String getEncryptedString(@PathVariable Long id) {
        return qrCodeService.encodeHashId(id);
    }

    @GetMapping("/decrypt/{id}")
    public Long getDecryptedString(@PathVariable String id) {
        return qrCodeService.decodeHashId(id);
    }

    @GetMapping("/csv-download")
    public void sampleCsvDownLoad(@RequestParam(defaultValue = "1") Long count) throws IOException {
        qrCodeService.generateQRCodeCsv(count);
    }

    @GetMapping("/zip")
    public ResponseEntity<byte[]> downloadQRCodes(@RequestParam(defaultValue = "1") Long count) throws IOException, WriterException {
        return qrCodeService.downloadQRCodes(count);
    }

    @GetMapping("/threadzip")
    public ResponseEntity<byte[]> downloadQRCodesByThread(@RequestParam(defaultValue = "1") Long count) throws IOException, InterruptedException {
        return qrCodeService.downloadQRCodesByThread(count);
    }
}