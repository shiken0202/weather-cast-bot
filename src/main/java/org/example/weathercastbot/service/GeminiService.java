package org.example.weathercastbot.service;

import org.example.weathercastbot.dto.WeatherInfoDto;

public interface GeminiService {

    /**
     * Generates a conversational weather response using Gemini API.
     * @param userMessage The user's natural language question
     * @param targetLocation The resolved location target
     * @param weatherContext Current daily weather context
     * @param realTimeContext Real time observation string
     * @param town3hContext 3-hour forecast raw JSON
     * @param weeklyContext 1-week forecast raw JSON
     * @return Generated natural language response
     */
    String generateWeatherResponse(String userMessage, String targetLocation, WeatherInfoDto weatherContext, 
                                   String realTimeContext, String town3hContext, String weeklyContext);
                                   
    String generateMultiWeatherResponse(String userMessage, String combinedContext);

    /**
     * Rewrites the CWA weather warning description to be explicitly localized for a specific county/city.
     */
    String rewriteWarningDescription(String locationName, String originalDescription);
}
