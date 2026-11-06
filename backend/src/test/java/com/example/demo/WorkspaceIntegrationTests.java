package com.example.demo;

import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.demo.entity.User;
import com.example.demo.entity.Workspace;
import com.example.demo.entity.WorkspaceMember;
import com.example.demo.enums.Role;
import com.example.demo.enums.WorkspaceRole;
import com.example.demo.repository.UserRepository;
import com.example.demo.repository.WorkspaceMemberRepository;
import com.example.demo.repository.WorkspaceRepository;
import com.example.demo.security.JwtService;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class WorkspaceIntegrationTests {
	private static final String TEST_SECRET = "MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=";

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private WorkspaceRepository workspaceRepository;

	@Autowired
	private WorkspaceMemberRepository memberRepository;

	@Autowired
	private JwtService jwtService;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@DynamicPropertySource
	static void configureProperties(DynamicPropertyRegistry registry) {
		registry.add("app.jwt.secret", () -> TEST_SECRET);
	}

	@Test
	void flywayV2CreatesWorkspaceTablesAndIndexes() {
		assertEquals(1, jdbcTemplate.queryForObject(
				"SELECT count(*) FROM flyway_schema_history WHERE version = '2' AND success", Integer.class));
		assertTrue(jdbcTemplate.queryForObject(
				"SELECT to_regclass('public.workspaces') IS NOT NULL", Boolean.class));
		assertTrue(jdbcTemplate.queryForObject(
				"SELECT to_regclass('public.workspace_members') IS NOT NULL", Boolean.class));
	}

	@Test
	void createWorkspaceMakesCreatorOwner() throws Exception {
		User owner = createUser(Role.MEMBER);
		CreatedWorkspace created = createWorkspace(owner, "Engineering Team");

		assertEquals(WorkspaceRole.OWNER.name(), created.role());
		assertEquals(1, created.memberCount());
		assertTrue(created.slug().startsWith("engineering-team-"));
		Workspace workspace = workspaceRepository.findById(created.id()).orElseThrow();
		WorkspaceMember membership = memberRepository
				.findByWorkspace_IdAndUser_Id(created.id(), owner.getId()).orElseThrow();
		assertEquals(owner.getId(), workspace.getOwner().getId());
		assertEquals(WorkspaceRole.OWNER, membership.getRole());
		mockMvc.perform(get("/api/v1/workspaces/{id}", created.id())
						.header("Authorization", bearer(owner)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.id", is(created.id().toString())))
				.andExpect(jsonPath("$.currentUserRole", is("OWNER")));
	}

	@Test
	void listOnlyReturnsWorkspacesThePrincipalBelongsTo() throws Exception {
		User first = createUser(Role.MEMBER);
		User second = createUser(Role.MEMBER);
		CreatedWorkspace mine = createWorkspace(first, "Mine");
		createWorkspace(second, "Theirs");

		mockMvc.perform(get("/api/v1/workspaces").header("Authorization", bearer(first)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.length()", is(1)))
				.andExpect(jsonPath("$[0].id", is(mine.id().toString())));
	}

	@Test
	void nonMemberCannotReadWorkspaceDetailsOrMembers() throws Exception {
		User owner = createUser(Role.MEMBER);
		User outsider = createUser(Role.MEMBER);
		CreatedWorkspace workspace = createWorkspace(owner, "Private Team");

		mockMvc.perform(get("/api/v1/workspaces/{id}", workspace.id())
						.header("Authorization", bearer(outsider)))
				.andExpect(status().isForbidden());
		mockMvc.perform(get("/api/v1/workspaces/{id}/members", workspace.id())
						.header("Authorization", bearer(outsider)))
				.andExpect(status().isForbidden());
		mockMvc.perform(get("/api/v1/workspaces"))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void ownerCanAddAnExistingUserButCannotAddThemTwice() throws Exception {
		User owner = createUser(Role.MEMBER);
		User invited = createUser(Role.MEMBER);
		CreatedWorkspace workspace = createWorkspace(owner, "Team");

		MvcResult added = mockMvc.perform(post("/api/v1/workspaces/{id}/members", workspace.id())
						.header("Authorization", bearer(owner))
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"email":"%s"}
								""".formatted(invited.getEmail().toUpperCase())))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.role", is("MEMBER")))
				.andExpect(jsonPath("$.email", is(invited.getEmail())))
				.andReturn();
		UUID memberId = UUID.fromString(com.jayway.jsonpath.JsonPath.read(
				added.getResponse().getContentAsString(), "$.id"));

		mockMvc.perform(post("/api/v1/workspaces/{id}/members", workspace.id())
						.header("Authorization", bearer(owner))
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"email":"%s"}
								""".formatted(invited.getEmail())))
				.andExpect(status().isConflict());
		mockMvc.perform(get("/api/v1/workspaces/{id}/members", workspace.id())
						.header("Authorization", bearer(owner)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.length()", is(2)))
				.andExpect(jsonPath("$[1].id", is(memberId.toString())));
		assertEquals(2, memberRepository.countByWorkspace_Id(workspace.id()));
	}

	@Test
	void memberCannotAddOthersAndGlobalAdminRoleDoesNotGrantWorkspacePermissions() throws Exception {
		User owner = createUser(Role.MEMBER);
		User member = createUser(Role.ADMIN);
		User target = createUser(Role.MEMBER);
		CreatedWorkspace workspace = createWorkspace(owner, "Team");
		addMember(owner, workspace.id(), member);

		mockMvc.perform(post("/api/v1/workspaces/{id}/members", workspace.id())
						.header("Authorization", bearer(member))
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"email":"%s"}
								""".formatted(target.getEmail())))
				.andExpect(status().isForbidden());
		mockMvc.perform(put("/api/v1/workspaces/{id}", workspace.id())
						.header("Authorization", bearer(member))
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"name":"Changed","description":"No permission"}
								"""))
				.andExpect(status().isForbidden());
	}

	@Test
	void ownerAndAdminRoleChangeRulesAreEnforced() throws Exception {
		User owner = createUser(Role.MEMBER);
		User admin = createUser(Role.MEMBER);
		User otherAdmin = createUser(Role.MEMBER);
		User member = createUser(Role.MEMBER);
		CreatedWorkspace workspace = createWorkspace(owner, "Team");
		UUID adminMemberId = addMember(owner, workspace.id(), admin);
		UUID otherAdminMemberId = addMember(owner, workspace.id(), otherAdmin);
		UUID memberId = addMember(owner, workspace.id(), member);
		UUID ownerMemberId = memberRepository
				.findByWorkspace_IdAndUser_Id(workspace.id(), owner.getId()).orElseThrow().getId();

		changeRole(owner, workspace.id(), adminMemberId, "ADMIN")
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.role", is("ADMIN")));
		changeRole(owner, workspace.id(), otherAdminMemberId, "ADMIN").andExpect(status().isOk());

		changeRole(admin, workspace.id(), memberId, "OWNER").andExpect(status().isForbidden());
		changeRole(admin, workspace.id(), adminMemberId, "OWNER").andExpect(status().isForbidden());
		changeRole(admin, workspace.id(), otherAdminMemberId, "MEMBER").andExpect(status().isForbidden());
		changeRole(owner, workspace.id(), ownerMemberId, "MEMBER").andExpect(status().isForbidden());
		changeRole(admin, workspace.id(), memberId, "ADMIN")
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.role", is("ADMIN")));
		changeRole(owner, workspace.id(), otherAdminMemberId, "MEMBER")
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.role", is("MEMBER")));
	}

	@Test
	void removalRulesProtectOwnerAndAdminMembers() throws Exception {
		User owner = createUser(Role.MEMBER);
		User admin = createUser(Role.MEMBER);
		User member = createUser(Role.MEMBER);
		CreatedWorkspace workspace = createWorkspace(owner, "Team");
		UUID ownerMemberId = memberRepository
				.findByWorkspace_IdAndUser_Id(workspace.id(), owner.getId()).orElseThrow().getId();
		UUID adminMemberId = addMember(owner, workspace.id(), admin);
		UUID memberId = addMember(owner, workspace.id(), member);
		changeRole(owner, workspace.id(), adminMemberId, "ADMIN").andExpect(status().isOk());

		mockMvc.perform(delete("/api/v1/workspaces/{id}/members/{memberId}", workspace.id(), adminMemberId)
						.header("Authorization", bearer(admin)))
				.andExpect(status().isForbidden());
		mockMvc.perform(delete("/api/v1/workspaces/{id}/members/{memberId}", workspace.id(), ownerMemberId)
						.header("Authorization", bearer(owner)))
				.andExpect(status().isForbidden());
		mockMvc.perform(delete("/api/v1/workspaces/{id}/members/{memberId}", workspace.id(), memberId)
						.header("Authorization", bearer(admin)))
				.andExpect(status().isNoContent());
		mockMvc.perform(delete("/api/v1/workspaces/{id}/members/{memberId}", workspace.id(), adminMemberId)
						.header("Authorization", bearer(owner)))
				.andExpect(status().isNoContent());
	}

	@Test
	void ownerCannotLeaveButMemberCan() throws Exception {
		User owner = createUser(Role.MEMBER);
		User member = createUser(Role.MEMBER);
		CreatedWorkspace workspace = createWorkspace(owner, "Team");
		addMember(owner, workspace.id(), member);

		mockMvc.perform(delete("/api/v1/workspaces/{id}/members/me", workspace.id())
						.header("Authorization", bearer(owner)))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.message",
						is("Transfer workspace ownership before leaving the workspace.")));
		mockMvc.perform(delete("/api/v1/workspaces/{id}/members/me", workspace.id())
						.header("Authorization", bearer(member)))
				.andExpect(status().isNoContent());
		assertFalse(memberRepository.existsByWorkspace_IdAndUser_Id(workspace.id(), member.getId()));
	}

	@Test
	void adminCanUpdateButCannotDeleteWorkspaceAndOwnerCanDeleteIt() throws Exception {
		User owner = createUser(Role.MEMBER);
		User admin = createUser(Role.MEMBER);
		CreatedWorkspace workspace = createWorkspace(owner, "Original");
		UUID adminMemberId = addMember(owner, workspace.id(), admin);
		changeRole(owner, workspace.id(), adminMemberId, "ADMIN").andExpect(status().isOk());

		mockMvc.perform(put("/api/v1/workspaces/{id}", workspace.id())
						.header("Authorization", bearer(admin))
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"name":"Updated name","description":"Updated description"}
								"""))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.name", is("Updated name")));
		mockMvc.perform(delete("/api/v1/workspaces/{id}", workspace.id())
						.header("Authorization", bearer(admin)))
				.andExpect(status().isForbidden());
		mockMvc.perform(delete("/api/v1/workspaces/{id}", workspace.id())
						.header("Authorization", bearer(owner)))
				.andExpect(status().isNoContent());
		assertFalse(workspaceRepository.existsById(workspace.id()));
		assertEquals(0, memberRepository.countByWorkspace_Id(workspace.id()));
	}

	@Test
	void adminCanPromoteMemberButOwnerIsNeverAnAssignableRole() throws Exception {
		User owner = createUser(Role.MEMBER);
		User admin = createUser(Role.MEMBER);
		User member = createUser(Role.MEMBER);
		CreatedWorkspace workspace = createWorkspace(owner, "Team");
		UUID adminId = addMember(owner, workspace.id(), admin);
		UUID memberId = addMember(owner, workspace.id(), member);
		changeRole(owner, workspace.id(), adminId, "ADMIN").andExpect(status().isOk());

		changeRole(admin, workspace.id(), memberId, "ADMIN")
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.role", is("ADMIN")));
		assertTrue(memberRepository.findById(memberId).orElseThrow().getRole() == WorkspaceRole.ADMIN);
	}

	private User createUser(Role role) {
		User user = new User();
		user.setFullName("Workspace Test");
		user.setEmail(UUID.randomUUID() + "@workspace.test");
		user.setPassword("$2a$10$" + "a".repeat(53));
		user.setRole(role);
		user.setActive(true);
		return userRepository.saveAndFlush(user);
	}

	private CreatedWorkspace createWorkspace(User owner, String name) throws Exception {
		MvcResult result = mockMvc.perform(post("/api/v1/workspaces")
						.header("Authorization", bearer(owner))
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"name":"%s","description":"Workspace test"}
								""".formatted(name)))
				.andExpect(status().isCreated())
				.andReturn();
		String body = result.getResponse().getContentAsString();
		return new CreatedWorkspace(
				UUID.fromString(com.jayway.jsonpath.JsonPath.read(body, "$.id")),
				com.jayway.jsonpath.JsonPath.read(body, "$.slug"),
				com.jayway.jsonpath.JsonPath.read(body, "$.currentUserRole"),
				com.jayway.jsonpath.JsonPath.read(body, "$.memberCount"));
	}

	private UUID addMember(User actor, UUID workspaceId, User user) throws Exception {
		MvcResult result = mockMvc.perform(post("/api/v1/workspaces/{id}/members", workspaceId)
						.header("Authorization", bearer(actor))
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"email":"%s"}
								""".formatted(user.getEmail())))
				.andExpect(status().isCreated())
				.andReturn();
		return UUID.fromString(com.jayway.jsonpath.JsonPath.read(
				result.getResponse().getContentAsString(), "$.id"));
	}

	private org.springframework.test.web.servlet.ResultActions changeRole(
			User actor, UUID workspaceId, UUID memberId, String role) throws Exception {
		return mockMvc.perform(put(
						"/api/v1/workspaces/{id}/members/{memberId}/role", workspaceId, memberId)
				.header("Authorization", bearer(actor))
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"role":"%s"}
						""".formatted(role)));
	}

	private String bearer(User user) {
		return "Bearer " + jwtService.generateToken(user);
	}

	private record CreatedWorkspace(UUID id, String slug, String role, int memberCount) {
	}
}
