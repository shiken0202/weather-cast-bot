package org.example.weathercastbot.bot;

import com.linecorp.bot.messaging.client.MessagingApiClient;
import com.linecorp.bot.webhook.model.EventMode;
import com.linecorp.bot.webhook.model.MessageEvent;
import com.linecorp.bot.webhook.model.TextMessageContent;
import com.linecorp.bot.webhook.model.UserSource;
import org.example.weathercastbot.entity.Location;
import org.example.weathercastbot.entity.Platform;
import org.example.weathercastbot.service.CWAService;
import org.example.weathercastbot.service.GeminiService;
import org.example.weathercastbot.service.SubscriptionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;

public class LineBotHandlerTest {

    private LineBotHandler lineBotHandler;
    private SubscriptionService subscriptionService;
    private CWAService cwaService;
    private GeminiService geminiService;

    @BeforeEach
    public void setup() {
        subscriptionService = mock(SubscriptionService.class);
        cwaService = mock(CWAService.class);
        geminiService = mock(GeminiService.class);

        lineBotHandler = new LineBotHandler(subscriptionService, cwaService, geminiService, Optional.empty());
    }

    @Test
    public void testHandleTextMessageEvent_Subscribe() {
        MessageEvent event = mock(MessageEvent.class);
        TextMessageContent content = mock(TextMessageContent.class);
        UserSource source = mock(UserSource.class);

        when(event.message()).thenReturn(content);
        when(content.text()).thenReturn("訂閱 台中市");
        when(event.source()).thenReturn(source);
        when(source.userId()).thenReturn("U1234567890");
        when(event.replyToken()).thenReturn("replyToken123");

        // Note: reply message sending logic triggers an interaction with the optional MessagingApiClient...
        lineBotHandler.handleMessageEvent(event);

        verify(subscriptionService, times(1)).subscribe(Platform.LINE, "U1234567890", "臺中市");
    }
}
