package dev.bryrich.credapp.user;

/** One user group on the superuser's overview: who's in it and who's waiting to be let in. */
public record UserGroupSummary(Long id, String name, String joinCode, long accounts, long pendingApproval) {
}
