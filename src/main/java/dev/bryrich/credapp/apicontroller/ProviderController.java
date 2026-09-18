package dev.bryrich.credapp.apicontroller;

import dev.bryrich.credapp.dto.CreateProviderRequest;
import dev.bryrich.credapp.dto.ProviderResponse;
import dev.bryrich.credapp.entity.Provider;
import dev.bryrich.credapp.service.ProviderService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/api/providers")
public class ProviderController {

    private final ProviderService providerService;

    public ProviderController(ProviderService providerService) {
        this.providerService = providerService;
    }

    @GetMapping("/{id}")
    public ProviderResponse getById(@PathVariable Long id) {
        return ProviderResponse.from(providerService.findById(id));
    }

    @GetMapping
    public Page<ProviderResponse> search(@RequestParam String lastName, Pageable pageable) {
        return providerService.search(lastName, pageable).map(ProviderResponse::from);
    }

    @PostMapping
    public ResponseEntity<ProviderResponse> create(@Valid @RequestBody CreateProviderRequest request) {
        Provider saved = providerService.create(request.toEntity());
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(saved.getId())
                .toUri();
        return ResponseEntity.created(location).body(ProviderResponse.from(saved));
    }
}