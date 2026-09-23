package dev.bryrich.credapp.tracking;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TrackingSettingsService {

    private final TrackingSettingsRepository repository;

    public TrackingSettingsService(TrackingSettingsRepository repository) {
        this.repository = repository;
    }

    /** The group's saved settings, or the defaults if it hasn't saved any. */
    @Transactional(readOnly = true)
    public TrackingSettings forGroup(Long userGroupId) {
        return repository.findById(userGroupId).orElseGet(() -> new TrackingSettings(userGroupId));
    }

    @Transactional
    public TrackingSettings save(Long userGroupId, TrackingSettingsForm form) {
        TrackingSettings settings = repository.findById(userGroupId)
                .orElseGet(() -> new TrackingSettings(userGroupId));
        form.applyTo(settings);
        return repository.save(settings);
    }
}
