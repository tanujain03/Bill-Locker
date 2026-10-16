package project.bill_locker.servicerecord;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface ServiceRecordRepository extends JpaRepository<ServiceRecord, UUID> {
 Optional<ServiceRecord> findByIdAndUserId(UUID id, UUID userId);
 @Query("""
  select r from ServiceRecord r join fetch r.document d join fetch r.item
  where r.userId=:userId and d.status=project.bill_locker.document.DocumentStatus.SAVED
  order by r.serviceDate desc, r.createdAt desc, r.id desc
  """)
 List<ServiceRecord> findOwned(UUID userId);
 @Query("""
  select r from ServiceRecord r join fetch r.document d join fetch r.item i
  where r.userId=:userId and i.id=:productId and d.status=project.bill_locker.document.DocumentStatus.SAVED
  order by r.serviceDate desc, r.createdAt desc, r.id desc
  """)
 List<ServiceRecord> findProductHistory(UUID userId, UUID productId);
}
