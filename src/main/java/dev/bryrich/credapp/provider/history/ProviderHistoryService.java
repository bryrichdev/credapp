package dev.bryrich.credapp.provider.history;

import dev.bryrich.credapp.provider.Provider;
import dev.bryrich.credapp.provider.ProviderNotFoundException;
import dev.bryrich.credapp.provider.ProviderRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.function.Consumer;

/** A provider's training and work history. Saved as part of the provider's one form. */
@Service
public class ProviderHistoryService {

    private final ProviderTrainingRepository training;
    private final WorkHistoryRepository work;
    private final ProviderRepository providers;

    public ProviderHistoryService(ProviderTrainingRepository training, WorkHistoryRepository work,
                                  ProviderRepository providers) {
        this.training = training;
        this.work = work;
        this.providers = providers;
    }

    @Transactional(readOnly = true)
    public List<ProviderTraining> findTraining(Long providerId) {
        return training.findByProviderIdOrderByStartDateDesc(providerId);
    }

    @Transactional(readOnly = true)
    public List<WorkHistoryEntry> findWork(Long providerId) {
        return work.findByProviderIdOrderByStartDateDesc(providerId);
    }

    @Transactional
    public ProviderTraining addTraining(Long providerId, ProviderTraining entry) {
        entry.setProvider(provider(providerId));
        return training.save(entry);
    }

    @Transactional
    public void updateTraining(Long id, Long providerId, Consumer<ProviderTraining> changes) {
        changes.accept(training.findByIdAndProviderId(id, providerId).orElseThrow(() -> missing(id)));
    }

    @Transactional
    public void deleteTraining(Long id, Long providerId) {
        training.delete(training.findByIdAndProviderId(id, providerId).orElseThrow(() -> missing(id)));
    }

    @Transactional
    public WorkHistoryEntry addWork(Long providerId, WorkHistoryEntry entry) {
        entry.setProvider(provider(providerId));
        return work.save(entry);
    }

    @Transactional
    public void updateWork(Long id, Long providerId, Consumer<WorkHistoryEntry> changes) {
        changes.accept(work.findByIdAndProviderId(id, providerId).orElseThrow(() -> missing(id)));
    }

    @Transactional
    public void deleteWork(Long id, Long providerId) {
        work.delete(work.findByIdAndProviderId(id, providerId).orElseThrow(() -> missing(id)));
    }

    private Provider provider(Long providerId) {
        return providers.findById(providerId).orElseThrow(() -> new ProviderNotFoundException(providerId));
    }

    private static NoSuchElementException missing(Long id) {
        return new NoSuchElementException("No history entry " + id + " for this provider");
    }
}
