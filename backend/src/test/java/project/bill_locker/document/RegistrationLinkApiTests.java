package project.bill_locker.document;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import project.bill_locker.FakeDetailExtractor;
import project.bill_locker.FakeRegistrationFinder;

/** Task 5: warranty registration links — from the bill, from its QR code, or (brand confirmed by the user) found on the web. */
class RegistrationLinkApiTests extends DocumentApiTestBase {

	@Autowired
	FakeDetailExtractor ai;

	@Autowired
	FakeRegistrationFinder web;

	@AfterEach
	void resetFakes() {
		ai.reset();
		web.reset();
	}

	private ResultActions findPage(String token, String id, String json) throws Exception {
		return mvc.perform(post("/api/documents/" + id + "/registration-page").header("Authorization", bearer(token))
				.contentType(MediaType.APPLICATION_JSON).content(json));
	}

	@Test
	void aLinkPrintedOnTheBillIsKeptAsDocument() throws Exception {
		String token = registerAndGetToken(uniqueEmail("printed"));
		String id = uploadPdf(token, "bill.pdf");
		ai.willReturn(billWith(new DocumentItemView("Phone", null, null, null, 12, null, null, null, "Samsung",
				"https://www.samsung.com/in/support/register", null)));

		mvc.perform(post("/api/documents/" + id + "/extract").header("Authorization", bearer(token)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.items[0].brand").value("Samsung"))
				.andExpect(jsonPath("$.items[0].registrationUrl").value("https://www.samsung.com/in/support/register"))
				.andExpect(jsonPath("$.items[0].registrationSource").value("DOCUMENT"));
	}

	@Test
	void aQrCodeOnTheBillGivesTheLink() throws Exception {
		String token = registerAndGetToken(uniqueEmail("qr"));
		String link = "https://www.gonoise.com/pages/warranty-registration?ref=bill";
		String response = upload(token, "bill.png", "image/png", qrPng(link))
				.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
		String id = com.jayway.jsonpath.JsonPath.read(response, "$.id");
		// The AI didn't copy the link itself: the decoded QR link still reaches the product.
		ai.willReturn(billWith(new DocumentItemView("Noise Buds", null, null, null, 12, null, null, null, "Noise", null, null)));

		mvc.perform(post("/api/documents/" + id + "/extract").header("Authorization", bearer(token)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.items[0].registrationUrl").value(link))
				.andExpect(jsonPath("$.items[0].registrationSource").value("QR_CODE"));
	}

	@Test
	void confirmedBrandOpensItsOfficialPageAndKeepsIt() throws Exception {
		String token = registerAndGetToken(uniqueEmail("confirm"));
		String id = saveBill(token, """
				{"documentType": "INVOICE", "items": [{"productName": "Noise Buds VS104", "brand": "Noise"}]}
				""");

		findPage(token, id, """
				{"position": 0, "brand": "Noise"}
				""")
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.url").value("https://noise.example/warranty-registration"))
				.andExpect(jsonPath("$.source").value("WEB_SEARCH"));
		assertThat(web.searchedBrands()).containsExactly("Noise");

		// Kept on the product: next time it's simply a link.
		mvc.perform(get("/api/documents/" + id).header("Authorization", bearer(token)))
				.andExpect(jsonPath("$.items[0].registrationUrl").value("https://noise.example/warranty-registration"))
				.andExpect(jsonPath("$.items[0].registrationSource").value("WEB_SEARCH"));
	}

	@Test
	void theUsersCorrectedBrandIsSearched() throws Exception {
		String token = registerAndGetToken(uniqueEmail("corrected"));
		String id = saveBill(token, """
				{"items": [{"productName": "Buds VS104", "brand": "Nois"}]}
				""");

		findPage(token, id, """
				{"position": 0, "brand": " Noise "}
				""").andExpect(jsonPath("$.url").value("https://noise.example/warranty-registration"));
		assertThat(web.searchedBrands()).containsExactly("Noise");
	}

	@Test
	void nothingFoundGivesAGoogleSearch() throws Exception {
		String token = registerAndGetToken(uniqueEmail("nothing"));
		web.findNothing();
		String id = saveBill(token, """
				{"items": [{"productName": "Noise Buds", "brand": "Noise"}]}
				""");

		findPage(token, id, """
				{"position": 0, "brand": "Noise"}
				""")
				.andExpect(jsonPath("$.source").value("SEARCH"))
				.andExpect(jsonPath("$.url").value("https://www.google.com/search?q=Noise+warranty+registration"));
	}

	@Test
	void needsABrandAndAnExistingProduct() throws Exception {
		String token = registerAndGetToken(uniqueEmail("checks"));
		String id = saveBill(token, """
				{"items": [{"productName": "Noise Buds"}]}
				""");

		findPage(token, id, """
				{"position": 0, "brand": "  "}
				""")
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.fieldErrors.brand").exists());
		findPage(token, id, """
				{"position": 5, "brand": "Noise"}
				""")
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("ITEM_NOT_FOUND"));
		assertThat(web.searchedBrands()).isEmpty();
	}

	@Test
	void savedLinksMustBeWebLinksAndTypedOnesAreTheUsers() throws Exception {
		String token = registerAndGetToken(uniqueEmail("typed"));
		String id = uploadPdf(token, "bill.pdf");

		save(token, id, """
				{"items": [{"productName": "TV", "registrationUrl": "javascript:alert(1)"}]}
				""")
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.fieldErrors['items[0].registrationUrl']").exists());

		save(token, id, """
				{"items": [{"productName": "TV", "registrationUrl": "https://lg.example/register"}]}
				""")
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.items[0].registrationSource").value("USER"));
	}

	@Test
	void anotherUsersDocumentIsNotFound() throws Exception {
		String owner = registerAndGetToken(uniqueEmail("owner"));
		String id = saveBill(owner, """
				{"items": [{"productName": "Noise Buds", "brand": "Noise"}]}
				""");
		findPage(registerAndGetToken(uniqueEmail("other")), id, """
				{"position": 0, "brand": "Noise"}
				""").andExpect(status().isNotFound());
		assertThat(web.searchedBrands()).isEmpty();
	}

	private ResultActions save(String token, String id, String json) throws Exception {
		return mvc.perform(put("/api/documents/" + id).header("Authorization", bearer(token))
				.contentType(MediaType.APPLICATION_JSON).content(json));
	}

	private static DocumentDetails billWith(DocumentItemView item) {
		return new DocumentDetails(DocumentType.INVOICE, "INV-5", "Croma", null, null, null, null, null,
				LocalDate.of(2026, 9, 1), null, new BigDecimal("2499.00"), List.of(item));
	}

	/** A PNG "photo of a bill" holding one QR code. */
	static byte[] qrPng(String text) throws Exception {
		BitMatrix matrix = new QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, 400, 400);
		BufferedImage image = new BufferedImage(matrix.getWidth(), matrix.getHeight(), BufferedImage.TYPE_INT_RGB);
		for (int x = 0; x < matrix.getWidth(); x++) {
			for (int y = 0; y < matrix.getHeight(); y++) {
				image.setRGB(x, y, matrix.get(x, y) ? 0x000000 : 0xFFFFFF);
			}
		}
		ByteArrayOutputStream png = new ByteArrayOutputStream();
		ImageIO.write(image, "png", png);
		return png.toByteArray();
	}
}
