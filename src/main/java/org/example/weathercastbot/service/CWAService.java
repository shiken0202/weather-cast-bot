package org.example.weathercastbot.service;

import org.example.weathercastbot.dto.EarthquakeDto;
import org.example.weathercastbot.dto.TyphoonDto;
import org.example.weathercastbot.dto.TownshipForecastDto;
import org.example.weathercastbot.dto.WeatherInfoDto;
import org.example.weathercastbot.dto.WeatherWarningDto;

import java.util.Optional;

public interface CWAService {

    
    /**
     * Gets the daily 36-hour forecast for a specific location.
     * @param locationName e.g., "臺北市"
     * @return WeatherInfoDto if found.
     */
    Optional<WeatherInfoDto> getDailyForecast(String locationName);

    /**
     * Checks if it will rain in the short term (useful for the 30-min alert).
     * @param locationName exact location name
     * @return true if probability is high or radar shows rain.
     */
    boolean isGoingToRain(String locationName);

    /**
     * Gets the daily rain probability percentage (0-100).
     * @param locationName e.g., "臺北市"
     * @return The rain chance percentage, or 0 if unavailable.
     */
    int getDailyRainChance(String locationName);
    /**
     * Gets the real-time observation data (like temperature).
     * @param locationName e.g., "臺北市"
     */
    Optional<String> getRealTimeObservation(String locationName);

    /**
     * Gets active general weather warnings (e.g. Heavy Rain, Strong Wind) from W-C0033-002.
     * @param locationName the location name to filter (e.g. 台北市)
     * @return List of active weather warnings
     */
    java.util.List<WeatherWarningDto> getWeatherWarnings(String locationName);

    /**
     * Gets active thunderstorm alerts.
     * @param locationName the location name to filter (e.g. 台北市)
     * @return Optional containing the thunderstorm alert if present
     */
    Optional<String> getThunderstormAlerts(String locationName);

    /**
     * Gets a summary of all active weather warnings across Taiwan.
     * @return A formatted string summarizing all active warnings.
     */
    String getAllActiveWarningsSummary();

    /**
     * Retrieves the latest earthquake reports (significant and local).
     * @return List containing the latest earthquake DTOs.
     */
    java.util.List<EarthquakeDto> getLatestEarthquakes();

    /**
     * Retrieves the latest typhoon warnings.
     * @return List containing the latest typhoon DTOs.
     */
    java.util.List<TyphoonDto> getLatestTyphoonWarnings();

    /**
     * Gets the 3-day 3-hour forecast for a specific township (F-D0047-089).
     */
    Optional<TownshipForecastDto> get3HourForecast(String county, String town);

    /**
     * Gets the 1-week forecast for a specific township (F-D0047-091).
     */
    Optional<String> getWeeklyForecast(String county, String town);

    /**
     * Gets a specific Township daily summary separating daytime and nighttime forecasts.
     */
    Optional<org.example.weathercastbot.dto.TownshipDailyForecastDto> getTownshipDailySummary(String county, String town);
}
