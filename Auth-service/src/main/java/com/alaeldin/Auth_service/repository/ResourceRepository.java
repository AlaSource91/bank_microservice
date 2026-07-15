package com.alaeldin.Auth_service.repository;

import com.alaeldin.Auth_service.constant.ResourceName;
import com.alaeldin.Auth_service.model.Resource;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * Spring Data JPA repository for {@link Resource} entities.
 *
 * @see Resource
 * @see ResourceName
 */
@Repository
@Transactional(readOnly = true)
public interface ResourceRepository extends JpaRepository<Resource, Long> {

    /**
     * Finds a resource by its {@link ResourceName} enum value.
     *
     * <p>This is the primary lookup used when resolving a permission — callers
     * pass a {@link ResourceName} constant (e.g. {@code ResourceName.ACCOUNT})
     * rather than a raw string to ensure type safety.</p>
     *
     * @param name the logical resource identifier to search for
     * @return an {@link Optional} containing the matching resource, or empty if not found
     */
    Optional<Resource> findByName(ResourceName name);

    /**
     * Checks whether a resource with the given {@link ResourceName} already exists.
     * Used during application startup seeding to prevent duplicate inserts.
     *
     * @param name the logical resource identifier to check
     * @return {@code true} if a resource with this name already exists
     */
    boolean existsByName(ResourceName name);
}
