package org.example.weathercastbot.repository;

import org.example.weathercastbot.entity.Location;
import org.example.weathercastbot.entity.Platform;
import org.example.weathercastbot.entity.Subscriber;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Transactional
public class SubscriberRepositoryTest {

    @Autowired
    private SubscriberRepository subscriberRepository;

    @Autowired
    private LocationRepository locationRepository;

    @Test
    public void testSaveAndRetrieveSubscriberWithLocation() {
        Location taipei = new Location("台北市");
        locationRepository.save(taipei);

        Subscriber discordUser = new Subscriber(Platform.DISCORD, "12345678");
        discordUser.addLocation(taipei);
        subscriberRepository.save(discordUser);

        List<Subscriber> foundSubscribers = subscriberRepository.findByTrackedLocations_Name("台北市");

        assertFalse(foundSubscribers.isEmpty());
        assertEquals(1, foundSubscribers.size());
        assertEquals("12345678", foundSubscribers.get(0).getPlatformId());
        assertEquals(Platform.DISCORD, foundSubscribers.get(0).getPlatform());
    }
}
