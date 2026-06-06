package com.hjan.actone.script.controller;

import com.hjan.actone.common.convention.Result;
import com.hjan.actone.common.web.Results;
import org.springframework.core.io.ClassPathResource;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

@RestController
@RequestMapping("/api/script/demo")
public class DemoController {

    private static final String DEMO_NOVEL_PATH = "demo/星轨回音.txt";

    @GetMapping("/novel")
    public Result<DemoNovelResponse> getDemoNovel() throws IOException {
        ClassPathResource resource = new ClassPathResource(DEMO_NOVEL_PATH);
        String text = resource.getContentAsString(StandardCharsets.UTF_8);
        return Results.success(new DemoNovelResponse(
                "星轨回音",
                "HJan",
                "在赛博朋克都市中，记忆打捞员林溯为寻找失踪妹妹，意外卷入意识篡改的惊天阴谋。",
                text
        ));
    }

    public record DemoNovelResponse(
            String title,
            String author,
            String logline,
            String text
    ) {
    }
}
