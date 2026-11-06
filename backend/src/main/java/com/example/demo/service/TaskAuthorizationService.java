package com.example.demo.service;

import com.example.demo.entity.Task;
import com.example.demo.exception.TaskNotFoundException;
import com.example.demo.repository.TaskRepository;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class TaskAuthorizationService {
	private final TaskRepository taskRepository;
	private final ProjectAuthorizationService projectAuthorizationService;

	public TaskAuthorizationService(
			TaskRepository taskRepository,
			ProjectAuthorizationService projectAuthorizationService) {
		this.taskRepository = taskRepository;
		this.projectAuthorizationService = projectAuthorizationService;
	}

	public Access requireTaskAccess(UUID taskId, String principalName) {
		Task task = taskRepository.findById(taskId).orElseThrow(TaskNotFoundException::new);
		ProjectAuthorizationService.Access projectAccess = projectAuthorizationService
				.requireProjectAccess(task.getProject().getId(), principalName);
		return new Access(task, projectAccess);
	}

	public record Access(Task task, ProjectAuthorizationService.Access projectAccess) {
	}
}
