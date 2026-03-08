package com.rumal.analytics_service.config;

import com.rumal.analytics_service.service.AnalyticsLiveMessageSubscriber;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

@Configuration
public class AnalyticsLiveRedisConfig {

    @Bean
    public RedisMessageListenerContainer analyticsLiveRedisMessageListenerContainer(
            RedisConnectionFactory redisConnectionFactory,
            AnalyticsLiveMessageSubscriber analyticsLiveMessageSubscriber,
            @Value("${analytics.live.redis-channel:analytics:live:dashboard:v1}") String analyticsLiveRedisChannel
    ) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(redisConnectionFactory);
        container.addMessageListener(analyticsLiveMessageSubscriber, new ChannelTopic(analyticsLiveRedisChannel));
        return container;
    }
}
