package com.comptaassist.cabinet_service.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

@Service
@Slf4j
public class PdfSignatureService {

    @Value("${gotenberg.url:http://localhost:3002}")
    private String gotenbergUrl;

    private final RestTemplate restTemplate = new RestTemplate();

    public byte[] genererPdfAvecSignatureComptableSeulement(
            String htmlOriginal,
            String signatureComptableBase64,
            String nomComptable) {

        String date = java.time.LocalDate.now()
                .format(java.time.format.DateTimeFormatter
                        .ofPattern("dd/MM/yyyy"));

        String blockSignatures =
                "<div style='display:grid;grid-template-columns:1fr 1fr;" +
                        "gap:50px;margin-top:40px;padding-top:16px;" +
                        "border-top:2px solid #000;page-break-inside:avoid'>" +

                        "<div style='text-align:center'>" +
                        "  <div style='font-size:11px;font-weight:bold;" +
                        "text-transform:uppercase;border-bottom:1px solid #000;" +
                        "padding-bottom:4px;margin-bottom:8px'>" +
                        "Signature du Cabinet</div>" +
                        "  <img src='" + signatureComptableBase64 + "' " +
                        "style='width:180px;height:60px;object-fit:contain;" +
                        "border-bottom:1px solid #000;display:block;margin:0 auto 6px'/>" +
                        "  <div style='font-weight:bold;font-size:11px'>" +
                        nomComptable + "</div>" +
                        "  <div style='font-size:10px;color:#444'>" +
                        "Signé le " + date + "</div>" +
                        "  <div style='font-size:10px;margin-top:4px'>" +
                        "&#10003; Signature électronique</div>" +
                        "</div>" +

                        "<div style='text-align:center'>" +
                        "  <div style='font-size:11px;font-weight:bold;" +
                        "text-transform:uppercase;border-bottom:1px solid #000;" +
                        "padding-bottom:4px;margin-bottom:8px'>" +
                        "Signature du Client</div>" +
                        "  <div style='height:60px;margin-bottom:6px;" +
                        "border-bottom:1px solid #000;display:flex;" +
                        "align-items:center;justify-content:center;" +
                        "color:#999;font-size:11px;font-style:italic'>" +
                        "En attente de signature...</div>" +
                        "  <div style='font-size:10px;color:#444'>" +
                        "Cachet et signature</div>" +
                        "</div>" +

                        "</div>";

        String html = remplacerZoneSignatures(htmlOriginal, blockSignatures);
        return convertirHtmlEnPdf(html);
    }

    public byte[] genererPdfAvecSignatures(
            String htmlOriginal,
            String signatureComptableBase64,
            String nomComptable,
            String signatureClientBase64,
            String nomClient) {

        String date = java.time.LocalDate.now()
                .format(java.time.format.DateTimeFormatter
                        .ofPattern("dd/MM/yyyy"));

        String blockSignatures =
                "<div style='display:grid;grid-template-columns:1fr 1fr;" +
                        "gap:50px;margin-top:40px;padding-top:16px;" +
                        "border-top:2px solid #000;page-break-inside:avoid'>" +

                        "<div style='text-align:center'>" +
                        "  <div style='font-size:11px;font-weight:bold;" +
                        "text-transform:uppercase;border-bottom:1px solid #000;" +
                        "padding-bottom:4px;margin-bottom:8px'>" +
                        "Signature du Cabinet</div>" +
                        "  <img src='" + signatureComptableBase64 + "' " +
                        "style='width:180px;height:60px;object-fit:contain;" +
                        "border-bottom:1px solid #000;display:block;margin:0 auto 6px'/>" +
                        "  <div style='font-weight:bold;font-size:11px'>" +
                        nomComptable + "</div>" +
                        "  <div style='font-size:10px;color:#444'>" +
                        "Signé le " + date + "</div>" +
                        "  <div style='font-size:10px;margin-top:4px'>" +
                        "&#10003; Signature électronique</div>" +
                        "</div>" +

                        "<div style='text-align:center'>" +
                        "  <div style='font-size:11px;font-weight:bold;" +
                        "text-transform:uppercase;border-bottom:1px solid #000;" +
                        "padding-bottom:4px;margin-bottom:8px'>" +
                        "Signature du Client</div>" +
                        "  <img src='" + signatureClientBase64 + "' " +
                        "style='width:180px;height:60px;object-fit:contain;" +
                        "border-bottom:1px solid #000;display:block;margin:0 auto 6px'/>" +
                        "  <div style='font-weight:bold;font-size:11px'>" +
                        nomClient + "</div>" +
                        "  <div style='font-size:10px;color:#444'>" +
                        "Signé le " + date + "</div>" +
                        "  <div style='font-size:10px;margin-top:4px'>" +
                        "&#10003; Signature électronique</div>" +
                        "</div>" +

                        "</div>";

        String html = remplacerZoneSignatures(htmlOriginal, blockSignatures);
        return convertirHtmlEnPdf(html);
    }

    private String remplacerZoneSignatures(
            String html, String blockSignatures) {
        if (html.contains("class='signatures-zone'") ||
                html.contains("class=\"signatures-zone\"")) {
            html = html.replaceAll(
                    "(?s)<div class=['\"]signatures-zone['\"].*?</div>\\s*</body>",
                    blockSignatures + "</body>"
            );
        } else {
            html = html.replace("</body>",
                    blockSignatures + "</body>");
        }
        return html;
    }

    private byte[] convertirHtmlEnPdf(String html) {
        try {
            String boundary = "BOUNDARY" + System.currentTimeMillis();
            String CRLF = "\r\n";

            byte[] part1 = (
                    "--" + boundary + CRLF +
                            "Content-Disposition: form-data; name=\"files\";" +
                            " filename=\"index.html\"" + CRLF +
                            "Content-Type: text/html" + CRLF + CRLF
            ).getBytes("UTF-8");
            byte[] part2 = html.getBytes("UTF-8");
            byte[] part3 = (CRLF + "--" + boundary + "--" + CRLF)
                    .getBytes("UTF-8");

            byte[] body = new byte[
                    part1.length + part2.length + part3.length];
            System.arraycopy(part1, 0, body, 0, part1.length);
            System.arraycopy(part2, 0, body,
                    part1.length, part2.length);
            System.arraycopy(part3, 0, body,
                    part1.length + part2.length, part3.length);

            HttpHeaders headers = new HttpHeaders();
            headers.set("Content-Type",
                    "multipart/form-data; boundary=" + boundary);

            ResponseEntity<byte[]> response = restTemplate.exchange(
                    gotenbergUrl + "/forms/chromium/convert/html",
                    HttpMethod.POST,
                    new HttpEntity<>(body, headers),
                    byte[].class
            );
            return response.getBody();
        } catch (Exception e) {
            log.error("Erreur Gotenberg : {}", e.getMessage());
            throw new RuntimeException(
                    "Erreur génération PDF : " + e.getMessage());
        }
    }
}