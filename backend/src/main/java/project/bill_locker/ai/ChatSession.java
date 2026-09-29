package project.bill_locker.ai;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;
import project.bill_locker.common.AuditableEntity;
import project.bill_locker.user.User;

/** One AI assistant conversation; updatedAt changes with every new message. */
@Entity
@Table(name = "chat_sessions", indexes = @Index(name = "idx_chat_sessions_user_updated", columnList = "user_id, updated_at DESC"))
@Getter
@Setter
@NoArgsConstructor
public class ChatSession extends AuditableEntity {

	@NotNull
	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "user_id", nullable = false, foreignKey = @ForeignKey(name = "fk_chat_sessions_user"))
	@OnDelete(action = OnDeleteAction.CASCADE)
	private User user;

	@Size(max = 120)
	@Column(name = "title", length = 120)
	private String title;

	@OneToMany(mappedBy = "session", cascade = CascadeType.ALL, orphanRemoval = true)
	@OrderBy("createdAt ASC")
	private List<ChatMessage> messages = new ArrayList<>();

	public ChatSession(User user, String title) {
		this.user = user;
		this.title = title;
	}

	public ChatMessage addMessage(ChatMessage message) {
		messages.add(message);
		message.setSession(this);
		return message;
	}
}
