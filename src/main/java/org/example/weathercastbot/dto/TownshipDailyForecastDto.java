package org.example.weathercastbot.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class TownshipDailyForecastDto {
    private String county;
    private String town;

    private String dayDescription; // e.g., 陰短暫雨
    private String dayPop;         // e.g., 20%
    private String dayMinTemp;     // e.g., 20
    private String dayMaxTemp;     // e.g., 23

    private String nightDescription; 
    private String nightPop;         
    private String nightMinTemp;     
    private String nightMaxTemp;     
}
