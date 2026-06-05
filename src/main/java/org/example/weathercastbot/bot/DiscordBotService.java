package org.example.weathercastbot.bot;

import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.requests.GatewayIntent;
import org.example.weathercastbot.dto.WeatherInfoDto;
import org.example.weathercastbot.entity.Platform;
import org.example.weathercastbot.service.SubscriptionService;
import org.example.weathercastbot.service.CWAService;
import org.example.weathercastbot.service.GeminiService;
import org.example.weathercastbot.util.LocationResolverUtil;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.util.List;
import java.util.concurrent.CompletableFuture;

@Slf4j
@Service
public class DiscordBotService extends ListenerAdapter {

    @Value("${discord.bot.token}")
    private String botToken;

    private final SubscriptionService subscriptionService;
    private final CWAService cwaService;
    private final GeminiService geminiService;
    private JDA jda;

    public DiscordBotService(SubscriptionService subscriptionService,
                             CWAService cwaService,
                             GeminiService geminiService) {
        this.subscriptionService = subscriptionService;
        this.cwaService = cwaService;
        this.geminiService = geminiService;
    }

    @PostConstruct
    public void init() {
        if ("mock-token".equals(botToken)) {
            log.info("Mock Discord Token detected. Skipping JDA initialization for tests.");
            return;
        }
        try {
            jda = JDABuilder.createDefault(botToken)
                    .enableIntents(GatewayIntent.MESSAGE_CONTENT)
                    .addEventListeners(this)
                    .build();
            jda.awaitReady();
            log.info("Discord Bot is online!");
        } catch (Exception e) {
            log.error("Failed to start Discord Bot", e);
        }
    }

    @PreDestroy
    public void cleanup() {
        if (jda != null) {
            log.info("Shutting down JDA to prevent zombie listeners...");
            jda.shutdown();
        }
    }

    @Override
    public void onMessageReceived(MessageReceivedEvent event) {
        if (event.getAuthor().isBot()) return;

        String channelId = event.getChannel().getId();
        String message = event.getMessage().getContentRaw().trim();

        if (message.startsWith("!訂閱 ")) {
            handleSubscribe(channelId, message, event);
        } else if (message.startsWith("!解除訂閱 ")) {
            handleUnsubscribe(channelId, message, event);
        } else if (message.startsWith("!設定靜音 ")) {
            handleSetQuiet(channelId, message, event);
        } else if (message.startsWith("!取消靜音")) {
            handleClearQuiet(channelId, event);
        } else if (message.startsWith("!訂閱列表") || message.startsWith("!追蹤名單")) {
            handleListSubscriptions(channelId, event);
        } else if (message.contains("天氣") || message.contains("下雨") || message.contains("降雨")|| message.contains("溫度")|| message.contains("氣溫")|| message.contains("SK")|| message.contains("幾度")) {
            // General query using Gemini
            handleGeminiQuery(channelId, message, event);
        }
    }

    private void handleSubscribe(String channelId, String messageText, MessageReceivedEvent event) {
        String rawLoc = messageText.replace("!訂閱 ", "").trim();
        LocationResolverUtil.LocationRef ref = LocationResolverUtil.resolve(rawLoc);
        if (!ref.isValid()) {
            event.getChannel().sendMessage("⚠️ 找不到「" + rawLoc + "」這個地區喔！請輸入台灣的縣市或鄉鎮區名稱（例如：台北市、三重區）。").queue();
            return;
        }
        String locationAlias = ref.hasTown() ? ref.getTown() : ref.getCounty();

        subscriptionService.subscribe(Platform.DISCORD, channelId, locationAlias);
        
        List<String> list = subscriptionService.getTrackedLocations(Platform.DISCORD, channelId);
        event.getChannel().sendMessage("✅ 已為您成功訂閱【" + locationAlias + "】！目前此頻道總共追蹤了 " + list.size() + " 個地區。").queue();
    }

    private void handleUnsubscribe(String channelId, String messageText, MessageReceivedEvent event) {
        String rawLoc = messageText.replace("!解除訂閱 ", "").trim();
        LocationResolverUtil.LocationRef ref = LocationResolverUtil.resolve(rawLoc);
        String locationAlias = ref.hasTown() ? ref.getTown() : ref.getCounty();

        subscriptionService.unsubscribe(Platform.DISCORD, channelId, locationAlias);
        
        List<String> list = subscriptionService.getTrackedLocations(Platform.DISCORD, channelId);
        event.getChannel().sendMessage("✅ 已取消訂閱【" + locationAlias + "】！目前此頻道總共追蹤了 " + list.size() + " 個地區。").queue();
    }

    private void handleSetQuiet(String channelId, String messageText, MessageReceivedEvent event) {
        String[] parts = messageText.replace("!設定靜音 ", "").trim().split("-");
        if (parts.length != 2) {
            event.getChannel().sendMessage("⚠️ 格式錯誤！請依照此格式輸入：「!設定靜音 23:00-07:00」").queue();
            return;
        }
        String start = parts[0].trim();
        String end = parts[1].trim();
        try {
            java.time.LocalTime.parse(start);
            java.time.LocalTime.parse(end);
        } catch (Exception e) {
            event.getChannel().sendMessage("⚠️ 時間格式錯誤！請依照此格式輸入：「!設定靜音 23:00-07:00」，記得中間要有冒號跟一槓喔！").queue();
            return;
        }
        subscriptionService.setQuietHours(Platform.DISCORD, channelId, start, end);
        event.getChannel().sendMessage("🔇 已為此頻道設定靜音時段為 " + start + " 到 " + end + "。在此期間，氣象警報將以「無聲訊息(@silent)」傳送，不會觸發全頻道的紅色通知或音效！").queue();
    }

    private void handleClearQuiet(String channelId, MessageReceivedEvent event) {
        subscriptionService.clearQuietHours(Platform.DISCORD, channelId);
        event.getChannel().sendMessage("🔊 已為此頻道取消靜音時段設定。未來所有警告都將正常通知！").queue();
    }

    private void handleListSubscriptions(String channelId, MessageReceivedEvent event) {
        List<String> list = subscriptionService.getTrackedLocations(Platform.DISCORD, channelId);
        if (list.isEmpty()) {
            event.getChannel().sendMessage("此頻道尚未訂閱任何地區喔！\n請輸入「!訂閱 台北市」來開始追蹤。").queue();
        } else {
            event.getChannel().sendMessage("📜 此頻道的訂閱列表 (共 " + list.size() + " 個地區)：\n- " + String.join("\n- ", list)).queue();
        }
    }

    private void handleGeminiQuery(String channelId, String messageText, MessageReceivedEvent event) {
        event.getChannel().sendTyping().queue();
        
        List<LocationResolverUtil.LocationRef> refs = LocationResolverUtil.parseLocationsFromText(messageText);
        if (refs.isEmpty()) {
            List<String> list = subscriptionService.getTrackedLocations(Platform.DISCORD, channelId);
            if (!list.isEmpty()) {
                refs = list.stream()
                        .map(LocationResolverUtil::resolve)
                        .collect(java.util.stream.Collectors.toList());
            } else {
                refs = List.of(LocationResolverUtil.resolve("臺北市"));
            }
        }

        if (refs.size() > 5) {
            refs = refs.subList(0, 5); // Limit to 5 regions max
        }

        List<CompletableFuture<String>> futures = refs.stream().map(locRef -> {
            String locationAlias = locRef.getCounty() + (locRef.hasTown() ? locRef.getTown() : "");
            String county = locRef.getCounty();
            String town = locRef.hasTown() ? locRef.getTown() : "";

            return CompletableFuture.supplyAsync(() -> {
                CompletableFuture<WeatherInfoDto> dailyFut = CompletableFuture.supplyAsync(() -> cwaService.getDailyForecast(county).orElse(null));
                CompletableFuture<String> rtcFut = CompletableFuture.supplyAsync(() -> cwaService.getRealTimeObservation(locationAlias).orElse(null));
                CompletableFuture<org.example.weathercastbot.dto.TownshipForecastDto> townFut = CompletableFuture.supplyAsync(() -> cwaService.get3HourForecast(county, town).orElse(null));
                CompletableFuture<String> weeklyFut = CompletableFuture.supplyAsync(() -> cwaService.getWeeklyForecast(county, town).orElse(null));
                CompletableFuture<java.util.Optional<String>> thunderstormFut = CompletableFuture.supplyAsync(() -> cwaService.getThunderstormAlerts(locationAlias));
                CompletableFuture<java.util.List<String>> warningsFut = CompletableFuture.supplyAsync(() -> cwaService.getWeatherWarnings(locationAlias));

                CompletableFuture.allOf(dailyFut, rtcFut, townFut, weeklyFut, thunderstormFut, warningsFut).join();

                WeatherInfoDto daily = dailyFut.join();
                String rtc = rtcFut.join();
                String town3h = townFut.join() != null ? townFut.join().getWeeklyWeatherContext() : null;
                String weekly = weeklyFut.join();
                java.util.Optional<String> thunderstorm = thunderstormFut.join();
                java.util.List<String> warnings = warningsFut.join();

                String globalWarnings = null;
                if (messageText.contains("全台") || messageText.contains("台灣") || messageText.contains("哪個地區") || messageText.contains("哪裡") || messageText.contains("哪縣市")) {
                    globalWarnings = cwaService.getAllActiveWarningsSummary();
                }

                StringBuilder sb = new StringBuilder();
                sb.append("---【").append(locationAlias).append("】---\n");
                if (thunderstorm.isPresent()) sb.append("大雷雨特報: ").append(thunderstorm.get()).append("\n");
                if (!warnings.isEmpty()) sb.append("天氣警特報: ").append(String.join(", ", warnings)).append("\n");
                if (globalWarnings != null) {
                    sb.append("全台警報摘要 (若使用者詢問全台狀況請參考此列表): ").append(globalWarnings).append("\n");
                }
                if (daily != null) {
                    sb.append("今日預報: ").append(daily.getDescription())
                      .append(", 降雨機率=").append(daily.getRainProbability())
                      .append(", 氣溫=").append(daily.getMinTemp()).append("~").append(daily.getMaxTemp()).append("度\n");
                }
                if (rtc != null) sb.append("即時: ").append(rtc).append("\n");
                if (town3h != null) sb.append("未來3天: ").append(town3h).append("\n");
                if (weekly != null) sb.append("一週: ").append(weekly).append("\n");
                return sb.toString();
            });
        }).collect(java.util.stream.Collectors.toList());

        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
        
        String combinedContext = futures.stream()
                .map(CompletableFuture::join)
                .collect(java.util.stream.Collectors.joining("\n"));

        String reply = geminiService.generateMultiWeatherResponse(messageText, combinedContext);
        event.getChannel().sendMessage(reply).queue();
    }

    // Exposed for Scheduler
    public void pushMessage(String targetChannelId, String text) {
        pushMessage(targetChannelId, text, false);
    }

    public void pushMessage(String targetChannelId, String text, boolean isSilent) {
        if (jda != null) {
            net.dv8tion.jda.api.entities.channel.middleman.MessageChannel channel = 
                jda.getChannelById(net.dv8tion.jda.api.entities.channel.middleman.MessageChannel.class, targetChannelId);
            if (channel != null) {
                // Prepending @silent is Discord's native way to suppress push notifications for a message
                String finalMessage = isSilent ? "@silent " + text : text;
                channel.sendMessage(finalMessage).queue();
            } else {
                log.warn("Discord push failed: MessageChannel ID {} not found", targetChannelId);
            }
        }
    }
}
