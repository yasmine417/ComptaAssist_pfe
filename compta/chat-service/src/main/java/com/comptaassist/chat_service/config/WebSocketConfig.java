package com.comptaassist.chat_service.config;

import com.comptaassist.chat_service.config.JwtUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.web.socket.config.annotation.*;

import java.security.Principal;
import java.util.List;

@Configuration
@EnableWebSocketMessageBroker
@RequiredArgsConstructor
@Slf4j
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final JwtUtil jwtUtil;

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        // Préfixe pour les messages envoyés par le broker
        registry.enableSimpleBroker("/topic", "/queue");
        // Préfixe pour les messages envoyés par le client
        registry.setApplicationDestinationPrefixes("/app");
        // Préfixe pour les messages personnels
        registry.setUserDestinationPrefix("/user");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws-chat")
                .setAllowedOriginPatterns("*")
                .withSockJS();
    }

    @Override
    public void configureClientInboundChannel(
            ChannelRegistration registration) {
        registration.interceptors(new ChannelInterceptor() {
            @Override
            public Message<?> preSend(Message<?> message,
                                      MessageChannel channel) {
                StompHeaderAccessor accessor =
                        MessageHeaderAccessor.getAccessor(
                                message, StompHeaderAccessor.class);

                if (accessor != null && StompCommand.CONNECT.equals(
                        accessor.getCommand())) {

                    String token = accessor
                            .getFirstNativeHeader("Authorization");
                    String clientId = accessor
                            .getFirstNativeHeader("clientId");

                    log.info("WS CONNECT - token={} clientId={}",
                            token != null ? "présent" : "absent",
                            clientId);

                    if (token != null
                            && token.startsWith("Bearer ")) {
                        token = token.substring(7);
                        try {
                            String userId = jwtUtil.extractUserId(token);
                            final String id = userId;
                            accessor.setUser(() -> id);
                            log.info("WS CONNECT JWT OK: userId={}", id);
                        } catch (Exception e) {
                            log.error("JWT invalide: {}", e.getMessage());
                        }
                    } else if (clientId != null
                            && !clientId.isBlank()) {
                        accessor.setUser(() -> clientId);
                        log.info("WS CONNECT clientId: {}", clientId);
                    }
                }
                return message;
            }






        });
    }
}