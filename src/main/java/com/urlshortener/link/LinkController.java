package com.urlshortener.link;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/links")
public class LinkController {

    private static final String MANAGEMENT_TOKEN_HEADER = "X-Management-Token";

    private final LinkService linkService;

    public LinkController(LinkService linkService) {
        this.linkService = linkService;
    }

    @PostMapping
    public ResponseEntity<LinkResponse> create(@Valid @RequestBody CreateLinkRequest request) {
        LinkResponse response = linkService.createLink(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Deactivates a link. Requires the management token returned once at creation — see
     * ShortLink's javadoc for why (no auth system to verify "ownership" any other way).
     */
    @DeleteMapping("/{code}")
    public ResponseEntity<Void> delete(@PathVariable String code,
                                        @RequestHeader(name = MANAGEMENT_TOKEN_HEADER, required = false) String managementToken) {
        linkService.deactivate(code, managementToken);
        return ResponseEntity.noContent().build();
    }
}
