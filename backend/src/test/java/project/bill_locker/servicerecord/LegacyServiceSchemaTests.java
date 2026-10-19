package project.bill_locker.servicerecord;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import project.bill_locker.document.DocumentApiTestBase;

/** The fixture creates a populated legacy service_records table before Hibernate starts. */
class LegacyServiceSchemaTests extends DocumentApiTestBase {
	@Autowired JdbcTemplate jdbc;

	@Test
	void legacyRowsRemainIntactAndNewServicesUseTheirOwnTable() throws Exception {
		String token = registerAndGetToken(uniqueEmail("legacy-service-schema"));
		String document = saveBill(token, "{\"items\":[{\"productName\":\"Phone\"}]}");
		mvc.perform(post("/api/documents/" + document + "/items/0/services")
				.header("Authorization", bearer(token)).contentType(MediaType.APPLICATION_JSON)
				.content("{\"type\":\"REPAIR\",\"title\":\"Port repaired\",\"serviceDate\":\"2026-10-01\"}"))
				.andExpect(status().isCreated());
		mvc.perform(get("/api/services").header("Authorization", bearer(token)))
				.andExpect(status().isOk()).andExpect(jsonPath("$[0].title").value("Port repaired"));
		assertThat(jdbc.queryForObject("SELECT legacy_note FROM service_records WHERE id='00000000-0000-0000-0000-000000000015'", String.class))
				.isEqualTo("Old service record retained");
		assertThat(jdbc.queryForList("""
				SELECT column_name FROM information_schema.columns
				WHERE table_schema='public' AND table_name='service_records'
				""", String.class)).containsExactlyInAnyOrder("id", "legacy_note");
		assertThat(jdbc.queryForObject("""
				SELECT has_table_privilege('bill_locker_ai', 'product_service_records', 'SELECT')
				""", Boolean.class)).isFalse();
	}
}
