package com.zhj.learn.aisuperhost.mq;

import org.apache.rocketmq.client.producer.SendResult;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class SummaryTaskProducer {
    private static final Logger LOG = LoggerFactory.getLogger(SummaryTaskProducer.class);

    private static final String TOPIC = "brain_summary_delay";
    private static final String TAG = "generate";

    private final RocketMQTemplate rocketMQTemplate;

    // RocketMQ 固定延迟等级（示例：3=10s，可按你需要调整）
    @Value("${summary.mq.delay-level:3}")
    private int delayLevel;

    public SummaryTaskProducer(RocketMQTemplate rocketMQTemplate) {
        this.rocketMQTemplate = rocketMQTemplate;
    }

    //顺序生产根据sessionId
    public SendResult sendOrderly(SummaryGenerateEvent event) {

        if (event == null || event.getSessionId() == null || event.getSessionId().isBlank()) {
            throw new IllegalArgumentException("event/sessionId must not be blank");
        }

        String destination = TOPIC + ":" + TAG;
        String key = event.getSessionId();
        LOG.info("MQ produce summary task begin. eventId={}, sessionId={}, turnNo={}, destination={}, key={}",
                event.getEventId(), event.getSessionId(), event.getEvictedTurnNo(), destination, key);
        SendResult result = rocketMQTemplate.syncSendOrderly(destination, event, key, 3000L);
        LOG.info("MQ produce summary task success. eventId={}, msgId={}, status={}",
                event.getEventId(), result == null ? null : result.getMsgId(), result == null ? null : result.getSendStatus());
        return result;
    }
}
