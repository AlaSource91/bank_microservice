package com.alaeldin.api_gateway.filter;

import org.springframework.boot.autoconfigure.web.WebProperties;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import java.util.Optional;
import java.util.UUID;

@Component
public class CorrelationIdFilter implements GlobalFilter
{
    public static final String CORRELATION_ID  = "X-Correlation-Id";
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {

        String correlationId = Optional.ofNullable(exchange.getRequest().getHeaders().getFirst(CORRELATION_ID))
                .orElse(UUID.randomUUID().toString());

        ServerHttpRequest mutatedRequest = exchange.getRequest().mutate()
                .header(CORRELATION_ID,correlationId)
                .build();

        ServerWebExchange  mutatedExchange = exchange.mutate().request(mutatedRequest).build();

        return chain.filter(mutatedExchange);
    }
}
