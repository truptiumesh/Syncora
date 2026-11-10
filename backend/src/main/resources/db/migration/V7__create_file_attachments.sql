CREATE TABLE file_attachments (
    id UUID PRIMARY KEY,
    original_filename VARCHAR(255) NOT NULL,
    storage_key VARCHAR(64) NOT NULL UNIQUE,
    content_type VARCHAR(100) NOT NULL,
    file_size BIGINT NOT NULL,
    uploader_id UUID NOT NULL,
    workspace_id UUID NOT NULL,
    project_id UUID,
    uploaded_at TIMESTAMP WITHOUT TIME ZONE NOT NULL,
    CONSTRAINT chk_file_attachments_filename CHECK (length(trim(original_filename)) > 0),
    CONSTRAINT chk_file_attachments_type CHECK (
        content_type IN ('application/pdf', 'image/jpeg', 'image/png', 'image/gif')
    ),
    CONSTRAINT chk_file_attachments_size CHECK (file_size > 0),
    CONSTRAINT fk_file_attachments_uploader FOREIGN KEY (uploader_id)
        REFERENCES users (id) ON DELETE RESTRICT,
    CONSTRAINT fk_file_attachments_workspace FOREIGN KEY (workspace_id)
        REFERENCES workspaces (id) ON DELETE CASCADE,
    CONSTRAINT fk_file_attachments_project_workspace FOREIGN KEY (project_id, workspace_id)
        REFERENCES projects (id, workspace_id) ON DELETE CASCADE
);

CREATE INDEX idx_file_attachments_workspace_uploaded
    ON file_attachments (workspace_id, uploaded_at DESC, id DESC)
    WHERE project_id IS NULL;
CREATE INDEX idx_file_attachments_project_uploaded
    ON file_attachments (project_id, uploaded_at DESC, id DESC)
    WHERE project_id IS NOT NULL;
CREATE INDEX idx_file_attachments_uploader ON file_attachments (uploader_id);

ALTER TABLE activity_records DROP CONSTRAINT chk_activity_type;
ALTER TABLE activity_records ADD CONSTRAINT chk_activity_type CHECK (activity_type IN (
    'WORKSPACE_CREATED', 'WORKSPACE_UPDATED', 'WORKSPACE_MEMBER_ADDED',
    'PROJECT_CREATED', 'PROJECT_UPDATED', 'PROJECT_MEMBER_ADDED',
    'TASK_CREATED', 'TASK_UPDATED', 'TASK_ASSIGNED', 'TASK_REASSIGNED', 'TASK_STATUS_CHANGED',
    'TASK_PRIORITY_CHANGED', 'TASK_COMMENT_ADDED',
    'CHAT_MESSAGE_SENT', 'CHAT_MESSAGE_EDITED', 'CHAT_MESSAGE_DELETED',
    'FILE_UPLOADED', 'FILE_DELETED'
));
