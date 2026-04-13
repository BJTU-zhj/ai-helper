package com.zhj.learn.aisuperhost.ai.advisor;

import com.zhj.learn.aisuperhost.domain.ChatSummary;
import com.zhj.learn.aisuperhost.domain.WindowTurn;
import com.zhj.learn.aisuperhost.service.ChatHistoryService;
import com.zhj.learn.aisuperhost.service.ChatSummaryService;
import com.zhj.learn.aisuperhost.service.RedisMemoryService;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.AdvisorChain;
import org.springframework.ai.chat.client.advisor.api.BaseChatMemoryAdvisor;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class MemoryLoadAdvisor implements BaseChatMemoryAdvisor {


    @Autowired
    private RedisMemoryService redisMemoryService;

    @Autowired
    private ChatSummaryService chatSummaryService;

    @Autowired
    private ChatHistoryService chatHistoryService;

    @Value("classpath:template/memory-context.st")
    private Resource memoryContext;

    @Override
    public String getName() {
        return BaseChatMemoryAdvisor.super.getName();
    }

    //执行自定义记忆逻辑，查redis获取本次的对话记忆
    @Override
    public ChatClientRequest before(ChatClientRequest chatClientRequest, AdvisorChain advisorChain) {

        //1、先获取对话id
        Map<String, Object> chatContext=chatClientRequest.context();
        String sessionId = getConversationId(chatContext,"default");

        //2、刷新redis或者直接从redis中获取摘要和窗口会话

        //先查redis-摘要,如果为空则从数据库查摘要并更新redis
        String summary = redisMemoryService.getSummary(sessionId);
        if (summary == null || summary.isBlank()) {
            ChatSummary chatSummary = chatSummaryService.getBySessionId(sessionId);
            summary = (chatSummary == null ? "" : chatSummary.getSummaryContent());
            redisMemoryService.saveSummary(sessionId, summary);
        }
        //查redis-窗口会话，如果为空则从数据库查窗口会话并更新redis
        List<WindowTurn> windowTurns = redisMemoryService.getWindowTurns(sessionId);
        if (windowTurns == null || windowTurns.isEmpty()) {
            windowTurns = chatHistoryService.getWindowTurnsForRedis(sessionId, redisMemoryService.getWindowSize());
            if (windowTurns != null && !windowTurns.isEmpty()) {
                redisMemoryService.appendWindowTurn(sessionId, windowTurns);
            }
        }

        // 3) 渲染模板并注入 memory_block
        String windowTurnsText = formatWindowTurns(windowTurns);

        Map<String, Object> vars = new HashMap<>();
        vars.put("summary", summary == null ? "" : summary);
        vars.put("window_turns", windowTurnsText);

        PromptTemplate memoryPromptTemplate = new PromptTemplate(memoryContext);

        String memoryBlock = memoryPromptTemplate.render(vars);

        Map<String, Object> newContext = new HashMap<>(chatContext);
        newContext.put("memory_block", memoryBlock);

        return chatClientRequest.mutate().context(newContext).build();
    }

    @Override
    public ChatClientResponse after(ChatClientResponse chatClientResponse, AdvisorChain advisorChain) {
        return chatClientResponse;
    }

    @Override
    public int getOrder() {
        return 100;
    }


    //格式化窗口会话
    private String formatWindowTurns(List<WindowTurn> turns) {
        if (turns == null || turns.isEmpty()) {
            return "（暂无最近窗口对话）";
        }
        StringBuilder sb = new StringBuilder();
        for (WindowTurn t : turns) {
            sb.append("第").append(t.getTurnNo() == null ? "-" : t.getTurnNo()).append("轮\n")
                    .append("用户: ").append(t.getUserContent() == null ? "" : t.getUserContent()).append("\n")
                    .append("助手: ").append(t.getAssistantContent() == null ? "" : t.getAssistantContent()).append("\n\n");
        }
        return sb.toString();
    }


}
