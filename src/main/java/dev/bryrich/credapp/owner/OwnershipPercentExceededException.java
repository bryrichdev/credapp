package dev.bryrich.credapp.owner;

import java.math.BigDecimal;

/**
 * Raised before saving when a group's ownership percentages would total more than 100.
 * A CHECK constraint can't catch this, because it cannot see the other rows.
 */
public class OwnershipPercentExceededException extends RuntimeException {
    public OwnershipPercentExceededException(Long groupId, BigDecimal attemptedTotal) {
        super("Ownership for group " + groupId + " would total " + attemptedTotal + "%, over 100%");
    }
}
