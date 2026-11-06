package com.example.demo.security;

import com.example.demo.entity.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import javax.crypto.SecretKey;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class JwtService {
	private static final String ISSUER = "syncora";

	private final SecretKey signingKey;
	private final Duration expiration;

	public JwtService(
			@Value("${app.jwt.secret}") String base64Secret,
			@Value("${app.jwt.expiration}") Duration expiration) {
		this.signingKey = Keys.hmacShaKeyFor(Decoders.BASE64.decode(base64Secret));
		this.expiration = expiration;
	}

	public String generateToken(User user) {
		Instant issuedAt = Instant.now();
		return Jwts.builder()
				.issuer(ISSUER)
				.subject(user.getEmail())
				.issuedAt(Date.from(issuedAt))
				.expiration(Date.from(issuedAt.plus(expiration)))
				.signWith(signingKey)
				.compact();
	}

	public String extractUsername(String token) {
		return parseClaims(token).getSubject();
	}

	private Claims parseClaims(String token) {
		return Jwts.parser()
				.verifyWith(signingKey)
				.requireIssuer(ISSUER)
				.build()
				.parseSignedClaims(token)
				.getPayload();
	}
}
