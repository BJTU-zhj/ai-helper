package com.zhj.learn.aisuperhost.ai.mcp;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "app.mcp.baidu")
@Getter
@Setter
public class BaiduMcpProperties {

    private boolean enabled;

    private String url;

    private String endpoint;

    private String apiKey;
}
