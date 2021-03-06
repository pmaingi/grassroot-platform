package za.org.grassroot.services.task;

import lombok.*;
import za.org.grassroot.core.domain.JpaEntityType;
import za.org.grassroot.core.enums.EventSpecialForm;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Builder @Getter @Setter @ToString @EqualsAndHashCode
public class VoteHelper {

    private String userUid;
    private String parentUid;
    @Builder.Default private JpaEntityType parentType = JpaEntityType.GROUP;
    private String name;
    private LocalDateTime eventStartDateTime;
    @Builder.Default private boolean includeSubGroups = false;
    private String description;
    private String taskImageKey;
    @Builder.Default private Set<String> assignMemberUids = Collections.emptySet();
    private List<String> options;
    @Builder.Default private boolean randomizeOptions = false;

    private EventSpecialForm specialForm;
    @Builder.Default private boolean sendNotifications = true;
    @Builder.Default private boolean excludeAbstain = false;

    private Map<Locale, String> multiLanguagePrompts;
    private Map<Locale, String> postVotePrompts;
    private Map<Locale, List<String>> multiLanguageOptions;

}
