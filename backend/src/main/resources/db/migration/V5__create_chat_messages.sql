CREATE TABLE chat_messages (
    id UUID PRIMARY KEY,
    project_id UUID NOT NULL,
    sender_id UUID NOT NULL,
    content VARCHAR(4000) NOT NULL,
    created_at TIMESTAMP WITHOUT TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITHOUT TIME ZONE NOT NULL,
    edited_at TIMESTAMP WITHOUT TIME ZONE,
    deleted_at TIMESTAMP WITHOUT TIME ZONE,
    CONSTRAINT chk_chat_messages_content
        CHECK (length(trim(content)) > 0 OR deleted_at IS NOT NULL),
    CONSTRAINT fk_chat_messages_project FOREIGN KEY (project_id)
        REFERENCES projects (id) ON DELETE CASCADE,
    CONSTRAINT fk_chat_messages_sender FOREIGN KEY (sender_id)
        REFERENCES users (id) ON DELETE RESTRICT
);

CREATE INDEX idx_chat_messages_project_created
    ON chat_messages (project_id, created_at, id);
CREATE INDEX idx_chat_messages_sender
    ON chat_messages (sender_id);
