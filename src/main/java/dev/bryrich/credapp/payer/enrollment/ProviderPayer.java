package dev.bryrich.credapp.payer.enrollment;

import dev.bryrich.credapp.payer.Payer;
import dev.bryrich.credapp.provider.Provider;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/** A provider's enrollment with one payer. At most one per provider and payer. */
@Entity
@Table(name = "provider_payers")
public class ProviderPayer extends Enrollment {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "provider_id", nullable = false, updatable = false)
    private Provider provider;

    protected ProviderPayer() {
    }

    public ProviderPayer(Provider provider, Payer payer) {
        super(payer);
        this.provider = provider;
    }

    public Provider getProvider() {
        return provider;
    }
}
