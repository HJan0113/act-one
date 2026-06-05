package com.hjan.actone.script.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hjan.actone.common.context.UserContext;
import com.hjan.actone.common.exception.ClientException;
import com.hjan.actone.script.dao.entity.ScriptDraftDO;
import com.hjan.actone.script.dao.entity.ScriptProjectDO;
import com.hjan.actone.script.dao.mapper.ScriptDraftMapper;
import com.hjan.actone.script.dao.mapper.ScriptProjectMapper;
import com.hjan.actone.script.model.ScriptDraftDetailResponse;
import com.hjan.actone.script.model.ScriptDraftListItemResponse;
import com.hjan.actone.script.model.ScriptDraftSaveRequest;
import com.hjan.actone.script.model.ScriptDraftSaveResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;

@Service
public class ScriptDraftService {

    private final ScriptDraftMapper scriptDraftMapper;
    private final ScriptProjectMapper scriptProjectMapper;
    private final ScriptYamlValidator scriptYamlValidator;

    public ScriptDraftService(ScriptDraftMapper scriptDraftMapper,
                              ScriptProjectMapper scriptProjectMapper,
                              ScriptYamlValidator scriptYamlValidator) {
        this.scriptDraftMapper = scriptDraftMapper;
        this.scriptProjectMapper = scriptProjectMapper;
        this.scriptYamlValidator = scriptYamlValidator;
    }

    @Transactional(rollbackFor = Exception.class)
    public ScriptDraftSaveResponse save(ScriptDraftSaveRequest request) {
        ScriptYamlValidationResult validation = scriptYamlValidator.validate(request.yaml());
        String projectId = requireText(request.projectId(), "项目 ID 不能为空。");
        verifyProjectOwned(projectId);

        ScriptDraftDO draft = new ScriptDraftDO();
        draft.setProjectId(projectId);
        draft.setTitle(requireText(request.title(), "草稿标题不能为空。"));
        draft.setSchemaVersion(requireText(request.schemaVersion(), "Schema 版本不能为空。"));
        draft.setYamlContent(validation.cleanedYaml());
        draft.setValidationStatus(validation.status());
        draft.setValidationError(validation.errorMessage());
        scriptDraftMapper.insert(draft);
        return new ScriptDraftSaveResponse(
                draft.getId(),
                draft.getProjectId(),
                draft.getTitle(),
                draft.getSchemaVersion(),
                draft.getValidationStatus(),
                draft.getValidationError()
        );
    }

    public List<ScriptDraftListItemResponse> listByProjectId(String projectId) {
        if (!StringUtils.hasText(projectId)) {
            throw new ClientException("项目 ID 不能为空。");
        }
        verifyProjectOwned(projectId.trim());
        List<ScriptDraftDO> drafts = scriptDraftMapper.selectList(new LambdaQueryWrapper<ScriptDraftDO>()
                .eq(ScriptDraftDO::getProjectId, projectId.trim())
                .orderByDesc(ScriptDraftDO::getUpdateTime));
        return drafts.stream().map(this::toListItem).toList();
    }

    public ScriptDraftDetailResponse detail(String draftId) {
        if (!StringUtils.hasText(draftId)) {
            throw new ClientException("草稿 ID 不能为空。");
        }
        ScriptDraftDO draft = scriptDraftMapper.selectById(draftId.trim());
        if (draft == null) {
            throw new ClientException("草稿不存在。");
        }
        verifyProjectOwned(draft.getProjectId());
        return toDetail(draft);
    }

    public ScriptDraftDetailResponse exportYaml(String draftId) {
        return detail(draftId);
    }

    private ScriptDraftListItemResponse toListItem(ScriptDraftDO draft) {
        return new ScriptDraftListItemResponse(
                draft.getId(),
                draft.getProjectId(),
                draft.getTitle(),
                draft.getSchemaVersion(),
                draft.getValidationStatus(),
                draft.getCreateTime(),
                draft.getUpdateTime()
        );
    }

    private ScriptDraftDetailResponse toDetail(ScriptDraftDO draft) {
        return new ScriptDraftDetailResponse(
                draft.getId(),
                draft.getProjectId(),
                draft.getTitle(),
                draft.getSchemaVersion(),
                draft.getYamlContent(),
                draft.getValidationStatus(),
                draft.getValidationError(),
                draft.getCreateTime(),
                draft.getUpdateTime()
        );
    }

    private String requireText(String value, String message) {
        if (!StringUtils.hasText(value)) {
            throw new ClientException(message);
        }
        return value.trim();
    }

    private void verifyProjectOwned(String projectId) {
        ScriptProjectDO project = scriptProjectMapper.selectOne(new LambdaQueryWrapper<ScriptProjectDO>()
                .eq(ScriptProjectDO::getId, projectId)
                .eq(ScriptProjectDO::getCreatedBy, UserContext.requireUser().userId()));
        if (project == null) {
            throw new ClientException("项目不存在或无权访问");
        }
    }
}
