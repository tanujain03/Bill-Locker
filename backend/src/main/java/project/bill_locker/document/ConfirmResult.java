package project.bill_locker.document;

import project.bill_locker.product.ProductResponse;

/** Response of confirming a document: the document (now CONFIRMED) and the saved product. */
public record ConfirmResult(DocumentSummary document, ProductResponse product) {
}
