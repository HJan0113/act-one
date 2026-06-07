package com.hjan.actone.infra.ai.model;

import com.hjan.actone.infra.ai.config.AiModelProperties;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.LongSupplier;

public class ModelHealthStore {

    private static final int DEFAULT_FAILURE_THRESHOLD = 2;
    private static final long DEFAULT_OPEN_DURATION_MS = 30000L;

    private final AiModelProperties properties;
    private final LongSupplier currentTimeMillis;
    private final Map<String, ModelHealth> healthById = new ConcurrentHashMap<>();

    public ModelHealthStore(AiModelProperties properties) {
        this(properties, System::currentTimeMillis);
    }

    ModelHealthStore(AiModelProperties properties, LongSupplier currentTimeMillis) {
        this.properties = Objects.requireNonNull(properties, "AiModelProperties 不能为空");
        this.currentTimeMillis = Objects.requireNonNull(currentTimeMillis, "currentTimeMillis 不能为空");
    }

    public boolean isUnavailable(String id) {
        if (id == null) {
            return true;
        }
        ModelHealth health = healthById.get(id);
        if (health == null) {
            return false;
        }
        long now = currentTimeMillis.getAsLong();
        if (health.state == State.OPEN && health.openUntil > now) {
            return true;
        }
        return health.state == State.HALF_OPEN && health.halfOpenInFlight;
    }

    public boolean allowCall(String id) {
        if (id == null) {
            return false;
        }
        long now = currentTimeMillis.getAsLong();
        AtomicBoolean allowed = new AtomicBoolean(false);
        healthById.compute(id, (key, value) -> {
            ModelHealth health = value == null ? new ModelHealth() : value;
            if (health.state == State.OPEN) {
                if (health.openUntil > now) {
                    return health;
                }
                health.state = State.HALF_OPEN;
                health.halfOpenInFlight = true;
                allowed.set(true);
                return health;
            }
            if (health.state == State.HALF_OPEN) {
                if (health.halfOpenInFlight) {
                    return health;
                }
                health.halfOpenInFlight = true;
                allowed.set(true);
                return health;
            }
            allowed.set(true);
            return health;
        });
        return allowed.get();
    }

    public void markSuccess(String id) {
        if (id == null) {
            return;
        }
        healthById.compute(id, (key, value) -> {
            ModelHealth health = value == null ? new ModelHealth() : value;
            health.state = State.CLOSED;
            health.consecutiveFailures = 0;
            health.openUntil = 0L;
            health.halfOpenInFlight = false;
            return health;
        });
    }

    public void markFailure(String id) {
        if (id == null) {
            return;
        }
        long now = currentTimeMillis.getAsLong();
        healthById.compute(id, (key, value) -> {
            ModelHealth health = value == null ? new ModelHealth() : value;
            if (health.state == State.HALF_OPEN) {
                open(health, now);
                return health;
            }
            health.consecutiveFailures++;
            if (health.consecutiveFailures >= failureThreshold()) {
                open(health, now);
            }
            return health;
        });
    }

    private void open(ModelHealth health, long now) {
        health.state = State.OPEN;
        health.openUntil = now + openDurationMs();
        health.consecutiveFailures = 0;
        health.halfOpenInFlight = false;
    }

    private int failureThreshold() {
        Integer value = properties.getSelection() == null ? null : properties.getSelection().getFailureThreshold();
        return value == null || value <= 0 ? DEFAULT_FAILURE_THRESHOLD : value;
    }

    private long openDurationMs() {
        Long value = properties.getSelection() == null ? null : properties.getSelection().getOpenDurationMs();
        return value == null || value <= 0 ? DEFAULT_OPEN_DURATION_MS : value;
    }

    private static final class ModelHealth {
        private int consecutiveFailures;
        private long openUntil;
        private boolean halfOpenInFlight;
        private State state = State.CLOSED;
    }

    private enum State {
        CLOSED,
        OPEN,
        HALF_OPEN
    }
}
