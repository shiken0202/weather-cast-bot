package org.example.weathercastbot.bot;

import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.unions.MessageChannelUnion;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.requests.restaction.MessageCreateAction;
import org.example.weathercastbot.entity.Platform;
import org.example.weathercastbot.service.CWAService;
import org.example.weathercastbot.service.GeminiService;
import org.example.weathercastbot.service.SubscriptionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

public class DiscordBotServiceTest {

    private DiscordBotService discordBotService;
    private SubscriptionService subscriptionService;
    private CWAService cwaService;
    private GeminiService geminiService;

    @BeforeEach
    public void setup() {
        subscriptionService = mock(SubscriptionService.class);
        cwaService = mock(CWAService.class);
        geminiService = mock(GeminiService.class);

        discordBotService = new DiscordBotService(subscriptionService, cwaService, geminiService);
    }

    @Test
    public void testOnMessageReceived_Subscribe() {
        MessageReceivedEvent event = mock(MessageReceivedEvent.class);
        User author = mock(User.class);
        MessageChannelUnion channel = mock(MessageChannelUnion.class);
        Message message = mock(Message.class);

        when(event.getAuthor()).thenReturn(author);
        when(author.isBot()).thenReturn(false);
        when(event.getChannel()).thenReturn(channel);
        when(channel.getId()).thenReturn("discord-channel-123");
        when(event.getMessage()).thenReturn(message);
        when(message.getContentRaw()).thenReturn("!訂閱 台北市");

        MessageCreateAction action = mock(MessageCreateAction.class);
        when(channel.sendMessage(anyString())).thenReturn(action);

        discordBotService.onMessageReceived(event);

        verify(subscriptionService, times(1)).subscribe(Platform.DISCORD, "discord-channel-123", "臺北市");
        verify(channel, times(1)).sendMessage((CharSequence) any());
    }
}
