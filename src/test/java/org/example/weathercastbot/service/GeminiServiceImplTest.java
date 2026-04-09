package org.example.weathercastbot.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.weathercastbot.service.impl.GeminiServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.net.http.HttpClient;
import java.net.http.HttpResponse;

import static org.mockito.Mockito.when;

public class GeminiServiceImplTest {

    private GeminiServiceImpl geminiService;
    private HttpClient mockHttpClient;

    @BeforeEach
    public void setup() {
        mockHttpClient = Mockito.mock(HttpClient.class);
        geminiService = new GeminiServiceImpl(mockHttpClient, new ObjectMapper(), "mock-api-key");
    }

    @Test
    public void testGenerateWeatherResponse() throws Exception {
        String mockJsonResponse = "{\n" +
                "  \"candidates\": [\n" +
                "    {\n" +
                "      \"content\": {\n" +
                "        \"parts\": [\n" +
                "          {\n" +
                "            \"text\": \"台北市今天天氣多雲時晴，氣溫約 22 到 28 度，降雨機率是 30%，外出記得帶把傘喔！\"\n" +
                "          }\n" +
                "        ]\n" +
                "      }\n" +
                "    }\n" +
                "  ]\n" +
                "}";

        HttpResponse<String> mockResponse = Mockito.mock(HttpResponse.class);
        when(mockResponse.statusCode()).thenReturn(200);
        when(mockResponse.body()).thenReturn(mockJsonResponse);
    }
}
