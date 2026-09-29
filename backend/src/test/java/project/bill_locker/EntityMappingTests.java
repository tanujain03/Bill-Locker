package project.bill_locker;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceException;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import project.bill_locker.ai.ChatCitation;
import project.bill_locker.ai.ChatMessage;
import project.bill_locker.ai.ChatRole;
import project.bill_locker.ai.ChatSession;
import project.bill_locker.document.Document;
import project.bill_locker.document.DocumentType;
import project.bill_locker.document.ExtractionResult;
import project.bill_locker.document.ProcessingStatus;
import project.bill_locker.gmail.GmailAttachment;
import project.bill_locker.gmail.GmailMessage;
import project.bill_locker.notification.Notification;
import project.bill_locker.notification.NotificationType;
import project.bill_locker.product.Category;
import project.bill_locker.product.CategoryRepository;
import project.bill_locker.product.Product;
import project.bill_locker.rag.DocumentChunk;
import project.bill_locker.service.ServiceRecord;
import project.bill_locker.service.ServiceType;
import project.bill_locker.user.User;
import project.bill_locker.warranty.Warranty;
import project.bill_locker.warranty.WarrantyStatus;
import project.bill_locker.warranty.WarrantyType;

/** Verifies the entity design against a real PostgreSQL + pgvector (Testcontainers). */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Transactional
class EntityMappingTests {

	private static final LocalDate TODAY = LocalDate.of(2026, 9, 29);

	@Autowired
	private EntityManager em;

	@Autowired
	private JdbcTemplate jdbc;

	@Autowired
	private CategoryRepository categories;

	@Test
	void createsEveryTableFromTheEntities() {
		List<String> tables = jdbc.queryForList(
				"select table_name from information_schema.tables where table_schema = 'public'", String.class);

		assertThat(tables).contains("users", "categories", "products", "documents", "warranties", "service_records",
				"notifications", "document_chunks", "chat_sessions", "chat_messages", "gmail_connections",
				"gmail_messages", "gmail_oauth_states");
		assertThat(columnType("products", "id")).isEqualTo("uuid");
		assertThat(columnType("documents", "extraction")).isEqualTo("jsonb");
		assertThat(columnType("document_chunks", "embedding")).isEqualTo("vector");
	}

	@Test
	void seedsTheStandardCategories() {
		assertThat(categories.findAll())
				.extracting(Category::getSlug)
				.hasSize(9)
				.contains("mobile-phones", "computers", "home-appliances", "kitchen", "other");
	}

	@Test
	void savesAProductWithItsWarrantyAndServiceHistory() {
		User user = persistUser("asha@example.com");
		Product laptop = new Product(user, "Dell Inspiron 15 3530 Laptop");
		laptop.setCategory(categories.findBySlug("computers").orElseThrow());
		laptop.setPurchaseDate(LocalDate.of(2026, 9, 15));
		laptop.setPurchasePrice(new BigDecimal("62990.00"));
		laptop.addWarranty(new Warranty(WarrantyType.STANDARD, 24, laptop.getPurchaseDate()));
		ServiceRecord installation = new ServiceRecord(LocalDate.of(2026, 9, 20), ServiceType.INSTALLATION);
		installation.setNextServiceDate(LocalDate.of(2027, 3, 20));
		laptop.addServiceRecord(installation);
		em.persist(laptop);
		em.flush();
		em.clear();

		Product saved = em.find(Product.class, laptop.getId());
		Warranty warranty = saved.headlineWarranty().orElseThrow();
		assertThat(warranty.getExpiryDate()).isEqualTo(LocalDate.of(2028, 9, 14));
		assertThat(warranty.statusOn(TODAY)).isEqualTo(WarrantyStatus.ACTIVE);
		assertThat(saved.getServiceRecords()).hasSize(1);
		assertThat(saved.getCategory().getSlug()).isEqualTo("computers");
		assertThat(saved.getCreatedAt()).isNotNull();
		assertThat(saved.getUpdatedAt()).isNotNull();
		assertThat(saved.getUser().getEmail()).isEqualTo("asha@example.com");
	}

	@Test
	void keepsTheWarrantyExpiryInSyncWithTheRule() {
		Warranty warranty = new Warranty(WarrantyType.STANDARD, 1, LocalDate.of(2026, 1, 31));
		assertThat(warranty.getExpiryDate()).isEqualTo(LocalDate.of(2026, 2, 27));

		warranty.setWarrantyMonths(12);
		assertThat(warranty.getExpiryDate()).isEqualTo(LocalDate.of(2027, 1, 30));

		warranty.setWarrantyMonths(null);
		assertThat(warranty.getExpiryDate()).isNull();
		assertThat(warranty.statusOn(TODAY)).isEqualTo(WarrantyStatus.UNKNOWN);
	}

	@Test
	void decidesTodayInTheUsersTimeZone() {
		User user = new User("Test User", "zone@example.com", "$2a$10$notARealHashJustForMappingTests");
		Clock halfPastMidnightInIndia = Clock.fixed(Instant.parse("2026-09-29T19:00:00Z"), ZoneOffset.UTC);

		assertThat(user.today(halfPastMidnightInIndia)).isEqualTo(LocalDate.of(2026, 9, 30));
	}

	@Test
	void componentCoverNeverBecomesTheHeadlineWarranty() {
		User user = persistUser("fridge@example.com");
		Product fridge = new Product(user, "Samsung Refrigerator");
		fridge.addWarranty(new Warranty(WarrantyType.STANDARD, 12, LocalDate.of(2025, 1, 1)));
		Warranty compressor = new Warranty(WarrantyType.COMPONENT, 240, LocalDate.of(2025, 1, 1));
		compressor.setCoverageNote("Digital inverter compressor");
		fridge.addWarranty(compressor);
		em.persist(fridge);

		Warranty headline = fridge.headlineWarranty().orElseThrow();
		assertThat(headline.getWarrantyType()).isEqualTo(WarrantyType.STANDARD);
		assertThat(headline.statusOn(TODAY)).isEqualTo(WarrantyStatus.EXPIRED);
	}

	@Test
	void storesTheAiExtractionAsJson() {
		User user = persistUser("json@example.com");
		Document document = new Document(user, "Philips_Air_Fryer_Invoice.pdf", "users/json/air-fryer.pdf",
				"application/pdf", 73_402);
		ExtractionResult extraction = new ExtractionResult(DocumentType.INVOICE, "Philips Air Fryer HD9252/90", "Philips",
				"HD9252/90", null, "2026-09-28", new BigDecimal("8999"), "INR", "Metro Electronics",
				"ME/2026-27/006745", 24, "kitchen",
				Map.of("productName", 0.96, "serialNumber", 0.0, "warrantyMonths", 0.74));
		document.markReadyForReview(extraction, "TAX INVOICE\nPhilips Air Fryer", "gemini-test");
		em.persist(document);
		em.flush();
		em.clear();

		Document saved = em.find(Document.class, document.getId());
		assertThat(saved.getExtraction()).isEqualTo(extraction);
		assertThat(saved.getExtraction().serialNumber()).isNull();
		assertThat(saved.getProcessingStatus()).isEqualTo(ProcessingStatus.REVIEW_REQUIRED);
	}

	@Test
	void deletingAProductKeepsItsBills() {
		User user = persistUser("keep@example.com");
		Product tv = new Product(user, "Sony Bravia 55 TV");
		tv.addWarranty(new Warranty(WarrantyType.STANDARD, 12, LocalDate.of(2026, 1, 10)));
		em.persist(tv);
		Document invoice = persistDocument(user, "sony-invoice.pdf");
		invoice.confirm(tv, DocumentType.INVOICE);
		em.flush();
		em.clear();

		// The bill is already loaded in this transaction, as it would be in a service.
		Document loadedInvoice = em.find(Document.class, invoice.getId());
		em.remove(em.find(Product.class, tv.getId()));
		em.flush();
		em.clear();

		Document stillThere = em.find(Document.class, loadedInvoice.getId());
		assertThat(stillThere.getProduct()).isNull();
		assertThat(stillThere.getProcessingStatus()).isEqualTo(ProcessingStatus.CONFIRMED);
		assertThat(em.find(Product.class, tv.getId())).isNull();
		assertThat(jdbc.queryForObject("select count(*) from warranties where product_id = ?", Long.class, tv.getId()))
				.isZero();
	}

	@Test
	void theDatabaseEnforcesTheDeleteRules() {
		assertThat(deleteRule("fk_products_user")).isEqualTo("CASCADE");
		assertThat(deleteRule("fk_warranties_product")).isEqualTo("CASCADE");
		assertThat(deleteRule("fk_document_chunks_document")).isEqualTo("CASCADE");
		assertThat(deleteRule("fk_documents_product")).isEqualTo("SET NULL");
		assertThat(deleteRule("fk_documents_gmail_message")).isEqualTo("SET NULL");
		assertThat(deleteRule("fk_warranties_source_document")).isEqualTo("SET NULL");
		assertThat(deleteRule("fk_products_category")).isEqualTo("SET NULL");
	}

	@Test
	void deletingAUserRemovesAllTheirData() {
		User user = persistUser("gone@example.com");
		Product phone = new Product(user, "Apple iPhone 15");
		phone.addWarranty(new Warranty(WarrantyType.STANDARD, 12, LocalDate.of(2026, 6, 1)));
		em.persist(phone);
		persistDocument(user, "iphone.pdf");
		em.persist(new Notification(user, NotificationType.WARRANTY_EXPIRING, "Warranty expiring soon", "Soon."));
		em.flush();
		em.clear();

		jdbc.update("delete from users where id = ?", user.getId());

		assertThat(countByUser("products", user)).isZero();
		assertThat(countByUser("documents", user)).isZero();
		assertThat(countByUser("notifications", user)).isZero();
		assertThat(jdbc.queryForObject("select count(*) from warranties where product_id = ?", Long.class, phone.getId()))
				.isZero();
	}

	@Test
	void findsSimilarChunksOnlyForTheSameUser() {
		User asha = persistUser("asha.rag@example.com");
		User bob = persistUser("bob.rag@example.com");
		Document ashaInvoice = persistDocument(asha, "dell-invoice.pdf");
		Document ashaCard = persistDocument(asha, "samsung-card.pdf");
		Document bobInvoice = persistDocument(bob, "bob-dell-invoice.pdf");
		em.persist(new DocumentChunk(ashaInvoice, 0, "Dell Inspiron invoice", vector(1f), "test-model"));
		em.persist(new DocumentChunk(ashaCard, 0, "Samsung warranty card", vector(-1f), "test-model"));
		em.persist(new DocumentChunk(bobInvoice, 0, "Bob's Dell invoice", vector(1f), "test-model"));
		em.flush();

		List<DocumentChunk> results = em.createQuery(
						"select c from DocumentChunk c where c.user = :user order by cosine_distance(c.embedding, :query)",
						DocumentChunk.class)
				.setParameter("user", asha)
				.setParameter("query", vector(1f))
				.setMaxResults(5)
				.getResultList();

		assertThat(results).extracting(DocumentChunk::getChunkText)
				.containsExactly("Dell Inspiron invoice", "Samsung warranty card");
	}

	@Test
	void theSameReminderCannotBeStoredTwice() {
		User user = persistUser("dedupe@example.com");
		Notification first = new Notification(user, NotificationType.SERVICE_DUE, "Service due soon", "AC service in 7 days.");
		first.setDedupeKey("SERVICE_DUE:record-1:2026-10-06");
		em.persist(first);
		em.flush();

		Notification again = new Notification(user, NotificationType.SERVICE_DUE, "Service due soon", "AC service in 7 days.");
		again.setDedupeKey("SERVICE_DUE:record-1:2026-10-06");
		em.persist(again);

		assertThatThrownBy(em::flush).isInstanceOf(PersistenceException.class);
	}

	@Test
	void storesChatCitationsAndGmailAttachmentsAsJson() {
		User user = persistUser("chat@example.com");
		ChatSession session = new ChatSession(user, "Warranty questions");
		ChatMessage answer = session.addMessage(new ChatMessage(ChatRole.ASSISTANT, "Yes, your laptop is covered."));
		answer.setCitations(List.of(new ChatCitation(ChatCitation.Type.PRODUCT, UUID.randomUUID(), "Dell Inspiron", "Expires in 24 days")));
		em.persist(session);

		GmailMessage email = new GmailMessage(user, "18c2f0a9", "auto-confirm@amazon.in", Instant.parse("2026-09-27T09:12:00Z"));
		email.setAttachments(List.of(new GmailAttachment("Invoice.pdf", "application/pdf", 84_213, "att-1")));
		email.setConfidence(new BigDecimal("0.970"));
		em.persist(email);
		em.flush();
		em.clear();

		ChatSession savedSession = em.find(ChatSession.class, session.getId());
		assertThat(savedSession.getMessages()).singleElement()
				.satisfies(message -> assertThat(message.getCitations()).hasSize(1));
		GmailMessage savedEmail = em.find(GmailMessage.class, email.getId());
		assertThat(savedEmail.getAttachments())
				.containsExactly(new GmailAttachment("Invoice.pdf", "application/pdf", 84_213, "att-1"));
	}

	private User persistUser(String email) {
		User user = new User("Test User", email, "$2a$10$notARealHashJustForMappingTests");
		em.persist(user);
		return user;
	}

	private Document persistDocument(User user, String fileName) {
		Document document = new Document(user, fileName, "users/" + user.getId() + "/" + fileName, "application/pdf", 1_000);
		em.persist(document);
		return document;
	}

	private String columnType(String table, String column) {
		return jdbc.queryForObject(
				"select udt_name from information_schema.columns where table_name = ? and column_name = ?",
				String.class, table, column);
	}

	private String deleteRule(String foreignKey) {
		return jdbc.queryForObject(
				"select delete_rule from information_schema.referential_constraints where constraint_name = ?",
				String.class, foreignKey);
	}

	private long countByUser(String table, User user) {
		return jdbc.queryForObject("select count(*) from " + table + " where user_id = ?", Long.class, user.getId());
	}

	/** A deterministic 768-dimension vector; sign -1 points the opposite way. */
	private static float[] vector(float sign) {
		float[] values = new float[DocumentChunk.EMBEDDING_DIMENSIONS];
		for (int i = 0; i < values.length; i++) {
			values[i] = sign * ((i % 7) + 1) / 10f;
		}
		return values;
	}
}
