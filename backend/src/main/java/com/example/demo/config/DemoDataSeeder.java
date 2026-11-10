package com.example.demo.config;

import com.example.demo.dto.request.CreateProjectRequest;
import com.example.demo.dto.request.CreateTaskRequest;
import com.example.demo.dto.request.CreateWorkspaceRequest;
import com.example.demo.entity.Project;
import com.example.demo.entity.User;
import com.example.demo.entity.Workspace;
import com.example.demo.enums.ProjectStatus;
import com.example.demo.enums.TaskPriority;
import com.example.demo.exception.UserNotFoundException;
import com.example.demo.repository.ProjectMemberRepository;
import com.example.demo.repository.ProjectRepository;
import com.example.demo.repository.TaskRepository;
import com.example.demo.repository.UserRepository;
import com.example.demo.repository.WorkspaceMemberRepository;
import com.example.demo.repository.WorkspaceRepository;
import com.example.demo.service.ProjectService;
import com.example.demo.service.TaskService;
import com.example.demo.service.WorkspaceService;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("dev & !prod")
@ConditionalOnProperty(name = "syncora.demo-seed.enabled", havingValue = "true")
public class DemoDataSeeder implements ApplicationRunner {
	private static final Logger logger = LoggerFactory.getLogger(DemoDataSeeder.class);

	private final String configuredEmail;
	private final UserRepository userRepository;
	private final WorkspaceRepository workspaceRepository;
	private final WorkspaceMemberRepository workspaceMemberRepository;
	private final ProjectRepository projectRepository;
	private final ProjectMemberRepository projectMemberRepository;
	private final TaskRepository taskRepository;
	private final WorkspaceService workspaceService;
	private final ProjectService projectService;
	private final TaskService taskService;

	public DemoDataSeeder(
			@Value("${syncora.demo-seed.user-email:}") String configuredEmail,
			UserRepository userRepository,
			WorkspaceRepository workspaceRepository,
			WorkspaceMemberRepository workspaceMemberRepository,
			ProjectRepository projectRepository,
			ProjectMemberRepository projectMemberRepository,
			TaskRepository taskRepository,
			WorkspaceService workspaceService,
			ProjectService projectService,
			TaskService taskService) {
		this.configuredEmail = configuredEmail;
		this.userRepository = userRepository;
		this.workspaceRepository = workspaceRepository;
		this.workspaceMemberRepository = workspaceMemberRepository;
		this.projectRepository = projectRepository;
		this.projectMemberRepository = projectMemberRepository;
		this.taskRepository = taskRepository;
		this.workspaceService = workspaceService;
		this.projectService = projectService;
		this.taskService = taskService;
	}

	@Override
	public void run(ApplicationArguments args) {
		String email = configuredEmail.trim().toLowerCase(Locale.ROOT);
		if (email.isBlank()) {
			throw new IllegalStateException(
					"Set SYNCORA_DEMO_SEED_USER_EMAIL to an existing account when demo seeding is enabled.");
		}
		User user = userRepository.findByEmail(email)
				.filter(User::isActive)
				.orElseThrow(UserNotFoundException::new);

		String nameSuffix = user.getId().toString().substring(0, 8);
		String workspaceName = "Syncora Demo " + nameSuffix;
		Workspace workspace = workspaceRepository.findByName(workspaceName).orElse(null);
		if (workspace == null) {
			workspaceService.create(email, new CreateWorkspaceRequest(
					workspaceName, "Development demo workspace."));
			workspace = workspaceRepository.findByName(workspaceName)
					.orElseThrow(() -> new IllegalStateException("The demo workspace could not be created."));
		}
		if (!workspaceMemberRepository.existsByWorkspace_IdAndUser_Id(workspace.getId(), user.getId())) {
			throw new IllegalStateException(
					"A workspace with the reserved demo name exists but the configured account is not a member.");
		}

		var workspaceId = workspace.getId();
		String projectName = "Syncora Demo Project " + nameSuffix;
		Project project = projectRepository.findByWorkspace_IdAndName(workspaceId, projectName)
				.orElseGet(() -> {
					var created = projectService.create(
							workspaceId,
							email,
							new CreateProjectRequest(projectName, "A seeded project for development.", ProjectStatus.ACTIVE, null, null));
					return projectRepository.findById(created.id()).orElseThrow();
				});
		if (!projectMemberRepository.existsByProject_IdAndUser_Id(project.getId(), user.getId())) {
			throw new IllegalStateException(
					"A project with the reserved demo name exists but the configured account is not a member.");
		}

		String taskTitle = "Review the Syncora demo workspace";
		if (taskRepository.findByProject_IdAndTitle(project.getId(), taskTitle).isEmpty()) {
			taskService.create(
					project.getId(),
					email,
					new CreateTaskRequest(taskTitle, "A sample task seeded for development.", TaskPriority.MEDIUM, user.getId(), null));
		}
		logger.info("Development demo data is ready for {}.", email);
	}
}
