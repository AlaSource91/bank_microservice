package com.alaeldin.Auth_service.repository;

import com.alaeldin.Auth_service.constant.Action;
import com.alaeldin.Auth_service.model.Permissions;
import com.alaeldin.Auth_service.model.Resource;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * Spring Data JPA repository for {@link Permissions} entities.
 *
 * @see Permissions
 * @see Resource
 * @see Action
 */
@Repository
@Transactional(readOnly = true)
public interface PermissionRepository extends JpaRepository<Permissions, Long> {

    /**
     * Returns all permissions associated with a given resource.
     * Used when listing what actions are available for a specific resource.
     *
     * @param resource the resource entity to filter by
     * @return list of permissions for the given resource
     */
    List<Permissions> findByResource(Resource resource);

    /**
     * Returns all permissions that grant a specific action.
     * Used to determine which resources a given action covers.
     *
     * @param action the action to filter by (e.g. {@link Action#READ})
     * @return list of permissions matching the given action
     */
    List<Permissions> findByAction(Action action);

    /**
     * Finds the unique permission for a specific resource + action combination.
     * Used to resolve a concrete {@link Permissions} row before assigning it to a role.
     *
     * @param resource the target resource entity
     * @param action   the action being granted
     * @return an {@link Optional} containing the matching permission, or empty if not found
     */
    Optional<Permissions> findByResourceAndAction(Resource resource, Action action);

    /**
     * Returns all permissions belonging to all roles of a given user.
     * Used when building the full granted-authority set for a Spring Security principal
     * without loading the entire role → permission graph into memory.
     *
     * @param userId the ID of the user
     * @return flat list of all permissions the user inherits through their roles
     */
    @Query("""
            SELECT p FROM Permissions p
            JOIN p.resource r
            WHERE p IN (
                SELECT perm FROM Role role
                JOIN role.permissions perm
                WHERE role IN (
                    SELECT ur FROM User u
                    JOIN u.roles ur
                    WHERE u.id = :userId
                )
            )
            """)
    List<Permissions> findAllPermissionsByUserId(@Param("userId") Long userId);

    /**
     * Checks whether a permission for a specific resource + action combination already exists.
     * Used during permission seeding and admin-panel duplicate prevention.
     *
     * @param resource the target resource entity
     * @param action   the action to check
     * @return {@code true} if the permission already exists
     */
    boolean existsByResourceAndAction(Resource resource, Action action);
}
