package com.alaeldin.Auth_service.repository;

import com.alaeldin.Auth_service.model.Role;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Spring Data JPA repository for {@link Role} entities.
 *
 * @see Role
 */
@Repository
@Transactional(readOnly = true)
public interface RoleRepository extends JpaRepository<Role, Long> {

    /**
     * Finds a role by its unique name (e.g. {@code "ADMIN"}, {@code "USER"}).
     *
     * @param name the role name to search for (case-sensitive)
     * @return an {@link Optional} containing the matching role, or empty if not found
     */
    Optional<Role> findByName(String name);

    /**
     * Finds all roles whose names are in the given set.
     * Used during user registration to assign default roles in bulk.
     *
     * @param names the set of role names to look up
     * @return list of matching roles (may be smaller than {@code names} if some are absent)
     */
    List<Role> findByNameIn(Set<String> names);

    /**
     * Checks whether a role with the given name already exists.
     * Used during role seeding to prevent duplicate inserts.
     *
     * @param name the role name to check (case-sensitive)
     * @return {@code true} if a role with this name already exists
     */
    boolean existsByName(String name);
}
