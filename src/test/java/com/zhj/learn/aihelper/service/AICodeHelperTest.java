package com.zhj.learn.aihelper.service;
import dev.langchain4j.data.message.ImageContent;
import dev.langchain4j.data.message.TextContent;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatLanguageModel;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class AICodeHelperTest {

    @Resource
    private ChatLanguageModel qwenChatModel;

    @Resource
    private AICodeHelperService aiCodeHelperService;

    @Resource
    private AICodeHelper aiCodeHelper;

    @Test
    void testChat() {
        String userInput = "你好";
        String result = aiCodeHelper.chat(userInput);
        System.out.println("请求信息: " + userInput);
        System.out.println("模型返回: " + result);
    }

    @Test
    void testChatWithImage() {
        UserMessage userMessage = UserMessage.from(
                TextContent.from("你好，帮我分析一下这张图片的内容"),
                ImageContent.from("https://bkimg.cdn.bcebos.com/pic/b21bb051f819861841bb490948ed2e738ad4e6bb")
        );

        String result = aiCodeHelper.chatWithImage(userMessage);
        System.out.println("请求信息: 你好，帮我分析一下这张图片的内容");
        System.out.println("模型返回: " + result);
    }

    //带会话记忆的聊天
    @Test
    void testChatWithService() {
        String userInput = "你好，我是小张？";
        String result = aiCodeHelper.chatWithService(userInput);
        System.out.println("请求信息: " + userInput);
        System.out.println("模型返回: " + result);
        String userInput2 = "你好，我是谁？";
        String result2 = aiCodeHelper.chatWithService(userInput);
        System.out.println("请求信息: " + userInput2);
        System.out.println("模型返回: " + result2);
    }
}
