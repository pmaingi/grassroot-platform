package za.org.grassroot.services;

import za.org.grassroot.core.domain.Group;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.dto.UserDTO;
import za.org.grassroot.core.enums.AlertPreference;
import za.org.grassroot.core.enums.UserMessagingPreference;
import za.org.grassroot.services.exception.NoSuchProfileException;
import za.org.grassroot.services.exception.NoSuchUserException;
import za.org.grassroot.services.exception.UserExistsException;

import java.util.List;
import java.util.Locale;

/**
 * @author Lesetse Kimwaga
 */
public interface UserManagementService {

    /*
    Methods to load a specific user
     */

    User load(String userUid);

    User loadOrCreateUser(String inputNumber); // used only in USSD where there is no registration process

    User findByInputNumber(String inputNumber) throws NoSuchUserException;

    User findByInputNumber(String inputNumber, String currentUssdMenu) throws NoSuchUserException;

    User fetchUserByUsername(String username);

    boolean userExist(String phoneNumber);

    List<User> searchByGroupAndNameNumber(String groupUid, String nameOrNumber);

    /*
    Methods to create a user, for various interfaces
     */

    User createUserProfile(User userProfile);

    User deactiveAndroidProfile(User user) throws NoSuchProfileException;

    UserDTO loadUserCreateRequest(String phoneNumber);

    User createUserWebProfile(User userProfile) throws UserExistsException;

    User createAndroidUserProfile(UserDTO userDTO) throws UserExistsException;

    String generateAndroidUserVerifier(String phoneNumber, String displayName);

    String regenerateUserVerifier(String phoneNumber);

    /*
    Methods to update user properties
     */

    void updateUser(String userUid, String displayName, AlertPreference alertPreference, Locale locale);

    void updateDisplayName(String userUid, String displayName);

    void setDisplayNameByOther(String updatingUserUid, String targetUserUid, String displayName);

    void updateUserLanguage(String userUid, Locale locale);

    void updateAlertPreferences(String userUid, AlertPreference alertPreference);

    void setMessagingPreference(String userUid, UserMessagingPreference preference);

    void setHasInitiatedUssdSession(String userUid);

    User resetUserPassword(String username, String newPassword, String token);

    /*
    Miscellaneous methods to query various properties about a user
     */

    boolean needsToRenameSelf(User sessionUser);

    void sendAndroidLinkSms(String userUid);

    Group fetchGroupUserMustRename(User user);

    List<String[]> findOthersInGraph(User user, String nameFragment);

}
