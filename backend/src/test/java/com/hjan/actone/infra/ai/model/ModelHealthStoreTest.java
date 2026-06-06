package com.hjan.actone.infra.ai.model;

import com.hjan.actone.infra.ai.config.AiModelProperties;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;

class ModelHealthStoreTest {

    @Test
    void tracksFailureOpenHalfOpenAndSuccess() {
        AtomicLong now = new AtomicLong(1000L);
        AiModelProperties properties = new AiModelProperties();
        properties.getSelection().setFailureThreshold(2);
        properties.getSelection().setOpenDurationMs(100L);
        ModelHealthStore store = new ModelHealthStore(properties, now::get);

        assertThat(store.allowCall("m1")).isTrue();
        store.markFailure("m1");
        assertThat(store.allowCall("m1")).isTrue();

        store.markFailure("m1");
        assertThat(store.isUnavailable("m1")).isTrue();
        assertThat(store.allowCall("m1")).isFalse();

        now.addAndGet(101L);
        assertThat(store.isUnavailable("m1")).isFalse();
        assertThat(store.allowCall("m1")).isTrue();
        assertThat(store.allowCall("m1")).isFalse();

        store.markSuccess("m1");
        assertThat(store.allowCall("m1")).isTrue();
        assertThat(store.isUnavailable("m1")).isFalse();
    }

    @Test
    void halfOpenFailureReopensCircuit() {
        AtomicLong now = new AtomicLong(1000L);
        AiModelProperties properties = new AiModelProperties();
        properties.getSelection().setFailureThreshold(1);
        properties.getSelection().setOpenDurationMs(100L);
        ModelHealthStore store = new ModelHealthStore(properties, now::get);

        store.markFailure("m1");
        now.addAndGet(101L);
        assertThat(store.allowCall("m1")).isTrue();
        store.markFailure("m1");

        assertThat(store.isUnavailable("m1")).isTrue();
    }
}
