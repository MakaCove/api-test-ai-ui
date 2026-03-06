package org.example.common.ai;

import com.openai.client.OpenAIClient;
import com.openai.client.okhttp.OpenAIOkHttpClient;
import com.openai.models.chat.completions.ChatCompletion;
import com.openai.models.chat.completions.ChatCompletionCreateParams;
import com.openai.models.chat.completions.ChatCompletionMessageParam;
import com.openai.models.chat.completions.ChatCompletionSystemMessageParam;
import com.openai.models.chat.completions.ChatCompletionUserMessageParam;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.config.AiConfig;
import org.example.settings.service.SettingsService;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class AiClientService {

    private final AiConfig aiConfig;
    private final SettingsService settingsService;

    private OpenAIClient createClient() {
        String endpoint = settingsService.get("ai.endpoint");
        if (endpoint == null || endpoint.isBlank()) {
            endpoint = aiConfig.getEndpoint();
        }
        String apiKey = settingsService.get("ai.api-key");
        if (apiKey == null || apiKey.isBlank()) {
            apiKey = aiConfig.getApiKey();
        }
        return OpenAIOkHttpClient.builder()
                .baseUrl(endpoint)
                .apiKey(apiKey)
                .build();
    }

    private String chat(String promptSystem, String promptUser) {
        try {
            OpenAIClient client = createClient();

            List<ChatCompletionMessageParam> messages = new ArrayList<>();
            if (promptSystem != null && !promptSystem.isEmpty()) {
                messages.add(ChatCompletionMessageParam.ofSystem(
                        ChatCompletionSystemMessageParam.builder().content(promptSystem).build()
                ));
            }
            messages.add(ChatCompletionMessageParam.ofUser(
                    ChatCompletionUserMessageParam.builder().content(promptUser).build()
            ));

            Double cfgTemp = aiConfig.getTemperature() != null ? aiConfig.getTemperature() : 0.3;
            String tempStr = settingsService.get("ai.temperature");
            double temperature = cfgTemp;
            if (tempStr != null) {
                try {
                    temperature = Double.parseDouble(tempStr);
                } catch (NumberFormatException ignored) {
                }
            }

            Integer cfgMax = aiConfig.getMaxTokens() != null ? aiConfig.getMaxTokens() : 4096;
            String maxStr = settingsService.get("ai.max-tokens");
            int maxTokens = cfgMax;
            if (maxStr != null) {
                try {
                    maxTokens = Integer.parseInt(maxStr);
                } catch (NumberFormatException ignored) {
                }
            }

            ChatCompletionCreateParams params = ChatCompletionCreateParams.builder()
                    .model(resolveModel())
                    .messages(messages)
                    .temperature(temperature)
                    .maxTokens(maxTokens)
                    .build();

            ChatCompletion completion = client.chat().completions().create(params);
            String content = completion.choices().get(0).message().content().orElse("");
            log.debug("AI 返回内容: {}", content);
            return content;
        } catch (Exception e) {
            log.error("调用 AI 接口失败: {}", e.getMessage(), e);
            throw new RuntimeException("调用 AI 接口失败: " + e.getMessage(), e);
        }
    }

    /**
     * 文档分析：输出接口列表 JSON
     */
    public String analyzeDocumentToApis(String promptSystem, String promptUser) {
        return chat(promptSystem, promptUser);
    }

    /**
     * 测试代码生成：输出测试代码文本
     */
    public String generateTestCode(String promptSystem, String promptUser) {
        return chat(promptSystem, promptUser);
    }

    private String resolveModel() {
        String modelFromDb = settingsService.get("ai.model");
        if (modelFromDb != null && modelFromDb.isBlank() == false) {
            return modelFromDb;
        }
        return aiConfig.getModel();
    }
}
