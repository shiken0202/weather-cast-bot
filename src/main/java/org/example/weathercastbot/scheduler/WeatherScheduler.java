package org.example.weathercastbot.scheduler;

import lombok.extern.slf4j.Slf4j;
import org.example.weathercastbot.bot.DiscordBotService;
import org.example.weathercastbot.bot.LineBotHandler;
import org.example.weathercastbot.dto.EarthquakeDto;
import org.example.weathercastbot.dto.WeatherInfoDto;
import org.example.weathercastbot.entity.Location;
import org.example.weathercastbot.entity.Platform;
import org.example.weathercastbot.entity.Subscriber;
import org.example.weathercastbot.service.CWAService;
import org.example.weathercastbot.service.SubscriptionService;
import org.example.weathercastbot.util.LocationResolverUtil;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

import org.example.weathercastbot.repository.RainAlertBlockRepository;
import org.example.weathercastbot.model.RainAlertBlock;

@Slf4j
@Component
public class WeatherScheduler {

    private final java.util.Set<String> processedEarthquakes = new java.util.concurrent.ConcurrentSkipListSet<>();
    
    private final SubscriptionService subscriptionService;
    private final CWAService cwaService;
    private final DiscordBotService discordBotService;
    private final LineBotHandler lineBotHandler;
    private final RainAlertBlockRepository rainAlertBlockRepository;

    public WeatherScheduler(SubscriptionService subscriptionService, 
                            CWAService cwaService, 
                            DiscordBotService discordBotService, 
                            LineBotHandler lineBotHandler,
                            RainAlertBlockRepository rainAlertBlockRepository) {
        this.subscriptionService = subscriptionService;
        this.cwaService = cwaService;
        this.discordBotService = discordBotService;
        this.lineBotHandler = lineBotHandler;
        this.rainAlertBlockRepository = rainAlertBlockRepository;
    }

    /**
     * Executes every day at 7:00 AM.
     */
    // 測試用：預計今早 09:17 發送報告，測試完記得改回 "0 0 7 * * ?"
    @Scheduled(cron = "0 0 7 * * ?", zone = "Asia/Taipei")
    @Transactional
    public void pushDailyForecast() {
        log.info("Starting Daily Forecast Push...");
        List<Location> locations = subscriptionService.getAllTrackedLocations();

        // Collect unique locations per subscriber
        java.util.Map<Subscriber, java.util.Set<String>> subscriberLocations = new java.util.HashMap<>();
        for (Location location : locations) {
            if (location.getSubscribers().isEmpty()) continue;
            
            for (Subscriber sub : location.getSubscribers()) {
                subscriberLocations.computeIfAbsent(sub, k -> new java.util.HashSet<>()).add(location.getName());
            }
        }

        // Cache CWA responses to optimize API calls
        java.util.Map<String, org.example.weathercastbot.dto.TownshipDailyForecastDto> forecastCache = new java.util.HashMap<>();

        for (java.util.Map.Entry<Subscriber, java.util.Set<String>> entry : subscriberLocations.entrySet()) {
            Subscriber sub = entry.getKey();
            StringBuilder combinedMessage = new StringBuilder("🌅 早安！這是本日您的專屬天氣預報：\n\n");
            boolean hasForecast = false;
            
            for (String locName : entry.getValue()) {
                if (!forecastCache.containsKey(locName)) {
                    LocationResolverUtil.LocationRef ref = LocationResolverUtil.resolve(locName);
                    String town = ref.hasTown() ? ref.getTown() : "";
                    Optional<org.example.weathercastbot.dto.TownshipDailyForecastDto> opt = cwaService.getTownshipDailySummary(ref.getCounty(), town);
                    opt.ifPresent(info -> forecastCache.put(locName, info));
                    if (opt.isEmpty()) forecastCache.put(locName, null);
                }

                org.example.weathercastbot.dto.TownshipDailyForecastDto forecast = forecastCache.get(locName);
                if (forecast != null) {
                    hasForecast = true;

                    String dayDetail = String.format("⛅ 白天 (06:00-18:00)：%s，降雨機率 %s%%，氣溫 %s~%s°C", 
                            forecast.getDayDescription(), 
                            forecast.getDayPop(), 
                            forecast.getDayMinTemp(), 
                            forecast.getDayMaxTemp());
                            
                    String nightDetail = String.format("🌙 晚間 (18:00-06:00)：%s，降雨機率 %s%%，氣溫 %s~%s°C", 
                            forecast.getNightDescription(), 
                            forecast.getNightPop(), 
                            forecast.getNightMinTemp(), 
                            forecast.getNightMaxTemp());

                    combinedMessage.append(String.format("📍 【%s】\n%s\n%s\n\n",
                            locName,
                            dayDetail,
                            nightDetail));
                }
            }
            
            if (hasForecast) {
                String message = combinedMessage.toString().trim();
                if (sub.getPlatform() == Platform.DISCORD) {
                    discordBotService.pushMessage(sub.getPlatformId(), message);
                } else if (sub.getPlatform() == Platform.LINE) {
                    lineBotHandler.pushMessage(sub.getPlatformId(), message);
                }
            }
        }
        log.info("Finished Daily Forecast Push.");
    }

    /**
     * Executes every 10 minutes.
     */
    @Scheduled(fixedRate = 600000)
    @Transactional
    public void checkRainAlerts() {
        log.info("Checking Rain Alerts...");
        List<Location> locations = subscriptionService.getAllTrackedLocations();
        java.util.Map<Subscriber, java.util.Map<String, java.util.List<String>>> subscriberAlertMap = new java.util.HashMap<>();

        for (Location location : locations) {
            if (location.getSubscribers().isEmpty()) continue;

            LocationResolverUtil.LocationRef locRef = LocationResolverUtil.resolve(location.getName());
            String county = locRef.getCounty();
            String town = locRef.hasTown() ? locRef.getTown() : "";

            Optional<String> thunderAlert = cwaService.getThunderstormAlerts(location.getName());
            if (thunderAlert.isPresent()) {
                String template = String.format("🚨 極端天氣警報：【%%s】%s", thunderAlert.get());
                for (Subscriber sub : location.getSubscribers()) {
                    subscriberAlertMap.computeIfAbsent(sub, k -> new java.util.LinkedHashMap<>())
                                      .computeIfAbsent(template, k -> new java.util.ArrayList<>())
                                      .add(location.getName());
                }
            } else {
                Optional<org.example.weathercastbot.dto.TownshipForecastDto> townOpt = cwaService.get3HourForecast(county, town);
                List<RainAlertBlock> notifiedBlocks = rainAlertBlockRepository.findByLocationId(location.getId());
                java.util.Set<String> notified = notifiedBlocks.stream()
                        .map(RainAlertBlock::getTimeBlock)
                        .collect(java.util.stream.Collectors.toSet());
                
                boolean isHeavyStorm = notified.contains("HEAVY_STORM");
                boolean isNormalStorm = notified.contains("NORMAL_STORM");
                boolean isLegacyStorm = notified.contains("ACTIVE_STORM");

                if (townOpt.isPresent()) {
                    int maxPop = townOpt.get().getUpcomingPop();
                    
                    if (maxPop >= 70 && !isHeavyStorm) {
                        String startBlock = townOpt.get().getRainStartTimeBlock();
                        String startFmt = "";
                        if (startBlock != null) {
                            startFmt = org.example.weathercastbot.util.TimeFormatUtil.convertToRelativeDay(startBlock).split("~")[0] + " 以後";
                        }
                        
                        String template = String.format("🚨 大雨預警：【%%s】預計於 %s 降雨機率高達 %d%%%%！請留意較大雨勢！", startFmt, maxPop);
                        for (Subscriber sub : location.getSubscribers()) {
                            subscriberAlertMap.computeIfAbsent(sub, k -> new java.util.LinkedHashMap<>())
                                              .computeIfAbsent(template, k -> new java.util.ArrayList<>())
                                              .add(location.getName());
                        }
                        
                        rainAlertBlockRepository.save(new RainAlertBlock(null, location.getId(), "HEAVY_STORM"));
                        notified.add("HEAVY_STORM");
                        
                        for (RainAlertBlock alertBlock : notifiedBlocks) {
                            if ("NORMAL_STORM".equals(alertBlock.getTimeBlock()) || "ACTIVE_STORM".equals(alertBlock.getTimeBlock())) {
                                rainAlertBlockRepository.delete(alertBlock);
                                notified.remove(alertBlock.getTimeBlock());
                            }
                        }
                        
                        if (!notified.contains("DAILY")) {
                             rainAlertBlockRepository.save(new RainAlertBlock(null, location.getId(), "DAILY"));
                             notified.add("DAILY");
                        }
                        
                    } else if (maxPop >= 40 && maxPop < 70 && !isNormalStorm && !isHeavyStorm) {
                        String startBlock = townOpt.get().getRainStartTimeBlock();
                        String startFmt = "";
                        if (startBlock != null) {
                            startFmt = org.example.weathercastbot.util.TimeFormatUtil.convertToRelativeDay(startBlock).split("~")[0] + " 以後";
                        }
                        
                        String template = String.format("☔️ 降雨預警：【%%s】預計於 %s 降雨機率將來到 %d%%%%，出門建議攜帶雨具。", startFmt, maxPop);
                        for (Subscriber sub : location.getSubscribers()) {
                            subscriberAlertMap.computeIfAbsent(sub, k -> new java.util.LinkedHashMap<>())
                                              .computeIfAbsent(template, k -> new java.util.ArrayList<>())
                                              .add(location.getName());
                        }
                        
                        rainAlertBlockRepository.save(new RainAlertBlock(null, location.getId(), "NORMAL_STORM"));
                        notified.add("NORMAL_STORM");
                        
                        for (RainAlertBlock alertBlock : notifiedBlocks) {
                            if ("ACTIVE_STORM".equals(alertBlock.getTimeBlock())) {
                                rainAlertBlockRepository.delete(alertBlock);
                                notified.remove("ACTIVE_STORM");
                            }
                        }
                        
                        if (!notified.contains("DAILY")) {
                             rainAlertBlockRepository.save(new RainAlertBlock(null, location.getId(), "DAILY"));
                             notified.add("DAILY");
                        }
                        
                    } else if (maxPop <= 20 && (isNormalStorm || isHeavyStorm || isLegacyStorm)) {
                        String template = String.format("🔄 天氣更新：【%%s】未來降雨機率已全面下修至 %d%%%%，警報解除！但仍須注意地面積水或偶發毛毛雨。", maxPop);
                        for (Subscriber sub : location.getSubscribers()) {
                            subscriberAlertMap.computeIfAbsent(sub, k -> new java.util.LinkedHashMap<>())
                                              .computeIfAbsent(template, k -> new java.util.ArrayList<>())
                                              .add(location.getName());
                        }
                        
                        for (RainAlertBlock alertBlock : notifiedBlocks) {
                            if ("HEAVY_STORM".equals(alertBlock.getTimeBlock()) || 
                                "NORMAL_STORM".equals(alertBlock.getTimeBlock()) || 
                                "ACTIVE_STORM".equals(alertBlock.getTimeBlock())) {
                                rainAlertBlockRepository.delete(alertBlock);
                            }
                        }
                        notified.remove("HEAVY_STORM");
                        notified.remove("NORMAL_STORM");
                        notified.remove("ACTIVE_STORM");
                        
                        if (!notified.contains("DAILY")) {
                             rainAlertBlockRepository.save(new RainAlertBlock(null, location.getId(), "DAILY"));
                             notified.add("DAILY");
                        }
                    }
                }

                if (!notified.contains("HEAVY_STORM") && !notified.contains("NORMAL_STORM") && !notified.contains("ACTIVE_STORM") && cwaService.isGoingToRain(county)) {
                    int chance = cwaService.getDailyRainChance(county);
                    if (!notified.contains("DAILY")) {
                        String template = String.format("⚠️ 降雨警報：【%%s】本日整體降雨機率達 %d%%%%，出門記得準備雨具！", chance);
                        
                        for (Subscriber sub : location.getSubscribers()) {
                            subscriberAlertMap.computeIfAbsent(sub, k -> new java.util.LinkedHashMap<>())
                                              .computeIfAbsent(template, k -> new java.util.ArrayList<>())
                                              .add(location.getName());
                        }
                        rainAlertBlockRepository.save(new RainAlertBlock(null, location.getId(), "DAILY"));
                        notified.add("DAILY");
                    }
                }
            }
            try {
                Thread.sleep(1000); // 1-second delay to prevent CWA API Connection Reset
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        // Build pendingAlerts by merging location names
        java.util.Map<Subscriber, java.util.List<String>> pendingAlerts = new java.util.HashMap<>();
        for (java.util.Map.Entry<Subscriber, java.util.Map<String, java.util.List<String>>> subEntry : subscriberAlertMap.entrySet()) {
            Subscriber sub = subEntry.getKey();
            for (java.util.Map.Entry<String, java.util.List<String>> msgEntry : subEntry.getValue().entrySet()) {
                String template = msgEntry.getKey();
                String locationsStr = String.join("、", msgEntry.getValue());
                String finalMessage = String.format(template, locationsStr);
                pendingAlerts.computeIfAbsent(sub, k -> new java.util.ArrayList<>()).add(finalMessage);
            }
        }

        // Process combined notifications for each subscriber
        for (java.util.Map.Entry<Subscriber, java.util.List<String>> entry : pendingAlerts.entrySet()) {
            Subscriber sub = entry.getKey();
            boolean isSilent = org.example.weathercastbot.util.TimeFormatUtil.isQuietHour(sub.getQuietStart(), sub.getQuietEnd());

            if (entry.getValue().size() == 1) {
                if (sub.getPlatform() == Platform.DISCORD) {
                    discordBotService.pushMessage(sub.getPlatformId(), entry.getValue().get(0));
                } else if (sub.getPlatform() == Platform.LINE) {
                    lineBotHandler.pushMessage(sub.getPlatformId(), entry.getValue().get(0), isSilent);
                }
            } else {
                String combinedMsg = "☔️ 降雨預警組合包：\n\n" + String.join("\n\n", entry.getValue());
                if (sub.getPlatform() == Platform.DISCORD) {
                    discordBotService.pushMessage(sub.getPlatformId(), combinedMsg);
                } else if (sub.getPlatform() == Platform.LINE) {
                    lineBotHandler.pushMessage(sub.getPlatformId(), combinedMsg, isSilent);
                }
            }
        }
    }

    /**
     * Executes every midnight to clear all rain alert locks for the new day.
     */
    @Scheduled(cron = "0 0 0 * * ?", zone = "Asia/Taipei")
    @Transactional
    public void resetDailyAlerts() {
        log.info("Resetting all rain alert blocks for the new day...");
        rainAlertBlockRepository.deleteAll();
    }

    /**
     * Executes every minute to check for new earthquakes.
     */
    @Scheduled(fixedRate = 60000)
    @Transactional
    public void checkEarthquakeAlerts() {
        List<EarthquakeDto> eqs = cwaService.getLatestEarthquakes();
        if (eqs == null || eqs.isEmpty()) return;

        for (EarthquakeDto eq : eqs) {
            if (eq == null || eq.getEarthquakeNo() == null || eq.getEarthquakeNo().isEmpty()) continue;
            
            if (!processedEarthquakes.contains(eq.getEarthquakeNo())) {
                boolean isStartup = processedEarthquakes.isEmpty();
                processedEarthquakes.add(eq.getEarthquakeNo());
                
                // Keep set small to prevent memory leaks over time
                if (processedEarthquakes.size() > 50) {
                    java.util.List<String> list = new java.util.ArrayList<>(processedEarthquakes);
                    processedEarthquakes.clear();
                    processedEarthquakes.addAll(list.subList(list.size() - 20, list.size()));
                }

                boolean shouldBroadcast = true;
                if (isStartup) {
                    // Check if earthquake is recent (< 10 minutes ago)
                    try {
                        java.time.format.DateTimeFormatter formatter = java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
                        java.time.LocalDateTime eqTime = java.time.LocalDateTime.parse(eq.getTime(), formatter);
                        java.time.LocalDateTime now = java.time.LocalDateTime.now(java.time.ZoneId.of("Asia/Taipei"));
                        long minutesAgo = java.time.Duration.between(eqTime, now).toMinutes();
                        
                        if (minutesAgo > 10) {
                            shouldBroadcast = false;
                            log.info("Skipping old earthquake {} on startup ({} mins ago)", eq.getEarthquakeNo(), minutesAgo);
                        }
                    } catch (Exception e) {
                        log.error("Failed to parse earthquake time: {}", eq.getTime(), e);
                        shouldBroadcast = false; // Safe fallback
                    }
                }

                if (shouldBroadcast) {
                    String message = String.format("🚨 【地震速報】\n%s\n發生時間：%s\n芮氏規模：%s\n地震深度：%s km\n震央位置：%s",
                            eq.getReportContent(), eq.getTime(), eq.getMagnitude(), eq.getDepth(), eq.getLocation());
                    
                    // Fetch all unique subscribers mapped to their affected local intensity
                    List<Location> locations = subscriptionService.getAllTrackedLocations();
                    java.util.Map<Subscriber, java.util.List<String>> subscriberIntensities = new java.util.HashMap<>();
                    
                    for (Location loc : locations) {
                        org.example.weathercastbot.util.LocationResolverUtil.LocationRef ref = org.example.weathercastbot.util.LocationResolverUtil.resolve(loc.getName());
                        String county = ref.getCounty();
                        
                        if (eq.getAffectedAreas() != null && eq.getAffectedAreas().containsKey(county)) {
                            String localIntensity = eq.getAffectedAreas().get(county);
                            String info = county + localIntensity;
                            
                            for (Subscriber sub : loc.getSubscribers()) {
                                subscriberIntensities.computeIfAbsent(sub, k -> new java.util.ArrayList<>()).add(info);
                            }
                        }
                    }
                    
                    // Broadcast localized messages only to affected subscribers
                    if (!subscriberIntensities.isEmpty()) {
                        for (java.util.Map.Entry<Subscriber, java.util.List<String>> entry : subscriberIntensities.entrySet()) {
                            Subscriber sub = entry.getKey();
                            java.util.List<String> distinctList = entry.getValue().stream().distinct().collect(java.util.stream.Collectors.toList());
                            String localMessage = message + "\n\n📍 本地最大震度：" + String.join("、", distinctList);
                            
                            if (sub.getPlatform() == Platform.DISCORD) {
                                discordBotService.pushMessage(sub.getPlatformId(), localMessage);
                            } else if (sub.getPlatform() == Platform.LINE) {
                                lineBotHandler.pushMessage(sub.getPlatformId(), localMessage);
                            }
                        }
                    }
                }
            }
        }
    }

    private void pushToSubscribers(java.util.Set<Subscriber> subscribers, String message) {
        for (Subscriber sub : subscribers) {
            if (sub.getPlatform() == Platform.DISCORD) {
                discordBotService.pushMessage(sub.getPlatformId(), message);
            } else if (sub.getPlatform() == Platform.LINE) {
                lineBotHandler.pushMessage(sub.getPlatformId(), message);
            }
        }
    }
}
