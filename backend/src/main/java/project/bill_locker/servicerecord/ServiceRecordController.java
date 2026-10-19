package project.bill_locker.servicerecord;

import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import project.bill_locker.security.CurrentUser;

@RestController
@RequestMapping("/api")
class ServiceRecordController {
 private final ServiceRecordService services;
 ServiceRecordController(ServiceRecordService services) { this.services=services; }
 @PostMapping("/documents/{id}/items/{position}/services") @ResponseStatus(HttpStatus.CREATED)
 ServiceRecordView create(@AuthenticationPrincipal Jwt jwt,@PathVariable UUID id,@PathVariable int position,
  @RequestParam(required=false) UUID productId,@Valid @RequestBody ServiceRecordRequest request) {
  return services.create(CurrentUser.id(jwt),id,position,productId,request);
 }
 @GetMapping("/documents/{id}/items/{position}/services")
 List<ServiceRecordView> history(@AuthenticationPrincipal Jwt jwt,@PathVariable UUID id,@PathVariable int position) {
  return services.forProduct(CurrentUser.id(jwt),id,position);
 }
 @GetMapping("/services")
 List<ServiceRecordView> list(@AuthenticationPrincipal Jwt jwt,@RequestParam(required=false) ServiceType type,
  @RequestParam(required=false) String q,@RequestParam(required=false) ServiceDueStatus due) {
  return services.list(CurrentUser.id(jwt),type,q,due);
 }
 @PutMapping("/services/{id}")
 ServiceRecordView update(@AuthenticationPrincipal Jwt jwt,@PathVariable UUID id,@Valid @RequestBody ServiceRecordRequest request) {
  return services.update(CurrentUser.id(jwt),id,request);
 }
 @DeleteMapping("/services/{id}") @ResponseStatus(HttpStatus.NO_CONTENT)
 void delete(@AuthenticationPrincipal Jwt jwt,@PathVariable UUID id) { services.delete(CurrentUser.id(jwt),id); }
}
