package org.example.weathercastbot.service;

import org.example.weathercastbot.entity.Location;
import org.example.weathercastbot.entity.Platform;
import org.example.weathercastbot.entity.Subscriber;

import java.util.List;
import java.util.Optional;

public interface SubscriptionService {
    
    /**
     * Subscribe a user/channel to a weather location.
     */
    void subscribe(Platform platform, String platformId, String locationName);

    /**
     * Unsubscribe a user/channel from a weather location.
     * @return true if successful, false if subscription was not found.
     */
    boolean unsubscribe(Platform platform, String platformId, String locationName);

    /**
     * Get the first tracked location for a user, used as context for Gemini questions.
     */
    Optional<String> getFirstTrackedLocation(Platform platform, String platformId);

    /**
     * Get all tracked locations for a user.
     */
    List<String> getTrackedLocations(Platform platform, String platformId);

    /**
     * Get all currently tracked locations along with their subscribers.
     */
    List<Location> getAllTrackedLocations();

    /**
     * Set quiet hours for a subscriber.
     */
    void setQuietHours(Platform platform, String platformId, String startHour, String endHour);

    /**
     * Clear quiet hours for a subscriber.
     */
    void clearQuietHours(Platform platform, String platformId);
}
