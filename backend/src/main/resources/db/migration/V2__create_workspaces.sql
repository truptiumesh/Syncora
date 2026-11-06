CREATE TABLE workspaces (
    id UUID PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    description VARCHAR(1000),
    slug VARCHAR(150) NOT NULL UNIQUE,
    owner_id UUID NOT NULL,
    created_at TIMESTAMP WITHOUT TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITHOUT TIME ZONE NOT NULL,
    CONSTRAINT fk_workspaces_owner FOREIGN KEY (owner_id)
        REFERENCES users (id) ON DELETE RESTRICT
);

CREATE TABLE workspace_members (
    id UUID PRIMARY KEY,
    workspace_id UUID NOT NULL,
    user_id UUID NOT NULL,
    role VARCHAR(20) NOT NULL,
    joined_at TIMESTAMP WITHOUT TIME ZONE NOT NULL,
    CONSTRAINT uk_workspace_members_workspace_user UNIQUE (workspace_id, user_id),
    CONSTRAINT chk_workspace_members_role CHECK (role IN ('OWNER', 'ADMIN', 'MEMBER')),
    CONSTRAINT fk_workspace_members_workspace FOREIGN KEY (workspace_id)
        REFERENCES workspaces (id) ON DELETE CASCADE,
    CONSTRAINT fk_workspace_members_user FOREIGN KEY (user_id)
        REFERENCES users (id) ON DELETE RESTRICT
);

CREATE INDEX idx_workspaces_owner_id ON workspaces (owner_id);
CREATE INDEX idx_workspace_members_user_id ON workspace_members (user_id);
CREATE INDEX idx_workspace_members_workspace_role ON workspace_members (workspace_id, role);
