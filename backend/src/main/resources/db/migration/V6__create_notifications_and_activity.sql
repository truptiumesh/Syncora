CREATE TABLE notifications (
    id UUID PRIMARY KEY,
    event_id UUID NOT NULL,
    recipient_id UUID NOT NULL,
    actor_id UUID,
    notification_type VARCHAR(40) NOT NULL,
    title VARCHAR(120) NOT NULL,
    message VARCHAR(500) NOT NULL,
    workspace_id UUID,
    project_id UUID,
    task_id UUID,
    task_comment_id UUID,
    chat_message_id UUID,
    is_read BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP WITHOUT TIME ZONE NOT NULL,
    read_at TIMESTAMP WITHOUT TIME ZONE,
    CONSTRAINT uk_notifications_recipient_event UNIQUE (recipient_id, event_id),
    CONSTRAINT chk_notifications_read_state CHECK (is_read = (read_at IS NOT NULL)),
    CONSTRAINT chk_notifications_type CHECK (notification_type IN (
        'TASK_ASSIGNED', 'TASK_REASSIGNED', 'TASK_STATUS_CHANGED', 'TASK_PRIORITY_CHANGED',
        'TASK_COMMENT_ADDED', 'PROJECT_MEMBER_ADDED', 'PROJECT_UPDATED', 'WORKSPACE_UPDATED',
        'WORKSPACE_MEMBER_ADDED'
    )),
    CONSTRAINT chk_notifications_title CHECK (length(trim(title)) > 0),
    CONSTRAINT chk_notifications_message CHECK (length(trim(message)) > 0),
    CONSTRAINT fk_notifications_recipient FOREIGN KEY (recipient_id)
        REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_notifications_actor FOREIGN KEY (actor_id)
        REFERENCES users (id) ON DELETE SET NULL,
    CONSTRAINT fk_notifications_workspace FOREIGN KEY (workspace_id)
        REFERENCES workspaces (id) ON DELETE SET NULL,
    CONSTRAINT fk_notifications_project FOREIGN KEY (project_id)
        REFERENCES projects (id) ON DELETE SET NULL,
    CONSTRAINT fk_notifications_task FOREIGN KEY (task_id)
        REFERENCES tasks (id) ON DELETE SET NULL,
    CONSTRAINT fk_notifications_task_comment FOREIGN KEY (task_comment_id)
        REFERENCES task_comments (id) ON DELETE SET NULL,
    CONSTRAINT fk_notifications_chat_message FOREIGN KEY (chat_message_id)
        REFERENCES chat_messages (id) ON DELETE SET NULL
);

CREATE INDEX idx_notifications_recipient_created
    ON notifications (recipient_id, created_at DESC, id DESC);
CREATE INDEX idx_notifications_unread
    ON notifications (recipient_id, created_at DESC, id DESC)
    WHERE is_read = FALSE;

CREATE TABLE activity_records (
    id UUID PRIMARY KEY,
    event_id UUID NOT NULL UNIQUE,
    actor_id UUID,
    activity_type VARCHAR(40) NOT NULL,
    description VARCHAR(500) NOT NULL,
    workspace_id UUID NOT NULL,
    project_id UUID,
    task_id UUID,
    created_at TIMESTAMP WITHOUT TIME ZONE NOT NULL,
    CONSTRAINT chk_activity_type CHECK (activity_type IN (
        'WORKSPACE_CREATED', 'WORKSPACE_UPDATED', 'WORKSPACE_MEMBER_ADDED',
        'PROJECT_CREATED', 'PROJECT_UPDATED', 'PROJECT_MEMBER_ADDED',
        'TASK_CREATED', 'TASK_UPDATED', 'TASK_ASSIGNED', 'TASK_REASSIGNED', 'TASK_STATUS_CHANGED',
        'TASK_PRIORITY_CHANGED', 'TASK_COMMENT_ADDED',
        'CHAT_MESSAGE_SENT', 'CHAT_MESSAGE_EDITED', 'CHAT_MESSAGE_DELETED'
    )),
    CONSTRAINT chk_activity_description CHECK (length(trim(description)) > 0),
    CONSTRAINT fk_activity_actor FOREIGN KEY (actor_id)
        REFERENCES users (id) ON DELETE SET NULL,
    CONSTRAINT fk_activity_workspace FOREIGN KEY (workspace_id)
        REFERENCES workspaces (id) ON DELETE CASCADE,
    CONSTRAINT fk_activity_project FOREIGN KEY (project_id)
        REFERENCES projects (id) ON DELETE CASCADE,
    CONSTRAINT fk_activity_task FOREIGN KEY (task_id)
        REFERENCES tasks (id) ON DELETE SET NULL
);

CREATE INDEX idx_activity_workspace_created
    ON activity_records (workspace_id, created_at DESC, id DESC);
CREATE INDEX idx_activity_project_created
    ON activity_records (project_id, created_at DESC, id DESC);
CREATE INDEX idx_activity_task_created
    ON activity_records (task_id, created_at DESC, id DESC);
