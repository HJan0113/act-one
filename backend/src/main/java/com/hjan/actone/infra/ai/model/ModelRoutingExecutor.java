package com.hjan.actone.infra.ai.model;

import com.hjan.actone.common.errorcode.BaseErrorCode;
import com.hjan.actone.common.exception.RemoteException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.Function;

public class ModelRoutingExecutor {

    private static final Logger log = LoggerFactory.getLogger(ModelRoutingExecutor.class);

    private final ModelHealthStore healthStore;

    public ModelRoutingExecutor(ModelHealthStore healthStore) {
        this.healthStore = Objects.requireNonNull(healthStore, "ModelHealthStore 不能为空");
    }

    public <C, T> T executeWithFallback(String capability,
                                        List<ModelTarget> targets,
                                        Function<ModelTarget, C> clientResolver,
                                        ModelCaller<C, T> caller) {
        return executeWithFallback(capability, targets, clientResolver, caller, (target, error) -> {
        });
    }

    public <C, T> T executeWithFallback(String capability,
                                        List<ModelTarget> targets,
                                        Function<ModelTarget, C> clientResolver,
                                        ModelCaller<C, T> caller,
                                        BiConsumer<ModelTarget, Exception> failureHandler) {
        String label = StringUtils.defaultIfBlank(capability, "model");
        if (targets == null || targets.isEmpty()) {
            throw new RemoteException("没有可用 " + label + " 模型候选", BaseErrorCode.REMOTE_ERROR);
        }

        Exception last = null;
        for (ModelTarget target : targets) {
            C client = clientResolver.apply(target);
            if (client == null) {
                log.warn("event=actone_model_client_missing capability={} provider={} modelId={}",
                        label, target.candidate().getProvider(), target.id());
                continue;
            }
            if (!healthStore.allowCall(target.id())) {
                continue;
            }
            try {
                T response = caller.call(client, target);
                healthStore.markSuccess(target.id());
                return response;
            } catch (Exception ex) {
                last = ex;
                healthStore.markFailure(target.id());
                failureHandler.accept(target, ex);
            }
        }

        if (last instanceof RuntimeException runtimeException) {
            throw runtimeException;
        }
        throw new RemoteException("所有 " + label + " 模型候选均失败", last, BaseErrorCode.REMOTE_ERROR);
    }

    private static final class StringUtils {

        private StringUtils() {
        }

        private static String defaultIfBlank(String value, String fallback) {
            return value == null || value.isBlank() ? fallback : value.trim();
        }
    }
}
