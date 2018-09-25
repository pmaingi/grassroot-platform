package za.org.grassroot.webapp.controller.rest.whatsapp;

import io.swagger.annotations.Api;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.MessageSource;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import za.org.grassroot.core.domain.BaseRoles;
import za.org.grassroot.core.domain.JpaEntityType;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.domain.campaign.Campaign;
import za.org.grassroot.core.domain.campaign.CampaignActionType;
import za.org.grassroot.core.domain.campaign.CampaignMessage;
import za.org.grassroot.core.domain.group.Group;
import za.org.grassroot.core.enums.Province;
import za.org.grassroot.core.enums.UserInterfaceType;
import za.org.grassroot.integration.authentication.CreateJwtTokenRequest;
import za.org.grassroot.integration.authentication.JwtService;
import za.org.grassroot.integration.authentication.JwtType;
import za.org.grassroot.services.AnalyticalService;
import za.org.grassroot.services.PermissionBroker;
import za.org.grassroot.services.async.AsyncUserLogger;
import za.org.grassroot.services.campaign.CampaignBroker;
import za.org.grassroot.services.group.GroupBroker;
import za.org.grassroot.services.user.UserManagementService;
import za.org.grassroot.webapp.controller.BaseController;
import za.org.grassroot.webapp.controller.rest.Grassroot2RestController;

import javax.annotation.PostConstruct;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@Slf4j @RestController @Grassroot2RestController
@RequestMapping("/v2/api/whatsapp") @Api("/v2/api/whatsapp")
@PreAuthorize("hasRole('ROLE_SYSTEM_CALL')")
public class WhatsAppRelatedController extends BaseController {

    private final List<RequestDataType> USER_DATA_REQUESTS_WITH_MSGS = Arrays.asList(
            RequestDataType.USER_NAME, RequestDataType.LOCATION_PROVINCE_OKAY, RequestDataType.LOCATION_GPS_REQUIRED, RequestDataType.NONE);

    private final JwtService jwtService;
    private final AsyncUserLogger userLogger;

    private CampaignBroker campaignBroker;
    private GroupBroker groupBroker;
    private MessageSource messageSource;
    private AnalyticalService analyticalService;

    @Autowired
    public WhatsAppRelatedController(UserManagementService userManagementService, PermissionBroker permissionBroker, JwtService jwtService, AsyncUserLogger userLogger) {
        super(userManagementService, permissionBroker);
        this.jwtService = jwtService;
        this.userLogger = userLogger;
    }

    @Autowired
    private void setCampaignBroker(CampaignBroker campaignBroker) {
        this.campaignBroker = campaignBroker;
    }

    @Autowired
    public void setGroupBroker(GroupBroker groupBroker) {
        this.groupBroker = groupBroker;
    }

    @Autowired
    public void setMessageSource(@Qualifier("messageSource") MessageSource messageSource) {
        this.messageSource = messageSource;
    }

    @Autowired(required = false)
    public void setAnalyticalService(AnalyticalService analyticalService) {
        this.analyticalService = analyticalService;
    }

    @PostConstruct
    public void init() {
        if (analyticalService != null) {
            log.info("Starting up WhatsApp controller: number users opted in: {}, number users used : {}", 
                    analyticalService.countUsersWithWhatsAppOptIn(), analyticalService.countUsersThatHaveUsedWhatsApp());
        }
    }

    // this will get called _a lot_ during a sesion (each message to and fro), so not yet introducting a record user log in
    @RequestMapping(value = "/user/id", method = RequestMethod.POST)
    public ResponseEntity fetchUserId(String msisdn) {
        User user = userManagementService.loadOrCreate(msisdn);
        userLogger.recordUserSession(user.getUid(), UserInterfaceType.WHATSAPP);
        return ResponseEntity.ok(user.getUid());
    }

    // for accessing standard user APIs, but is time limited, and does not include system roles
    // so in case overall microservice token is compromised, only some features can be called
    @RequestMapping(value = "/user/token", method = RequestMethod.POST)
    public ResponseEntity fetchRestrictedUserToken(String userId) {
        CreateJwtTokenRequest tokenRequest = new CreateJwtTokenRequest(JwtType.MSGING_CLIENT);
        tokenRequest.addClaim(JwtService.USER_UID_KEY, userId);
        tokenRequest.addClaim(JwtService.SYSTEM_ROLE_KEY, BaseRoles.ROLE_FULL_USER);
        userLogger.logUserLogin(userId, UserInterfaceType.WHATSAPP); // keep an eye on how often this gets called (may become redundant)
        return ResponseEntity.ok(jwtService.createJwt(tokenRequest));
    }


    @RequestMapping(value = "/phrase/search", method = RequestMethod.POST)
    public ResponseEntity<PhraseSearchResponse> checkIfPhraseTriggersCampaign(@RequestParam String phrase, @RequestParam String userId) {
        Campaign campaign = campaignBroker.findCampaignByJoinWord(phrase, userId, UserInterfaceType.WHATSAPP);
        Group group = campaign != null ? null : groupBroker.searchForGroupByWord(userId, phrase);
        log.info("Incoming phrase check, found ? : campaign: {}, group: {}", campaign != null, group != null);

        PhraseSearchResponse response;
        if (campaign != null) {
            // passing null as channel because reusing USSD, for now
            CampaignMessage message = campaignBroker.getOpeningMessage(campaign.getUid(), null, null, null);
            LinkedHashMap<String, String> menu = getMenuFromMessage(message);
            response = PhraseSearchResponse.builder()
                    .entityFound(true)
                    .entityType(JpaEntityType.CAMPAIGN)
                    .entityUid(campaign.getUid())
                    .responseMessages(getResponseMessages(message, menu.values()))
                    .responseMenu(menu)
                    .build();
        } else if (group != null) {
            RequestDataType outstandingUserInfo = checkForNextUserInfo(userId);
            List<String> messages = new ArrayList<>();
            messages.add(messageSource.getMessage("ussd.home.start.prompt.group.token.named",
                    new String[] { group.getName() }, Locale.ENGLISH));
            messages.addAll(dataRequestMessages(outstandingUserInfo));
            log.info("Adding user to group {}, outstanding data request: {}", group.getName(), outstandingUserInfo);
            response = PhraseSearchResponse.builder()
                    .entityFound(true)
                    .entityType(JpaEntityType.GROUP)
                    .entityUid(group.getUid())
                    .responseMessages(messages)
                    .requestDataType(outstandingUserInfo)
                    .build();
        } else {
            response = PhraseSearchResponse.notFoundResponse();
        }
        return ResponseEntity.ok(response);
    }

    @RequestMapping(value = "/entity/respond/{entityType}/{entityUid}", method = RequestMethod.POST)
    public ResponseEntity<EntityResponseToUser> processFurtherResponseToEntity(@PathVariable JpaEntityType entityType,
                                                                               @PathVariable String entityUid,
                                                                               @RequestParam String userId,
                                                                               @RequestBody EntityReplyFromUser userReply) {
        EntityResponseToUser response;
        log.info("Received user response: {}", userReply);
        if (userReply.getAuxProperties() != null && userReply.getAuxProperties().containsKey("requestDataType")) {
            response = replyToDataRequest(userId, userReply, entityType, entityUid);
        } else if (JpaEntityType.CAMPAIGN.equals(entityType)) {
            response = replyToCampaignMessage(userId, entityUid, userReply.getAuxProperties().get("PRIOR"),
                    CampaignActionType.valueOf(userReply.getMenuOptionPayload()), userReply.getUserMessage());
        } else {
            response = EntityResponseToUser.cannotRespond(entityType, entityUid);
        }
        log.info("Sending back to user: {}", response);
        return ResponseEntity.ok(response);
    }

    private EntityResponseToUser replyToDataRequest(String userId, EntityReplyFromUser userReply, JpaEntityType entityType, String entityId) {
        RequestDataType requestType = RequestDataType.valueOf(userReply.getAuxProperties().get("requestDataType"));
        switch (requestType) {
            case USER_NAME:                 userManagementService.updateDisplayName(userId, userId, userReply.getUserMessage());    break;
            case LOCATION_GPS_REQUIRED:     log.info("Well, we would be setting it from GPS here");                                 break;
            case LOCATION_PROVINCE_OKAY:    userManagementService.updateUserProvince(userId, Province.valueOf(userReply.getMenuOptionPayload()));     break;
            default: log.info("Got a user response we can't do anything with. Request type: {}, user response: {}", requestType, userReply);    break;
        }

        RequestDataType nextRequestType = checkForNextUserInfo(userId);

        return EntityResponseToUser.builder()
                .entityType(entityType)
                .entityUid(entityId)
                .messages(dataRequestMessages(nextRequestType))
                .requestDataType(nextRequestType)
                .build();
    }

    private EntityResponseToUser replyToCampaignMessage(String userId,
                                                        String campaignUid,
                                                        String priorMessageUid,
                                                        CampaignActionType action,
                                                        String userResponse) {
        log.info("Getting campaign message for action type {}, user response {}, campaign ID: {}", action, userResponse, campaignUid);

        switch (action) {
            case JOIN_GROUP:        campaignBroker.addUserToCampaignMasterGroup(campaignUid, userId, UserInterfaceType.WHATSAPP);   break;
            case SIGN_PETITION:     campaignBroker.signPetition(campaignUid, userId, UserInterfaceType.WHATSAPP);                   break;
            case TAG_ME:            campaignBroker.setUserJoinTopic(campaignUid, userId, userResponse, UserInterfaceType.WHATSAPP); break;
            case SHARE_SEND:        campaignBroker.sendShareMessage(campaignUid, userId, userResponse, null, UserInterfaceType.WHATSAPP);   break;
            default:                log.info("No action possible for incoming user action {}, just returning message", action); break;
        }

        List<CampaignMessage> nextMsgs = campaignBroker.findCampaignMessage(campaignUid, action, null);
        if (nextMsgs == null || nextMsgs.isEmpty()) {
            nextMsgs = Collections.singletonList(campaignBroker.findCampaignMessage(campaignUid, priorMessageUid, action));
        }
        log.info("Next campaign messages found: {}", nextMsgs);

        List<String> messageTexts = nextMsgs.stream().map(CampaignMessage::getMessage).collect(Collectors.toList());
        LinkedHashMap<String, String> actionOptions = nextMsgs.stream().filter(CampaignMessage::hasMenuOptions).findFirst()
                .map(this::getMenuFromMessage).orElse(new LinkedHashMap<>());
        messageTexts.addAll(actionOptions.values());

        RequestDataType requestDataType = actionOptions.isEmpty() ? checkForNextUserInfo(userId) : RequestDataType.MENU_SELECTION;
        if (USER_DATA_REQUESTS_WITH_MSGS.contains(requestDataType)) {
            messageTexts.addAll(dataRequestMessages(requestDataType));
        }

        return EntityResponseToUser.builder()
                .entityType(JpaEntityType.CAMPAIGN)
                .entityUid(campaignUid)
                .requestDataType(requestDataType)
                .messages(messageTexts)
                .menu(actionOptions)
                .build();
    }

    private List<String> getResponseMessages(CampaignMessage message, Collection<String> menuOptionTexts) {
        List<String> messages = new ArrayList<>();
        messages.add(message.getMessage());
        messages.addAll(menuOptionTexts);
        return messages;
    }

    private LinkedHashMap<String, String> getMenuFromMessage(CampaignMessage message) {
        LinkedHashMap<String, String> map = new LinkedHashMap<>();
        List<CampaignActionType> options = new ArrayList<>(message.getNextMessages().values());
        log.info("Next menu options, should be in order: {}", options);
        IntStream.range(0, options.size()).forEach(i ->
            map.put(options.get(i).toString(), (i + 1) + ". " + actionToMessage(options.get(i)))
        );
        return map;
    }

    private String actionToMessage(CampaignActionType action) {
        return messageSource.getMessage("ussd.campaign." + action.toString().toLowerCase(),
                null, action.toString(), Locale.ENGLISH);
    }

    private RequestDataType checkForNextUserInfo(String userId) {
        User user = userManagementService.load(userId);
        if (!user.hasName())
            return RequestDataType.USER_NAME;
        if (user.getProvince() == null)
            return RequestDataType.LOCATION_PROVINCE_OKAY;
        else
            return RequestDataType.NONE;
    }

    private List<String> dataRequestMessages(RequestDataType dataType) {
        List<String> messages = new ArrayList<>();
        switch (dataType) {
            case USER_NAME:
                messages.add(messageSource.getMessage("ussd.campaign.joined.name", null, Locale.ENGLISH));
                break;
            case LOCATION_GPS_REQUIRED:
                messages.add(messageSource.getMessage("ussd.campaign.joined.province", null, Locale.ENGLISH));
                break;
            case LOCATION_PROVINCE_OKAY:
                messages.add(messageSource.getMessage("ussd.campaign.joined.province", null, Locale.ENGLISH));
                break;
            case NONE:
                messages.add(messageSource.getMessage("ussd.campaign.exit_positive.generic", null, Locale.ENGLISH));
            default:
                log.info("Trying to extract messages for impossible data type request: {}", dataType);
                break;
        }
        log.info("Returning messages {} for data type {}", messages, dataType);
        return messages;
    }

}
