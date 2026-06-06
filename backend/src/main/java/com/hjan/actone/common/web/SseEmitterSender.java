package com.hjan.actone.common.web;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.concurrent.atomic.AtomicBoolean;

public class SseEmitterSender {

    private static final Logger log = LoggerFactory.getLogger(SseEmitterSender.class);

    private final AtomicBoolean closed = new AtomicBoolean(false);
    private final SseEmitter emitter;

    public SseEmitterSender(SseEmitter emitter) {
        this.emitter = emitter;
        emitter.onCompletion(() -> closed.set(true));
        emitter.onTimeout(() -> closed.set(true));
        emitter.onError(error -> closed.set(true));
    }

    public void sendEvent(String eventName, Object data) {
        if (closed.get()) {
            return;
        }
        try {
            emitter.send(SseEmitter.event().name(eventName).data(data));
        } catch (Exception error) {
            fail(error);
        }
    }

    public void complete() {
        if (closed.compareAndSet(false, true)) {
            emitter.complete();
        }
    }

    public void fail(Throwable error) {
        if (closed.compareAndSet(false, true)) {
            log.warn("SSE send failed", error);
            emitter.completeWithError(error);
        }
    }
}
