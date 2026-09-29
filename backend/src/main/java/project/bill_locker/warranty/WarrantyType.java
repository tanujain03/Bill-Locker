package project.bill_locker.warranty;

public enum WarrantyType {
	/** Manufacturer warranty — at most one per product. */
	STANDARD,
	/** Extension bought or registered later. */
	EXTENDED,
	/** Part-specific cover, e.g. "10 years on compressor". */
	COMPONENT
}
