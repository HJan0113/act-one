package com.hjan.actone.script.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hjan.actone.common.context.LoginUser;
import com.hjan.actone.common.context.UserContext;
import com.hjan.actone.common.exception.ClientException;
import com.hjan.actone.script.dao.entity.ScriptChapterDO;
import com.hjan.actone.script.dao.entity.ScriptProjectDO;
import com.hjan.actone.script.dao.mapper.ScriptChapterMapper;
import com.hjan.actone.script.dao.mapper.ScriptProjectMapper;
import com.hjan.actone.script.model.NovelChapter;
import com.hjan.actone.script.model.ScriptProjectCreateRequest;
import com.hjan.actone.script.model.ScriptProjectCreateResponse;
import com.hjan.actone.script.model.ScriptProjectListItemResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;

@Service
public class ScriptProjectService {

    private static final String DEFAULT_STATUS = "draft";

    private final NovelChapterParser chapterParser;
    private final ScriptProjectMapper projectMapper;
    private final ScriptChapterMapper chapterMapper;

    public ScriptProjectService(NovelChapterParser chapterParser,
                                ScriptProjectMapper projectMapper,
                                ScriptChapterMapper chapterMapper) {
        this.chapterParser = chapterParser;
        this.projectMapper = projectMapper;
        this.chapterMapper = chapterMapper;
    }

    @Transactional(rollbackFor = Exception.class)
    public ScriptProjectCreateResponse createFromText(ScriptProjectCreateRequest request) {
        List<NovelChapter> chapters = chapterParser.parse(request.text());
        if (chapters.size() < NovelChapterParser.MIN_CHAPTER_COUNT) {
            throw new ClientException("至少需要 3 个章节，当前识别到 " + chapters.size() + " 个。");
        }

        ScriptProjectDO project = new ScriptProjectDO();
        project.setTitle(resolveTitle(request.title(), chapters));
        project.setDescription(trimToNull(request.description()));
        project.setStatus(DEFAULT_STATUS);
        LoginUser loginUser = UserContext.requireUser();
        project.setCreatedBy(loginUser.userId());
        project.setUpdatedBy(loginUser.userId());
        projectMapper.insert(project);

        for (NovelChapter chapter : chapters) {
            ScriptChapterDO chapterDO = new ScriptChapterDO();
            chapterDO.setProjectId(project.getId());
            chapterDO.setChapterIndex(chapter.index());
            chapterDO.setTitle(chapter.title());
            chapterDO.setContent(chapter.content());
            chapterDO.setCharCount(chapter.charCount());
            chapterDO.setContentHash(sha256(chapter.content()));
            chapterMapper.insert(chapterDO);
        }

        return new ScriptProjectCreateResponse(project.getId(), project.getTitle(), chapters.size(), chapters);
    }

    public List<ScriptProjectListItemResponse> listMine() {
        String userId = UserContext.requireUser().userId();
        List<ScriptProjectDO> projects = projectMapper.selectList(new LambdaQueryWrapper<ScriptProjectDO>()
                .eq(ScriptProjectDO::getCreatedBy, userId)
                .orderByDesc(ScriptProjectDO::getUpdateTime));
        return projects.stream().map(this::toListItem).toList();
    }

    private String resolveTitle(String title, List<NovelChapter> chapters) {
        if (StringUtils.hasText(title)) {
            return title.trim();
        }
        if (!chapters.isEmpty() && StringUtils.hasText(chapters.get(0).title())) {
            return chapters.get(0).title();
        }
        return "未命名项目";
    }

    private String trimToNull(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim();
    }

    private ScriptProjectListItemResponse toListItem(ScriptProjectDO project) {
        return new ScriptProjectListItemResponse(
                project.getId(),
                project.getTitle(),
                project.getDescription(),
                project.getStatus(),
                project.getCreateTime(),
                project.getUpdateTime()
        );
    }

    private String sha256(String content) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(content.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 算法不可用", e);
        }
    }
}
