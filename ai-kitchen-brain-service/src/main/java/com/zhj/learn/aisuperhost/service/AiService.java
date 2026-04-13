package com.zhj.learn.aisuperhost.service;

import com.zhj.learn.aisuperhost.domain.WindowTurn;
import jakarta.annotation.Resource;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class AiService {

    @Resource
    private ChatClient qwenChatClient;

    @Resource
    private ChatClient deepSeekChatClient;

    @Value("classpath:template/summary-generate.st")
    private org.springframework.core.io.Resource summaryTemplate;

    //多轮对话记忆
    public String chat(String memoryId, String message){
        return qwenChatClient.prompt().user(message)
                .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, memoryId)
                        .param("raw_user_input", message))
                .call().content();
    }

    // 触发摘要生成（滚动摘要：旧摘要 + 本次增量对话）
    public String generateRollingSummary(String oldSummary, List<WindowTurn> incrementalDialogue) {
        PromptTemplate promptTemplate = new PromptTemplate(summaryTemplate);

        Map<String, Object> vars = new HashMap<>();
        vars.put("old_summary", safeText(oldSummary));
        vars.put("incremental_dialogue", buildIncrementalDialogue(incrementalDialogue));

        String systemPrompt = promptTemplate.render(vars);
        String result = deepSeekChatClient.prompt()
                .system(systemPrompt)
                .call()
                .content();

        return safeText(result);
    }

    private String safeText(String text) {
        return text == null ? "" : text.trim();
    }

    private String buildIncrementalDialogue(List<WindowTurn> turns) {
        if (turns == null || turns.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (WindowTurn turn : turns) {
            if (turn == null) {
                continue;
            }
            sb.append("第").append(turn.getTurnNo() == null ? "-" : turn.getTurnNo()).append("轮\n");
            sb.append("用户: ").append(safeText(turn.getUserContent())).append("\n");
            sb.append("助手: ").append(safeText(turn.getAssistantContent())).append("\n\n");
        }
        return sb.toString().trim();
    }


}
