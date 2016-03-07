package za.org.grassroot.services;

import org.springframework.data.domain.Page;
import org.springframework.security.access.prepost.PostFilter;
import org.springframework.security.access.prepost.PreAuthorize;
import za.org.grassroot.core.domain.Group;
import za.org.grassroot.core.domain.LogBook;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.dto.GroupDTO;
import za.org.grassroot.core.dto.GroupTreeDTO;
import za.org.grassroot.core.enums.GroupLogType;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Date;
import java.util.List;

/**
 * @author Lesetse Kimwaga
 */
public interface GroupManagementService {

    /*
    Methods to create, save and name groups
     */

    public Group saveGroup(Group groupToSave, boolean createGroupLog, String description, Long changedByUserId);

    public Group renameGroup(String groupUid, String newName, String changingUserUid);

    public Group groupToRename(User sessionUser);

    /*
    Methods to load and find groups
    */

    public Group loadGroup(Long groupId);

    public Group loadGroupByUid(String uid);

    public List<Group> getCreatedGroups(User creatingUser);

    public boolean hasActiveGroupsPartOf(User user); // replace in time wth getGroupsByPermission

    public List<Group> getActiveGroupsPartOf(User sessionUser);

    public List<GroupDTO> getActiveGroupsPartOfOrderedByRecent(User sessionUser);

    public Page<Group> getPageOfActiveGroups(User sessionUser, int pageNumber, int pageSize);

    public List<Group> getListGroupsFromLogbooks(List<LogBook> logBooks);

    /*
    Methods to find if a user has an outstanding group management action to perform or groups on which they can perform it
     */

    public boolean isUserInGroup(Group group, User user);

    /*
    Methods to work with group joining tokens and group discovery
     */

    public Group generateGroupToken(String groupUid, String generatingUserUid);

    public Group generateExpiringGroupToken(String groupUid, String userUid, Integer daysValid);

    public Group extendGroupToken(Group group, Integer daysExtension, User user);

    public Group closeGroupToken(String groupUid, String closingUserUid);

    public boolean groupHasValidToken(Group group);

    public Group findGroupByToken(String groupToken);

    public Group setGroupDiscoverable(Group group, boolean discoverable, Long userId);

    public List<Group> findDiscoverableGroups(String groupName);

    /*
    Methods do deal with sub groups and parent groups
     */

    public List<Group> getSubGroups(Group group);

    public List<User> getUsersInGroupNotSubGroups(Long groupId);

    public List<User> getAllUsersInGroupAndSubGroups(Long groupId);

    public List<User> getAllUsersInGroupAndSubGroups(Group group);

    public Group linkSubGroup(Group child, Group parent);

    List<Group> getAllParentGroups(Group group);

    /*
    Pass in the group you want to make a child as the 'possibleChildGroup', and the desired parent
    as the 'possibleParentGroup', and this will return true if the possible child is already in the parent chain
     of the possible parent, i.e., if it will create an infinite loop
     */
    boolean isGroupAlsoParent(Group possibleChildGroup, Group possibleParentGroup);

    /*
    Methods to set and retrieve some basic group properties
     */

    public Group setGroupDefaultReminderMinutes(Group group, Integer minutes);

    public Group setGroupDefaultLanguage(Group group, String locale, boolean setSubGroups);

    public Integer getGroupSize(Long groupId, boolean includeSubGroups);

    public LocalDateTime getLastTimeGroupActive(Group group);

    public LocalDateTime getLastTimeGroupModified(Group group);

    /*
    Methods to consolidate groups, and to manage active / inactive
     */

    public Group mergeGroups(Long firstGroupId, Long secondGroupId, Long mergingUserId,
                             boolean leaveActive, boolean orderSpecified, boolean createNew); // "normal" case is all 3 false

    public List<Group> getMergeCandidates(User mergingUser, Long firstGroupSelected);

    /*
    Methods to get group properties if paid or not
     */

    public boolean isGroupPaid(Group group);

    public boolean canGroupDoFreeForm(Group group);

    public boolean canGroupRelayMessage(Group group);

    /*
    Methods for system and account admin
     */

    List<Group> getAllGroups();

    Page<Group> getAllActiveGroupsPaginated(Integer pageNumber, Integer pageSize);

    List<Group> getGroupsFiltered(User createdByUser, Integer minGroupSize, Date createdAfterDate, Date createdBeforeDate);

    List<GroupTreeDTO> getGroupsMemberOfTree(Long userId);

    List<LocalDate> getMonthsGroupActive(Group group);

    //warning: to be used for integration test purposes only

    /*
    Recursive query better to use than recursive code calls
    */
    List<Group> findGroupAndSubGroupsById(Long groupId);
}
