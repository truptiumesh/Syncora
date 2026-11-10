package com.example.demo.security;

import com.example.demo.service.ChatMessageService;
import com.example.demo.service.DirectMessageService;
import com.example.demo.exception.DirectConversationNotFoundException;
import com.example.demo.exception.WorkspaceNotFoundException;
import com.example.demo.exception.ProjectNotFoundException;
import com.example.demo.exception.UserNotFoundException;
import io.jsonwebtoken.JwtException;
import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessagingException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.stereotype.Component;

@Component
public class ChatWebSocketAuthInterceptor implements ChannelInterceptor {
	private static final Pattern PROJECT_DESTINATION =
			Pattern.compile("^/(?:app|topic)/projects/([0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12})/messages$");
	private static final Pattern DIRECT_SUBSCRIPTION =
			Pattern.compile("^/topic/direct/([0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12})$");
	private static final Pattern DIRECT_SEND =
			Pattern.compile("^/app/direct/([0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12})/messages$");

	private final JwtService jwtService;
	private final UserDetailsService userDetailsService;
	private final ChatMessageService chatMessageService;
	private final DirectMessageService directMessageService;

	public ChatWebSocketAuthInterceptor(
			JwtService jwtService,
			UserDetailsService userDetailsService,
			ChatMessageService chatMessageService,
			DirectMessageService directMessageService) {
		this.jwtService = jwtService;
		this.userDetailsService = userDetailsService;
		this.chatMessageService = chatMessageService;
		this.directMessageService = directMessageService;
	}

	@Override
	public Message<?> preSend(Message<?> message, MessageChannel channel) {
		StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
		if (accessor == null) {
			throw new MessagingException("Invalid STOMP message.");
		}
		StompCommand command = accessor.getCommand();
		if (command == StompCommand.CONNECT) {
			accessor.setUser(authenticate(accessor));
			return message;
		}

		if (command == StompCommand.DISCONNECT || command == null) {
			return message;
		}
		if (accessor.getUser() == null) {
			throw new MessagingException("Authentication is required.");
		}

		if (command == StompCommand.SUBSCRIBE || command == StompCommand.SEND) {
			String destination = accessor.getDestination();
			if (command == StompCommand.SUBSCRIBE
					&& destination != null
					&& destination.startsWith("/user/")
					&& !destination.equals("/user/queue/notifications")) {
				throw new MessagingException("The user destination is invalid.");
			}
			if (command == StompCommand.SUBSCRIBE
					&& "/user/queue/notifications".equals(destination)) {
				return message;
			}
			String expectedPrefix = command == StompCommand.SEND ? "/app/" : "/topic/";
			if (destination == null || !destination.startsWith(expectedPrefix)) {
				if (command == StompCommand.SEND || (destination != null
						&& (destination.startsWith("/topic/projects/")
								|| destination.startsWith("/topic/direct/")))) {
					if (destination != null && destination.startsWith("/topic/direct/")) {
						throw new MessagingException("The direct message destination is invalid.");
					}
					throw new MessagingException("The chat destination is invalid.");
				}
				return message;
			}
			Matcher matcher = PROJECT_DESTINATION.matcher(destination);
			Matcher directMatcher = (command == StompCommand.SEND ? DIRECT_SEND : DIRECT_SUBSCRIPTION)
					.matcher(destination);
			if (directMatcher.matches()) {
				UUID conversationId = UUID.fromString(directMatcher.group(1));
				try {
					directMessageService.authorizeConversation(
							conversationId, accessor.getUser().getName());
				} catch (AccessDeniedException | DirectConversationNotFoundException
						| UserNotFoundException | WorkspaceNotFoundException exception) {
					throw new MessagingException("You do not have access to this direct conversation.");
				}
				return message;
			}
			if (!matcher.matches()) {
				throw new MessagingException("The chat destination is invalid.");
			}
			UUID projectId = UUID.fromString(matcher.group(1));
			try {
				chatMessageService.authorizeProject(projectId, accessor.getUser().getName());
			} catch (AccessDeniedException | ProjectNotFoundException | UserNotFoundException exception) {
				throw new MessagingException("You do not have access to this project chat.");
			}
		}
		return message;
	}

	private UsernamePasswordAuthenticationToken authenticate(StompHeaderAccessor accessor) {
		List<String> authorizationHeaders = accessor.getNativeHeader("Authorization");
		if (authorizationHeaders == null || authorizationHeaders.size() != 1
				|| !authorizationHeaders.getFirst().startsWith("Bearer ")) {
			throw new MessagingException("A bearer token is required.");
		}
		try {
			String username = jwtService.extractUsername(authorizationHeaders.getFirst().substring(7));
			UserDetails userDetails = userDetailsService.loadUserByUsername(username);
			if (!userDetails.isEnabled()) {
				throw new MessagingException("The bearer token is invalid.");
			}
			return UsernamePasswordAuthenticationToken.authenticated(
					userDetails, null, userDetails.getAuthorities());
		} catch (JwtException | IllegalArgumentException | AuthenticationException exception) {
			throw new MessagingException("The bearer token is invalid.");
		}
	}
}
