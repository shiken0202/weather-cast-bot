package org.example.weathercastbot.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class WeatherWarningDto {
    private String warningName;
    private String description;
}
