package com.alaeldin.bank_simulator_service.util;

import com.fasterxml.jackson.core.JsonProcessingException;

import java.util.concurrent.TimeoutException;

/**
 * Classifies a Kafka-related {@link Throwable} into one of three categories
 * to drive targeted diagnostic log messages.
 */
public final class KafkaErrorClassifier {

    private KafkaErrorClassifier() {}

public     enum Category { CONNECTIVITY, TIMEOUT, SERIALIZATION, UNKNOWN }

    public static Category classify(Throwable ex) {
        if (ex == null) return Category.UNKNOWN;
        if (ex instanceof TimeoutException)              return Category.TIMEOUT;
        if (ex instanceof JsonProcessingException)       return Category.SERIALIZATION;
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

    /** Returns the message of the deepest cause in the exception chain. */
   public static String rootMessage(Throwable ex) {
        Throwable root = ex;
        while (root.getCause() != null) root = root.getCause();
        return root.getMessage();
    }
}

