package vn.edu.huit.smartparking.backend.security.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;

public final class LoginAttemptThrottle {
    static final int MAX_TRACKED_KEYS = 10_000;

    private final Clock clock;
    private final int maximumFailures;
    private final Duration window;
    private final Map<Key, State> states = new HashMap<>();

    public LoginAttemptThrottle(Clock clock, int maximumFailures, Duration window) {
        this.clock = clock;
        this.maximumFailures = maximumFailures;
        this.window = window;
    }

    public Attempt beginAttempt(String username, String sourceIp) {
        Key key = key(username, sourceIp);
        State state;
        synchronized (states) {
            state = states.get(key);
            if (state == null) {
                if (states.size() >= MAX_TRACKED_KEYS) {
                    pruneExpiredEntries(clock.instant());
                    if (states.size() >= MAX_TRACKED_KEYS) {
                        return new Attempt(key, null, true);
                    }
                }
                state = new State();
                states.put(key, state);
            }
            state.references++;
        }

        state.lock.lock();
        prune(state.failures, clock.instant());
        return new Attempt(key, state, state.failures.size() >= maximumFailures);
    }

    private Key key(String username, String sourceIp) {
        return new Key(UsernameCanonicalizer.canonicalize(username), sourceIp == null ? "" : sourceIp);
    }

    private void recordFailure(State state) {
        Instant now = clock.instant();
        prune(state.failures, now);
        if (state.failures.size() < maximumFailures) {
            state.failures.addLast(now);
        }
    }

    private void pruneExpiredEntries(Instant now) {
        Iterator<Map.Entry<Key, State>> entries = states.entrySet().iterator();
        while (entries.hasNext()) {
            State state = entries.next().getValue();
            if (state.references == 0) {
                prune(state.failures, now);
                if (state.failures.isEmpty()) {
                    entries.remove();
                }
            }
        }
    }

    private void release(Key key, State state) {
        state.lock.unlock();
        synchronized (states) {
            state.references--;
            if (state.references == 0) {
                prune(state.failures, clock.instant());
                if (state.failures.isEmpty()) {
                    states.remove(key, state);
                }
            }
        }
    }

    private void prune(Deque<Instant> attempts, Instant now) {
        Instant cutoff = now.minus(window);
        while (!attempts.isEmpty() && !attempts.peekFirst().isAfter(cutoff)) {
            attempts.removeFirst();
        }
    }

    public final class Attempt implements AutoCloseable {
        private final Key key;
        private final State state;
        private final boolean blocked;
        private boolean outcomeRecorded;
        private boolean closed;

        private Attempt(Key key, State state, boolean blocked) {
            this.key = key;
            this.state = state;
            this.blocked = blocked;
        }

        public boolean isBlocked() {
            return blocked;
        }

        public void recordFailure() {
            if (state != null && !blocked && !outcomeRecorded && !closed) {
                LoginAttemptThrottle.this.recordFailure(state);
                outcomeRecorded = true;
            }
        }

        public void recordSuccess() {
            if (state != null && !blocked && !outcomeRecorded && !closed) {
                state.failures.clear();
                outcomeRecorded = true;
            }
        }

        @Override
        public void close() {
            if (!closed) {
                closed = true;
                if (state != null) {
                    release(key, state);
                }
            }
        }
    }

    private static final class State {
        private final ReentrantLock lock = new ReentrantLock();
        private final Deque<Instant> failures = new ArrayDeque<>();
        private int references;
    }

    private record Key(String username, String sourceIp) {}
}
