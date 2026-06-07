package com.hjan.actone.script.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hjan.actone.common.exception.ClientException;
import com.hjan.actone.script.dao.entity.ScriptDraftDO;
import com.hjan.actone.script.dao.mapper.ScriptDraftMapper;
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

    private static final String VALIDATION_STATUS_UNCHECKED = "unchecked";

    private final ScriptDraftMapper scriptDraftMapper;

    public ScriptDraftService(ScriptDraftMapper scriptDraftMapper) {
        this.scriptDraftMapper = scriptDraftMapper;
    }

    @Transactional(rollbackFor = Exception.class)
    public ScriptDraftSaveResponse save(ScriptDraftSaveRequest request) {
        ScriptDraftDO draft = new ScriptDraftDO();
        draft.setProjectId(request.projectId().trim());
        draft.setTitle(request.title().trim());
        draft.setSchemaVersion(request.schemaVersion().trim());
        draft.setYamlContent(request.yaml().trim());
        draft.setValidationStatus(VALIDATION_STATUS_UNCHECKED);
        scriptDraftMapper.insert(draft);
        return new ScriptDraftSaveResponse(
                draft.getId(),
                draft.getProjectId(),
                draft.getTitle(),
                draft.getSchemaVersion(),
                draft.getValidationStatus()
        );
    }

    public List<ScriptDraftListItemResponse> listByProjectId(String projectId) {
        if (!StringUtils.hasText(projectId)) {
            throw new ClientException("项目 ID 不能为空。");
        }
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
        return toDetail(draft);
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
}
