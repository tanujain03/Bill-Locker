package project.bill_locker.servicerecord;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import project.bill_locker.common.ApiException;
import project.bill_locker.document.*;

@Service
public class ServiceRecordService {
 private final ServiceRecordRepository records;
 private final DocumentRepository documents;
 private final Clock clock;
 public ServiceRecordService(ServiceRecordRepository records, DocumentRepository documents, Clock clock) {
  this.records=records; this.documents=documents; this.clock=clock;
 }
 @Transactional
 public ServiceRecordView create(UUID userId, UUID documentId, int position, UUID expectedProductId, ServiceRecordRequest request) {
  Document document=ownedDocument(userId,documentId); requireSaved(document);
  DocumentItem item=document.getItems().stream().filter(i->i.getPosition()==position).findFirst().orElseThrow(ServiceRecordService::notFound);
  // A stale product tab must never record a repair against whichever product moved into its position.
  if (expectedProductId!=null && !expectedProductId.equals(item.getId())) throw notFound();
  ServiceRecord record=new ServiceRecord(userId,document,item);
  apply(userId,record,request);
  records.saveAndFlush(record);
  return history(userId,item.getId()).stream().filter(r->r.id().equals(record.getId())).findFirst().orElseThrow();
 }
 @Transactional(readOnly=true)
 public List<ServiceRecordView> forProduct(UUID userId, UUID documentId, int position) {
  Document d=ownedDocument(userId,documentId); requireSaved(d);
  DocumentItem item=d.getItems().stream().filter(i->i.getPosition()==position).findFirst().orElseThrow(ServiceRecordService::notFound);
  return history(userId,item.getId());
 }
 @Transactional(readOnly=true)
 public List<ServiceRecordView> history(UUID userId, UUID productId) { return views(records.findProductHistory(userId,productId)); }
 @Transactional(readOnly=true)
 public List<ServiceRecordView> list(UUID userId, ServiceType type, String q, ServiceDueStatus due) {
  String search=q==null ? "" : q.strip().toLowerCase(Locale.ROOT);
  return views(records.findOwned(userId)).stream().filter(r->type==null || r.type()==type)
   .filter(r->due==null || (r.dueStatus()==due && (due==ServiceDueStatus.NONE || r.reminderActive())))
   .filter(r->search.isEmpty() || java.util.stream.Stream.of(r.title(),r.description(),r.providerName(),r.productName(),r.claimNumber())
    .anyMatch(s->s!=null && s.toLowerCase(Locale.ROOT).contains(search))).toList();
 }
 @Transactional(readOnly=true)
 public List<ServiceRecordView> due(UUID userId) {
  return list(userId,null,null,null).stream().filter(ServiceRecordView::reminderActive)
   .filter(r->r.dueStatus()==ServiceDueStatus.OVERDUE || r.dueStatus()==ServiceDueStatus.DUE_SOON)
   .sorted(Comparator.comparing(ServiceRecordView::nextDueDate)).toList();
 }
 @Transactional
 public ServiceRecordView update(UUID userId, UUID id, ServiceRecordRequest request) {
  ServiceRecord r=owned(userId,id); requireSaved(r.getDocument()); apply(userId,r,request);
  records.flush();
  return history(userId,r.getItem().getId()).stream().filter(v->v.id().equals(id)).findFirst().orElseThrow();
 }
 @Transactional
 public void delete(UUID userId, UUID id) { records.delete(owned(userId,id)); }
 private ServiceRecord owned(UUID userId, UUID id) { return records.findByIdAndUserId(id,userId).orElseThrow(ServiceRecordService::notFound); }
 private Document ownedDocument(UUID userId, UUID id) { return documents.findByIdAndUserId(id,userId).orElseThrow(ServiceRecordService::notFound); }
 private static void requireSaved(Document document) {
  if (document.getStatus()!=DocumentStatus.SAVED) throw new ApiException(HttpStatus.CONFLICT,"DOCUMENT_NOT_SAVED","Save and review the bill before adding or editing service records.");
 }
 private void apply(UUID userId, ServiceRecord record, ServiceRecordRequest request) {
  Map<String,String> errors=new LinkedHashMap<>();
  if (request.serviceDate().isAfter(LocalDate.now(clock))) errors.put("serviceDate","Must not be in the future.");
  if (request.nextDueDate()!=null && request.nextDueDate().isBefore(request.serviceDate())) errors.put("nextDueDate","Must be on or after the service date.");
  if (!errors.isEmpty()) throw new ApiException(HttpStatus.BAD_REQUEST,"VALIDATION_ERROR","Check the service details.",errors);
  Document attachment=request.attachmentDocumentId()==null ? null : ownedDocument(userId,request.attachmentDocumentId());
  record.update(request,attachment);
 }
 private List<ServiceRecordView> views(List<ServiceRecord> rows) {
  // Only the latest scheduled service per product drives reminders; history is never discarded.
  Set<UUID> products=new HashSet<>(); Set<UUID> active=new HashSet<>();
  for (ServiceRecord r:rows) if (r.getNextDueDate()!=null && products.add(r.getItem().getId())) active.add(r.getId());
  return rows.stream().map(r->view(r,active.contains(r.getId()))).toList();
 }
 private ServiceRecordView view(ServiceRecord r, boolean active) {
  return new ServiceRecordView(r.getId(),r.getDocument().getId(),r.getItem().getId(),r.getItem().getPosition(),r.getItem().getProductName(),
   r.getType(),r.getTitle(),r.getDescription(),r.getServiceDate(),r.getProviderName(),r.getProviderContact(),r.getCost(),
   r.isCoveredByWarranty(),r.getClaimNumber(),r.getClaimStatus(),r.getNextDueDate(),r.getRepeatMonths(),
   r.getAttachment()==null ? null : r.getAttachment().getId(),ServiceRules.status(r.getNextDueDate(),LocalDate.now(clock)),active && r.getNextDueDate()!=null);
 }
 public static BigDecimal totalCost(List<ServiceRecordView> history) {
  return history.stream().map(ServiceRecordView::cost).filter(Objects::nonNull).reduce(BigDecimal.ZERO,BigDecimal::add).setScale(2);
 }
 public static LocalDate nextDue(List<ServiceRecordView> history) {
  return history.stream().filter(ServiceRecordView::reminderActive).map(ServiceRecordView::nextDueDate).findFirst().orElse(null);
 }
 private static ApiException notFound() { return new ApiException(HttpStatus.NOT_FOUND,"SERVICE_NOT_FOUND","That service record or product was not found."); }
}
