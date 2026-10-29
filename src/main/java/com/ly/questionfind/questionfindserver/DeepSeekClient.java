package com.ly.questionfind.questionfindserver;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class DeepSeekClient {

    private static final String SYSTEM_PROMPT =
            "帮我解答题目，给出题号和对应的选项按{题号.选项}的格式返回，例如{1.A}{2.B}{3.A、B}，返回了3个题，每个题的题号和选项对应，不需要返回其他解释说明；如果是多选，你需要给出多个选项；如果当前题目不完整或者选项不完整或者选项数量不够，你可以直接忽略不返回该题答案；下面的内容中可能有一些跟题目无关的内容，如果有一些跟题目无关的内容，你可以忽略。";

    private final RestClient restClient;
    private final String apiKey;
    private final String apiUrl;
    private final String model;

    public DeepSeekClient(
            @Value("${deepseek.api-key}") String apiKey,
            @Value("${deepseek.api-url}") String apiUrl,
            @Value("${deepseek.model}") String model
    ) {
        this.apiKey = apiKey;
        this.apiUrl = apiUrl;
        this.model = model;
        this.restClient = RestClient.create();
    }

    public String call(String question) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("DeepSeek API Key 未配置，请在启动服务端时设置环境变量 DEEPSEEK_API_KEY");
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", model);
        body.put("stream", false);
        body.put("messages", List.of(
                Map.of("role", "system", "content", SYSTEM_PROMPT),
                Map.of("role", "user", "content", question)
        ));
        body.put("thinking", Map.of("type", "enabled"));
        body.put("reasoning_effort", "high");

        Map<?, ?> response = restClient.post()
                .uri(apiUrl)
                .contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", "Bearer " + apiKey)
                .body(body)
                .retrieve()
                .body(Map.class);

        return extractContent(response);
    }

    @SuppressWarnings("unchecked")
    private String extractContent(Map<?, ?> response) {
        if (response == null) {
            return "";
        }
        Object choices = response.get("choices");
        if (!(choices instanceof List<?> list) || list.isEmpty()) {
            return "";
        }
        Object first = list.get(0);
        if (!(first instanceof Map<?, ?> choice)) {
            return "";
        }
        Object message = choice.get("message");
        if (!(message instanceof Map<?, ?> messageMap)) {
            return "";
        }
        Object content = messageMap.get("content");
        return content == null ? "" : content.toString();
    }
}
