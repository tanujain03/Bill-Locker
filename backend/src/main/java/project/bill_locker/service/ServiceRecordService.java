package project.bill_locker.service;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import project.bill_locker.common.ApiException;
import project.bill_locker.product.Product;
import project.bill_locker.product.ProductService;

/** The repair and maintenance history of the signed-in user's products. */
@Service
public class ServiceRecordService {

	private final ServiceRecordRepository records;
	private final ProductService productService;

	public ServiceRecordService(ServiceRecordRepository records, ProductService productService) {
		this.records = records;
		this.productService = productService;
	}

	/** Newest service first; {@code productId} null means the records of all products. */
	@Transactional(readOnly = true)
	public List<ServiceRecordResponse> list(UUID userId, UUID productId) {
		return records.findForUser(userId, productId).stream().map(ServiceRecordResponse::from).toList();
	}

	@Transactional
	public ServiceRecordResponse create(UUID userId, ServiceRecordInput input) {
		checkNextServiceDate(input);
		Product product = productService.findOwned(userId, input.productId());
		return ServiceRecordResponse.from(records.save(new ServiceRecord(product, input)));
	}

	@Transactional
	public ServiceRecordResponse update(UUID userId, UUID recordId, ServiceRecordInput input) {
		ServiceRecord record = findOwned(userId, recordId);
		checkNextServiceDate(input);
		record.update(productService.findOwned(userId, input.productId()), input);
		return ServiceRecordResponse.from(record);
	}

	@Transactional
	public void delete(UUID userId, UUID recordId) {
		records.delete(findOwned(userId, recordId));
	}

	/** A rule about two fields together, so it can't be a single-field annotation. */
	private static void checkNextServiceDate(ServiceRecordInput input) {
		if (input.nextServiceDate() != null && !input.nextServiceDate().isAfter(input.serviceDate())) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Please check the highlighted fields.",
					Map.of("nextServiceDate", "The next service must be after the service date"));
		}
	}

	/** Someone else's record looks like a missing one (404). */
	private ServiceRecord findOwned(UUID userId, UUID recordId) {
		return records.findByIdAndProductUserId(recordId, userId)
				.orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "SERVICE_RECORD_NOT_FOUND",
						"The requested service record was not found."));
	}
}
