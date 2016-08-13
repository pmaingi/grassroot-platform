package za.org.grassroot.services;

import za.org.grassroot.core.domain.Group;
import za.org.grassroot.core.domain.GroupJoinRequest;

import java.util.List;

public interface GroupJoinRequestService {

    String open(String requestorUid, String groupUid, String description);

    void approve(String userUid, String requestUid);

    void decline(String userUid, String requestUid);

    void cancel(String requestorUid, String groupUid);

    void remind(String requestorUid, String groupUid);

    GroupJoinRequest loadRequest(String requestUid);

    List<GroupJoinRequest> getOpenRequestsForGroup(String groupUid);

    List<GroupJoinRequest> getOpenRequestsForUser(String userUid);

    List<GroupJoinRequest> getOpenUserRequestsForGroupList(String requestorUid, List<Group> possibleGroups);
    
}
