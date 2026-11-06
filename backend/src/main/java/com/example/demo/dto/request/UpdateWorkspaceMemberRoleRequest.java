package com.example.demo.dto.request;

import com.example.demo.enums.WorkspaceRole;
import jakarta.validation.constraints.NotNull;

public record UpdateWorkspaceMemberRoleRequest(@NotNull WorkspaceRole role) {
}
