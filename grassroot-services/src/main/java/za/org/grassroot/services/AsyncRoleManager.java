package za.org.grassroot.services;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.org.grassroot.core.domain.BaseRoles;
import za.org.grassroot.core.domain.Group;
import za.org.grassroot.core.domain.Role;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.repository.GroupRepository;
import za.org.grassroot.core.repository.RoleRepository;
import za.org.grassroot.core.repository.UserRepository;
import za.org.grassroot.services.enums.GroupPermissionTemplate;

import java.util.List;

/**
 * Created by luke on 2016/02/17.
 */
@Service
@Transactional
@Lazy
public class AsyncRoleManager implements AsyncRoleService {

    private static final Logger log = LoggerFactory.getLogger(AsyncRoleManager.class);

    @Autowired
    GroupRepository groupRepository;

    @Autowired
    UserRepository userRepository;

    @Autowired
    RoleRepository roleRepository;

    @Autowired
    PermissionsManagementService permissionsManagementService;

    @Autowired
    GroupAccessControlManagementService groupAccessControlManagementService;

    @Autowired
    RoleManagementService roleManagementService;


    private Role fixAndReturnGroupRole(String roleName, Group group, GroupPermissionTemplate template) {
        group.setGroupRoles(roleManagementService.createGroupRoles(group.getUid()));
        groupRepository.saveAndFlush(group);
        return fetchGroupRole(roleName, group.getUid());
    }

    private Role fixPermissionsForRole(Role role, GroupPermissionTemplate template) {
        log.error("Uh oh, for some reason the role permissions weren't set previously");
        role.setPermissions(permissionsManagementService.getPermissions(role.getName(), template));
        return roleRepository.save(role);
    }

    @Async
    @Override
    public void resetGroupToDefaultRolesPermissions(Long groupId, GroupPermissionTemplate template, User callingUser) {

        log.info("Resetting group to creator as organizer, rest as members ... ");
        Long startTime = System.currentTimeMillis();
        Group group = groupRepository.findOne(groupId);
        Long creatingUserId = group.getCreatedByUser().getId();

        List<User> groupMembers = userRepository.findByGroupsPartOfAndIdNot(group, creatingUserId);

        Role ordinaryRole = fetchGroupRole(BaseRoles.ROLE_ORDINARY_MEMBER, group.getUid());
        if (ordinaryRole == null) { ordinaryRole = fixAndReturnGroupRole(BaseRoles.ROLE_ORDINARY_MEMBER, group, template); }

        if (ordinaryRole.getPermissions() == null || ordinaryRole.getPermissions().isEmpty())
            ordinaryRole = fixPermissionsForRole(ordinaryRole, template);

        addRoleToGroupAndUser(BaseRoles.ROLE_GROUP_ORGANIZER, group, group.getCreatedByUser(), callingUser);
        addRoleToGroupAndUsers(BaseRoles.ROLE_ORDINARY_MEMBER, group, groupMembers, callingUser);

        Long endTime = System.currentTimeMillis();
        log.info(String.format("Added roles to members, total time took %d msecs", endTime - startTime));
        log.info("Exiting the resetGroupToDefault method ...");
    }

    // @Async
    @Override
    // @Transactional
    public void addRoleToGroupAndUser(String roleName, Group group, User addingToUser, User callingUser) {

        // note: this doesn't work during group creation because Hibernate hasn't cached yet

        Role role = fetchGroupRole(roleName, group.getUid());
        addingToUser = flushUserRolesInGroup(addingToUser, group.getId());

        if (role==null) { role = fixAndReturnGroupRole(roleName, group, GroupPermissionTemplate.DEFAULT_GROUP); }
        // if (role == null) { throw new GroupHasNoRolesException(); }

        log.info("Retrieved the following role: " + role.describe());
        if (role.getPermissions() == null || role.getPermissions().isEmpty()) {
            role = fixPermissionsForRole(role, GroupPermissionTemplate.DEFAULT_GROUP);
        }
        addingToUser.addStandardRole(role);
        // addingToUser = userRepository.save(addingToUser); // this causes issues with the ACL (many)

        // now that we have a role with the right set of permissions, finish off by wiring up access control
        groupAccessControlManagementService.addUserGroupPermissions(group, addingToUser, callingUser, role.getPermissions());

    }

    private void addRoleToGroupAndUsers(String roleName, Group group, List<User> addingToUsers, User callingUser) {
        Role role = fetchGroupRole(roleName, group.getUid());
        if (role == null) { role = fixAndReturnGroupRole(roleName, group, GroupPermissionTemplate.DEFAULT_GROUP); }

        // todo: make this work off a template instead
        if (role.getPermissions() == null || role.getPermissions().isEmpty()) {
            role = fixPermissionsForRole(role, GroupPermissionTemplate.DEFAULT_GROUP);
        }

        for (User user : addingToUsers) {
            user.addStandardRole(role);
        }
        groupAccessControlManagementService.addUsersGroupPermissions(group, addingToUsers, callingUser, role.getPermissions());
    }

    private Role fetchGroupRole(String roleName, String groupUid) {
        return roleRepository.findByNameAndGroupUid(roleName, groupUid);
    }

    private User flushUserRolesInGroup(User user, Long groupId) {
        Role oldRole = roleManagementService.getUserRoleInGroup(user, groupId);
        if (oldRole != null) {
            log.info("Found a group role to flush! User ... " + user.nameToDisplay() + " ... and role ... " + oldRole.toString());
            user.removeStandardRole(oldRole);
        } else {
            log.info("Didn't find a role to flush ...");
        }
        return user;
    }

}
