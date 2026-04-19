package com.zhj.learn.aihelper.service.rag;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;

@Configuration
public class RagPostgresConfig {

    @Bean(name = "ragDataSourceProperties")
    @ConfigurationProperties(prefix = "app.rag.postgres")
    public DataSourceProperties ragDataSourceProperties() {
        return new DataSourceProperties();
    }

    @Bean(name = "ragDataSource")
    public DataSource ragDataSource(@Qualifier("ragDataSourceProperties") DataSourceProperties properties) {
        return properties.initializeDataSourceBuilder().build();
    }

    @Bean(name = "ragJdbcTemplate")
    public JdbcTemplate ragJdbcTemplate(@Qualifier("ragDataSource") DataSource dataSource) {
        return new JdbcTemplate(dataSource);
    }
}

