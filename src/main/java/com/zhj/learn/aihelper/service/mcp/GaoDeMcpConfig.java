package com.zhj.learn.aihelper.service.mcp;

import dev.langchain4j.mcp.McpToolProvider;
import dev.langchain4j.mcp.client.DefaultMcpClient;
import dev.langchain4j.mcp.client.McpClient;
import dev.langchain4j.mcp.client.transport.McpTransport;
import dev.langchain4j.mcp.client.transport.http.HttpMcpTransport;
import dev.langchain4j.mcp.client.transport.http.StreamableHttpMcpTransport;
import dev.langchain4j.mcp.client.transport.stdio.StdioMcpTransport;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/*
 * 高德地图配置
 * 高德地图提供的 Streamable HTTP 模式在底层是基于 SSE (Server-Sent Events) 协议的。
 * 在 LangChain4j 中，接入这种模式的 Java 代码实现主要分为三个步骤：
 * 配置传输层、创建 MCP 客户端、挂载到 AI Service。
 */


@Configuration
public class GaoDeMcpConfig {

    @Value("${gaodeditu.api-key}")
    private String apiKey;

    @Value("${gaodeditu.map-url}")
    private String mapUrl;

    @Bean
    public McpToolProvider mcpToolProvider() {
        //定义url
        String gaoDeMapUrl=mapUrl+apiKey;
        //定义传输层
        McpTransport transport= StreamableHttpMcpTransport.builder()
                .url(gaoDeMapUrl)
                .timeout(Duration.ofSeconds(60))
                .build();
        //创建客户端
        McpClient client= DefaultMcpClient.builder().transport( transport).build();

        //获取工具
        return McpToolProvider.builder().mcpClients(client).build();
    }

    @Bean
    public McpToolProvider mcpToolProviderLocal(){
        //映射json中的配置
        Map<String,String> env=new HashMap<>();
        env.put("AMAP_MAPS_API_KEY", apiKey);
        //配置传输层
        McpTransport transport= StdioMcpTransport.builder()
                .command(List.of("cmd", "/c", "npx", "-y", "@amap/amap-maps-mcp-server"))
                .environment(env)
                .build();
        //配置客户端
        McpClient client= DefaultMcpClient.builder().transport(transport).build();
        //配置工具
        return McpToolProvider.builder().mcpClients(client)
                .failIfOneServerFails(true)
                .build();
    }

}
