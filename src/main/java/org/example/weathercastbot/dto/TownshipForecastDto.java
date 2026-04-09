package org.example.weathercastbot.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class TownshipForecastDto {
    private String county;
    private String town;
    
    // For Scheduler logic (finding the next immediate 3-hour rain block)
    private boolean isGoingToRainSoon;
    private String upcomingRainTimeBlock; // e.g., "15:00 ~ 18:00"
    private int upcomingPop; // e.g., 80
    
    private String rainStartTimeBlock; // The first block >= 50% PoP
    private int rainStartPop;
    
    // For daily forecast duration logic
    private boolean rainingUntilNight;
    private String rainEndTime; // e.g., "18:00"
    
    // For alert cancellation lookup
    private java.util.Map<String, Integer> popTimeline;
    
    // For Gemini Context (raw condensed data to save tokens)
    private String weeklyWeatherContext;
}
