package dev.bryrich.credapp.service;

import dev.bryrich.credapp.entity.Provider;
import dev.bryrich.credapp.repository.ProviderRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import dev.bryrich.credapp.exception.ProviderNotFoundException;

import java.util.List;


@Service
public class ProviderService {
    private final ProviderRepository providerRepository;
    public ProviderService (ProviderRepository providerRepository) {
        this.providerRepository = providerRepository;
    }
    @Transactional(readOnly = true)
    public Provider findById(Long id) {
        return providerRepository.findById(id)
                .orElseThrow(() -> new ProviderNotFoundException(id));
    }

    @Transactional(readOnly = true)
    public Provider findByIdWithLicenses(Long id) {
        return providerRepository.findByIdWithLicenses(id)
                .orElseThrow(() -> new ProviderNotFoundException(id));
    }

    @Transactional(readOnly = true)
    public List<Provider> search(String lastName) {
        return providerRepository.findByLastNameContainingIgnoreCase(lastName);
    }

    @Transactional
    public Provider create(Provider provider) {
        return providerRepository.save(provider);
    }

}
