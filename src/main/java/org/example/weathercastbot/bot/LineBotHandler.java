package org.example.weathercastbot.bot;

import com.linecorp.bot.messaging.client.MessagingApiClient;
import com.linecorp.bot.messaging.model.PushMessageRequest;
import com.linecorp.bot.messaging.model.ReplyMessageRequest;
import com.linecorp.bot.messaging.model.TextMessage;
import com.linecorp.bot.webhook.model.CallbackRequest;
import com.linecorp.bot.webhook.model.Event;
import com.linecorp.bot.webhook.model.FollowEvent;
import com.linecorp.bot.webhook.model.MessageEvent;
import com.linecorp.bot.webhook.model.TextMessageContent;
import lombok.extern.slf4j.Slf4j;
import org.example.weathercastbot.dto.WeatherInfoDto;
import org.example.weathercastbot.entity.Platform;
import org.example.weathercastbot.service.CWAService;
import org.example.weathercastbot.service.GeminiService;
import org.example.weathercastbot.service.SubscriptionService;
import org.example.weathercastbot.util.LocationResolverUtil;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

@Slf4j
@RestController
public class LineBotHandler {

    private final SubscriptionService subscriptionService;
    private final CWAService cwaService;
    private final GeminiService geminiService;
    private final Optional<MessagingApiClient> lineMessagingClient;

    public LineBotHandler(SubscriptionService subscriptionService, 
                          CWAService cwaService, 
                          GeminiService geminiService,
                          Optional<MessagingApiClient> lineMessagingClient) {
        this.subscriptionService = subscriptionService;
        this.cwaService = cwaService;
        this.geminiService = geminiService;
        this.lineMessagingClient = lineMessagingClient;
    }

    @PostMapping("/callback")
    public ResponseEntity<Void> callback(@RequestBody CallbackRequest request) {
        if (request.events() == null) {
            return ResponseEntity.ok().build();
        }
        for (Event event : request.events()) {
            if (event instanceof MessageEvent) {
                handleMessageEvent((MessageEvent) event);
            } else if (event instanceof FollowEvent) {
                handleFollowEvent((FollowEvent) event);
            }
        }
        return ResponseEntity.ok().build();
    }

    public void handleMessageEvent(MessageEvent event) {
        if (!(event.message() instanceof TextMessageContent)) return;
        
        TextMessageContent content = (TextMessageContent) event.message();
        String userId = event.source().userId();
        String text = content.text().trim();
        String replyToken = event.replyToken();

        if (isSubscriptionCommand(text)) {
            handleSubscription(userId, text, replyToken);
        } else if (text.contains("天氣") || text.contains("下雨")|| text.contains("降雨")|| text.contains("溫度")|| text.contains("氣溫")|| text.contains("SK")|| text.contains("幾度")) {
            // Tell LINE to show the "..." typing indicator for up to 20 seconds (completely free!)
            lineMessagingClient.ifPresent(client -> {
                try {
                    client.showLoadingAnimation(new com.linecorp.bot.messaging.model.ShowLoadingAnimationRequest(userId, 20)).get();
                } catch (Exception e) {
                    log.error("Failed to show loading animation", e);
                }
            });

            // Process heavy Gemini / CWA task asynchronously
            CompletableFuture.runAsync(() -> {
                String replyText = handleGeminiQuery(userId, text);
                // Use the free reply token instead of push!
                replyMessage(replyToken, replyText);
            });
        } else {
            replyMessage(replyToken, "歡迎使用天氣推播機器人！\n您可以使用「訂閱 台北市」來追蹤天氣，或直接問我天氣狀況！");
        }
    }

    public void handleFollowEvent(FollowEvent event) {
        String userId = event.source().userId();
        // LINE follow event, by default we don't subscribe to a location until they ask
        log.info("New LINE Follower registered: {}", userId);
    }

    private boolean isSubscriptionCommand(String text) {
        return text.startsWith("訂閱 ") || text.startsWith("解除訂閱 ") || text.equals("訂閱列表") || text.equals("追蹤名單") || text.startsWith("設定靜音") || text.equals("取消靜音");
    }

    private void handleSubscription(String userId, String text, String replyToken) {
        if (text.equals("訂閱列表") || text.equals("追蹤名單")) {
            List<String> list = subscriptionService.getTrackedLocations(Platform.LINE, userId);
            if (list.isEmpty()) {
                replyMessage(replyToken, "您尚未訂閱任何地區喔！\n請輸入「訂閱 台北市」來開始追蹤。");
            } else {
                replyMessage(replyToken, "📜 您的訂閱列表 (共 " + list.size() + " 個地區)：\n- " + String.join("\n- ", list));
            }
        } else if (text.startsWith("訂閱 ")) {
            String rawLoc = text.replace("訂閱 ", "").trim();
            LocationResolverUtil.LocationRef ref = LocationResolverUtil.resolve(rawLoc);
            if (!ref.isValid()) {
                replyMessage(replyToken, "⚠️ 找不到「" + rawLoc + "」這個地區喔！請輸入台灣的縣市或鄉鎮區名稱（例如：台北市、三重區）。");
                return;
            }
            String locName = ref.hasTown() ? ref.getTown() : ref.getCounty();

            subscriptionService.subscribe(Platform.LINE, userId, locName);
            List<String> list = subscriptionService.getTrackedLocations(Platform.LINE, userId);
            replyMessage(replyToken, "✅ 已為您成功訂閱【" + locName + "】！目前您總共追蹤了 " + list.size() + " 個地區。");
        } else if (text.startsWith("解除訂閱 ")) {
            String rawLoc = text.replace("解除訂閱 ", "").trim();
            LocationResolverUtil.LocationRef ref = LocationResolverUtil.resolve(rawLoc);
            String locName = ref.hasTown() ? ref.getTown() : ref.getCounty();

            subscriptionService.unsubscribe(Platform.LINE, userId, locName);
            List<String> list = subscriptionService.getTrackedLocations(Platform.LINE, userId);
            replyMessage(replyToken, "✅ 已取消訂閱【" + locName + "】！目前您總共追蹤了 " + list.size() + " 個地區。");
        } else if (text.startsWith("設定靜音")) {
            String[] parts = text.replace("設定靜音", "").trim().split("-");
            if (parts.length != 2) {
                replyMessage(replyToken, "⚠️ 格式錯誤！請依照此格式輸入：「設定靜音 23:00-07:00」");
                return;
            }
            String start = parts[0].trim();
            String end = parts[1].trim();
            try {
                java.time.LocalTime.parse(start);
                java.time.LocalTime.parse(end);
            } catch (Exception e) {
                replyMessage(replyToken, "⚠️ 時間格式錯誤！請依照此格式輸入：「設定靜音 23:00-07:00」，記得中間要有冒號跟一槓喔！");
                return;
            }
            subscriptionService.setQuietHours(Platform.LINE, userId, start, end);
            replyMessage(replyToken, "🔇 已為您設定靜音時段為 " + start + " 到 " + end + "。在此期間，氣象警報將以無聲訊息(Silent Push)默默傳送給您，絕不吵您睡覺！");
        } else if (text.equals("取消靜音")) {
            subscriptionService.clearQuietHours(Platform.LINE, userId);
            replyMessage(replyToken, "🔊 已為您取消靜音時段設定。未來所有氣象警告都將會有通知聲！");
        }
    }

    private String handleGeminiQuery(String userId, String message) {
            List<LocationResolverUtil.LocationRef> refs = LocationResolverUtil.parseLocationsFromText(message);
            if (refs.isEmpty()) {
                List<String> list = subscriptionService.getTrackedLocations(Platform.LINE, userId);
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
                    CompletableFuture<Optional<String>> thunderstormFut = CompletableFuture.supplyAsync(() -> cwaService.getThunderstormAlerts(locationAlias));
                    CompletableFuture<List<String>> warningsFut = CompletableFuture.supplyAsync(() -> cwaService.getWeatherWarnings(locationAlias));

                    CompletableFuture.allOf(dailyFut, rtcFut, townFut, weeklyFut, thunderstormFut, warningsFut).join();

                    WeatherInfoDto daily = dailyFut.join();
                    String rtc = rtcFut.join();
                    String town3h = townFut.join() != null ? townFut.join().getWeeklyWeatherContext() : null;
                    String weekly = weeklyFut.join();
                    Optional<String> thunderstorm = thunderstormFut.join();
                    List<String> warnings = warningsFut.join();
                    
                    String globalWarnings = null;
                    if (message.contains("全台") || message.contains("台灣") || message.contains("哪個地區") || message.contains("哪裡") || message.contains("哪縣市")) {
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
                        sb.append("近期概況: ").append(daily.getDescription())
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

            return geminiService.generateMultiWeatherResponse(message, combinedContext);
    }

    private void replyMessage(String replyToken, String text) {
        final String finalText = (text == null || text.trim().isEmpty()) 
                ? "抱歉，大腦網路暫時無法產生有效的天氣預報內容，請稍後再試！" 
                : text;
                
        lineMessagingClient.ifPresent(client -> {
            try {
                ReplyMessageRequest request = new ReplyMessageRequest(
                        replyToken, 
                        List.of(new TextMessage(finalText)),
                        false
                );
                client.replyMessage(request).get();
            } catch (Exception e) {
                log.error("Failed to reply LINE message", e);
            }
        });
    }

    // Exposed for Scheduler
    public void pushMessage(String targetUserId, String text) {
        pushMessage(targetUserId, text, false);
    }

    public void pushMessage(String targetUserId, String text, boolean isSilent) {
        lineMessagingClient.ifPresent(client -> {
            try {
                PushMessageRequest pushMessageRequest = new PushMessageRequest(
                        targetUserId,
                        List.of(new TextMessage(text)),
                        isSilent,
                        null
                );
                client.pushMessage(java.util.UUID.randomUUID(), pushMessageRequest).get();
            } catch (Exception e) {
                log.error("Failed to push LINE message to {}", targetUserId, e);
            }
        });
    }
}
