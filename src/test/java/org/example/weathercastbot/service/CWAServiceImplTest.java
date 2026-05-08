package org.example.weathercastbot.service;

import org.example.weathercastbot.dto.EarthquakeDto;
import org.example.weathercastbot.dto.WeatherInfoDto;
import org.example.weathercastbot.service.impl.CWAServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

public class CWAServiceImplTest {

    private CWAServiceImpl cwaService;
    private HttpClient mockHttpClient;

    @BeforeEach
    public void setup() {
        mockHttpClient = Mockito.mock(HttpClient.class);
        cwaService = new CWAServiceImpl(mockHttpClient, "mock-api-key");
    }

    @Test
    public void testGetDailyForecast_Success() throws Exception {
        String mockJsonResponse = "{\n" +
                "  \"records\": {\n" +
                "    \"location\": [\n" +
                "      {\n" +
                "        \"locationName\": \"臺北市\",\n" +
                "        \"weatherElement\": [\n" +
                "          {\"elementName\": \"Wx\", \"time\": [{\"parameter\": {\"parameterName\": \"多雲時晴\"}}]},\n" +
                "          {\"elementName\": \"PoP\", \"time\": [{\"parameter\": {\"parameterName\": \"30\"}}]},\n" +
                "          {\"elementName\": \"MinT\", \"time\": [{\"parameter\": {\"parameterName\": \"22\"}}]},\n" +
                "          {\"elementName\": \"MaxT\", \"time\": [{\"parameter\": {\"parameterName\": \"28\"}}]}\n" +
                "        ]\n" +
                "      }\n" +
                "    ]\n" +
                "  }\n" +
                "}";

        HttpResponse<String> mockResponse = Mockito.mock(HttpResponse.class);
        when(mockResponse.statusCode()).thenReturn(200);
        when(mockResponse.body()).thenReturn(mockJsonResponse);
        when(mockHttpClient.send(any(HttpRequest.class), Mockito.<HttpResponse.BodyHandler<String>>any())).thenReturn(mockResponse);

        Optional<WeatherInfoDto> result = cwaService.getDailyForecast("臺北市");

        assertTrue(result.isPresent());
        assertEquals("臺北市", result.get().getLocationName());
        assertEquals("多雲時晴", result.get().getDescription());
        assertEquals("30%", result.get().getRainProbability());
        assertEquals("22", result.get().getMinTemp());
        assertEquals("28", result.get().getMaxTemp());
    }

    @Test
    public void testIsGoingToRain_True() throws Exception {
        String mockJsonResponse = "{\n" +
                "  \"records\": {\n" +
                "    \"location\": [\n" +
                "      {\n" +
                "        \"locationName\": \"基隆市\",\n" +
                "        \"weatherElement\": [\n" +
                "          {\"elementName\": \"Wx\", \"time\": [{\"parameter\": {\"parameterName\": \"短暫陣雨\"}}]},\n" +
                "          {\"elementName\": \"PoP\", \"time\": [{\"parameter\": {\"parameterName\": \"80\"}}]},\n" +
                "          {\"elementName\": \"MinT\", \"time\": [{\"parameter\": {\"parameterName\": \"19\"}}]},\n" +
                "          {\"elementName\": \"MaxT\", \"time\": [{\"parameter\": {\"parameterName\": \"21\"}}]}\n" +
                "        ]\n" +
                "      }\n" +
                "    ]\n" +
                "  }\n" +
                "}";

        HttpResponse<String> mockResponse = Mockito.mock(HttpResponse.class);
        when(mockResponse.statusCode()).thenReturn(200);
        when(mockResponse.body()).thenReturn(mockJsonResponse);
        when(mockHttpClient.send(any(HttpRequest.class), Mockito.<HttpResponse.BodyHandler<String>>any())).thenReturn(mockResponse);

        assertTrue(cwaService.isGoingToRain("基隆市"));
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testGetLatestEarthquake_Success() throws Exception {
        String mockJsonResponse = "{\n" +
                "  \"records\": {\n" +
                "    \"Earthquake\": [\n" +
                "      {\n" +
                "        \"EarthquakeNo\": \"112003\",\n" +
                "        \"ReportContent\": \"發生規模5.2有感地震\",\n" +
                "        \"EarthquakeInfo\": {\n" +
                "          \"OriginTime\": \"2024-02-14 12:00:00\",\n" +
                "          \"FocalDepth\": 15.5,\n" +
                "          \"EarthquakeMagnitude\": {\"MagnitudeValue\": 5.2},\n" +
                "          \"Epicenter\": {\"Location\": \"花蓮縣\"}\n" +
                "        },\n" +
                "        \"Intensity\": {\n" +
                "          \"ShakingArea\": [\n" +
                "            {\"AreaName\": \"花蓮縣\", \"AreaIntensity\": \"4級\"},\n" +
                "            {\"AreaName\": \"臺東縣\", \"AreaIntensity\": \"3級\"}\n" +
                "          ]\n" +
                "        }\n" +
                "      }\n" +
                "    ]\n" +
                "  }\n" +
                "}";

        HttpResponse<String> mockResponse = Mockito.mock(HttpResponse.class);
        when(mockResponse.statusCode()).thenReturn(200);
        when(mockResponse.body()).thenReturn(mockJsonResponse);
        when(mockHttpClient.send(any(HttpRequest.class), Mockito.<HttpResponse.BodyHandler<String>>any())).thenReturn(mockResponse);

        java.util.List<EarthquakeDto> result = cwaService.getLatestEarthquakes();

        assertFalse(result.isEmpty());
        EarthquakeDto eq = result.get(0);
        assertEquals("112003", eq.getEarthquakeNo());
        assertEquals("發生規模5.2有感地震", eq.getReportContent());
        assertEquals("5.2", eq.getMagnitude());
        assertEquals("15.5", eq.getDepth());
        assertEquals("花蓮縣", eq.getLocation());
        assertEquals("2024-02-14 12:00:00", eq.getTime());
    }
}
