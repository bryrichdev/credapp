package dev.bryrich.credapp.service;

import dev.bryrich.credapp.entity.Provider;
import dev.bryrich.credapp.repository.ProviderRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import dev.bryrich.credapp.exception.ProviderNotFoundException;


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
    public Page<Provider> search(String lastName, Pageable pageable) {
        return providerRepository.findByLastNameContainingIgnoreCase(lastName, pageable);
    }

    @Transactional(readOnly = true)
    public Page<Provider> findAll(Pageable pageable) {
        return providerRepository.findAll(pageable);
    }

    @Transactional
    public Provider create(Provider provider) {
        return providerRepository.save(provider);
    }

    @Transactional
    public void delete(Long id) {
        Provider provider = providerRepository.findById(id)
                .orElseThrow(() -> new ProviderNotFoundException(id));
        providerRepository.delete(provider);
    }

}
