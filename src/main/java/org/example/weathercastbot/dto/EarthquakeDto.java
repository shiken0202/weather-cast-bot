package org.example.weathercastbot.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class EarthquakeDto {
    private String earthquakeNo;
    private String reportContent;
    private String magnitude;
    private String depth;
    private String location;
    private String time;
    private java.util.Map<String, String> affectedAreas;
}
