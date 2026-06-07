package com.hjan.actone.common.log;

import com.hjan.actone.common.errorcode.BaseErrorCode;
import com.hjan.actone.common.exception.RemoteException;
import com.hjan.actone.infra.ai.error.ModelClientErrorType;
import com.hjan.actone.infra.ai.error.ModelClientException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AiExceptionDiagnosticsTest {

    @Test
    void exposesSafeConfigurationCauseWithoutSecrets() {
        ModelClientException modelError = new ModelClientException(
                "Spring AI provider apiKey 未配置",
                ModelClientErrorType.CONFIGURATION_ERROR,
                null
        );
        RemoteException mapped = new RemoteException(
                "AI 模型配置不可用，请联系管理员",
                modelError,
                BaseErrorCode.REMOTE_ERROR
        );

        assertThat(AiExceptionDiagnostics.safeMessage(mapped))
                .isEqualTo("AI 模型配置不可用，请联系管理员");
        assertThat(AiExceptionDiagnostics.modelErrorType(mapped))
                .isEqualTo("CONFIGURATION_ERROR");
        assertThat(AiExceptionDiagnostics.rootCauseSafeMessage(mapped))
                .isEqualTo("Spring AI provider apiKey 未配置");
    }

    @Test
    void suppressesUnsafeProviderMessageBody() {
        ModelClientException modelError = new ModelClientException(
                "HTTP body contains api-key=secret and source novel",
                ModelClientErrorType.SERVER_ERROR,
                500
        );

        assertThat(AiExceptionDiagnostics.safeMessage(modelError)).isNull();
        assertThat(AiExceptionDiagnostics.rootCauseSafeMessage(modelError)).isNull();
    }
}
