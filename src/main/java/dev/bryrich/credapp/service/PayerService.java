package dev.bryrich.credapp.service;

import dev.bryrich.credapp.entity.Payer;
import dev.bryrich.credapp.exception.PayerNotFoundException;
import dev.bryrich.credapp.repository.PayerRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.function.Consumer;

@Service
public class PayerService {

    private final PayerRepository payerRepository;

    public PayerService(PayerRepository payerRepository) {
        this.payerRepository = payerRepository;
    }

    @Transactional(readOnly = true)
    public Payer findById(Long id) {
        return payerRepository.findById(id)
                .orElseThrow(() -> new PayerNotFoundException(id));
    }

    @Transactional(readOnly = true)
    public Payer findByIdWithContacts(Long id) {
        return payerRepository.findWithContactsById(id)
                .orElseThrow(() -> new PayerNotFoundException(id));
    }

    @Transactional(readOnly = true)
    public boolean existsByName(String name) {
        return payerRepository.existsByNameIgnoreCase(name);
    }

    @Transactional(readOnly = true)
    public Page<Payer> search(String name, Pageable pageable) {
        return payerRepository.findByNameContainingIgnoreCase(name, pageable);
    }

    @Transactional(readOnly = true)
    public Page<Payer> findAll(Pageable pageable) {
        return payerRepository.findAll(pageable);
    }

    @Transactional(readOnly = true)
    public List<Payer> findAllForSelect() {
        return payerRepository.findAll(Sort.by("name"));
    }

    @Transactional
    public Payer create(Payer payer) {
        return payerRepository.save(payer);
    }

    @Transactional
    public Payer update(Long id, Consumer<Payer> changes) {
        Payer payer = payerRepository.findById(id)
                .orElseThrow(() -> new PayerNotFoundException(id));
        changes.accept(payer);
        return payer;
    }

    @Transactional
    public void delete(Long id) {
        Payer payer = payerRepository.findById(id)
                .orElseThrow(() -> new PayerNotFoundException(id));
        payerRepository.delete(payer);
    }
}
