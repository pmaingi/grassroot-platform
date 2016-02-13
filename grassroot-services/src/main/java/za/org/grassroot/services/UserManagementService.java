package za.org.grassroot.services;

import org.springframework.data.domain.Page;
import za.org.grassroot.core.domain.Group;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.services.exception.NoSuchUserException;
import za.org.grassroot.services.exception.UserExistsException;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;

/**
 * @author Lesetse Kimwaga
 */
public interface UserManagementService {

    /*
    Methods to create and load a specific user
     */

    User createUserProfile(User userProfile);

    User createUserWebProfile(User userProfile) throws UserExistsException;

    User getUserById(Long userId);

    boolean userExist(String phoneNumber);

    boolean isFirstInitiatedSession(String phoneNumber);

    boolean isFirstInitiatedSession(User user);

    User save(User userToSave);

    void saveList(List<User> usersToSave);

    User loadOrSaveUser(String inputNumber);

    User loadOrSaveUser(String inputNumber, String currentUssdMenu);

    void saveUssdMenu(User user, String menuToSave);

    User loadOrSaveUser(String inputNumber, boolean isInitiatingSession);

    public User loadOrSaveUser(User passedUser);

    User findByInputNumber(String inputNumber) throws NoSuchUserException;

    User findByInputNumber(String inputNumber, String currentUssdMenu) throws NoSuchUserException;

    User fetchUserByUsername(String username);

    User setInitiatedSession(User sessionUser);

    User loadUser(Long userId);

    /*
    Methods to return lists of users
     */

    List<User> getAllUsers();

    Integer getUserCount();

    Page<User> getDeploymentLog(Integer pageNumber);

    List<User> searchByInputNumber(String inputNumber);

    List<User> searchByDisplayName(String displayName);

    List<User> searchByGroupAndNameNumber(Long groupId, String nameOrNumber);

    User reformatPhoneNumber(User sessionUser);

    List<User> getUsersFromNumbers(List<String> listOfNumbers);

    List<User> getGroupMembersSortedById(Group group);

    List<User> getExistingUsersFromNumbers(List<String> listOfNumbers);

    List<User> getGroupMembersWithoutCreator(Group group);

    List<User> getGroupMembersWithout(Group group, Long excludedUserId);


    /*
    Methods to set and retrieve varfious properties about a user
     */

    boolean needsToRenameSelf(User sessionUser);

    boolean needsToRSVP(User sessionUser);

    boolean needsToVote(User sessionUser);

    boolean needsToVoteOrRSVP(User sessionUser);
    
    User resetUserPassword(String username, String newPassword, String token);

    User resetUserPassword(String username, String newPassword, User adminUser, String adminPassword);

    String getLastUssdMenu(User sessionUser);

    User resetLastUssdMenu(User sessionUser);

    User setLastUssdMenu(User sessionUser, String lastUssdMenu);

    User setDisplayName(User user, String displayName);

    String getDisplayName(Long userId);

    User setUserLanguage(User sessionUser, String locale);

    User setUserLanguage(Long userId, String locale);

    String getUserLocale(User sessionUser);

    LinkedHashMap<String, String> getImplementedLanguages();

    /*
    Methods to return masked user entities for system analysis
    todo: add security to the methods that load users which are not masked
     */

    User loadUserMasked(Long userId);

    List<User> loadAllUsersMasked();

    List<User> loadSubsetUsersMasked(List<Long> ids);

}
