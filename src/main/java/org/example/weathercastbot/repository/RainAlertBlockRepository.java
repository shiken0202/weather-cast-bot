package org.example.weathercastbot.repository;

import org.example.weathercastbot.model.RainAlertBlock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface RainAlertBlockRepository extends JpaRepository<RainAlertBlock, Long> {
    List<RainAlertBlock> findByLocationId(Long locationId);
    void deleteByLocationIdAndTimeBlock(Long locationId, String timeBlock);
}
