package project.bill_locker.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import project.bill_locker.ApiTest;
import project.bill_locker.TestFiles;
import project.bill_locker.processing.DocumentProcessor;

/** Reminders made by the daily job, the "document ready" notice, and reading them in the app. */
class NotificationApiTests extends ApiTest {

	private static final LocalDate TODAY = LocalDate.now();

	@Autowired
	private ReminderService reminderService;

	@Autowired
	private DocumentProcessor processor;

	@Test
	void theDailyJobRemindsAboutWarrantiesAndServicesOnlyOnce() throws Exception {
		String token = registerAndGetToken(uniqueEmail("reminders"));
		createProduct(token, """
				{"name": "Dell Laptop", "purchaseDate": "%s", "warrantyMonths": 12}
				""".formatted(TODAY.minusMonths(12).plusDays(10))); // ends in about 10 days
		createProduct(token, """
				{"name": "LG Refrigerator", "purchaseDate": "%s", "warrantyMonths": 12}
				""".formatted(TODAY.minusMonths(12).minusDays(20))); // ended about 20 days ago
		createProduct(token, """
				{"name": "Old TV", "purchaseDate": "%s", "warrantyMonths": 12}
				""".formatted(TODAY.minusYears(3))); // ended long ago: no reminder any more
		String acId = createProduct(token, """
				{"name": "Split AC"}
				""");
		mvc.perform(post("/api/service-records")
						.header(AUTHORIZATION, bearer(token))
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"productId": "%s", "serviceDate": "%s", "serviceType": "ROUTINE_MAINTENANCE", "nextServiceDate": "%s"}
								""".formatted(acId, TODAY.minusMonths(6), TODAY.plusDays(3))))
				.andExpect(status().isCreated());

		UUID userId = userId(token);
		assertThat(reminderService.createReminders(userId, TODAY)).isEqualTo(3);
		assertThat(reminderService.createReminders(userId, TODAY)).as("running again adds nothing").isZero();

		mvc.perform(get("/api/notifications").header(AUTHORIZATION, bearer(token)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$", hasSize(3)))
				.andExpect(jsonPath("$[*].type", containsInAnyOrder("WARRANTY_EXPIRING", "WARRANTY_EXPIRED", "SERVICE_DUE")))
				.andExpect(jsonPath("$[?(@.type == 'WARRANTY_EXPIRING')].message",
						containsInAnyOrder(containsString("Your Dell Laptop warranty expires in"))))
				.andExpect(jsonPath("$[?(@.type == 'SERVICE_DUE')].message",
						containsInAnyOrder(containsString("Your Split AC service is due in 3 days"))))
				.andExpect(jsonPath("$[0].read").value(false));
		mvc.perform(get("/api/notifications/unread-count").header(AUTHORIZATION, bearer(token)))
				.andExpect(jsonPath("$.count").value(3));
	}

	@Test
	void notificationsCanBeMarkedRead() throws Exception {
		String token = registerAndGetToken(uniqueEmail("read"));
		createProduct(token, """
				{"name": "Kettle", "purchaseDate": "%s", "warrantyMonths": 12}
				""".formatted(TODAY.minusMonths(12).plusDays(5)));
		createProduct(token, """
				{"name": "Mixer", "purchaseDate": "%s", "warrantyMonths": 12}
				""".formatted(TODAY.minusMonths(12).plusDays(6)));
		reminderService.createReminders(userId(token), TODAY);
		String list = mvc.perform(get("/api/notifications").header(AUTHORIZATION, bearer(token)))
				.andReturn().getResponse().getContentAsString();
		String firstId = JsonPath.read(list, "$[0].id");

		mvc.perform(patch("/api/notifications/" + firstId + "/read").header(AUTHORIZATION, bearer(token)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.read").value(true));
		mvc.perform(get("/api/notifications/unread-count").header(AUTHORIZATION, bearer(token)))
				.andExpect(jsonPath("$.count").value(1));

		mvc.perform(post("/api/notifications/read-all").header(AUTHORIZATION, bearer(token)))
				.andExpect(status().isNoContent());
		mvc.perform(get("/api/notifications/unread-count").header(AUTHORIZATION, bearer(token)))
				.andExpect(jsonPath("$.count").value(0));

		String stranger = registerAndGetToken(uniqueEmail("read-stranger"));
		mvc.perform(patch("/api/notifications/" + firstId + "/read").header(AUTHORIZATION, bearer(stranger)))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("NOTIFICATION_NOT_FOUND"));
	}

	@Test
	void aReadDocumentSendsAReadyForReviewNotification() throws Exception {
		String token = registerAndGetToken(uniqueEmail("ready"));
		String documentId = uploadFile(token, "invoice.pdf", "application/pdf",
				TestFiles.pdfWithText("TAX INVOICE", "Invoice No: ME/2026-27/006745", "Grand Total Rs. 8,999.00"), null);

		processor.processPendingDocuments();

		mvc.perform(get("/api/notifications").header(AUTHORIZATION, bearer(token)))
				.andExpect(jsonPath("$", hasSize(1)))
				.andExpect(jsonPath("$[0].type").value("DOCUMENT_PROCESSED"))
				.andExpect(jsonPath("$[0].documentId").value(documentId))
				.andExpect(jsonPath("$[0].title").value("Document ready for review"))
				.andExpect(jsonPath("$[0].message", containsString("invoice.pdf")));
	}

	private UUID userId(String token) throws Exception {
		String me = mvc.perform(get("/api/auth/me").header(AUTHORIZATION, bearer(token)))
				.andReturn().getResponse().getContentAsString();
		return UUID.fromString(JsonPath.read(me, "$.id"));
	}
}
