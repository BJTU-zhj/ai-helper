package com.zhj.learn.aisuperhost.mq;


import com.zhj.learn.aisuperhost.service.MemoryPersistService;
import org.apache.rocketmq.spring.annotation.ConsumeMode;
import org.apache.rocketmq.spring.annotation.MessageModel;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
@RocketMQMessageListener(
        topic = "brain_summary_delay",
        selectorExpression = "generate",
        consumerGroup = "summary-generate-consumer",
        consumeMode = ConsumeMode.ORDERLY,
        messageModel = MessageModel.CLUSTERING
        )

public class SummaryGenerateConsumer implements RocketMQListener<SummaryGenerateEvent> {

    private static final Logger LOG = LoggerFactory.getLogger(SummaryGenerateConsumer.class);

    private final MemoryPersistService memoryPersistService;

    public SummaryGenerateConsumer(MemoryPersistService memoryPersistService) {
        this.memoryPersistService = memoryPersistService;
    }

    @Override
    public void onMessage(SummaryGenerateEvent event) {
        long start = System.currentTimeMillis();
        try {
            LOG.info("MQ consume summary task begin. eventId={}, sessionId={}, turnNo={}",
                    event == null ? null : event.getEventId(),
                    event == null ? null : event.getSessionId(),
                    event == null ? null : event.getEvictedTurnNo());
            memoryPersistService.handleSummaryGenerateEvent(event);
            long costMs = System.currentTimeMillis() - start;
            LOG.info("MQ consume summary task success. eventId={}, sessionId={}, turnNo={}",
                    event == null ? null : event.getEventId(),
                    event == null ? null : event.getSessionId(),
                    event == null ? null : event.getEvictedTurnNo());
            LOG.info("MQ consume summary task cost. eventId={}, costMs={}",
                    event == null ? null : event.getEventId(), costMs);
        } catch (IllegalArgumentException e) {
            // 不可重试错误：记录并吞掉，避免无意义重试
            long costMs = System.currentTimeMillis() - start;
            LOG.error("Non-retryable summary event error. eventId={}, sessionId={}",
                    event == null ? null : event.getEventId(),
                    event == null ? null : event.getSessionId(), e);
            LOG.error("MQ consume summary task failed cost. eventId={}, costMs={}",
                    event == null ? null : event.getEventId(), costMs);
        } catch (Exception e) {
            // 可重试错误：抛出异常给 RocketMQ 触发重试
            long costMs = System.currentTimeMillis() - start;
            LOG.error("Retryable summary event error. eventId={}, sessionId={}",
                    event == null ? null : event.getEventId(),
                    event == null ? null : event.getSessionId(), e);
            LOG.error("MQ consume summary task failed cost. eventId={}, costMs={}",
                    event == null ? null : event.getEventId(), costMs);
            throw e;
        }
    }
}
