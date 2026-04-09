package org.example.weathercastbot.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "rain_alert_blocks")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RainAlertBlock {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long locationId;

    @Column(nullable = false)
    private String timeBlock;
}
