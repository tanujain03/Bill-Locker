package project.bill_locker.document.registration;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;

/** The small parts of task 5, without Spring and without going online. */
class RegistrationPartsTests {

	private final QrCodeReader qr = new QrCodeReader();

	@Test
	void qrReaderFindsWebLinksOnly() throws Exception {
		assertThat(qr.links(png("https://www.gonoise.com/pages/warranty"), "image/png"))
				.containsExactly("https://www.gonoise.com/pages/warranty");
		// A UPI payment code isn't a page to open.
		assertThat(qr.links(png("upi://pay?pa=shop@upi&am=499"), "image/png")).isEmpty();
	}

	@Test
	void qrReaderNeverFails() {
		assertThat(qr.links("not an image".getBytes(), "image/png")).isEmpty();
		assertThat(qr.links("%PDF-1.4 broken".getBytes(), "application/pdf")).isEmpty();
	}

	@Test
	void linkCheckerOnlyAllowsPublicHttps() {
		assertThat(LinkChecker.isPublicHttps(URI.create("http://example.com/"))).isFalse(); // not https
		assertThat(LinkChecker.isPublicHttps(URI.create("https://localhost/admin"))).isFalse();
		assertThat(LinkChecker.isPublicHttps(URI.create("https://127.0.0.1/"))).isFalse();
		assertThat(LinkChecker.isPublicHttps(URI.create("https://10.1.2.3/"))).isFalse();
		assertThat(LinkChecker.isPublicHttps(URI.create("https://192.168.1.1/"))).isFalse();
		assertThat(LinkChecker.isPublicHttps(URI.create("https://169.254.169.254/latest/meta-data"))).isFalse(); // cloud metadata
		assertThat(LinkChecker.isPublicHttps(URI.create("https://[::1]/"))).isFalse();
		assertThat(LinkChecker.isPublicHttps(URI.create("https://8.8.8.8/"))).isTrue(); // a public address (no lookup needed)
	}

	@Test
	void linkCheckerRefusesBeforeConnecting() {
		assertThat(new LinkChecker().opens("https://127.0.0.1:8080/api/users")).isEmpty();
		assertThat(new LinkChecker().opens("javascript:alert(1)")).isEmpty();
	}

	@Test
	void searchAnswerLinksPreferTheBrandsDomain() {
		String answer = """
				The registration page is on Amazon: https://www.amazon.in/noise-buds,
				but the official one is **https://www.gonoise.com/pages/warranty-registration**.
				Source: https://vertexaisearch.cloud.google.com/grounding-api-redirect/abc
				""";
		assertThat(GeminiRegistrationFinder.candidates(answer, "Noise")).containsExactly(
				"https://www.gonoise.com/pages/warranty-registration", "https://www.amazon.in/noise-buds");
	}

	private static byte[] png(String text) throws Exception {
		BitMatrix matrix = new QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, 300, 300);
		BufferedImage image = new BufferedImage(matrix.getWidth(), matrix.getHeight(), BufferedImage.TYPE_INT_RGB);
		for (int x = 0; x < matrix.getWidth(); x++) {
			for (int y = 0; y < matrix.getHeight(); y++) {
				image.setRGB(x, y, matrix.get(x, y) ? 0x000000 : 0xFFFFFF);
			}
		}
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		ImageIO.write(image, "png", out);
		return out.toByteArray();
	}
}
