package com.zhj.learn.aisuperhost.ai.mcp;


import io.modelcontextprotocol.client.McpSyncClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.mcp.SyncMcpToolCallbackProvider;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Configuration
@Slf4j
public class MyMcpProvider {

    /**
     * 创建一个高德MCP回调提供者
     * @param mcpSyncClients
     * @return
     */
    @Bean
    public SyncMcpToolCallbackProvider myMcpToolCallbackProvider(
            ObjectProvider<McpSyncClient> mcpSyncClientProvider,
            @Autowired(required = false) @Qualifier("baiduWebSearchMcpSyncClient") McpSyncClient baiduWebSearchMcpSyncClient){
        Set<McpSyncClient> uniqueClients = new LinkedHashSet<>(mcpSyncClientProvider.orderedStream().toList());
        if (baiduWebSearchMcpSyncClient != null) {
            uniqueClients.add(baiduWebSearchMcpSyncClient);
        }
        List<McpSyncClient> mcpSyncClients = uniqueClients.stream().toList();
        for (McpSyncClient client : mcpSyncClients) {
            try {
                client.initialize();
                log.info("MCP client initialized in provider. server={}, tools={}",
                        client.getServerInfo(), client.listTools());
            } catch (Exception e) {
                log.warn("MCP client initialize failed in provider. server={}",
                        client.getServerInfo(), e);
            }
        }
        List<String> serverNames = mcpSyncClients.stream()
                .map(client -> client.getServerInfo() == null ? "unknown" : String.valueOf(client.getServerInfo().name()))
                .toList();
        log.info("MCP sync clients discovered: count={}, servers={}", mcpSyncClients.size(), serverNames);
        if (mcpSyncClients.isEmpty()) {
            log.warn("No MCP sync client discovered. ChatClient will have no MCP tools.");
        }
        log.info("MCP clients mounted to ChatClient: count={}", mcpSyncClients.size());
        return new SyncMcpToolCallbackProvider(mcpSyncClients);
    }
}
