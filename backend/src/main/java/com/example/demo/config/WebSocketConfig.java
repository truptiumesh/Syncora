package com.example.demo.config;

import com.example.demo.security.ChatWebSocketAuthInterceptor;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {
	private final ChatWebSocketAuthInterceptor authInterceptor;
	private final List<String> allowedOrigins;

	public WebSocketConfig(
			ChatWebSocketAuthInterceptor authInterceptor,
			@Value("${app.cors.allowed-origins}") String configuredOrigins) {
		this.authInterceptor = authInterceptor;
		this.allowedOrigins = List.of(configuredOrigins.split(",")).stream()
				.map(String::trim)
				.filter(origin -> !origin.isEmpty())
				.toList();
		if (allowedOrigins.isEmpty()) {
			throw new IllegalStateException("At least one WebSocket allowed origin must be configured.");
		}
	}

	@Override
	public void configureMessageBroker(MessageBrokerRegistry registry) {
		registry.enableSimpleBroker("/topic");
		registry.setApplicationDestinationPrefixes("/app");
	}

	@Override
	public void registerStompEndpoints(StompEndpointRegistry registry) {
		registry.addEndpoint("/ws").setAllowedOrigins(allowedOrigins.toArray(String[]::new));
	}

	@Override
	public void configureClientInboundChannel(ChannelRegistration registration) {
		registration.interceptors(authInterceptor);
	}
}
