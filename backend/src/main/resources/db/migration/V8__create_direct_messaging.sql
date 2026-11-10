CREATE TABLE direct_conversations (
    id UUID PRIMARY KEY,
    workspace_id UUID NOT NULL,
    participant_one_id UUID NOT NULL,
    participant_two_id UUID NOT NULL,
    created_at TIMESTAMP WITHOUT TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITHOUT TIME ZONE NOT NULL,
    CONSTRAINT chk_direct_conversations_distinct_participants
        CHECK (participant_one_id <> participant_two_id),
    CONSTRAINT uk_direct_conversations_pair
        UNIQUE (workspace_id, participant_one_id, participant_two_id),
    CONSTRAINT fk_direct_conversations_workspace
        FOREIGN KEY (workspace_id) REFERENCES workspaces (id) ON DELETE CASCADE,
    CONSTRAINT fk_direct_conversations_participant_one
        FOREIGN KEY (workspace_id, participant_one_id)
        REFERENCES workspace_members (workspace_id, user_id) ON DELETE CASCADE,
    CONSTRAINT fk_direct_conversations_participant_two
        FOREIGN KEY (workspace_id, participant_two_id)
        REFERENCES workspace_members (workspace_id, user_id) ON DELETE CASCADE
);

CREATE INDEX idx_direct_conversations_participant_one
    ON direct_conversations (workspace_id, participant_one_id, updated_at DESC);
CREATE INDEX idx_direct_conversations_participant_two
    ON direct_conversations (workspace_id, participant_two_id, updated_at DESC);

CREATE TABLE direct_messages (
    id UUID PRIMARY KEY,
    conversation_id UUID NOT NULL,
    sender_id UUID NOT NULL,
    content VARCHAR(4000) NOT NULL,
    created_at TIMESTAMP WITHOUT TIME ZONE NOT NULL,
    CONSTRAINT chk_direct_messages_content CHECK (length(trim(content)) > 0),
    CONSTRAINT fk_direct_messages_conversation
        FOREIGN KEY (conversation_id) REFERENCES direct_conversations (id) ON DELETE CASCADE,
    CONSTRAINT fk_direct_messages_sender
        FOREIGN KEY (sender_id) REFERENCES users (id) ON DELETE RESTRICT
);

CREATE INDEX idx_direct_messages_conversation_created
    ON direct_messages (conversation_id, created_at ASC, id ASC);
