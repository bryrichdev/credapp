package dev.bryrich.credapp.payer.enrollment;

import dev.bryrich.credapp.group.Group;
import dev.bryrich.credapp.payer.Payer;
import dev.bryrich.credapp.payer.PayerContact;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/**
 * A group's enrollment with one payer. At most one per group and payer. Larger groups often
 * have an account rep at the payer; accountRep is that person, one of the payer's contacts.
 */
@Entity
@Table(name = "group_payers")
public class GroupPayer extends Enrollment {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "group_id", nullable = false, updatable = false)
    private Group group;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "account_rep_id")
    private PayerContact accountRep;

    protected GroupPayer() {
    }

    public GroupPayer(Group group, Payer payer) {
        super(payer);
        this.group = group;
    }

    public Group getGroup() {
        return group;
    }

    public PayerContact getAccountRep() {
        return accountRep;
    }

    public void setAccountRep(PayerContact accountRep) {
        this.accountRep = accountRep;
    }
}
