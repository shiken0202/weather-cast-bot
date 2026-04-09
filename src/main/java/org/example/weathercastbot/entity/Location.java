package org.example.weathercastbot.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "locations")
@Getter
@Setter
@NoArgsConstructor
public class Location {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // e.g., "台北市"
    @Column(nullable = false, unique = true)
    private String name;

    @ManyToMany(mappedBy = "trackedLocations")
    private Set<Subscriber> subscribers = new HashSet<>();

    public Location(String name) {
        this.name = name;
    }
}
