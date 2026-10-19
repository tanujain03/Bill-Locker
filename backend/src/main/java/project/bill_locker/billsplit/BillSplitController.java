package project.bill_locker.billsplit;
import jakarta.validation.Valid;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import project.bill_locker.security.CurrentUser;
@RestController
@RequestMapping("/api/splits")
class BillSplitController {
 private final BillSplitService service;
 BillSplitController(BillSplitService service) {this.service=service;}
 @GetMapping("/groups") List<SplitViews.GroupSummary> list(@AuthenticationPrincipal Jwt jwt) {return service.list(CurrentUser.id(jwt));}
 @PostMapping("/groups") @ResponseStatus(HttpStatus.CREATED)
 SplitViews.GroupDetail create(@AuthenticationPrincipal Jwt jwt,@Valid @RequestBody SplitRequests.Group r) {return service.create(CurrentUser.id(jwt),r);}
 @GetMapping("/groups/{id}") SplitViews.GroupDetail detail(@AuthenticationPrincipal Jwt jwt,@PathVariable UUID id) {return service.detail(CurrentUser.id(jwt),id);}
 @DeleteMapping("/groups/{id}") @ResponseStatus(HttpStatus.NO_CONTENT)
 void delete(@AuthenticationPrincipal Jwt jwt,@PathVariable UUID id) {service.delete(CurrentUser.id(jwt),id);}
 @PostMapping("/groups/{id}/members") @ResponseStatus(HttpStatus.CREATED)
 SplitViews.GroupDetail member(@AuthenticationPrincipal Jwt jwt,@PathVariable UUID id,@Valid @RequestBody SplitRequests.Member r) {return service.addMember(CurrentUser.id(jwt),id,r);}
 @PostMapping("/groups/{id}/members/{memberId}/invitation")
 SplitViews.Invitation invite(@AuthenticationPrincipal Jwt jwt,@PathVariable UUID id,@PathVariable UUID memberId) {return service.invite(CurrentUser.id(jwt),id,memberId);}
 @DeleteMapping("/groups/{id}/members/{memberId}/invitation") @ResponseStatus(HttpStatus.NO_CONTENT)
 void revoke(@AuthenticationPrincipal Jwt jwt,@PathVariable UUID id,@PathVariable UUID memberId) {service.revoke(CurrentUser.id(jwt),id,memberId);}
 @PostMapping("/invitations/preview")
 SplitViews.Preview preview(@Valid @RequestBody SplitRequests.Token r) {return service.preview(r.token());}
 @PostMapping("/invitations/accept")
 SplitViews.GroupDetail accept(@AuthenticationPrincipal Jwt jwt,@Valid @RequestBody SplitRequests.Token r) {return service.accept(CurrentUser.id(jwt),r.token());}
 @PostMapping("/groups/{id}/expenses") @ResponseStatus(HttpStatus.CREATED)
 SplitViews.GroupDetail expense(@AuthenticationPrincipal Jwt jwt,@PathVariable UUID id,@Valid @RequestBody SplitRequests.Expense r) {return service.expense(CurrentUser.id(jwt),id,r);}
 @PostMapping("/groups/{id}/expenses/{expenseId}/void")
 SplitViews.GroupDetail voidExpense(@AuthenticationPrincipal Jwt jwt,@PathVariable UUID id,@PathVariable UUID expenseId) {return service.voidExpense(CurrentUser.id(jwt),id,expenseId);}
 @PostMapping("/groups/{id}/settlements") @ResponseStatus(HttpStatus.CREATED)
 SplitViews.GroupDetail report(@AuthenticationPrincipal Jwt jwt,@PathVariable UUID id,@Valid @RequestBody SplitRequests.Settlement r) {return service.report(CurrentUser.id(jwt),id,r);}
 @PostMapping("/groups/{id}/settlements/{settlementId}/confirm")
 SplitViews.GroupDetail confirm(@AuthenticationPrincipal Jwt jwt,@PathVariable UUID id,@PathVariable UUID settlementId) {return service.decide(CurrentUser.id(jwt),id,settlementId,true);}
 @PostMapping("/groups/{id}/settlements/{settlementId}/reject")
 SplitViews.GroupDetail reject(@AuthenticationPrincipal Jwt jwt,@PathVariable UUID id,@PathVariable UUID settlementId) {return service.decide(CurrentUser.id(jwt),id,settlementId,false);}
}
