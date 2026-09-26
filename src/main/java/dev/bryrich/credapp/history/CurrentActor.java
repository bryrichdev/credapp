package dev.bryrich.credapp.history;

/**
 * Who is making changes on this thread, for the change history. Set once per request by
 * {@link ActorFilter}; empty on background threads and before sign-in is resolved.
 */
final class CurrentActor {

    record Actor(Long id, String email) {
    }

    private static final ThreadLocal<Actor> ACTOR = new ThreadLocal<>();

    private CurrentActor() {
    }

    static Actor get() {
        return ACTOR.get();
    }

    static void set(Actor actor) {
        ACTOR.set(actor);
    }

    static void clear() {
        ACTOR.remove();
    }
}
