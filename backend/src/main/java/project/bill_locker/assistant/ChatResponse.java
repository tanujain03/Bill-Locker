package project.bill_locker.assistant;

/** The answer, plus the SQL that was run for it (null when no query ran), for "Show query". */
public record ChatResponse(String answer, String sql) {
}
