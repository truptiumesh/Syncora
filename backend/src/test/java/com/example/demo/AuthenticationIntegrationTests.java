package com.example.demo;

import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.demo.entity.User;
import com.example.demo.enums.Role;
import com.example.demo.repository.UserRepository;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;
import javax.crypto.SecretKey;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AuthenticationIntegrationTests {
	private static final String TEST_SECRET = "MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=";
	private static final String PASSWORD = "StrongPassword123";

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private PasswordEncoder passwordEncoder;

	@DynamicPropertySource
	static void configureProperties(DynamicPropertyRegistry registry) {
		registry.add("app.jwt.secret", () -> TEST_SECRET);
	}

	@Test
	void corsAllowsLocalAndProductionOriginsAndRejectsUnconfiguredOrigins() throws Exception {
		for (String origin : new String[] {
				"http://localhost:5173",
				"http://127.0.0.1:5173",
				"https://syncora-frontend.vercel.app"}) {
			for (String method : new String[] {"POST", "PATCH"}) {
				mockMvc.perform(options("/api/v1/auth/login")
								.header("Origin", origin)
								.header("Access-Control-Request-Method", method)
								.header("Access-Control-Request-Headers", "content-type,authorization"))
						.andExpect(status().isOk())
						.andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
								.header().string("Access-Control-Allow-Origin", origin))
						.andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
								.header().string("Access-Control-Allow-Methods", org.hamcrest.Matchers.containsString(method)));
			}
		}

		mockMvc.perform(options("/api/v1/auth/login")
						.header("Origin", "https://untrusted.example")
						.header("Access-Control-Request-Method", "POST")
						.header("Access-Control-Request-Headers", "content-type,authorization"))
				.andExpect(status().isForbidden());
	}

	@Test
	void registrationPersistsHashedMemberAndReturnsSafeUser() throws Exception {
		String email = uniqueEmail();
		mockMvc.perform(post("/api/v1/auth/register")
						.contentType(MediaType.APPLICATION_JSON)
						.content(registerBody(email)))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.user.email", is(email)))
				.andExpect(jsonPath("$.user.role", is("MEMBER")))
				.andExpect(jsonPath("$.user.password").doesNotExist())
				.andExpect(jsonPath("$.tokenType", is("Bearer")));

		User savedUser = userRepository.findByEmail(email).orElseThrow();
		org.junit.jupiter.api.Assertions.assertNotEquals(PASSWORD, savedUser.getPassword());
		org.junit.jupiter.api.Assertions.assertTrue(passwordEncoder.matches(PASSWORD, savedUser.getPassword()));
		org.junit.jupiter.api.Assertions.assertEquals(Role.MEMBER, savedUser.getRole());
	}

	@Test
	void duplicateEmailIsRejected() throws Exception {
		String email = uniqueEmail();
		register(email);

		mockMvc.perform(post("/api/v1/auth/register")
						.contentType(MediaType.APPLICATION_JSON)
						.content(registerBody(email)))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.message", is("An account with this email already exists.")));
	}

	@Test
	void loginSucceedsWithCorrectCredentials() throws Exception {
		String email = uniqueEmail();
		register(email);

		mockMvc.perform(post("/api/v1/auth/login")
						.contentType(MediaType.APPLICATION_JSON)
						.content(loginBody(email, PASSWORD)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.token").isNotEmpty())
				.andExpect(jsonPath("$.user.password").doesNotExist());
	}

	@Test
	void loginRejectsInvalidCredentials() throws Exception {
		String email = uniqueEmail();
		register(email);

		mockMvc.perform(post("/api/v1/auth/login")
						.contentType(MediaType.APPLICATION_JSON)
						.content(loginBody(email, "WrongPassword123")))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void profileRequiresValidTokenAndAcceptsValidToken() throws Exception {
		String email = uniqueEmail();
		String token = register(email);

		mockMvc.perform(get("/api/v1/users/me"))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.message", is("Authentication is required.")));

		mockMvc.perform(get("/api/v1/users/me").header("Authorization", bearer(token)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.email", is(email)))
				.andExpect(jsonPath("$.password").doesNotExist());

		mockMvc.perform(get("/api/v1/users/me").header("Authorization", bearer("not-a-jwt")))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.message", is("Invalid or expired token.")));
	}

	@Test
	void profileCanBeUpdatedWithoutChangingRole() throws Exception {
		String email = uniqueEmail();
		String token = register(email);

		mockMvc.perform(put("/api/v1/users/me")
						.header("Authorization", bearer(token))
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"fullName":"Updated Name","bio":"A short bio","profileImageUrl":"https://example.com/avatar.png"}
								"""))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.fullName", is("Updated Name")))
				.andExpect(jsonPath("$.role", is("MEMBER")));

		User updatedUser = userRepository.findByEmail(email).orElseThrow();
		org.junit.jupiter.api.Assertions.assertEquals("A short bio", updatedUser.getBio());
		org.junit.jupiter.api.Assertions.assertEquals(Role.MEMBER, updatedUser.getRole());
	}

	@Test
	void passwordCanBeChangedAndOldPasswordStopsWorking() throws Exception {
		String email = uniqueEmail();
		String token = register(email);

		mockMvc.perform(put("/api/v1/users/me/password")
						.header("Authorization", bearer(token))
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"currentPassword":"StrongPassword123","newPassword":"AnotherPassword456"}
								"""))
				.andExpect(status().isNoContent());

		mockMvc.perform(post("/api/v1/auth/login")
						.contentType(MediaType.APPLICATION_JSON)
						.content(loginBody(email, PASSWORD)))
				.andExpect(status().isUnauthorized());

		mockMvc.perform(post("/api/v1/auth/login")
						.contentType(MediaType.APPLICATION_JSON)
						.content(loginBody(email, "AnotherPassword456")))
				.andExpect(status().isOk());

		User savedUser = userRepository.findByEmail(email).orElseThrow();
		org.junit.jupiter.api.Assertions.assertTrue(passwordEncoder.matches(
				"AnotherPassword456", savedUser.getPassword()));
	}

	@Test
	void passwordChangeRejectsAnIncorrectCurrentPassword() throws Exception {
		String token = register(uniqueEmail());

		mockMvc.perform(put("/api/v1/users/me/password")
						.header("Authorization", bearer(token))
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"currentPassword":"WrongPassword123","newPassword":"AnotherPassword456"}
								"""))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message", is("Current password is incorrect.")));
	}

	@Test
	void expiredTokenIsRejected() throws Exception {
		Instant now = Instant.now();
		SecretKey secretKey = Keys.hmacShaKeyFor(Decoders.BASE64.decode(TEST_SECRET));
		String expiredToken = Jwts.builder()
				.issuer("syncora")
				.subject("expired@example.com")
				.issuedAt(Date.from(now.minusSeconds(120)))
				.expiration(Date.from(now.minusSeconds(60)))
				.signWith(secretKey)
				.compact();

		mockMvc.perform(get("/api/v1/users/me").header("Authorization", bearer(expiredToken)))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.message", is("Invalid or expired token.")));
	}

	private String register(String email) throws Exception {
		MvcResult result = mockMvc.perform(post("/api/v1/auth/register")
						.contentType(MediaType.APPLICATION_JSON)
						.content(registerBody(email)))
				.andExpect(status().isCreated())
				.andReturn();
		return com.jayway.jsonpath.JsonPath.read(result.getResponse().getContentAsString(), "$.token");
	}

	private String registerBody(String email) {
		return """
				{"fullName":"Test User","email":"%s","password":"%s"}
				""".formatted(email, PASSWORD);
	}

	private String loginBody(String email, String password) {
		return """
				{"email":"%s","password":"%s"}
				""".formatted(email, password);
	}

	private String uniqueEmail() {
		return UUID.randomUUID() + "@example.com";
	}

	private String bearer(String token) {
		return "Bearer " + token;
	}
}
