package com.pesitwizard.client.controller;

import com.pesitwizard.client.entity.Partner;
import com.pesitwizard.client.service.PartnerService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** REST API for managing PeSIT partners */
@RestController
@RequestMapping("/api/v1/partners")
@RequiredArgsConstructor
public class PartnerController {

    private final PartnerService partnerService;

    @GetMapping
    public List<Partner> getAllPartners() {
        return partnerService.getAllPartners();
    }

    @GetMapping("/{id}")
    public ResponseEntity<Partner> getPartner(@PathVariable String id) {
        return partnerService
                .getPartner(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/by-partner-id/{partnerId}")
    public ResponseEntity<Partner> getByPartnerId(@PathVariable String partnerId) {
        return partnerService
                .getByPartnerId(partnerId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Partner createPartner(@Valid @RequestBody Partner partner) {
        return partnerService.createPartner(partner);
    }

    @PutMapping("/{id}")
    public ResponseEntity<Partner> updatePartner(
            @PathVariable String id, @Valid @RequestBody Partner partner) {
        return partnerService
                .updatePartner(id, partner)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deletePartner(@PathVariable String id) {
        partnerService.deletePartner(id);
    }
}
