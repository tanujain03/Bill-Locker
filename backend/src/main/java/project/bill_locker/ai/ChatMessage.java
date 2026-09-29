package project.bill_locker.ai;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;
import org.hibernate.type.SqlTypes;
import project.bill_locker.common.BaseEntity;

/** One turn of a conversation. AI metrics are filled for assistant messages only. */
@Entity
@Table(name = "chat_messages", indexes = @Index(name = "idx_chat_messages_session_created", columnList = "session_id, created_at"))
@Getter
@Setter
@NoArgsConstructor
public class ChatMessage extends BaseEntity {

	@NotNull
	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "session_id", nullable = false, foreignKey = @ForeignKey(name = "fk_chat_messages_session"))
	@OnDelete(action = OnDeleteAction.CASCADE)
	private ChatSession session;

	@NotNull
	@Enumerated(EnumType.STRING)
	@Column(name = "role", nullable = false, length = 10)
	private ChatRole role;

	@NotBlank
	@Size(max = 20000)
	@Column(name = "content", nullable = false, columnDefinition = "text")
	private String content;

	/** Products/documents the answer used (stored as jsonb). */
	@JdbcTypeCode(SqlTypes.JSON)
	@Column(name = "citations")
	private List<ChatCitation> citations = new ArrayList<>();

	@Size(max = 100)
	@Column(name = "ai_model", length = 100)
	private String aiModel;

	@Column(name = "prompt_tokens")
	private Integer promptTokens;

	@Column(name = "completion_tokens")
	private Integer completionTokens;

	@Column(name = "latency_ms")
	private Integer latencyMs;

	public ChatMessage(ChatRole role, String content) {
		this.role = role;
		this.content = content;
	}
}
