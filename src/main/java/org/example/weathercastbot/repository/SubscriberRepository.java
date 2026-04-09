package org.example.weathercastbot.repository;

import org.example.weathercastbot.entity.Platform;
import org.example.weathercastbot.entity.Subscriber;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SubscriberRepository extends JpaRepository<Subscriber, Long> {

    Optional<Subscriber> findByPlatformAndPlatformId(Platform platform, String platformId);

    List<Subscriber> findByTrackedLocations_Name(String locationName);
}
