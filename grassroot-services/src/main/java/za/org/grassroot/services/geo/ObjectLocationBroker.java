package za.org.grassroot.services.geo;

import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.domain.geo.GeoLocation;
import za.org.grassroot.core.domain.geo.ObjectLocation;
import za.org.grassroot.services.group.GroupLocationFilter;

import java.security.InvalidParameterException;
import java.util.List;

public interface ObjectLocationBroker {

    List<ObjectLocation> fetchGroupsNearby(GeoLocation location, Integer rsdius, String searchTerm, String filterTerm, String userUid)
        throws InvalidParameterException;

    List<ObjectLocation> fetchPublicGroupsNearbyWithLocation(GeoLocation geoLocation, Integer radius)
            throws InvalidParameterException;

    List<ObjectLocation> fetchGroupsNearbyWithLocation(GeoLocation geoLocation, Integer radius, Integer publicOrPrivate)
            throws InvalidParameterException;

    List<ObjectLocation> fetchLocationsWithFilter(GroupLocationFilter filter);

    List<ObjectLocation> fetchMeetingLocationsNearUser(User user, GeoLocation geoLocation, Integer radius, GeographicSearchType searchType, String searchTerm)
            throws InvalidParameterException;

    GeoLocation fetchBestGuessUserLocation(String userUid);

    List<ObjectLocation> fetchUserGroupsNearThem(String userUid, GeoLocation location, Integer radiusMetres, String filterTerm)
            throws InvalidParameterException;

}
