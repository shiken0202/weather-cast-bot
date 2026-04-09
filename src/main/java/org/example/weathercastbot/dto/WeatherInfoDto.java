package org.example.weathercastbot.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WeatherInfoDto {
    private String locationName;
    private String description;   // e.g. "多雲時晴"
    private String maxTemp;
    private String minTemp;
    private String rainProbability; // e.g. "30%"
}
