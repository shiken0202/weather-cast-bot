package org.example.weathercastbot.service;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

@SpringBootTest
public class CWADebugTest {

    @Autowired
    private CWAService cwaService;

    @Autowired
    private org.springframework.core.env.Environment env;

    @Test
    public void testFetchWarnings() throws Exception {
        String apiKey = env.getProperty("cwa.api.key");
        String url = String.format("https://opendata.cwa.gov.tw/api/v1/rest/datastore/W-C0033-002?Authorization=%s", apiKey);
        
        HttpRequest request = HttpRequest.newBuilder().uri(URI.create(url)).GET().build();
        HttpResponse<String> response = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
        
        System.out.println("=== CWA API RESPONSE ===");
        System.out.println(response.body());
        System.out.println("========================");
    }
}
