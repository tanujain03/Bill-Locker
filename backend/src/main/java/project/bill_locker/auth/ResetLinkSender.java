package project.bill_locker.auth;

/**
 * Delivers a password reset link to the account owner: {@link EmailResetLinkSender}
 * (SMTP) or {@link LogResetLinkSender} (backend log), chosen by {@link ResetLinkSenderConfig}.
 * {@link PasswordResetService} doesn't know or care which one it gets.
 */
public interface ResetLinkSender {

	void send(String email, String resetLink);
}
