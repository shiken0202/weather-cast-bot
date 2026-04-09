package org.example.weathercastbot.service.impl;

import org.example.weathercastbot.entity.Location;
import org.example.weathercastbot.entity.Platform;
import org.example.weathercastbot.entity.Subscriber;
import org.example.weathercastbot.repository.LocationRepository;
import org.example.weathercastbot.repository.SubscriberRepository;
import org.example.weathercastbot.service.SubscriptionService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
public class SubscriptionServiceImpl implements SubscriptionService {

    private final SubscriberRepository subscriberRepository;
    private final LocationRepository locationRepository;

    public SubscriptionServiceImpl(SubscriberRepository subscriberRepository, LocationRepository locationRepository) {
        this.subscriberRepository = subscriberRepository;
        this.locationRepository = locationRepository;
    }

    @Override
    @Transactional
    public void subscribe(Platform platform, String platformId, String locationName) {
        Location location = locationRepository.findByName(locationName)
                .orElseGet(() -> locationRepository.save(new Location(locationName)));

        Subscriber subscriber = subscriberRepository.findByPlatformAndPlatformId(platform, platformId)
                .orElseGet(() -> new Subscriber(platform, platformId));

        subscriber.addLocation(location);
        subscriberRepository.save(subscriber);
    }

    @Override
    @Transactional
    public boolean unsubscribe(Platform platform, String platformId, String locationName) {
        Optional<Subscriber> subOpt = subscriberRepository.findByPlatformAndPlatformId(platform, platformId);
        if (subOpt.isPresent()) {
            Subscriber sub = subOpt.get();
            Optional<Location> locOpt = locationRepository.findByName(locationName);
            if (locOpt.isPresent()) {
                sub.removeLocation(locOpt.get());
                subscriberRepository.save(sub);
                return true;
            }
        }
        return false;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<String> getFirstTrackedLocation(Platform platform, String platformId) {
        Optional<Subscriber> subOpt = subscriberRepository.findByPlatformAndPlatformId(platform, platformId);
        if (subOpt.isPresent() && !subOpt.get().getTrackedLocations().isEmpty()) {
            return Optional.of(subOpt.get().getTrackedLocations().iterator().next().getName());
        }
        return Optional.empty();
    }

    @Override
    @Transactional(readOnly = true)
    public List<Location> getAllTrackedLocations() {
        return locationRepository.findAll();
    }

    @Override
    @Transactional(readOnly = true)
    public List<String> getTrackedLocations(Platform platform, String platformId) {
        return subscriberRepository.findByPlatformAndPlatformId(platform, platformId)
                .map(subscriber -> subscriber.getTrackedLocations().stream()
                        .map(Location::getName)
                        .toList())
                .orElse(List.of());
    }

    @Override
    @Transactional
    public void setQuietHours(Platform platform, String platformId, String startHour, String endHour) {
        Subscriber subscriber = subscriberRepository.findByPlatformAndPlatformId(platform, platformId)
                .orElseGet(() -> new Subscriber(platform, platformId));
        subscriber.setQuietStart(startHour);
        subscriber.setQuietEnd(endHour);
        subscriberRepository.save(subscriber);
    }

    @Override
    @Transactional
    public void clearQuietHours(Platform platform, String platformId) {
        subscriberRepository.findByPlatformAndPlatformId(platform, platformId).ifPresent(sub -> {
            sub.setQuietStart(null);
            sub.setQuietEnd(null);
            subscriberRepository.save(sub);
        });
    }
}
