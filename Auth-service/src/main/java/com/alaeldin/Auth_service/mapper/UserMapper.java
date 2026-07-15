package com.alaeldin.Auth_service.mapper;

import com.alaeldin.Auth_service.dto.UserProfileResponse;
import com.alaeldin.Auth_service.model.Role;
import com.alaeldin.Auth_service.model.User;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;
import org.mapstruct.NullValueMappingStrategy;
import org.mapstruct.ReportingPolicy;

import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

@Mapper(componentModel = "spring",
        unmappedTargetPolicy = ReportingPolicy.IGNORE,
        nullValueMappingStrategy = NullValueMappingStrategy.RETURN_NULL)
public interface UserMapper {

    @Mapping(target = "roles", qualifiedByName = "roleNamesToRoles")
    @Mapping(target = "id", source = "userId")
    User toUser(UserProfileResponse userProfileResponse);

    @Mapping(target = "roles", qualifiedByName = "rolesToRoleNames")
    @Mapping(target = "userId", source = "id")
    UserProfileResponse toUserProfileResponse(User user);

    /**
     * Converts a set of Role entities to a set of role name strings.
     *
     * @param roles the set of Role entities
     * @return a set of role names
     */
    @Named("rolesToRoleNames")
    default Set<String> rolesToRoleNames(Set<Role> roles) {
        if (roles == null) {
            return new HashSet<>();
        }
        return roles.stream()
                .map(Role::getName)
                .collect(Collectors.toCollection(HashSet::new));
    }

    /**
     * Converts a set of role name strings to a set of Role entities.
     * Note: This creates Role objects with only the name field populated.
     * For proper entity mapping with database lookup, consider using a repository-based approach.
     *
     * @param roleNames the set of role name strings
     * @return a set of Role entities with names populated
     */
    @Named("roleNamesToRoles")
    default Set<Role> roleNamesToRoles(Set<String> roleNames) {
        if (roleNames == null) {
            return new HashSet<>();
        }
        return roleNames.stream()
                .map(name -> {
                    Role role = new Role();
                    role.setName(name);
                    return role;
                })
                .collect(Collectors.toCollection(HashSet::new));
    }
}
