package org.example.weathercastbot.service.impl;

import org.example.weathercastbot.dto.EarthquakeDto;
import org.example.weathercastbot.dto.TownshipForecastDto;
import org.example.weathercastbot.dto.WeatherInfoDto;
import org.example.weathercastbot.service.CWAService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Optional;

import org.json.JSONArray;
import org.json.JSONObject;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class CWAServiceImpl implements CWAService {

    @Value("${api.cwa.key}")
    private String apiKey;

    private final HttpClient httpClient;

    private static class CacheEntry<T> {
        final T data;
        final long expiresAt;
        CacheEntry(T data, long ttlMillis) { this.data = data; this.expiresAt = System.currentTimeMillis() + ttlMillis; }
        boolean isValid() { return System.currentTimeMillis() <= expiresAt; }
    }

    private final java.util.Map<String, CacheEntry<WeatherInfoDto>> dailyCache = new java.util.concurrent.ConcurrentHashMap<>();
    private final java.util.Map<String, CacheEntry<String>> rtcCache = new java.util.concurrent.ConcurrentHashMap<>();
    private final java.util.Map<String, CacheEntry<TownshipForecastDto>> townCache = new java.util.concurrent.ConcurrentHashMap<>();
    private final java.util.Map<String, CacheEntry<String>> weeklyCache = new java.util.concurrent.ConcurrentHashMap<>();
    private static final long CACHE_TTL = 5 * 60 * 1000;

    public CWAServiceImpl() {
        this.httpClient = HttpClient.newBuilder().version(HttpClient.Version.HTTP_2).build();
    }

    // Constructor for testing
    public CWAServiceImpl(HttpClient httpClient, String apiKey) {
        this.httpClient = httpClient;
        this.apiKey = apiKey;
    }

    @Override
    public Optional<WeatherInfoDto> getDailyForecast(String locationName) {
        String normalizedLocation = normalizeLocationName(locationName);
        if (normalizedLocation == null) return Optional.empty();
        
        CacheEntry<WeatherInfoDto> cached = dailyCache.get(normalizedLocation);
        if (cached != null && cached.isValid()) return Optional.ofNullable(cached.data);

        String encodedLocation = URLEncoder.encode(normalizedLocation, StandardCharsets.UTF_8);
        
        // CWA API F-C0032-001 (一般天氣預報-今明 36 小時天氣預報)
        String url = String.format("https://opendata.cwa.gov.tw/api/v1/rest/datastore/F-C0032-001?Authorization=%s&locationName=%s",
                apiKey, encodedLocation);
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .GET()
                    .build();

            HttpResponse<String> response = fetchWithRetry(request);
            if (response.statusCode() != 200) {
                log.error("Failed to fetch CWA data: HTTP {}", response.statusCode());
                return Optional.empty();
            }

            JSONObject json = new JSONObject(response.body());
            JSONArray locations = json.getJSONObject("records").getJSONArray("location");
            if (locations.length() == 0) {
                return Optional.empty();
            }

            JSONObject loc = locations.getJSONObject(0);
            JSONArray weatherElements = loc.getJSONArray("weatherElement");

            String Wx = extractElement(weatherElements, "Wx");
            String PoP = extractElement(weatherElements, "PoP");
            String MinT = extractElement(weatherElements, "MinT");
            String MaxT = extractElement(weatherElements, "MaxT");

            WeatherInfoDto result = WeatherInfoDto.builder()
                    .locationName(normalizedLocation)
                    .description(Wx)
                    .rainProbability(PoP + "%")
                    .minTemp(MinT)
                    .maxTemp(MaxT)
                    .build();
            dailyCache.put(normalizedLocation, new CacheEntry<>(result, CACHE_TTL));
            return Optional.of(result);

        } catch (Exception e) {
            log.error("Error calling CWA API: ", e);
            return Optional.empty();
        }
    }

    @Override
    public boolean isGoingToRain(String locationName) {
        return getDailyRainChance(locationName) >= 50;
    }

    @Override
    public int getDailyRainChance(String locationName) {
        Optional<WeatherInfoDto> forecast = getDailyForecast(locationName);
        if (forecast.isPresent()) {
            String pop = forecast.get().getRainProbability().replace("%", "");
            try {
                return Integer.parseInt(pop);
            } catch (NumberFormatException e) {
                return 0;
            }
        }
        return 0;
    }

    private String extractElement(JSONArray elements, String elementName) {
        for (int i = 0; i < elements.length(); i++) {
            JSONObject el = elements.getJSONObject(i);
            if (el.getString("elementName").equals(elementName)) {
                return el.getJSONArray("time").getJSONObject(0).getJSONObject("parameter").getString("parameterName");
            }
        }
        return "";
    }

    @Override
    public Optional<String> getRealTimeObservation(String locationName) {
        String normalizedLocation = normalizeLocationName(locationName);
        if (normalizedLocation == null) return Optional.empty();
        
        CacheEntry<String> cached = rtcCache.get(normalizedLocation);
        if (cached != null && cached.isValid()) return Optional.ofNullable(cached.data);

        String url = String.format("https://opendata.cwa.gov.tw/api/v1/rest/datastore/O-A0001-001?Authorization=%s", apiKey);

        try {
            HttpRequest request = HttpRequest.newBuilder().uri(URI.create(url)).GET().build();
            HttpResponse<String> response = fetchWithRetry(request);
            if (response.statusCode() != 200) return Optional.empty();

            JSONObject json = new JSONObject(response.body());
            JSONArray stations = json.getJSONObject("records").getJSONArray("Station");

            double bestTemp = -99.0;
            int bestScore = -1;

            for (int i = 0; i < stations.length(); i++) {
                JSONObject station = stations.getJSONObject(i);
                JSONObject geoInfo = station.optJSONObject("GeoInfo");
                if (geoInfo != null) {
                    String county = geoInfo.optString("CountyName", "");
                    String town = geoInfo.optString("TownName", "");
                    String stationName = station.optString("StationName", "");
                    
                    if (normalizedLocation.contains(county) || county.contains(normalizedLocation)) {
                        JSONObject weatherElement = station.optJSONObject("WeatherElement");
                        if (weatherElement != null) {
                            double temp = weatherElement.optDouble("AirTemperature", -99.0);
                            if (temp != -99.0) {
                                int score = 0;
                                // 1. Exact town match is the best
                                if (!town.isEmpty() && normalizedLocation.contains(town)) {
                                    score = 100;
                                } 
                                // 2. Main city station is second best (e.g. station '臺北' for '臺北市')
                                else if (stationName.equals(county.replace("市", "").replace("縣", ""))) {
                                    score = 50;
                                }
                                // 板橋 is the main station for 新北市
                                else if ("新北市".equals(county) && "板橋".equals(stationName)) {
                                    score = 50;
                                }
                                // 3. Fallback: highest temperature (avoids mountain freezing stations)
                                else {
                                    score = 10;
                                }

                                // Update if score is better, or if score is a 10-tie but temperature is higher
                                if (score > bestScore || (score == 10 && score == bestScore && temp > bestTemp)) {
                                    bestScore = score;
                                    bestTemp = temp;
                                }
                            }
                        }
                    }
                }
            }

            if (bestScore > -1 && bestTemp != -99.0) {
                String res = String.format("目前即時氣溫：%.1f°C", bestTemp);
                rtcCache.put(normalizedLocation, new CacheEntry<>(res, CACHE_TTL));
                return Optional.of(res);
            }
        } catch (Exception e) {
            log.error("Error fetching real-time observation: ", e);
        }
        return Optional.empty();
    }

    @Override
    public Optional<String> getThunderstormAlerts(String locationName) {
        String normalizedLocation = normalizeLocationName(locationName);
        String url = String.format("https://opendata.cwa.gov.tw/api/v1/rest/datastore/W-C0033-003?Authorization=%s", apiKey);
        if (normalizedLocation == null) return Optional.empty();

        try {
            java.net.http.HttpRequest request = java.net.http.HttpRequest.newBuilder().uri(java.net.URI.create(url)).GET().build();
            java.net.http.HttpResponse<String> response = fetchWithRetry(request);
            if (response.statusCode() != 200) return Optional.empty();

            org.json.JSONObject json = new org.json.JSONObject(response.body());
            org.json.JSONObject records = json.optJSONObject("records");
            if (records == null) return Optional.empty();
            
            org.json.JSONArray infoArr = records.optJSONArray("info");
            if (infoArr == null || infoArr.length() == 0) return Optional.empty();

            for (int i = 0; i < infoArr.length(); i++) {
                org.json.JSONObject info = infoArr.getJSONObject(i);
                String language = info.optString("language", "");
                if (!"zh-TW".equals(language)) continue;
                
                org.json.JSONArray areas = info.optJSONArray("area");
                if (areas == null) continue;

                for (int k = 0; k < areas.length(); k++) {
                    org.json.JSONObject area = areas.getJSONObject(k);
                    String areaDesc = area.optString("areaDesc", "");
                    if (!areaDesc.isEmpty() && (areaDesc.contains(normalizedLocation) || normalizedLocation.contains(areaDesc))) {
                        String headline = info.optString("headline", "大雷雨特報");
                        if (headline.contains("大雷雨") || headline.contains("雷雨")) {
                            return Optional.of(headline + " 即時特報！請慎防劇烈降雨、雷擊。");
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.error("Error fetching thunderstorm alerts: ", e);
        }
        return Optional.empty();
    }

    @Override
    public java.util.List<String> getWeatherWarnings(String locationName) {
        String normalizedLocation = normalizeLocationName(locationName);
        java.util.List<String> warnings = new java.util.ArrayList<>();
        if (normalizedLocation == null) return warnings;

        String url = String.format("https://opendata.cwa.gov.tw/api/v1/rest/datastore/W-C0033-002?Authorization=%s", apiKey);

        try {
            java.net.http.HttpRequest request = java.net.http.HttpRequest.newBuilder().uri(java.net.URI.create(url)).GET().build();
            java.net.http.HttpResponse<String> response = fetchWithRetry(request);
            if (response.statusCode() != 200) return warnings;

            org.json.JSONObject json = new org.json.JSONObject(response.body());
            org.json.JSONObject records = json.optJSONObject("records");
            if (records == null) return warnings;
            
            org.json.JSONArray recordArr = records.optJSONArray("record");
            if (recordArr == null || recordArr.length() == 0) return warnings;

            for (int i = 0; i < recordArr.length(); i++) {
                org.json.JSONObject record = recordArr.getJSONObject(i);
                
                org.json.JSONObject hazardConditions = record.optJSONObject("hazardConditions");
                if (hazardConditions == null) continue;

                org.json.JSONObject hazardsObj = hazardConditions.optJSONObject("hazards");
                if (hazardsObj == null) continue;

                org.json.JSONArray hazards = hazardsObj.optJSONArray("hazard");
                if (hazards == null) continue;

                for (int j = 0; j < hazards.length(); j++) {
                    org.json.JSONObject hazard = hazards.getJSONObject(j);
                    org.json.JSONObject info = hazard.optJSONObject("info");
                    if (info == null) continue;

                    String language = info.optString("language", "");
                    if ("zh-TW".equals(language)) {
                        org.json.JSONObject affectedAreas = info.optJSONObject("affectedAreas");
                        if (affectedAreas != null) {
                            org.json.JSONArray locations = affectedAreas.optJSONArray("location");
                            if (locations != null) {
                                for (int k = 0; k < locations.length(); k++) {
                                    org.json.JSONObject loc = locations.getJSONObject(k);
                                    String locName = loc.optString("locationName", "");
                                    if (!locName.isEmpty() && (locName.contains(normalizedLocation) || normalizedLocation.contains(locName))) {
                                        String phenomena = info.optString("phenomena", "");
                                        String significance = info.optString("significance", "");
                                        warnings.add(phenomena + significance);
                                        break; 
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.error("Error fetching weather warnings: ", e);
        }
        return warnings;
    }

    @Override
    public String getAllActiveWarningsSummary() {
        java.util.Map<String, java.util.Set<String>> allWarnings = new java.util.HashMap<>();
        try {
            // W-C0033-002
            String url2 = String.format("https://opendata.cwa.gov.tw/api/v1/rest/datastore/W-C0033-002?Authorization=%s", apiKey);
            java.net.http.HttpRequest req2 = java.net.http.HttpRequest.newBuilder().uri(java.net.URI.create(url2)).GET().build();
            java.net.http.HttpResponse<String> res2 = fetchWithRetry(req2);
            if (res2.statusCode() == 200) {
                org.json.JSONObject json = new org.json.JSONObject(res2.body());
                org.json.JSONObject records = json.optJSONObject("records");
                if (records != null) {
                    org.json.JSONArray recordArr = records.optJSONArray("record");
                    if (recordArr != null) {
                        for (int i = 0; i < recordArr.length(); i++) {
                            org.json.JSONObject record = recordArr.getJSONObject(i);
                            org.json.JSONObject hazardConditions = record.optJSONObject("hazardConditions");
                            if (hazardConditions == null) continue;
                            org.json.JSONObject hazardsObj = hazardConditions.optJSONObject("hazards");
                            if (hazardsObj == null) continue;
                            org.json.JSONArray hazards = hazardsObj.optJSONArray("hazard");
                            if (hazards == null) continue;
                            for (int j = 0; j < hazards.length(); j++) {
                                org.json.JSONObject info = hazards.getJSONObject(j).optJSONObject("info");
                                if (info == null || !"zh-TW".equals(info.optString("language", ""))) continue;
                                org.json.JSONObject affectedAreas = info.optJSONObject("affectedAreas");
                                if (affectedAreas != null) {
                                    org.json.JSONArray locations = affectedAreas.optJSONArray("location");
                                    if (locations != null) {
                                        String alertName = info.optString("phenomena", "") + info.optString("significance", "");
                                        for (int k = 0; k < locations.length(); k++) {
                                            String locName = locations.getJSONObject(k).optString("locationName", "");
                                            if (!locName.isEmpty()) {
                                                allWarnings.computeIfAbsent(locName, x -> new java.util.HashSet<>()).add(alertName);
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // W-C0033-003
            String url3 = String.format("https://opendata.cwa.gov.tw/api/v1/rest/datastore/W-C0033-003?Authorization=%s", apiKey);
            java.net.http.HttpRequest req3 = java.net.http.HttpRequest.newBuilder().uri(java.net.URI.create(url3)).GET().build();
            java.net.http.HttpResponse<String> res3 = fetchWithRetry(req3);
            if (res3.statusCode() == 200) {
                org.json.JSONObject json = new org.json.JSONObject(res3.body());
                org.json.JSONObject records = json.optJSONObject("records");
                if (records != null) {
                    org.json.JSONArray infoArr = records.optJSONArray("info");
                    if (infoArr != null) {
                        for (int i = 0; i < infoArr.length(); i++) {
                            org.json.JSONObject info = infoArr.getJSONObject(i);
                            if (!"zh-TW".equals(info.optString("language", ""))) continue;
                            org.json.JSONArray areas = info.optJSONArray("area");
                            if (areas != null) {
                                String headline = info.optString("headline", "大雷雨");
                                if (headline.contains("大雷雨") || headline.contains("雷雨")) {
                                    for (int k = 0; k < areas.length(); k++) {
                                        String areaDesc = areas.getJSONObject(k).optString("areaDesc", "");
                                        if (!areaDesc.isEmpty()) {
                                            allWarnings.computeIfAbsent(areaDesc.substring(0, Math.min(3, areaDesc.length())), x -> new java.util.HashSet<>()).add("大雷雨特報");
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.error("Error fetching all warnings summary: ", e);
        }

        if (allWarnings.isEmpty()) return "全台目前無特別警報。";

        return allWarnings.entrySet().stream()
                .map(e -> e.getKey() + "(" + String.join(", ", e.getValue()) + ")")
                .collect(java.util.stream.Collectors.joining("；"));
    }

    @Override
    public java.util.List<EarthquakeDto> getLatestEarthquakes() {
        java.util.List<EarthquakeDto> results = new java.util.ArrayList<>();
        
        EarthquakeDto significant = fetchEarthquakeFromApi("E-A0015-001");
        if (significant != null) results.add(significant);
        
        EarthquakeDto local = fetchEarthquakeFromApi("E-A0016-001");
        if (local != null) results.add(local);
        
        return results;
    }

    private EarthquakeDto fetchEarthquakeFromApi(String endpoint) {
        String url = String.format("https://opendata.cwa.gov.tw/api/v1/rest/datastore/%s?Authorization=%s", endpoint, apiKey);
        try {
            HttpRequest request = HttpRequest.newBuilder().uri(URI.create(url)).GET().build();
            HttpResponse<String> response = fetchWithRetry(request);
            if (response.statusCode() != 200) return null;

            JSONObject json = new JSONObject(response.body());
            JSONObject records = json.optJSONObject("records");
            if (records == null) return null;

            JSONArray earthquakes = records.optJSONArray("Earthquake");
            if (earthquakes == null || earthquakes.length() == 0) return null;

            JSONObject latestEq = earthquakes.getJSONObject(0);
            String earthquakeNo = latestEq.optString("EarthquakeNo", "");
            // E-A0016-001 might not have a clean EarthquakeNo, fallback to time if empty
            String reportContent = latestEq.optString("ReportContent", "");

            JSONObject eqInfo = latestEq.optJSONObject("EarthquakeInfo");
            String time = eqInfo != null ? eqInfo.optString("OriginTime", "") : "";
            if (earthquakeNo.isEmpty() && !time.isEmpty()) {
                earthquakeNo = "LOCAL_" + time.replaceAll("[^0-9]", "");
            }

            double depth = eqInfo != null ? eqInfo.optDouble("FocalDepth", 0.0) : 0.0;
            
            String magnitude = "";
            if (eqInfo != null && eqInfo.optJSONObject("EarthquakeMagnitude") != null) {
                magnitude = String.valueOf(eqInfo.getJSONObject("EarthquakeMagnitude").optDouble("MagnitudeValue", 0.0));
            }

            String location = "";
            if (eqInfo != null && eqInfo.optJSONObject("Epicenter") != null) {
                location = eqInfo.getJSONObject("Epicenter").optString("Location", "");
            }

            java.util.Map<String, String> affectedAreas = new java.util.HashMap<>();
            JSONObject intensity = latestEq.optJSONObject("Intensity");
            if (intensity != null) {
                JSONArray shakingAreas = intensity.optJSONArray("ShakingArea");
                if (shakingAreas != null) {
                    for (int i = 0; i < shakingAreas.length(); i++) {
                        JSONObject area = shakingAreas.getJSONObject(i);
                        String countyNamesCombined = area.optString("CountyName", "");
                        String areaIntensity = area.optString("AreaIntensity", "");
                        if (!countyNamesCombined.isEmpty() && !areaIntensity.isEmpty()) {
                            // CWA might group counties e.g. "臺中市、臺東縣"
                            String[] counties = countyNamesCombined.split("、");
                            for (String c : counties) {
                                String cleanC = c.trim();
                                if (!affectedAreas.containsKey(cleanC)) {
                                    affectedAreas.put(cleanC, areaIntensity);
                                }
                            }
                        }
                    }
                }
            }

            return EarthquakeDto.builder()
                    .earthquakeNo(earthquakeNo)
                    .reportContent(reportContent)
                    .time(time)
                    .depth(String.valueOf(depth))
                    .magnitude(magnitude)
                    .location(location)
                    .affectedAreas(affectedAreas)
                    .build();
        } catch (Exception e) {
            log.error("Error fetching earthquake data from {}: ", endpoint, e);
        }
        return null;
    }

    @Override
    public Optional<TownshipForecastDto> get3HourForecast(String county, String town) {
        String cacheKey = (county == null ? "" : county) + "|" + (town == null ? "" : town);
        CacheEntry<TownshipForecastDto> cached = townCache.get(cacheKey);
        if (cached != null && cached.isValid()) return Optional.ofNullable(cached.data);

        String url;
        if (town == null || town.isEmpty()) {
            String encodedCounty = county != null && !county.isEmpty() ? URLEncoder.encode(county, StandardCharsets.UTF_8) : "";
            url = String.format("https://opendata.cwa.gov.tw/api/v1/rest/datastore/F-D0047-089?Authorization=%s&locationsName=%s", apiKey, encodedCounty);
        } else {
            String endpointId = getEndpointId(county, false);
            String encodedTown = URLEncoder.encode(town, StandardCharsets.UTF_8);
            url = String.format("https://opendata.cwa.gov.tw/api/v1/rest/datastore/%s?Authorization=%s&LocationName=%s", endpointId, apiKey, encodedTown);
        }
        
        try {
            HttpRequest request = HttpRequest.newBuilder().uri(URI.create(url)).GET().build();
            HttpResponse<String> response = fetchWithRetry(request);
            if (response.statusCode() != 200) return Optional.empty();

            JSONObject json = new JSONObject(response.body());
            JSONObject records = json.optJSONObject("records");
            if (records == null) return Optional.empty();
            
            JSONArray locationsArr = records.optJSONArray("Locations");
            if (locationsArr == null || locationsArr.length() == 0) return Optional.empty();
            
            JSONArray locationArr = locationsArr.getJSONObject(0).optJSONArray("Location");
            if (locationArr == null || locationArr.length() == 0) return Optional.empty();
            
            JSONObject targetLocation = locationArr.getJSONObject(0);
            String targetName = (town != null && !town.isEmpty()) ? town : county;
            for (int i = 0; i < locationArr.length(); i++) {
                if (targetName.equals(locationArr.getJSONObject(i).optString("LocationName", ""))) {
                    targetLocation = locationArr.getJSONObject(i);
                    break;
                }
            }
            JSONArray weatherElements = targetLocation.optJSONArray("WeatherElement");
            if (weatherElements == null) return Optional.empty();
            
            int nextPop = 0;
            String popTime = "";
            int startPop = 0;
            String startTimeBlock = null;
            
            boolean rainingUntilNight = false;
            String rainEndTime = null;
            String todayStr = null;
            boolean hasRainToday = false;
            
            java.util.Map<String, Integer> popTimeline = new java.util.LinkedHashMap<>();
            
            for (int i = 0; i < weatherElements.length(); i++) {
                JSONObject we = weatherElements.getJSONObject(i);
                String en = we.optString("ElementName", "");
                // F-D0047 uses Chinese names like "3小時降雨機率" or "12小時降雨機率"
                if ("3小時降雨機率".equals(en) || "6小時降雨機率".equals(en) || "12小時降雨機率".equals(en)) {
                    JSONArray times = we.optJSONArray("Time");
                    if (times != null && times.length() > 0) {
                        todayStr = times.getJSONObject(0).optString("StartTime", "").substring(0, 10);
                        java.time.LocalDateTime maxAlertTime = java.time.LocalDateTime.now().plusHours(6);
                        java.time.format.DateTimeFormatter formatter = java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

                        // Find the time block with the HIGHEST PoP and the FIRST block >= 50%
                        for (int j = 0; j < times.length(); j++) {
                            JSONObject timeSlot = times.getJSONObject(j);
                            JSONArray elementValues = timeSlot.optJSONArray("ElementValue");
                            if (elementValues != null && elementValues.length() > 0) {
                                String val = elementValues.getJSONObject(0).optString("ProbabilityOfPrecipitation", "0");
                                if (!" ".equals(val) && !val.isEmpty()) {
                                    try {
                                        int pop = Integer.parseInt(val);
                                        
                                        String stFull = timeSlot.optString("StartTime", "");
                                        String etFull = timeSlot.optString("EndTime", "");
                                        java.time.LocalDateTime startTime;
                                        try {
                                            startTime = java.time.OffsetDateTime.parse(stFull).toLocalDateTime();
                                        } catch (java.time.format.DateTimeParseException ex) {
                                            startTime = java.time.LocalDateTime.parse(stFull, formatter);
                                        }
                                        java.time.LocalDateTime endTime;
                                        try {
                                            endTime = java.time.OffsetDateTime.parse(etFull).toLocalDateTime();
                                        } catch (java.time.format.DateTimeParseException ex) {
                                            endTime = java.time.LocalDateTime.parse(etFull, formatter);
                                        }
                                        String st = org.example.weathercastbot.util.TimeFormatUtil.formatWithDayOfWeek(stFull);
                                        if (etFull.length() >= 16) {
                                            st += "~" + etFull.substring(11, 16);
                                        }
                                        
                                        java.time.LocalDateTime nowTpe = java.time.LocalDateTime.now(java.time.ZoneId.of("Asia/Taipei"));
                                        if (endTime.isAfter(nowTpe)) {
                                            popTimeline.put(st, pop);
                                        }
                                        
                                        // Only trigger alerts for events starting in the future and within the next 24 hours
                                        if (startTime.isBefore(maxAlertTime) && endTime.isAfter(nowTpe)) {
                                            if (pop >= 50 && startTimeBlock == null) {
                                                startTimeBlock = st;
                                                startPop = pop;
                                            }

                                            if (pop > nextPop) {
                                                nextPop = pop;
                                                // Format: MM-dd HH:mm
                                                popTime = st;
                                            }
                                        }
                                        
                                        if (pop >= 30 && todayStr != null && stFull.startsWith(todayStr)) {
                                            hasRainToday = true;
                                            etFull = timeSlot.optString("EndTime", "");
                                            if (etFull.length() >= 16) {
                                                rainEndTime = etFull.substring(11, 16); // "18:00" or "00:00"
                                            }
                                        }
                                    } catch (NumberFormatException ignored) {}
                                }
                            }
                        }
                    }
                    if (hasRainToday) {
                        if ("00:00".equals(rainEndTime) || "06:00".equals(rainEndTime) || rainEndTime == null) {
                            rainingUntilNight = true;
                            rainEndTime = null;
                        }
                    } else {
                        rainEndTime = null;
                    }
                    break; // only process one PoP element type
                }
            }

            TownshipForecastDto result = TownshipForecastDto.builder()
                    .county(county)
                    .town(town)
                    .isGoingToRainSoon(nextPop >= 50)
                    .upcomingPop(nextPop)
                    .upcomingRainTimeBlock(popTime)
                    .rainStartPop(startPop)
                    .rainStartTimeBlock(startTimeBlock)
                    .rainingUntilNight(rainingUntilNight)
                    .rainEndTime(rainEndTime)
                    .popTimeline(popTimeline)
                    .weeklyWeatherContext(compressWeatherJson(targetLocation))
                    .build();
            townCache.put(cacheKey, new CacheEntry<>(result, CACHE_TTL));
            return Optional.of(result);

        } catch (Exception e) {
            log.error("Error fetching 3-hour forecast for " + town, e);
        }
        return Optional.empty();
    }

    @Override
    public Optional<String> getWeeklyForecast(String county, String town) {
        String cacheKey = (county == null ? "" : county) + "|" + (town == null ? "" : town);
        CacheEntry<String> cached = weeklyCache.get(cacheKey);
        if (cached != null && cached.isValid()) return Optional.ofNullable(cached.data);

        String url;
        if (town == null || town.isEmpty()) {
            String encodedCounty = county != null && !county.isEmpty() ? URLEncoder.encode(county, StandardCharsets.UTF_8) : "";
            url = String.format("https://opendata.cwa.gov.tw/api/v1/rest/datastore/F-D0047-091?Authorization=%s&locationsName=%s&LocationName=%s", apiKey, encodedCounty, encodedCounty);
        } else {
            String endpointId = getEndpointId(county, true);
            String encodedTown = URLEncoder.encode(town, StandardCharsets.UTF_8);
            url = String.format("https://opendata.cwa.gov.tw/api/v1/rest/datastore/%s?Authorization=%s&LocationName=%s", endpointId, apiKey, encodedTown);
        }
        
        try {
            HttpRequest request = HttpRequest.newBuilder().uri(URI.create(url)).GET().build();
            HttpResponse<String> response = fetchWithRetry(request);
            if (response.statusCode() == 200) {
                JSONObject json = new JSONObject(response.body());
                JSONObject records = json.optJSONObject("records");
                if (records != null) {
                    JSONArray locationsArr = records.optJSONArray("Locations");
                    JSONArray locationArr = null;

                    if (locationsArr != null && locationsArr.length() > 0) {
                        JSONObject countyObj = locationsArr.getJSONObject(0);
                        for (int i = 0; i < locationsArr.length(); i++) {
                            if (county.equals(locationsArr.getJSONObject(i).optString("LocationsName", ""))) {
                                countyObj = locationsArr.getJSONObject(i);
                                break;
                            }
                        }
                        locationArr = countyObj.optJSONArray("Location");
                    } else {
                        locationArr = records.optJSONArray("Location");
                    }

                    if (locationArr != null && locationArr.length() > 0) {
                        JSONObject targetLocation = locationArr.getJSONObject(0);
                        String targetName = (town != null && !town.isEmpty()) ? town : county;
                        
                        for (int i = 0; i < locationArr.length(); i++) {
                            if (targetName.equals(locationArr.getJSONObject(i).optString("LocationName", ""))) {
                                targetLocation = locationArr.getJSONObject(i);
                                break;
                            }
                        }
                        
                        String res = compressWeatherJson(targetLocation);
                        weeklyCache.put(cacheKey, new CacheEntry<>(res, CACHE_TTL));
                        return Optional.of(res);
                    }
                }
            }
        } catch (Exception e) {
            log.error("Error fetching weekly forecast", e);
        }
        return Optional.empty();
    }

    private String normalizeLocationName(String locationName) {
        if (locationName == null) return null;
        return locationName.replace("台", "臺");
    }

    private String getEndpointId(String county, boolean isWeekly) {
        if (county == null || county.isEmpty()) return isWeekly ? "F-D0047-091" : "F-D0047-089";
        
        switch (county) {
            case "宜蘭縣": return isWeekly ? "F-D0047-003" : "F-D0047-001";
            case "桃園市": return isWeekly ? "F-D0047-007" : "F-D0047-005";
            case "新竹縣": return isWeekly ? "F-D0047-011" : "F-D0047-009";
            case "苗栗縣": return isWeekly ? "F-D0047-015" : "F-D0047-013";
            case "彰化縣": return isWeekly ? "F-D0047-019" : "F-D0047-017";
            case "南投縣": return isWeekly ? "F-D0047-023" : "F-D0047-021";
            case "雲林縣": return isWeekly ? "F-D0047-027" : "F-D0047-025";
            case "嘉義縣": return isWeekly ? "F-D0047-031" : "F-D0047-029";
            case "屏東縣": return isWeekly ? "F-D0047-035" : "F-D0047-033";
            case "臺東縣": return isWeekly ? "F-D0047-039" : "F-D0047-037";
            case "花蓮縣": return isWeekly ? "F-D0047-043" : "F-D0047-041";
            case "澎湖縣": return isWeekly ? "F-D0047-047" : "F-D0047-045";
            case "基隆市": return isWeekly ? "F-D0047-051" : "F-D0047-049";
            case "新竹市": return isWeekly ? "F-D0047-055" : "F-D0047-053";
            case "嘉義市": return isWeekly ? "F-D0047-059" : "F-D0047-057";
            case "臺北市": return isWeekly ? "F-D0047-063" : "F-D0047-061";
            case "高雄市": return isWeekly ? "F-D0047-067" : "F-D0047-065";
            case "新北市": return isWeekly ? "F-D0047-071" : "F-D0047-069";
            case "臺中市": return isWeekly ? "F-D0047-075" : "F-D0047-073";
            case "臺南市": return isWeekly ? "F-D0047-079" : "F-D0047-077";
            case "連江縣": return isWeekly ? "F-D0047-083" : "F-D0047-081";
            case "金門縣": return isWeekly ? "F-D0047-087" : "F-D0047-085";
            default: return isWeekly ? "F-D0047-091" : "F-D0047-089";
        }
    }

    private String compressWeatherJson(JSONObject locationObj) {
        if (locationObj == null) return "";
        StringBuilder sb = new StringBuilder();
        sb.append(locationObj.optString("LocationName", "")).append(":\n");
        
        JSONArray elements = locationObj.optJSONArray("WeatherElement");
        if (elements == null) return locationObj.toString();
        
        java.util.Set<String> keep = java.util.Set.of("溫度", "最高溫度", "最低溫度", "12小時降雨機率", "3小時降雨機率", "天氣現象");
        
        for (int i = 0; i < elements.length(); i++) {
            JSONObject el = elements.getJSONObject(i);
            String name = el.optString("ElementName", "");
            if (keep.contains(name)) {
                sb.append("[").append(name).append("]:");
                JSONArray times = el.optJSONArray("Time");
                if (times != null) {
                    for (int j = 0; j < Math.min(times.length(), 14); j++) {
                        JSONObject t = times.getJSONObject(j);
                        String st = t.optString("StartTime", "");
                        if (st.isEmpty()) st = t.optString("DataTime", "");
                        
                        if (st.length() >= 16) st = org.example.weathercastbot.util.TimeFormatUtil.formatWithDayOfWeek(st);
                        
                        JSONArray vals = t.optJSONArray("ElementValue");
                        String v = "";
                        if (vals != null && vals.length() > 0) {
                            JSONObject valObj = vals.getJSONObject(0);
                            if (valObj.has("Temperature")) v = valObj.optString("Temperature");
                            else if (valObj.has("ProbabilityOfPrecipitation")) v = valObj.optString("ProbabilityOfPrecipitation") + "%";
                            else if (valObj.has("Weather")) v = valObj.optString("Weather");
                            else {
                                java.util.Iterator<String> keys = valObj.keys();
                                if (keys.hasNext()) v = valObj.optString(keys.next());
                            }
                        }
                        sb.append(st).append("=").append(v).append(", ");
                    }
                }
                sb.append("\n");
            }
        }
        return sb.toString();
    }

    @Override
    public Optional<org.example.weathercastbot.dto.TownshipDailyForecastDto> getTownshipDailySummary(String county, String town) {
        if (town == null || town.isEmpty()) {
            return getCountyDailySummary(county);
        }

        String endpointId = getEndpointId(county, true); // Use weekly endpoint to get 12-hour PoP
        String encodedTown = town != null ? java.net.URLEncoder.encode(town, java.nio.charset.StandardCharsets.UTF_8) : "";
        String url = String.format("https://opendata.cwa.gov.tw/api/v1/rest/datastore/%s?Authorization=%s&locationName=%s", endpointId, apiKey, encodedTown);

        try {
            HttpRequest request = HttpRequest.newBuilder().uri(java.net.URI.create(url)).GET().build();
            java.net.http.HttpResponse<String> response = fetchWithRetry(request);
            if (response.statusCode() == 200) {
                org.json.JSONObject json = new org.json.JSONObject(response.body());
                org.json.JSONObject records = json.optJSONObject("records");
                if (records != null) {
                    org.json.JSONArray locationsOuter = records.optJSONArray("Locations"); // Note: uppercase
                    if (locationsOuter != null && !locationsOuter.isEmpty()) {
                        org.json.JSONArray locationArr = locationsOuter.getJSONObject(0).optJSONArray("Location"); // Note: uppercase

                        if (locationArr != null && locationArr.length() > 0) {
                            String targetName = (town != null && !town.isEmpty()) ? town : county;
                            org.json.JSONObject targetLocation = locationArr.getJSONObject(0);

                            for (int i = 0; i < locationArr.length(); i++) {
                                if (targetName.equals(locationArr.getJSONObject(i).optString("LocationName", ""))) {
                                    targetLocation = locationArr.getJSONObject(i);
                                    break;
                                }
                            }

                            org.example.weathercastbot.dto.TownshipDailyForecastDto.TownshipDailyForecastDtoBuilder dtoBuilder = 
                                org.example.weathercastbot.dto.TownshipDailyForecastDto.builder()
                                    .county(county)
                                    .town(targetName);

                            org.json.JSONArray elements = targetLocation.optJSONArray("WeatherElement");
                            if (elements == null) return Optional.empty();

                            String todayDate = java.time.LocalDate.now(java.time.ZoneId.of("Asia/Taipei")).toString();
                            String dayStartMatchFmt1 = todayDate + "T06:00:00+08:00";
                            String dayStartMatchFmt2 = todayDate + " 06:00:00";
                            String nightStartMatchFmt1 = todayDate + "T18:00:00+08:00";
                            String nightStartMatchFmt2 = todayDate + " 18:00:00";

                            for (int i = 0; i < elements.length(); i++) {
                                org.json.JSONObject el = elements.getJSONObject(i);
                                String elName = el.optString("ElementName", "");
                                org.json.JSONArray times = el.optJSONArray("Time");
                                if (times == null) continue;

                                for (int j = 0; j < times.length(); j++) {
                                    org.json.JSONObject t = times.getJSONObject(j);
                                    String st = t.optString("StartTime", "");
                                    
                                    org.json.JSONArray vals = t.optJSONArray("ElementValue");
                                    String val = "";
                                    if (vals != null && vals.length() > 0) {
                                        org.json.JSONObject valObj = vals.getJSONObject(0);
                                        // Try to find the generic value key or any key that is not generic descriptions
                                        if (valObj.has("value")) {
                                            val = valObj.optString("value");
                                        } else {
                                            java.util.Iterator<String> keys = valObj.keys();
                                            while(keys.hasNext()) {
                                                String k = keys.next();
                                                if (!"measures".equalsIgnoreCase(k) && !"Measure".equalsIgnoreCase(k)) {
                                                    val = valObj.optString(k);
                                                    break;
                                                }
                                            }
                                        }
                                    }

                                    if (st.equals(dayStartMatchFmt1) || st.equals(dayStartMatchFmt2)) {
                                        if ("天氣現象".equals(elName) || "Wx".equals(elName)) dtoBuilder.dayDescription(val);
                                        else if ("12小時降雨機率".equals(elName) || "PoP12h".equals(elName)) dtoBuilder.dayPop(val);
                                        else if ("最高溫度".equals(elName) || "MaxT".equals(elName)) dtoBuilder.dayMaxTemp(val);
                                        else if ("最低溫度".equals(elName) || "MinT".equals(elName)) dtoBuilder.dayMinTemp(val);
                                    } else if (st.equals(nightStartMatchFmt1) || st.equals(nightStartMatchFmt2)) {
                                        if ("天氣現象".equals(elName) || "Wx".equals(elName)) dtoBuilder.nightDescription(val);
                                        else if ("12小時降雨機率".equals(elName) || "PoP12h".equals(elName)) dtoBuilder.nightPop(val);
                                        else if ("最高溫度".equals(elName) || "MaxT".equals(elName)) dtoBuilder.nightMaxTemp(val);
                                        else if ("最低溫度".equals(elName) || "MinT".equals(elName)) dtoBuilder.nightMinTemp(val);
                                    }
                                }
                            }

                            // Fallback to empty if not found
                            org.example.weathercastbot.dto.TownshipDailyForecastDto dto = dtoBuilder.build();
                            if (dto.getDayPop() == null) dto.setDayPop("0");
                            if (dto.getNightPop() == null) dto.setNightPop("0");
                            
                            return Optional.of(dto);
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.error("Error fetching TownshipDailyForecastDto", e);
        }
        return Optional.empty();
    }

    private Optional<org.example.weathercastbot.dto.TownshipDailyForecastDto> getCountyDailySummary(String county) {
        String encodedLocation = java.net.URLEncoder.encode(normalizeLocationName(county), java.nio.charset.StandardCharsets.UTF_8);
        String url = String.format("https://opendata.cwa.gov.tw/api/v1/rest/datastore/F-C0032-001?Authorization=%s&locationName=%s", apiKey, encodedLocation);
        try {
            HttpRequest request = HttpRequest.newBuilder().uri(java.net.URI.create(url)).GET().build();
            java.net.http.HttpResponse<String> response = fetchWithRetry(request);
            if (response.statusCode() == 200) {
                org.json.JSONObject json = new org.json.JSONObject(response.body());
                org.json.JSONArray locations = json.optJSONObject("records").optJSONArray("location");
                if (locations != null && locations.length() > 0) {
                    org.json.JSONArray elements = locations.getJSONObject(0).optJSONArray("weatherElement");
                    org.example.weathercastbot.dto.TownshipDailyForecastDto.TownshipDailyForecastDtoBuilder dtoBuilder = 
                        org.example.weathercastbot.dto.TownshipDailyForecastDto.builder().county(county).town("");

                    for (int i = 0; i < elements.length(); i++) {
                        org.json.JSONObject el = elements.getJSONObject(i);
                        String elName = el.optString("elementName", "");
                        org.json.JSONArray times = el.optJSONArray("time");
                        if (times != null && times.length() >= 2) {
                            String dayVal = times.getJSONObject(0).optJSONObject("parameter").optString("parameterName", "0");
                            String nightVal = times.getJSONObject(1).optJSONObject("parameter").optString("parameterName", "0");
                            
                            if ("Wx".equals(elName)) {
                                dtoBuilder.dayDescription(dayVal);
                                dtoBuilder.nightDescription(nightVal);
                            } else if ("PoP".equals(elName)) {
                                dtoBuilder.dayPop(dayVal);
                                dtoBuilder.nightPop(nightVal);
                            } else if ("MinT".equals(elName)) {
                                dtoBuilder.dayMinTemp(dayVal);
                                dtoBuilder.nightMinTemp(nightVal);
                            } else if ("MaxT".equals(elName)) {
                                dtoBuilder.dayMaxTemp(dayVal);
                                dtoBuilder.nightMaxTemp(nightVal);
                            }
                        }
                    }
                    return Optional.of(dtoBuilder.build());
                }
            }
        } catch (Exception e) {
            log.error("Error fetching getCountyDailySummary", e);
        }
        return Optional.empty();
    }

    private HttpResponse<String> fetchWithRetry(HttpRequest request) throws Exception {
        HttpRequest newRequest = HttpRequest.newBuilder(request, (n, v) -> true)
                .timeout(java.time.Duration.ofSeconds(10))
                .build();
        
        int maxRetries = 3;
        for (int i = 0; i < maxRetries; i++) {
            try {
                return httpClient.send(newRequest, HttpResponse.BodyHandlers.ofString());
            } catch (Exception e) {
                if (i == maxRetries - 1) throw e;
                log.warn("API request failed (Attempt {}/{}). Retrying in 1 second...", i + 1, maxRetries);
                Thread.sleep(1000);
            }
        }
        throw new RuntimeException("Unreachable");
    }
}
