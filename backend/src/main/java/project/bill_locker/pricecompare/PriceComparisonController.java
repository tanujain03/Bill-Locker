package project.bill_locker.pricecompare;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import project.bill_locker.security.CurrentUser;
@RestController @RequestMapping("/api/price-comparison")
class PriceComparisonController {
 private final PriceComparisonService service;
 PriceComparisonController(PriceComparisonService service) {this.service=service;}
 @GetMapping("/status") PriceComparisonView.Status status(@AuthenticationPrincipal Jwt jwt) {return service.status(CurrentUser.id(jwt));}
 @PostMapping("/store-link") PriceComparisonView.StoreLink store(@AuthenticationPrincipal Jwt jwt,@Valid @RequestBody StoreLinkRequest r) {return service.storeLink(CurrentUser.id(jwt),r);}
 @PostMapping("/search") PriceComparisonView search(@AuthenticationPrincipal Jwt jwt,@Valid @RequestBody PriceComparisonRequest r) {
  return service.search(CurrentUser.id(jwt),r);
 }
}
