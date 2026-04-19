package com.zhj.learn.aisuperhost.ai.rag;

import jakarta.annotation.PostConstruct;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.rag.Query;
import org.springframework.ai.rag.preretrieval.query.transformation.QueryTransformer;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;
import org.springframework.util.StreamUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

@Component
public class MyQueryTransformer implements QueryTransformer {

    private final ChatClient deepSeekChatClient;

    @Value("classpath:template/query-rewrite.st")
    private Resource systemTemplate;

    private String rewriteSystemPrompt;

    MyQueryTransformer(@Qualifier("deepSeekChatClient") ChatClient deepSeekChatClient){
        this.deepSeekChatClient = deepSeekChatClient;
    }

    @PostConstruct
    public void initRewritePrompt() throws IOException {
        this.rewriteSystemPrompt = StreamUtils.copyToString(systemTemplate.getInputStream(), StandardCharsets.UTF_8).trim();
    }

    @Override
    public Query transform(Query query) {
        List<Message> history = query.history() == null ? List.of() : query.history();

        // 1) 基于现有 history 构造“查询改写输入消息”
        // 规则：仅替换第一条 system（原 system-prompt-v1.st）为 query-rewrite.st，其余保持不变。
        List<Message> rewriteMessages = buildRewriteMessages(history, query.text());

        // 2) 调用改写模型
        String rewriteText = deepSeekChatClient.prompt().messages(rewriteMessages).call().content();
        if (rewriteText == null || rewriteText.isBlank()) {
            rewriteText = query.text();
        }
        rewriteText = rewriteText.trim();

        // 3) 生成下游使用的 history：将“最后一条用户提问”替换为改写后的提问
        List<Message> rewrittenHistory = replaceLastUserMessage(history, rewriteText);

        // 4) 下游继续传递 context/history/text，保持链路信息完整
        return query.mutate()
                .text(rewriteText)
                .history(rewrittenHistory)
                .build();
    }

    private List<Message> buildRewriteMessages(List<Message> history, String rawQueryText) {
        List<Message> rewriteMessages = new ArrayList<>();

        if (history.isEmpty()) {
            rewriteMessages.add(new SystemMessage(rewriteSystemPrompt));
            rewriteMessages.add(new UserMessage(rawQueryText));
            return rewriteMessages;
        }

        boolean firstSystemReplaced = false;
        for (Message msg : history) {
            if (!firstSystemReplaced && msg instanceof SystemMessage) {
                rewriteMessages.add(new SystemMessage(rewriteSystemPrompt));
                firstSystemReplaced = true;
                continue;
            }
            rewriteMessages.add(msg);
        }

        if (!firstSystemReplaced) {
            rewriteMessages.add(0, new SystemMessage(rewriteSystemPrompt));
        }

        if (rewriteMessages.stream().noneMatch(UserMessage.class::isInstance)) {
            rewriteMessages.add(new UserMessage(rawQueryText));
        }

        return rewriteMessages;
    }

    private List<Message> replaceLastUserMessage(List<Message> history, String rewrittenText) {
        List<Message> result = new ArrayList<>(history == null ? List.of() : history);
        for (int i = result.size() - 1; i >= 0; i--) {
            if (result.get(i) instanceof UserMessage) {
                result.set(i, new UserMessage(rewrittenText));
                return result;
            }
        }
        result.add(new UserMessage(rewrittenText));
        return result;
    }
}
