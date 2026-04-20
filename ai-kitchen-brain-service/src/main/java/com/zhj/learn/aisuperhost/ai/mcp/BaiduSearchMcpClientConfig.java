package com.zhj.learn.aisuperhost.ai.mcp;

import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.WebClientStreamableHttpTransport;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
@Slf4j
@ConditionalOnProperty(prefix = "app.mcp.baidu", name = "enabled", havingValue = "true")
public class BaiduSearchMcpClientConfig {

    @Bean
    public McpSyncClient baiduWebSearchMcpSyncClient(BaiduMcpProperties properties) {
        if (!StringUtils.hasText(properties.getApiKey())) {
            throw new IllegalStateException("Baidu MCP api-key is empty. Please configure app.mcp.baidu.api-key");
        }
        WebClient.Builder webClientBuilder=WebClient.builder().baseUrl(properties.getUrl()).defaultHeader(
                HttpHeaders.AUTHORIZATION,toBearerToken(properties.getApiKey()));
        var transport=WebClientStreamableHttpTransport.builder(webClientBuilder).endpoint(properties.getEndpoint()).build();

        McpSyncClient client=McpClient.sync(transport).build();
        client.initialize();
        log.info("Baidu MCP client initialized,server={},tools={}", client.getServerInfo(), client.listTools());
        return client;
    }

    private String toBearerToken(String rawToken) {
        if (rawToken.startsWith("Bearer ")) {
            return rawToken;
        }
        return "Bearer " + rawToken;
    }
}
