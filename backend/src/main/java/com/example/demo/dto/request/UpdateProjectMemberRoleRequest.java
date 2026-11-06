package com.example.demo.dto.request;

import com.example.demo.enums.ProjectRole;
import jakarta.validation.constraints.NotNull;

public record UpdateProjectMemberRoleRequest(@NotNull ProjectRole role) {
}
