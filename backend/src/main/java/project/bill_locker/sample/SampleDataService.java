package project.bill_locker.sample;

import java.time.Clock;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import project.bill_locker.common.ApiException;
import project.bill_locker.document.DocumentService;

/**
 * "Try with sample data": fills the account with demo bills, and removes them again.
 * Only bills marked as sample are ever removed; the user's own bills are never touched.
 */
@Service
public class SampleDataService {

	private final DocumentService documents;
	private final Clock clock;

	public SampleDataService(DocumentService documents, Clock clock) {
		this.documents = documents;
		this.clock = clock;
	}

	public SampleDataStatus status(UUID userId) {
		return new SampleDataStatus(documents.countSamples(userId));
	}

	/** All bills in one transaction: either the whole set is added, or none. Twice → 409. */
	@Transactional
	public SampleDataStatus add(UUID userId) {
		if (documents.countSamples(userId) > 0) {
			throw new ApiException(HttpStatus.CONFLICT, "SAMPLE_DATA_EXISTS",
					"The sample data is already in your account. Remove it first to add it again.");
		}
		for (SampleBills.SampleBill bill : SampleBills.forToday(LocalDate.now(clock))) {
			documents.createSample(userId, bill.fileName(), SamplePdf.render(bill.details()), bill.details());
		}
		return status(userId);
	}

	/** Returns how many sample bills were removed (0 when there were none). */
	@Transactional
	public int remove(UUID userId) {
		return documents.deleteSamples(userId);
	}
}
