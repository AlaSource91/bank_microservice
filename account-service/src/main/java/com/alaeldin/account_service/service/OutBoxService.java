package com.alaeldin.account_service.service;

import com.alaeldin.account_service.constant.OutBoxStatus;
import com.alaeldin.account_service.dto.OutBoxEventRequest;
import com.alaeldin.account_service.exception.ResourceNotFoundException;
import com.alaeldin.account_service.model.OutboxEvent;
import com.alaeldin.account_service.repository.OutBoxEventRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

@Service
@Slf4j
@RequiredArgsConstructor
public class OutBoxService {

    private final OutBoxEventRepository outBoxEventRepository;
    private final ObjectMapper objectMapper;
    private final RedisTemplate<String, String> redisTemplate;


    /**
     * Save event to outbox with idempotency key linkage
     * This method ensures atomicity and prevents duplicate events
     *
     * @param outBoxEventRequest the event request containing all necessary data
     * @return the saved OutboxEvent or existing event if idempotency key already exists
     */
    @Transactional
    public OutboxEvent saveEvent(OutBoxEventRequest outBoxEventRequest) {

        try
        {
            //validate input
            validateOutboxEventRequest(outBoxEventRequest);
            //check For Existing even First(idempotency)
            return outBoxEventRepository.findByIdempotencyKey(outBoxEventRequest.getIdempotencyKey())
                    .map(existingEvent -> {
                        log.debug("Event With idempotency Key {} already exists : id = {} status = {}"
                                ,outBoxEventRequest.getIdempotencyKey(), existingEvent.getId() , existingEvent.getStatus()) ;
                    return existingEvent;
                    })
                    .orElseGet(() -> createNewOutboxEvent(outBoxEventRequest));
        }
        catch (DataIntegrityViolationException e)
        {
            log.debug("Concurrent insertion detect for idempotency key {} , fetching existing event ",outBoxEventRequest.getIdempotencyKey());
           return outBoxEventRepository.findByIdempotencyKey(outBoxEventRequest
                   .getIdempotencyKey())
                   .orElseThrow(() -> new RuntimeException("Failed to retrieve event after concurrent insertion " + e));
        }
        catch (Exception ex)
        {
            log.error("Failed to save event to Outbox : aggregateId ={} , eventType = {} , error ={}", outBoxEventRequest.getAggregateId(), outBoxEventRequest.getEventType(), ex.getMessage(), ex);
            throw new RuntimeException("Failed to save event to Outbox", ex);
        }
    }

    private OutboxEvent createNewOutboxEvent(OutBoxEventRequest outBoxEventRequest)  {

        try {
            String jsonPayload = objectMapper.writeValueAsString(outBoxEventRequest.getEventPayload());

            OutboxEvent outboxEvent = OutboxEvent.builder()
                    .aggregateId(outBoxEventRequest.getAggregateId())
                    .aggregateType(outBoxEventRequest.getAggregateType())
                    .eventType(outBoxEventRequest.getEventType())
                    .eventPayload(jsonPayload)
                    .idempotencyKey(outBoxEventRequest.getIdempotencyKey())
                    .createdAt(LocalDateTime.now())
                    .status(OutBoxStatus.PENDING)
                    .retryCount(0)
                    .maxRetries(3)
                    .build();

            log.info("Saved new Event to OutBox : id ={} , aggregateId ={} , eventType ={} , eventPayload ={}",
                    outboxEvent.getId(), outboxEvent.getAggregateId(), outboxEvent.getEventType(), jsonPayload);

            return outBoxEventRepository.save(outboxEvent);
        }
        catch (Exception ex)
        {
            log.error(" Failed to Create new OutBox Event : {}"
                    , ex.getMessage(), ex);

            throw new RuntimeException(ex);
        }
    }


    private void validateOutboxEventRequest(OutBoxEventRequest outboxEventRequest) {

        if (outboxEventRequest == null)
        {
            throw new IllegalArgumentException("outboxEventRequest cannot be null");
        }

        if (outboxEventRequest.getAggregateId() == null || outboxEventRequest.getAggregateId().trim().isEmpty())
        {
            throw new IllegalArgumentException("aggregateId cannot be null");
        }
        if (outboxEventRequest.getAggregateType() == null || outboxEventRequest.getAggregateType().trim().isEmpty())
        {
            throw new IllegalArgumentException("aggregateType cannot be null");
        }

        if (outboxEventRequest.getEventType() == null || outboxEventRequest.getEventType().trim().isEmpty())
        {
            throw new IllegalArgumentException("eventType cannot be null");
        }
        if (outboxEventRequest.getEventPayload() == null)
        {
            throw new IllegalArgumentException("eventPayload cannot be null");
        }

        if (outboxEventRequest.getIdempotencyKey() == null || outboxEventRequest.getIdempotencyKey().trim().isEmpty())
        {
            throw new IllegalArgumentException("idempotencyKey cannot be null");
        }

    }

    /**
     * Mark event as successfully published
     * Updates the event status and optionally caches result in Redis
     *
     * @param eventId the ID of the event to mark as published
     * @param idempotencyKey the idempotency key for Redis caching
     */
    @Transactional
    public void markEventAsPublished(Long eventId, String idempotencyKey) {

        try
        {
            LocalDateTime publishedAt = LocalDateTime.now();
            //try atomic update first (more efficient for   concurrent scenarios)
            boolean updated = atomicStatusUpdate(eventId ,OutBoxStatus.PROCESSING
                    ,OutBoxStatus.SENT ,publishedAt);
           if (!updated)
           {
               //Fallback to manual update if not is processing status
               OutboxEvent outboxEvent = findById(eventId);
               outboxEvent.setStatus(OutBoxStatus.SENT);
               outboxEvent.setPublishedAt(publishedAt);
               outboxEvent.setErrorMessage(null);

               outBoxEventRepository.save(outboxEvent);

               log.debug(" Updated event status via fallback method: id={}", eventId);
           }
           //Cache in Redis for consumer  notification
           cachePublishedEventInRedis(idempotencyKey);
        }
        catch (Exception ex)
        {
            log.error("Failed to mark event as published : eventId ={} , error={} ",eventId, ex.getMessage(), ex );
            throw new RuntimeException("Failed to mark event as published", ex);
        }
    }

    private void cachePublishedEventInRedis(String idempotencyKey) {

       try
       {
            if (redisTemplate != null &&  idempotencyKey != null)
            {
                redisTemplate.opsForValue()
                        .set(
                                idempotencyKey
                                , "published"
                                , Duration.ofMinutes(5)
                        );

                log.debug(" Cached published event in Redis: key={}", idempotencyKey);
            }
       }
       catch (Exception ex)
       {
           log.warn("Failed to cache published event in Redis : {}", ex.getMessage(), ex);
       }
    }

    private OutboxEvent findById(Long eventId) {

            return outBoxEventRepository.findById(eventId)
                    .orElseThrow(() -> new ResourceNotFoundException("OutboxEvent", "id", eventId.toString()));


    }

    @Transactional
    private boolean atomicStatusUpdate(Long eventId, OutBoxStatus currentStatus , OutBoxStatus outBoxStatus, LocalDateTime publishedAt) {

        try
        {
            int updated = outBoxEventRepository.updateEventStatus(
                    eventId ,currentStatus ,outBoxStatus ,publishedAt

            );

            if(updated > 0)
            {
                log.debug(" Atomically update event {} from {} to {}"
                        , eventId, currentStatus, outBoxStatus );

                return true;
            }
            else {
                log.debug("Event {} not in expected status {} for atomic update"
                        , eventId, currentStatus, outBoxStatus );
                return false;
            }
        }
        catch (Exception ex) {

            log.error("Failed to mark event as published : eventId ={} , error={}"
                    , eventId, ex.getMessage(), ex );
            return false;
        }
    }

    /**
     * Mark event as failed and schedule retry with exponential backoff
     *
     * @param eventId the ID of the event that failed
     * @param errorMessage optional error message for logging
     */
       @Transactional
       public void markEventAsFailed(Long eventId, String errorMessage)
       {
           try
           {
               OutboxEvent outboxEvent = findById(eventId);
               // Increment  retry Count
               outboxEvent.setRetryCount(outboxEvent.getRetryCount() + 1);
               outboxEvent.setErrorMessage(errorMessage);

               //Check if exceeded max retries
               if (outboxEvent.getRetryCount() >= outboxEvent.getMaxRetries())
               {
                   outboxEvent.setStatus(OutBoxStatus.DEAD);
                   outboxEvent.setNextRetryAt(null);
                   outBoxEventRepository.save(outboxEvent);

                   log.error(" Event exceeded max retries and marked as DEAD: id={}, retryCount={}, error={}",
                           eventId, outboxEvent.getRetryCount(), errorMessage);
                 return;
               }

               //Set Status Back to Pending for Retry  and calculate next retry with exponential backoff
               outboxEvent.setStatus(OutBoxStatus.PENDING);
               int delayMinutes = (int) Math.pow(2, outboxEvent.getRetryCount());
               outboxEvent.setNextRetryAt(LocalDateTime.now().plusMinutes(delayMinutes));
               outBoxEventRepository.save(outboxEvent);

               log.warn("Event retry scheduled: id={}, retryCount={}/{}, nextRetry={}, delayMinutes={}, error={}",
               eventId, outboxEvent.getRetryCount(), outboxEvent.getMaxRetries(), outboxEvent.getNextRetryAt(), delayMinutes, errorMessage);

           }
           catch (Exception ex)
           {
              log.error("Failed to mark event as failed : eventId ={} , error={}",  eventId, ex.getMessage(), ex );
              throw  new RuntimeException("Failed to mark event as failed", ex);
           }
       }

    /**
     * Lock and get a batch of events for distributed processing
     * This method uses database-level locking to prevent concurrent processing
     *
     * @param batchSize maximum number of events to lock and return
     * @return list of locked events ready for processing
     */
       @Transactional
       public List<OutboxEvent> lockBatchForPublishing(int batchSize)
     {
         LocalDateTime now = LocalDateTime.now();
         return outBoxEventRepository.lockBatchForPublishing(now, batchSize);
     }

   @Transactional
   public void  resetEventToPending(Long eventId , String reason)
   {
       try
       {
           OutboxEvent outboxEvent = findById(eventId);
           outboxEvent.setStatus(OutBoxStatus.PENDING);
           outboxEvent.setErrorMessage(reason);
           outBoxEventRepository.save(outboxEvent);

           log.warn("Event reset to pending: id={}, reason={}", eventId, reason);
       }
       catch (Exception ex)
       {
           log.error(" Failed to reset event {} to PENDING: {}", eventId, ex.getMessage(), ex);

       }
   }

   @Getter
   @AllArgsConstructor
    public static class OutboxStatistics {
       private final Long unpublishedEvents;
       private final Long sentEvents;
       private final Long failedEvents;
       private final long deadEvents;
       private final long processingEvents;


       public long getTotalEvents() {
           return unpublishedEvents + sentEvents + failedEvents + deadEvents + processingEvents;
       }
   }
}
