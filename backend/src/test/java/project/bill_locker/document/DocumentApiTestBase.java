package project.bill_locker.document;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.nio.charset.StandardCharsets;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.ResultActions;
import project.bill_locker.ApiTest;

/** Helpers shared by the document API tests. */
public abstract class DocumentApiTestBase extends ApiTest {

	/** The smallest "file" our type check accepts as a PDF: it starts with %PDF. */
	protected static byte[] pdfBytes() {
		return "%PDF-1.4\n%test\n".getBytes(StandardCharsets.US_ASCII);
	}

	protected ResultActions upload(String token, String fileName, String contentType, byte[] bytes) throws Exception {
		return mvc.perform(multipart("/api/documents/upload")
				.file(new MockMultipartFile("file", fileName, contentType, bytes))
				.header("Authorization", bearer(token)));
	}

	/** Uploads a small PDF and returns the new document's id. */
	protected String uploadPdf(String token, String fileName) throws Exception {
		String response = upload(token, fileName, "application/pdf", pdfBytes())
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();
		return JsonPath.read(response, "$.id");
	}

	/** Uploads a PDF and saves these details on it (status SAVED); returns the document id. */
	protected String saveBill(String token, String detailsJson) throws Exception {
		String id = uploadPdf(token, "bill.pdf");
		mvc.perform(put("/api/documents/" + id)
						.header("Authorization", bearer(token))
						.contentType(MediaType.APPLICATION_JSON)
						.content(detailsJson))
				.andExpect(status().isOk());
		return id;
	}
}
