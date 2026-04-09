package org.example.weathercastbot.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "subscribers")
@Getter
@Setter
@NoArgsConstructor
public class Subscriber {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private Platform platform;

    // Platform specific ID (Discord channel ID or LINE User ID)
    @Column(nullable = false, unique = true)
    private String platformId;

    @Column(name = "quiet_start")
    private String quietStart;

    @Column(name = "quiet_end")
    private String quietEnd;

    @ManyToMany(cascade = {CascadeType.MERGE})
    @JoinTable(name = "subscriber_locations",
            joinColumns = @JoinColumn(name = "subscriber_id"),
            inverseJoinColumns = @JoinColumn(name = "location_id"))
    private Set<Location> trackedLocations = new HashSet<>();

    public Subscriber(Platform platform, String platformId) {
        this.platform = platform;
        this.platformId = platformId;
    }

    public void addLocation(Location location) {
        trackedLocations.add(location);
        location.getSubscribers().add(this);
    }

    public void removeLocation(Location location) {
        trackedLocations.remove(location);
        location.getSubscribers().remove(this);
    }
}
