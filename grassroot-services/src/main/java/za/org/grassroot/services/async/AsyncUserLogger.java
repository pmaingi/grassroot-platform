package za.org.grassroot.services.async;

import za.org.grassroot.core.domain.UidIdentifiable;
import za.org.grassroot.core.domain.UserLog;
import za.org.grassroot.core.domain.geo.GeoLocation;
import za.org.grassroot.core.enums.LocationSource;
import za.org.grassroot.core.enums.UserInterfaceType;
import za.org.grassroot.core.enums.UserLogType;

import java.time.Instant;
import java.util.Set;

/**
 * Created by luke on 2016/02/22.
 */
public interface AsyncUserLogger {

    void logUserLogin(String userUid, UserInterfaceType channel);

    /**
     * Generic method to record a user event, typically user creation, language change, etc.
     * @param userUid The uid of the User entity to save
     * @param userLogType The type of user log to be recorded
     * @param description An optional description field (can be null)
     * @param channel The channel used, if known
     */
    void recordUserLog(String userUid, UserLogType userLogType, String description, UserInterfaceType channel);

    void storeUserLogs(Set<UserLog> userLogSet);

    /**
     * Records a user logging on to the system, to be used for reporting & analysis
     * @param userUid The uid of the user that has initiated the session
     * @param interfaceType The interface used; must be non-null
     * @param linkedEntity A linked entity if, for example, this session is associated to a group join or event or similar
     */
    void recordUserSession(String userUid, UserInterfaceType interfaceType, UidIdentifiable linkedEntity);

    /**
     * Records where a user is; only used when explicitly asking user for location
     * @param userUid The uid of the user
     * @param location The location of the user
     * @param locationSource How the location is obtained
     */
    void recordUserLocation(String userUid, GeoLocation location, LocationSource locationSource, UserInterfaceType channel);

    /**
     * Records if a user was interrupted on a menu (one of those which stores the URL...), and, if so, on which menu
     * (just the relative URL, stripping out params and host name)
     * @param userUid The user who was interrupted
     * @param savedUrl The url as saved
     */
    void recordUssdInterruption(String userUid, String savedUrl);

    /**
     * Records the string a user has entered (notably in USSD) expecting us to understand it. To collect for later
     * analysis to improve the date/time parser's capabilities.
     * @param userUid The uid of the user that entered the string
     * @param dateTimeString The string that the user entered (the raw input)
     * @param action The action the user was trying to perform (meeting/vote/to-do)
     * @param interfaceType The interface through which the string was entered
     */
    void recordUserInputtedDateTime(String userUid, String dateTimeString, String action, UserInterfaceType interfaceType);

    /**
     * Count the number of times a user has initiated a session, through a given interface (optional)
     * in a given time period (optional)
     * @param userUid The uid of the user whose activity is being queried
     * @param interfaceType The interface, to restrict the entry. If null, then count is across all interfaces.
     * @param start The start period. If null, will count all the way back to user's first entry.
     * @param end The end period. If null, will count until present moment.
     * @return
     */
    int numberSessions(String userUid, UserInterfaceType interfaceType, Instant start, Instant end);

    /**
     * Check if the user has skipped setting their name in USSD on the opening screen (and then stop asking)
     * @param userUid The user uid
     * @return true if the user has entered '0' to skip name setting
     */
    boolean hasSkippedName(String userUid);

    boolean hasSkippedProvince(String userUid);

    boolean hasChangedLanguage(String userUid);

    void removeAllUserInfoLogs(String userUid);
}
