package com.zhj.learn.aigateway.filter;

import java.net.URI;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;

import reactor.core.publisher.Mono;

import static org.springframework.cloud.gateway.support.ServerWebExchangeUtils.GATEWAY_REQUEST_URL_ATTR;
import static org.springframework.cloud.gateway.support.ServerWebExchangeUtils.GATEWAY_ROUTE_ATTR;

@Component
public class GatewayForwardLogFilter implements GlobalFilter, Ordered {

    private static final Logger LOG = LoggerFactory.getLogger(GatewayForwardLogFilter.class);

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, org.springframework.cloud.gateway.filter.GatewayFilterChain chain) {
        URI requestUri = exchange.getRequest().getURI();
        String method = exchange.getRequest().getMethod().name();
        long startTime = System.currentTimeMillis();

        return chain.filter(exchange).doFinally(signalType -> {
            Route route = exchange.getAttribute(GATEWAY_ROUTE_ATTR);
            URI targetUri = exchange.getAttribute(GATEWAY_REQUEST_URL_ATTR);

            String routeId = route != null ? route.getId() : "unmatched";
            String forwardTo = targetUri != null ? targetUri.toString() : "N/A";
            Integer statusCode = exchange.getResponse().getStatusCode() != null
                    ? exchange.getResponse().getStatusCode().value() : null;
            long costMs = System.currentTimeMillis() - startTime;

            LOG.info("[Gateway-Forward] {} {} -> route={} -> target={} status={} cost={}ms",
                    method, requestUri, routeId, forwardTo, statusCode, costMs);
        });
    }

    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE;
    }
}

