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
    private final project.bill_locker.document.DocumentRepository bills;
    private final project.bill_locker.servicerecord.ServiceRecordService services;

	public SampleDataService(DocumentService documents, Clock clock,
            project.bill_locker.document.DocumentRepository bills,
            project.bill_locker.servicerecord.ServiceRecordService services) {
		this.documents = documents;
		this.clock = clock;
        this.bills = bills; this.services = services;
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
        addServices(userId);
		return status(userId);
	}

    private void addServices(UUID userId) {
        LocalDate today = LocalDate.now(clock);
        for (var bill : bills.findSavedWithItems(userId)) {
            if (!bill.isSample()) continue;
            for (var item : bill.getItems()) {
                String name = item.getProductName();
                if (name == null) continue;
                project.bill_locker.servicerecord.ServiceType type;
                String title; String provider; java.math.BigDecimal cost; LocalDate date; LocalDate due = null;
                if (name.startsWith("LG 260")) {
                    type=project.bill_locker.servicerecord.ServiceType.ROUTINE_SERVICE; title="Condenser cleaning and cooling check";
                    provider="LG Authorised Service"; cost=new java.math.BigDecimal("650"); date=today.minusDays(20); due=today.plusDays(12);
                } else if (name.startsWith("Dell Inspiron")) {
                    type=project.bill_locker.servicerecord.ServiceType.ROUTINE_SERVICE; title="Fan cleaning and thermal inspection";
                    provider="Dell Service Centre"; cost=new java.math.BigDecimal("900"); date=today.minusDays(60); due=today.minusDays(5);
                } else if (name.startsWith("Samsung Galaxy")) {
                    type=project.bill_locker.servicerecord.ServiceType.WARRANTY_CLAIM; title="Charging port repaired under warranty";
                    provider="Samsung Service Centre"; cost=java.math.BigDecimal.ZERO; date=today.minusDays(8);
                } else if (name.startsWith("boAt Rockerz")) {
                    type=project.bill_locker.servicerecord.ServiceType.PART_REPLACED; title="Ear cushions replaced";
                    provider="Audio Care"; cost=new java.math.BigDecimal("450"); date=today.minusDays(10);
                } else continue;
                boolean claim=type==project.bill_locker.servicerecord.ServiceType.WARRANTY_CLAIM;
                services.create(userId,bill.getId(),item.getPosition(),item.getId(),
                    new project.bill_locker.servicerecord.ServiceRecordRequest(type,title,"Sample service record",date,provider,
                    null,cost,claim,claim ? "SAMPLE-CLM-1042" : null,
                    claim ? project.bill_locker.servicerecord.ClaimStatus.RESOLVED : null,due,null,null));
            }
        }
    }

	/** Returns how many sample bills were removed (0 when there were none). */
	@Transactional
	public int remove(UUID userId) {
		return documents.deleteSamples(userId);
	}
}
