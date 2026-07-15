package com.alaeldin.Auth_service.util;

import com.fasterxml.jackson.core.JacksonException;

import java.util.concurrent.TimeoutException;

/**
 * Classifies a Kafka-related {@link Throwable} into one of three categories
 * to drive targeted diagnostic log messages in {@link com.alaeldin.Auth_service.job.OutboxPublisher}.
 *
 * <p>This is a pure utility class — it has no state and must not be instantiated.</p>
 */
public final class KafkaErrorClassifier {

    private KafkaErrorClassifier() {}

    /** Diagnostic category returned by {@link #classify(Throwable)}. */
    public enum Category {
        CONNECTIVITY,
        TIMEOUT,
        SERIALIZATION,
        UNKNOWN
    }

    /**
     * Inspects the exception type and message chain to return the most specific category.
     *
     * @param ex the throwable to classify; {@code null} returns {@link Category#UNKNOWN}
     * @return the most specific matching {@link Category}
     */
    public static Category classify(Throwable ex) {
        if (ex == null)                                  return Category.UNKNOWN;
        if (ex instanceof TimeoutException)              return Category.TIMEOUT;
        if (ex instanceof JacksonException)             return Category.SERIALIZATION;

        String msg = rootMessage(ex);
        if (msg == null)                                 return Category.UNKNOWN;

        String lower = msg.toLowerCase();
        if (lower.contains("connection") || lower.contains("refused")
                || lower.contains("bootstrap") || lower.contains("network"))
            return Category.CONNECTIVITY;
        if (lower.contains("timeout"))                   return Category.TIMEOUT;
        if (lower.contains("serial") || lower.contains("json"))
            return Category.SERIALIZATION;

        return Category.UNKNOWN;
    }

    /**
     * Returns the message of the deepest cause in the exception chain.
     *
     * @param ex the throwable to inspect
     * @return the root-cause message, or {@code null} if the root cause has none
     */
    public static String rootMessage(Throwable ex) {
        Throwable root = ex;
        while (root.getCause() != null) {
            root = root.getCause();
        }
        return root.getMessage();
    }
}
