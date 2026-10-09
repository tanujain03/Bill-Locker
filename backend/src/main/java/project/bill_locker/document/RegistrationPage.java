package project.bill_locker.document;

/**
 * The answer: the page to open in a new tab. WEB_SEARCH = the brand's official page,
 * found and checked to open; SEARCH = nothing reliable found, so a Google search.
 */
public record RegistrationPage(String url, RegistrationSource source) {
}
