package com.alaeldin.bank_simulator_service.exception;

/**
 * Exception thrown when an optimistic lock version mismatch occurs during concurrent updates.
 * This indicates that the entity was modified by another transaction after it was read.
 */
public class ConcurrentModificationException extends RuntimeException {

    private final String resourceName;
    private final String resourceId;
    private final Long expectedVersion;
    private final Long actualVersion;

    /**
     * Constructs a new ConcurrentModificationException.
     *
     * @param resourceName the name of the resource (e.g., "BankAccount")
     * @param resourceId the identifier of the resource
     * @param expectedVersion the version expected during update
     * @param actualVersion the actual version in the database
     */
    public ConcurrentModificationException(
            String resourceName,
            String resourceId,
            Long expectedVersion,
            Long actualVersion) {
        super(String.format(
                "Concurrent modification detected on %s %s. Expected version: %d, Actual version: %d",
                resourceName, resourceId, expectedVersion, actualVersion
        ));
        this.resourceName = resourceName;
        this.resourceId = resourceId;
        this.expectedVersion = expectedVersion;
        this.actualVersion = actualVersion;
    }

    /**
     * Constructs a new ConcurrentModificationException with a custom message.
     *
     * @param message the custom error message
     */
    public ConcurrentModificationException(String message) {
        super(message);
        this.resourceName = null;
        this.resourceId = null;
        this.expectedVersion = null;
        this.actualVersion = null;
    }

    public String getResourceName() {
        return resourceName;
    }

    public String getResourceId() {
        return resourceId;
    }

    public Long getExpectedVersion() {
        return expectedVersion;
    }

    public Long getActualVersion() {
        return actualVersion;
    }
}
