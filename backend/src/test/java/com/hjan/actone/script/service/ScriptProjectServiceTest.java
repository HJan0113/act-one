package com.hjan.actone.script.service;

import com.hjan.actone.common.context.LoginUser;
import com.hjan.actone.common.context.UserContext;
import com.hjan.actone.common.exception.ClientException;
import com.hjan.actone.script.dao.entity.ScriptChapterDO;
import com.hjan.actone.script.dao.entity.ScriptProjectDO;
import com.hjan.actone.script.dao.mapper.ScriptChapterMapper;
import com.hjan.actone.script.dao.mapper.ScriptProjectMapper;
import com.hjan.actone.script.model.ScriptProjectCreateRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ScriptProjectServiceTest {

    private final NovelChapterParser chapterParser = new NovelChapterParser();
    private final ScriptProjectMapper projectMapper = mock(ScriptProjectMapper.class);
    private final ScriptChapterMapper chapterMapper = mock(ScriptChapterMapper.class);
    private final ScriptProjectService service = new ScriptProjectService(chapterParser, projectMapper, chapterMapper);

    @BeforeEach
    void setUp() {
        UserContext.set(new LoginUser("user_1", "admin", "admin", ""));
    }

    @AfterEach
    void tearDown() {
        UserContext.clear();
    }

    @Test
    void createFromTextPersistsProjectAndChapters() {
        when(projectMapper.insert(any(ScriptProjectDO.class))).thenAnswer(invocation -> {
            ScriptProjectDO project = invocation.getArgument(0);
            project.setId("project_1");
            return 1;
        });
        when(chapterMapper.insert(any(ScriptChapterDO.class))).thenReturn(1);

        var response = service.createFromText(new ScriptProjectCreateRequest("测试项目", null, validNovelText()));

        assertThat(response.projectId()).isEqualTo("project_1");
        assertThat(response.chapterCount()).isEqualTo(3);
        ArgumentCaptor<ScriptProjectDO> captor = ArgumentCaptor.forClass(ScriptProjectDO.class);
        verify(projectMapper).insert(captor.capture());
        assertThat(captor.getValue().getCreatedBy()).isEqualTo("user_1");
        assertThat(captor.getValue().getUpdatedBy()).isEqualTo("user_1");
        verify(chapterMapper, times(3)).insert(any(ScriptChapterDO.class));
    }

    @Test
    void listMineReturnsOnlyCurrentUserProjects() {
        ScriptProjectDO project = new ScriptProjectDO();
        project.setId("project_1");
        project.setTitle("测试项目");
        project.setDescription("描述");
        project.setStatus("draft");
        when(projectMapper.selectList(any())).thenReturn(List.of(project));

        var response = service.listMine();

        assertThat(response).hasSize(1);
        assertThat(response.get(0).projectId()).isEqualTo("project_1");
        assertThat(response.get(0).title()).isEqualTo("测试项目");
    }

    @Test
    void createFromTextRejectsLessThanThreeChapters() {
        assertThatThrownBy(() -> service.createFromText(new ScriptProjectCreateRequest(null, null, """
                第 1 章
                内容
                第 2 章
                内容
                """)))
                .isInstanceOf(ClientException.class)
                .hasMessageContaining("至少需要 3 个章节");
    }

    @Test
    void createFromTextUsesFirstChapterTitleWhenProjectTitleBlank() {
        when(projectMapper.insert(any(ScriptProjectDO.class))).thenAnswer(invocation -> {
            ScriptProjectDO project = invocation.getArgument(0);
            project.setId("project_2");
            return 1;
        });
        when(chapterMapper.insert(any(ScriptChapterDO.class))).thenReturn(1);
        ArgumentCaptor<ScriptProjectDO> captor = ArgumentCaptor.forClass(ScriptProjectDO.class);

        service.createFromText(new ScriptProjectCreateRequest(" ", null, validNovelText()));

        verify(projectMapper).insert(captor.capture());
        assertThat(captor.getValue().getTitle()).isEqualTo("第 1 章 开端");
    }

    private String validNovelText() {
        return """
                第 1 章 开端
                第一章内容。

                第 2 章 发展
                第二章内容。

                第 3 章 选择
                第三章内容。
                """;
    }
}
