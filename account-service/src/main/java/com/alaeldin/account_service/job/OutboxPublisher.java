package com.alaeldin.account_service.job;

import com.alaeldin.account_service.model.OutboxEvent;
import com.alaeldin.account_service.service.OutBoxService;
import com.alaeldin.account_service.util.KafkaErrorClassifier;
import com.alaeldin.account_service.util.TopicResolver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Component
@Slf4j
@RequiredArgsConstructor
public class OutboxPublisher
{
    private final OutBoxService outBoxService;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final TopicResolver topicResolver;
    @Value("${app.outbox.batch-size:20}")
    private int batchSize;
    @Value("${app.outbox.publish-timeout-seconds:30}")
    private int publishTimeoutSeconds;

    // -------------------------------------------------------------------------
    // Scheduled entry point
    // -------------------------------------------------------------------------
    @Scheduled(fixedDelayString = "${app.outbox.poll-interval-ms:10000}")
    public void  publishOutboxEvents()
    {
        try
        {

            List<OutboxEvent> events = outBoxService.lockBatchForPublishing(batchSize);
            if (CollectionUtils.isEmpty(events))
            {
                log.debug("No Pending OutBox events");
                return;
            }
            log.info("Processing {} outbox event(s)", events.size());
            events.forEach(this::publishSingleEvent);

        }
        catch (Exception e)
        {
               log.error("Outbox polling cycle failed : {}" , e.getMessage());
        }
    }
     //-------------------------------------------------------------------
    // Single-event publishing
     //--------------------------------------------------------------------

    private void publishSingleEvent(OutboxEvent event)
    {
        String payload =resolvePayload(event);
        String topic = topicResolver.resolve(event.getAggregateType());

        log.debug("Publishing event: id = {} , aggregateId={} , type={} , topic ={}" +
                "attempt={}/{}" , event.getId() , event.getAggregateId()
                ,event.getEventType(), topic, event.getRetryCount() + 1, event.getMaxRetries());

       try
       {
            kafkaTemplate.send(topic, event.getAggregateId(), payload)
                    .orTimeout(publishTimeoutSeconds , TimeUnit.SECONDS)
                    .whenComplete((result , ex ) ->{
                        handleResult(event, topic , result , ex);
                    });
       }

       catch (Exception ex)
       {
           log.error("Failed to enqueue event id ={} : {}", event.getId(), ex.getMessage(), ex);
           outBoxService.markEventAsFailed(event.getId(), ex.getMessage());
       }
    }

    // -------------------------------------------------------------------------
    // Result handlers
    // -------------------------------------------------------------------------
    private void handleResult(OutboxEvent event, String topic, SendResult<String, String> result, Throwable ex) {

             if(ex == null)
             {
                 onSuccess(event , topic , result);
             }
             else {
                 onFailure(event , topic , ex);
             }
    }

    private void onFailure(OutboxEvent event, String topic, Throwable ex) {

        String errorMsg = ex.getMessage();
        String rootCause = KafkaErrorClassifier.rootMessage(ex);

        try
        {
            outBoxService.markEventAsFailed(event.getId(), ex.getMessage());
        }
        catch (Exception markEx)
        {
            log.error("Failed to mark event id = {} as failed: {}" ,
                    event.getId(), markEx.getMessage(), markEx);
        }

        log.error(" Kafka send Failed : id = {} , aggregateId={} , type={} , topic ={} , error ={}"
                , event.getId(), event.getAggregateId(), event.getEventType(), topic, errorMsg);

    }

    private void onSuccess(OutboxEvent event, String topic, SendResult<String, String> result) {

        try
        {
            outBoxService.markEventAsPublished(
                    event.getId()  ,
                    event.getIdempotencyKey());

            log.info("Published event : id = {} , topic ={} , partition = {} , offset ={}" ,
                    event.getId() , topic , result.getRecordMetadata().partition(), result.getRecordMetadata().offset()
            );
        }
        catch (Exception ex)
        {
           log.error("Failed to mark event id = {} as published: {} " , event.getId() , ex.getMessage(), ex);
        }
    }

    private String resolvePayload(OutboxEvent event) {

        String payload = event.getEventPayload();
        if (payload == null || payload.isBlank())
        {
            log.warn("Empty payload for event id={} — using '{}'", event.getId(), "{}");
            return "{}";
        }

        return payload;
    }
}
