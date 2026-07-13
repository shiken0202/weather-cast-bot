package org.example.weathercastbot.scheduler;

import org.example.weathercastbot.bot.DiscordBotService;
import org.example.weathercastbot.bot.LineBotHandler;
import org.example.weathercastbot.dto.EarthquakeDto;
import org.example.weathercastbot.dto.WeatherInfoDto;
import org.example.weathercastbot.entity.Location;
import org.example.weathercastbot.entity.Platform;
import org.example.weathercastbot.entity.Subscriber;
import org.example.weathercastbot.service.CWAService;
import org.example.weathercastbot.service.SubscriptionService;
import org.example.weathercastbot.service.GeminiService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.example.weathercastbot.model.RainAlertBlock;
import org.example.weathercastbot.repository.RainAlertBlockRepository;

import static org.mockito.Mockito.*;

public class WeatherSchedulerTest {

    private WeatherScheduler weatherScheduler;

    @Mock
    private SubscriptionService subscriptionService;
    @Mock
    private CWAService cwaService;
    @Mock
    private GeminiService geminiService;
    @Mock
    private DiscordBotService discordBotService;
    @Mock
    private LineBotHandler lineBotHandler;
    @Mock
    private RainAlertBlockRepository rainAlertBlockRepository;

    @BeforeEach
    public void setup() {
        MockitoAnnotations.openMocks(this);
        weatherScheduler = new WeatherScheduler(subscriptionService, cwaService, geminiService, discordBotService, lineBotHandler, rainAlertBlockRepository);
    }

    @Test
    public void testPushDailyForecast() {
        Location loc = new Location("台北市");
        loc.setId(1L);
        Subscriber lineSub = new Subscriber(Platform.LINE, "line-123");
        Subscriber discordSub = new Subscriber(Platform.DISCORD, "discord-456");
        loc.setSubscribers(Set.of(lineSub, discordSub));

        when(subscriptionService.getAllTrackedLocations()).thenReturn(List.of(loc));

        org.example.weathercastbot.dto.TownshipDailyForecastDto mockForecast = org.example.weathercastbot.dto.TownshipDailyForecastDto.builder()
                .county("臺北市")
                .town("")
                .dayDescription("多雲時晴")
                .dayMinTemp("20")
                .dayMaxTemp("25")
                .dayPop("10")
                .nightDescription("多雲")
                .nightMinTemp("19")
                .nightMaxTemp("22")
                .nightPop("0")
                .build();
        when(cwaService.getTownshipDailySummary("臺北市", "")).thenReturn(Optional.of(mockForecast));

        weatherScheduler.pushDailyForecast();

        String expectedMessage = "🌅 早安！這是本日您的專屬天氣預報：\n\n📍 【台北市】\n⛅ 白天 (06:00-18:00)：多雲時晴，降雨機率 10%，氣溫 20~25°C\n🌙 晚間 (18:00-06:00)：多雲，降雨機率 0%，氣溫 19~22°C";

        verify(lineBotHandler, times(1)).pushMessage(eq("line-123"), anyString());
        verify(discordBotService, times(1)).pushMessage(eq("discord-456"), anyString());
    }

    @Test
    public void testCheckRainAlerts() {
        Location loc = new Location("基隆市");
        loc.setId(2L);
        Subscriber lineSub = new Subscriber(Platform.LINE, "line-123");
        loc.setSubscribers(Set.of(lineSub));

        when(subscriptionService.getAllTrackedLocations()).thenReturn(List.of(loc));
        when(cwaService.getThunderstormAlerts("基隆市")).thenReturn(Optional.empty());
        when(cwaService.isGoingToRain("基隆市")).thenReturn(true);
        when(cwaService.getDailyRainChance("基隆市")).thenReturn(70);
        
        // Mocking an empty township context to hit the else block
        when(cwaService.get3HourForecast("基隆市", "")).thenReturn(Optional.empty());

        when(cwaService.get3HourForecast("基隆市", "")).thenReturn(Optional.empty());
        when(rainAlertBlockRepository.findByLocationId(2L)).thenReturn(List.of());

        weatherScheduler.checkRainAlerts();

        String expectedMessage = "⚠️ 降雨警報：【基隆市】本日全市整體機率達 70%，出門記得準備雨具！";
        verify(lineBotHandler, times(1)).pushMessage(eq("line-123"), anyString(), anyBoolean());
        verify(rainAlertBlockRepository, times(1)).save(any(RainAlertBlock.class));
    }

    @Test
    public void testCheckRainAlerts_Thunderstorm() {
        Location loc = new Location("台北市");
        loc.setId(3L);
        Subscriber lineSub = new Subscriber(Platform.LINE, "line-999");
        loc.setSubscribers(Set.of(lineSub));

        when(subscriptionService.getAllTrackedLocations()).thenReturn(List.of(loc));
        when(cwaService.getThunderstormAlerts("台北市")).thenReturn(Optional.of("大雷雨 即時特報！請慎防劇烈降雨、雷擊。"));

        weatherScheduler.checkRainAlerts();

        String expectedMessage = "🚨 極端天氣警報：【台北市】大雷雨 即時特報！請慎防劇烈降雨、雷擊。";
        verify(lineBotHandler, times(1)).pushMessage(eq("line-999"), anyString(), anyBoolean());
    }

    @Test
    public void testCheckEarthquakeAlerts() {
        Location loc = new Location("臺北市");
        loc.setId(4L);
        Subscriber lineSub = new Subscriber(Platform.LINE, "line-999");
        loc.setSubscribers(Set.of(lineSub));

        when(subscriptionService.getAllTrackedLocations()).thenReturn(List.of(loc));

        java.time.LocalDateTime now = java.time.LocalDateTime.now(java.time.ZoneId.of("Asia/Taipei"));
        java.time.format.DateTimeFormatter formatter = java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        
        EarthquakeDto eq1 = EarthquakeDto.builder()
                .earthquakeNo("001")
                .time(now.minusMinutes(15).format(formatter)) // older than 10 mins
                .build();
        when(cwaService.getLatestEarthquakes()).thenReturn(List.of(eq1));

        // Initial run (skips push due to being startup and eq is old)
        weatherScheduler.checkEarthquakeAlerts();
        verify(lineBotHandler, never()).pushMessage(anyString(), anyString(), anyBoolean());

        EarthquakeDto eq2 = EarthquakeDto.builder()
                .earthquakeNo("002")
                .reportContent("某地震")
                .time(now.minusMinutes(2).format(formatter)) // recent
                .magnitude("5.2")
                .depth("15.5")
                .location("花蓮縣")
                .affectedAreas(java.util.Map.of("臺北市", "2級"))
                .build();
        when(cwaService.getLatestEarthquakes()).thenReturn(List.of(eq2));

        // Second run with new ID (pushes)
        weatherScheduler.checkEarthquakeAlerts();

        verify(lineBotHandler, times(1)).pushMessage(eq("line-999"), anyString());
    }


}
