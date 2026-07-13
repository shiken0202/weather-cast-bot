package org.example.weathercastbot.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.example.weathercastbot.dto.GeminiRequestDto;
import org.example.weathercastbot.dto.GeminiResponseDto;
import org.example.weathercastbot.dto.WeatherInfoDto;
import org.example.weathercastbot.service.GeminiService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;

@Slf4j
@Service
public class GeminiServiceImpl implements GeminiService {

    @Value("${api.gemini.key}")
    private String apiKey;

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public GeminiServiceImpl() {
        this.httpClient = HttpClient.newBuilder().version(HttpClient.Version.HTTP_2).build();
        this.objectMapper = new ObjectMapper();
    }

    // Constructor for testing
    public GeminiServiceImpl(HttpClient httpClient, ObjectMapper objectMapper, String apiKey) {
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
        this.apiKey = apiKey;
    }

    @Override
    public String generateWeatherResponse(String userMessage, String targetLocation, WeatherInfoDto weatherContext,
            String realTimeContext, String town3hContext, String weeklyContext) {
        String prompt = buildPrompt(userMessage, targetLocation, weatherContext, realTimeContext, town3hContext,
                weeklyContext);

        GeminiRequestDto requestDto = GeminiRequestDto.builder()
                .contents(List.of(
                        GeminiRequestDto.Content.builder()
                                .parts(List.of(GeminiRequestDto.Part.builder().text(prompt).build()))
                                .build()))
                .build();

        return executeGeminiCall(requestDto);
    }

    @Override
    public String generateMultiWeatherResponse(String userMessage, String combinedContext) {
        StringBuilder prompt = new StringBuilder();
        prompt.append(
                "【語言與格式絕對指令】：你的所有回覆必須直接且『僅能』使用繁體中文，嚴禁使用任何其他語言（包括英文）。【警告】：絕對不能輸出你的思考過程 (Chain of Thought)、內部草稿、分析筆記或任何大綱！請直接給出您最終要對用戶說的話。\n\n");
        String nowStr = java.time.LocalDateTime.now(java.time.ZoneId.of("Asia/Taipei"))
                .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd EEEE HH:mm:ss"));
        prompt.append("【當前系統標準時間】：").append(nowStr).append(" (請以此時間為基準來判斷「今天」、「明天」與「後天」的預報對應時間)。\n");
        prompt.append(
                "【時間定義絕對指令】：氣象資料中的「06:00~18:00」屬於【白天(Daytime)】，絕對不可以稱為晚上！「18:00~06:00」才屬於【晚上/今晚/夜間(Night)】。當用戶詢問「今晚」時，請務必只讀取 18:00 之後的資料，嚴禁把 06:00~18:00 的狀況回答給今晚！\n\n");
        prompt.append("你是一個熱情、專業且友善的天氣推播機器人。以下是來自不同地區氣象站的原始資料組合。請根據這些資料，一次性綜合回答用戶的問題。\n");
        prompt.append("【各氣象站回傳資訊】：\n").append(combinedContext).append("\n\n");
        prompt.append("用戶詢問：「").append(userMessage).append(
                "」\n請依序簡明扼要地回報各地的天氣概況。若資料包含具體時間（例如明天、今晚等），請務必明確指出時間段（如「明天特定時段」）。【語句優化指令】：請務必將連續且機率相近的時段「合併描述」，例如：「今天 06:00 到 15:00 降雨機率維持 70%~80%」，嚴禁像流水帳一樣把 6點、9點、12點的機率一個個列出來！時間格式請一律使用「06:00」、「15:00」的標準數位格式。請直接輸出對話結果：");

        GeminiRequestDto requestDto = GeminiRequestDto.builder()
                .contents(List.of(
                        GeminiRequestDto.Content.builder()
                                .parts(List.of(GeminiRequestDto.Part.builder().text(prompt.toString()).build()))
                                .build()))
                .build();

        return executeGeminiCall(requestDto);
    }

    private String executeGeminiCall(GeminiRequestDto requestDto) {
        int maxRetries = 3;
        int delayMs = 2000;

        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            try {
                String requestBody = objectMapper.writeValueAsString(requestDto);
                String url = String.format(
                        "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash-lite:generateContent?key=%s",
                        apiKey);

                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(url))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                        .build();

                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

                if (response.statusCode() != 200) {
                    log.warn("Gemini API error (Attempt {}/{}): HTTP {} - {}", attempt, maxRetries, response.statusCode(), response.body());
                    if (attempt < maxRetries && (response.statusCode() == 503 || response.statusCode() == 429 || response.statusCode() >= 500)) {
                        Thread.sleep(delayMs);
                        delayMs *= 2; // Exponential backoff: 2s -> 4s
                        continue;
                    }
                    log.error("Failed to fetch Gemini data after {} attempts", maxRetries);
                    return "抱歉，目前 Google AI 伺服器正面臨瞬間的全球高流量，導致服務暫時無回應。系統已嘗試重新連線但未成功，請您稍後再試一次！";
                }

                GeminiResponseDto responseDto = objectMapper.readValue(response.body(), GeminiResponseDto.class);
                String result = sanitizeResponse(responseDto.getText());
                if (result.isEmpty()) {
                    log.error("Gemini returned an empty or completely hallucinated response.");
                    return "抱歉，目前 AI 產生了異常的內容，無法為您解析天氣，請稍後再試！";
                }
                return result;

            } catch (Exception e) {
                log.error("Error calling Gemini API (Attempt {}/{}): ", attempt, maxRetries, e);
                if (attempt < maxRetries) {
                    try {
                        Thread.sleep(delayMs);
                        delayMs *= 2;
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                    }
                    continue;
                }
                return "很抱歉，我目前無法連上大腦網路，無法回答您的問題。請稍後再試！";
            }
        }
        return "抱歉，目前 Google AI 伺服器正面臨瞬間的全球高流量，導致服務暫時無回應。系統已嘗試重新連線但未成功，請您稍後再試一次！";
    }

    /**
     * Strips any characters from non-CJK/non-ASCII Unicode blocks that the LLM
     * might hallucinate
     * (e.g., Devanagari like Hindi 'तीनों', Arabic, Thai).
     */
    private String sanitizeResponse(String text) {
        if (text == null)
            return "";

        // Fix Gemini's specific Hindi numeral hallucinations to keep sentences
        // grammatical
        String sanitized = text.replace("दोनों", "這兩個")
                .replace("तीनों", "這三個")
                .replace("चारों", "這四個")
                .replace("पांचों", "這五個");

        // Remove remaining Devanagari script safely without breaking symbols like °
        sanitized = sanitized.replaceAll("\\p{IsDevanagari}+", "");

        if (!sanitized.equals(text)) {
            log.warn("Sanitized foreign-script characters from Gemini response.");
        }

        return sanitized.trim();
    }

    private String buildPrompt(String userMessage, String targetLocation, WeatherInfoDto daily, String rtc,
            String town3h, String weekly) {
        StringBuilder prompt = new StringBuilder();
        prompt.append(
                "【語言與格式絕對指令】：你的所有回覆必須直接且『僅能』使用繁體中文，嚴禁使用任何其他語言（包括英文）。【警告】：絕對不能輸出你的思考過程 (Chain of Thought)、內部草稿、分析筆記或任何大綱！請直接給出您最終要對用戶說的話。\n\n");
        String nowStr = java.time.LocalDateTime.now(java.time.ZoneId.of("Asia/Taipei"))
                .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd EEEE HH:mm:ss"));
        prompt.append("【當前系統標準時間】：").append(nowStr).append(" (請以此時間為基準來判斷「今天」、「明天」與「後天」的預報對應時間)。\n");
        prompt.append(
                "【時間定義絕對指令】：氣象資料中的「06:00~18:00」屬於【白天(Daytime)】，絕對不可以稱為晚上！「18:00~06:00」才屬於【晚上/今晚/夜間(Night)】。當用戶詢問「今晚」時，請務必只讀取 18:00 之後的資料，嚴禁把 06:00~18:00 的狀況回答給今晚！\n\n");
        prompt.append("你是一個熱情、專業且友善的天氣推播機器人，請根據以下氣象署提供的即時原始資料，回答用戶的問題。\n");
        prompt.append("目標地區：").append(targetLocation).append("\n\n");

        if (daily != null) {
            prompt.append("【近期12小時概況】：大約 ").append(daily.getMinTemp()).append("~").append(daily.getMaxTemp())
                    .append("度，").append(daily.getDescription()).append("，降雨機率 ").append(daily.getRainProbability())
                    .append("（請注意：這是近期的狀況。若用戶詢問明天或其他時間的預報，請務必去讀取下方的詳細預報JSON並給出具體明天的機率，切勿張冠李戴）。\n");
        }
        if (rtc != null) {
            prompt.append("【即時觀測】：").append(rtc).append("\n");
        }
        if (town3h != null) {
            prompt.append("【未來3天詳細預報(含逐3小時降雨機率)原始JSON】：\n").append(town3h).append("\n");
        }
        if (weekly != null) {
            prompt.append("【未來一週天氣預報原始JSON】：\n").append(weekly).append("\n");
        }

        // If absolutely no context is found at all
        if (daily == null && rtc == null && town3h == null && weekly == null) {
            return "以下是用戶的詢問：「" + userMessage + "」。由於找不到當地氣象資料，請婉轉告知找不到相關天氣預報。";
        }

        prompt.append("\n用戶的訊息：「").append(userMessage).append("」\n");
        prompt.append(
                "請直接以自然對話回覆使用者的問題（不要輸出 Markdown 程式碼區塊，只輸出你的最終回答，嚴禁夾帶推理過程或是英文思考段落）。如果你在資料中看到類似 3 小時 JSON 預報，請自行將連續且變動不大的時段「整併」成一個範圍告訴用戶（例如：「今天 06:00 至 18:00 降雨機率皆維持在 70% 左右」），嚴禁像流水帳般把每一個時間點都講一次！時間請一律使用標準數位格式如「06:00」。");
        prompt.append("【重要指令】：回覆的首句請務必明確指出你正在播報的「縣市」與「鄉鎮」完整名稱（例如：「為您播報基隆市信義區的天氣...」或「關於臺北市信義區...」），以避免任何地名混淆！");
        return prompt.toString();
    }

    @Override
    public String rewriteWarningDescription(String locationName, String originalDescription) {
        if (originalDescription == null || originalDescription.isEmpty()) {
            return originalDescription;
        }

        try {
            String promptStr = String.format("以下是中央氣象署針對某些特報發布的公版總結文字：\n" +
                            "「%s」\n\n" +
                            "這段文字通常會統稱某些地理範圍（例如「嘉義以北」、「中南部」等），導致特定縣市的居民不知道自己是否包含在內。\n" +
                            "現在系統已經確認【%s】就在這次的特報影響範圍名單內。\n" +
                            "請你將這段氣象署的說明文字重新「微調改寫」，讓它讀起來像是專門對【%s】的居民說的，明確提到該縣市也在範圍內。\n" +
                            "規則：\n" +
                            "1. 語氣保持專業、客觀（像是氣象播報員），絕對不要過度誇大。\n" +
                            "2. 請保留原文中所有的降雨型態、影響現象（如坍方、落石、積水等），不可隨意刪減天氣現象描述。\n" +
                            "3. 句子要精簡順暢，長度與原文差不多，不要變成一長串囉嗦的解釋。\n" +
                            "4. 只輸出最終改寫的結果，不包含任何其他的對話或前言、問候語。",
                    originalDescription, locationName, locationName);

            GeminiRequestDto requestDto = GeminiRequestDto.builder()
                    .contents(List.of(
                            GeminiRequestDto.Content.builder()
                                    .parts(List.of(GeminiRequestDto.Part.builder().text(promptStr).build()))
                                    .build()))
                    .build();

            String result = executeGeminiCall(requestDto);
            if (!result.contains("抱歉，目前")) {
                return result.trim();
            }
        } catch (Exception e) {
            log.error("Failed to rewrite warning description with Gemini for {}: ", locationName, e);
        }

        return originalDescription; // Fallback to original description if AI fails
    }
}
