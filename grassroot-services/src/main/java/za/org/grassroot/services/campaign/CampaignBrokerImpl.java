package za.org.grassroot.services.campaign;

import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Sort;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import za.org.grassroot.core.domain.Broadcast;
import za.org.grassroot.core.domain.Group;
import za.org.grassroot.core.domain.Permission;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.domain.campaign.*;
import za.org.grassroot.core.domain.media.MediaFileRecord;
import za.org.grassroot.core.domain.media.MediaFunction;
import za.org.grassroot.core.domain.notification.CampaignSharingNotification;
import za.org.grassroot.core.enums.CampaignLogType;
import za.org.grassroot.core.enums.MessageVariationAssignment;
import za.org.grassroot.core.enums.UserInterfaceType;
import za.org.grassroot.core.repository.CampaignMessageRepository;
import za.org.grassroot.core.repository.CampaignRepository;
import za.org.grassroot.core.specifications.CampaignMessageSpecifications;
import za.org.grassroot.core.util.AfterTxCommitTask;
import za.org.grassroot.integration.MediaFileBroker;
import za.org.grassroot.services.PermissionBroker;
import za.org.grassroot.services.exception.CampaignCodeTakenException;
import za.org.grassroot.services.exception.GroupNotFoundException;
import za.org.grassroot.services.exception.NoPaidAccountException;
import za.org.grassroot.services.group.GroupBroker;
import za.org.grassroot.services.user.UserManagementService;
import za.org.grassroot.services.util.LogsAndNotificationsBroker;
import za.org.grassroot.services.util.LogsAndNotificationsBundle;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Service @Slf4j
public class CampaignBrokerImpl implements CampaignBroker {

    private static final Logger LOG = LoggerFactory.getLogger(CampaignBrokerImpl.class);

    // use this a lot in campaign message handling
    private static final String LOCALE_SEP = "___";

    private static final List<String> SYSTEM_CODES = Arrays.asList("123", "911");

    private static final String CAMPAIGN_NOT_FOUND_CODE = "campaign.not.found.error";

    private final CampaignRepository campaignRepository;
    private final CampaignMessageRepository campaignMessageRepository;
    private final CampaignStatsBroker campaignStatsBroker;

    private final GroupBroker groupBroker;
    private final UserManagementService userManager;
    private final LogsAndNotificationsBroker logsAndNotificationsBroker;
    private final PermissionBroker permissionBroker;
    private final MediaFileBroker mediaFileBroker;

    private final ApplicationEventPublisher eventPublisher;

    @Autowired
    public CampaignBrokerImpl(CampaignRepository campaignRepository, CampaignMessageRepository campaignMessageRepository, CampaignStatsBroker campaignStatsBroker, GroupBroker groupBroker, UserManagementService userManagementService,
                              LogsAndNotificationsBroker logsAndNotificationsBroker, PermissionBroker permissionBroker, MediaFileBroker mediaFileBroker, ApplicationEventPublisher eventPublisher){
        this.campaignRepository = campaignRepository;
        this.campaignMessageRepository = campaignMessageRepository;
        this.campaignStatsBroker = campaignStatsBroker;
        this.groupBroker = groupBroker;
        this.userManager = userManagementService;
        this.logsAndNotificationsBroker = logsAndNotificationsBroker;
        this.permissionBroker = permissionBroker;
        this.mediaFileBroker = mediaFileBroker;
        this.eventPublisher = eventPublisher;
    }

    @Override
    @Transactional(readOnly = true)
    public Campaign load(String campaignUid) {
        return campaignRepository.findOneByUid(Objects.requireNonNull(campaignUid));
    }

    @Override
    @Transactional(readOnly = true)
    public Set<Locale> getCampaignLanguages(String campaignUid) {
        Campaign campaign = campaignRepository.findOneByUid(campaignUid);
        return new HashSet<>(campaignMessageRepository.selectLocalesForCampaign(campaign));
    }

    @Override
    @Transactional(readOnly = true)
    public CampaignMessage getOpeningMessage(String campaignUid, Locale locale, UserInterfaceType channel, MessageVariationAssignment variation) {
        Campaign campaign = campaignRepository.findOneByUid(campaignUid);
        Locale safeLocale = locale == null ? Locale.ENGLISH : locale;
        UserInterfaceType safeChannel = channel == null ? UserInterfaceType.USSD : channel;
        MessageVariationAssignment safeVariation = variation == null ? MessageVariationAssignment.DEFAULT: variation;
        List<CampaignMessage> messages = campaignMessageRepository.findAll(
                CampaignMessageSpecifications.ofTypeForCampaign(campaign, CampaignActionType.OPENING, safeLocale, safeChannel, safeVariation));
        if (messages.isEmpty()) {
            log.error("Error! Cannot find opening message for campaign");
            return null;
        } else if (messages.size() > 1) {
            log.error("Error! More than one opening message active for campaign");
            return messages.get(0);
        } else {
            return messages.get(0);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public CampaignMessage loadCampaignMessage(String messageUid, String userUid) {
        Objects.requireNonNull(messageUid);
        Objects.requireNonNull(userUid); // todo: add in logging here
        return campaignMessageRepository.findOneByUid(messageUid);
    }


    @Override
    @Transactional(readOnly = true)
    public List<Campaign> getCampaignsCreatedByUser(String userUid) {
        Objects.requireNonNull(userUid);
        User user = userManager.load(userUid);
        return campaignRepository.findByCreatedByUser(user, new Sort("createdDateTime"));
    }

    @Override
    public List<Campaign> getCampaignsCreatedLinkedToGroup(String groupUid) {
        return campaignRepository.findByMasterGroupUid(groupUid, new Sort("createdDateTime"));
    }

    @Override
    @Transactional
    public Campaign getCampaignDetailsByCode(String campaignCode, String userUid, boolean storeLog, UserInterfaceType channel){
        Objects.requireNonNull(campaignCode);
        Campaign campaign = getCampaignByCampaignCode(campaignCode);
        if (campaign != null && storeLog) {
            Objects.requireNonNull(userUid);
            User user = userManager.load(userUid);
            persistCampaignLog(new CampaignLog(user, CampaignLogType.CAMPAIGN_FOUND, campaign, channel, campaignCode));
            campaignStatsBroker.clearCampaignStatsCache(campaign.getUid());
        }
        return campaign;
    }

    @Override
    @Transactional(readOnly = true)
    public Set<String> getActiveCampaignCodes() {
        Set<String> campaignCodes = campaignRepository.fetchAllActiveCampaignCodes();
        campaignCodes.addAll(SYSTEM_CODES);
        return campaignCodes;
    }

    @Override
    @Transactional(readOnly = true)
    public boolean isCodeTaken(String proposedCode, String campaignUid) {
        Objects.requireNonNull(proposedCode);
        if (campaignUid != null) {
            Campaign campaign = campaignRepository.findOneByUid(campaignUid);
            log.info("well, we're looking for campaign, it has code = {}, proposed = {}, is active = {}",
                    campaign.getCampaignCode(), proposedCode, campaign.isActive());
            if (campaign.isActive() && proposedCode.equals(campaign.getCampaignCode())) {
                return false; // because it's not taken by another campaign
            }
        }
        return campaignRepository.countByCampaignCodeAndEndDateTimeAfter(proposedCode, Instant.now()) > 0;
    }

    @Override
    @Transactional(readOnly = true)
    public Set<String> getActiveCampaignJoinTopics() {
        return campaignRepository.fetchAllActiveCampaignTags();
    }

    @Override
    @Transactional
    public void signPetition(String campaignUid, String userUid, UserInterfaceType channel) {
        Campaign campaign = campaignRepository.findOneByUid(Objects.requireNonNull(campaignUid));
        User user = userManager.load(userUid);

        CampaignLog campaignLog = new CampaignLog(user, CampaignLogType.CAMPAIGN_PETITION_SIGNED, campaign, channel, null);
        if (!StringUtils.isEmpty(campaign.getPetitionApi())) {
            log.info("firing at the petition API!", campaign.getPetitionApi());
        }
        LogsAndNotificationsBundle bundle = new LogsAndNotificationsBundle();
        bundle.addLog(campaignLog);
        logsAndNotificationsBroker.asyncStoreBundle(bundle);
        campaignStatsBroker.clearCampaignStatsCache(campaignUid);
    }

    @Async
    @Override
    @Transactional
    public void sendShareMessage(String campaignUid, String sharingUserUid, String sharingNumber, String defaultTemplate, UserInterfaceType channel) {
        Campaign campaign = campaignRepository.findOneByUid(Objects.requireNonNull(campaignUid));
        User user = userManager.load(Objects.requireNonNull(sharingUserUid));

        // todo: check against budget, also increase amount spent
        LogsAndNotificationsBundle bundle = new LogsAndNotificationsBundle();
        User targetUser = userManager.loadOrCreateUser(sharingNumber);
        CampaignLog campaignLog = new CampaignLog(user, CampaignLogType.CAMPAIGN_SHARED, campaign, channel, sharingNumber);
        bundle.addLog(campaignLog);

        // we default to english, because even if sharing user is in another language, the person receiving might not be
        List<CampaignMessage> messages = findCampaignMessage(campaignUid, CampaignActionType.SHARE_SEND, Locale.ENGLISH);
        final String msg = !messages.isEmpty() ? messages.get(0).getMessage() : defaultTemplate;
        final String template = msg.replace(Broadcast.NAME_FIELD_TEMPLATE, "%1$s")
                .replace(Broadcast.ENTITY_FIELD_TEMPLATE, "%2$s")
                .replace(Broadcast.INBOUND_FIELD_TEMPLATE, "%3$s");

        final String mergedMsg = String.format(template, user.getName(), campaign.getName(), campaign.getCampaignCode());
        CampaignSharingNotification sharingNotification = new CampaignSharingNotification(targetUser, mergedMsg, campaignLog);

        log.info("alright, we're storing this notification: {}", sharingNotification);
        bundle.addNotification(sharingNotification);
        log.info("and here is the bundle: {}", bundle);

        logsAndNotificationsBroker.storeBundle(bundle);
        campaignStatsBroker.clearCampaignStatsCache(campaignUid);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean isUserInCampaignMasterGroup(String campaignUid, String userUid) {
        Campaign campaign = campaignRepository.findOneByUid(Objects.requireNonNull(campaignUid));
        User user = userManager.load(Objects.requireNonNull(userUid));
        Group masterGroup = campaign.getMasterGroup();
        return masterGroup.hasMember(user);
    }

    @Override
    @Transactional
    public Campaign create(String campaignName, String campaignCode, String description, String userUid, String masterGroupUid, Instant startDate, Instant endDate, List<String> joinTopics, CampaignType campaignType, String url){
        Objects.requireNonNull(userUid);
        Objects.requireNonNull(masterGroupUid);
        Objects.requireNonNull(campaignType);

        User user = userManager.load(userUid);
        Group masterGroup = groupBroker.load(masterGroupUid);

        if (getActiveCampaignCodes().contains(campaignCode)) {
            throw new CampaignCodeTakenException();
        }

        if (masterGroup == null) {
            throw new GroupNotFoundException();
        }

        if (user.getPrimaryAccount() == null) {
            throw new NoPaidAccountException();
        }

        Campaign newCampaign = new Campaign(campaignName, campaignCode, description,user, startDate, endDate,campaignType, url, user.getPrimaryAccount());
        newCampaign.setMasterGroup(masterGroup);

        if(joinTopics != null && !joinTopics.isEmpty()){
            newCampaign.setJoinTopics(joinTopics.stream().map(String::trim).collect(Collectors.toList()));
            log.info("set campaign join topics ... {}", newCampaign.getJoinTopics());
        }

        Campaign perstistedCampaign = campaignRepository.saveAndFlush(newCampaign);
        CampaignLog campaignLog = new CampaignLog(newCampaign.getCreatedByUser(), CampaignLogType.CREATED_IN_DB, newCampaign, null, null);
        persistCampaignLog(campaignLog);
        return perstistedCampaign;
    }

    @Override
    @Transactional
    public Campaign setCampaignMessages(String userUid, String campaignUid, Set<CampaignMessageDTO> campaignMessages) {
        Objects.requireNonNull(userUid);
        Objects.requireNonNull(campaignUid);
        Objects.requireNonNull(campaignMessages);

        User user = userManager.load(userUid);
        Campaign campaign = campaignRepository.findOneByUid(campaignUid);

        validateUserCanModifyCampaign(user, campaign);

        campaign.setCampaignMessages(transformMessageDTOs(campaignMessages, campaign, user));

        Campaign updatedCampaign = campaignRepository.saveAndFlush(campaign);
        CampaignLog campaignLog = new CampaignLog(user, CampaignLogType.CAMPAIGN_MESSAGES_SET, campaign, null, null);
        persistCampaignLog(campaignLog);

        return updatedCampaign;
    }

    private Set<CampaignMessage> transformMessageDTOs(Set<CampaignMessageDTO> campaignMessages, Campaign campaign, User user) {
        // step one: find and map all the existing messages, if they exist
        Map<String, CampaignMessage> existingMessages = campaign.getCampaignMessages().stream()
                .collect(Collectors.toMap(cm -> cm.getMessageGroupId() + LOCALE_SEP + cm.getLocale(), cm -> cm));
        log.info("campaign already has: {}", existingMessages);

        // step two: explode each of the message DTOs into their separate locale-based messages, mapped by incoming ID and lang
        Map<String, CampaignMessage> explodedMessages = new LinkedHashMap<>();
        campaignMessages.forEach(cm -> explodedMessages.putAll(cm.getMessages().stream().collect(Collectors.toMap(
                msg -> cm.getMessageId() + LOCALE_SEP + msg.getLanguage(),
                msg -> updateExistingOrCreateNew(user, campaign, cm, msg, existingMessages)))));

        log.info("exploded message set: {}", explodedMessages);
        log.info("campaign messages look like: {}", campaignMessages);
        Map<String, CampaignMessageDTO> mappedMessages = campaignMessages.stream().collect(Collectors.toMap(
                CampaignMessageDTO::getMessageId, cm -> cm));

        // step two: go through each message, and wire up where to go next (better than earlier iterations, but can be cleaned)
        campaignMessages.forEach(cdto ->
                cdto.getLanguages().forEach(lang -> {
                    CampaignMessage cm = explodedMessages.get(cdto.getMessageId() + LOCALE_SEP + lang);
                    log.info("wiring up message: {}", cm);
                    cdto.getNextMsgIds().forEach(nextMsgId -> {
                        CampaignMessage nextMsg = explodedMessages.get(nextMsgId + LOCALE_SEP + lang);
                        log.info("for next msg {}, found CM: {}", nextMsgId + LOCALE_SEP + lang, nextMsg);
                        cm.addNextMessage(nextMsg.getUid(), mappedMessages.get(nextMsgId).getLinkedActionType());
                    });
                })
        );

        Set<CampaignMessage> messages = new HashSet<>(explodedMessages.values());
        log.info("completed transformation, unpacked {} messages", messages.size());
        return messages;
    }

    @Override
    public List<CampaignMessage> findCampaignMessage(String campaignUid, CampaignActionType linkedAction, Locale locale) {
        Campaign campaign = campaignRepository.findOneByUid(Objects.requireNonNull(campaignUid));
        return campaignMessageRepository.findAll(
                CampaignMessageSpecifications.ofTypeForCampaign(campaign, linkedAction, locale)
        );
    }

    private CampaignMessage updateExistingOrCreateNew(User user, Campaign campaign, CampaignMessageDTO cm,
                                                      MessageLanguagePair msg, Map<String, CampaignMessage> existingMsgs) {
        boolean newMessage = existingMsgs.isEmpty()
                || !existingMsgs.containsKey(cm.getMessageId() + LOCALE_SEP + msg.getLanguage());
        log.info("does message with this key: {}, exist? {}", cm.getMessageId(), !newMessage);

        if (newMessage) {
            return new CampaignMessage(user, campaign, cm.getLinkedActionType(), cm.getMessageId(),
                    msg.getLanguage(), msg.getMessage(), cm.getChannel(), cm.getVariation());
        } else {
            CampaignMessage message = existingMsgs.get(cm.getMessageId() + LOCALE_SEP + msg.getLanguage());
            message.setActionType(cm.getLinkedActionType());
            message.setMessage(msg.getMessage());
            return message;
        }
    }

    @Override
    @Transactional
    public Campaign updateMasterGroup(String campaignUid, String groupUid, String userUid){
        Group group = groupBroker.load(Objects.requireNonNull(groupUid));
        User user = userManager.load(Objects.requireNonNull(userUid));
        Campaign campaign = campaignRepository.findOneByUid(Objects.requireNonNull(campaignUid));

        if (group == null) {
            throw new IllegalArgumentException("Error! Group to set as master group cannot be null");
        }

        campaign.setMasterGroup(group);
        Campaign updatedCampaign = campaignRepository.saveAndFlush(campaign);
        CampaignLog campaignLog = new CampaignLog(user, CampaignLogType.CAMPAIGN_LINKED_GROUP,campaign, null, groupUid);
        persistCampaignLog(campaignLog);
        return updatedCampaign;
    }

    @Override
    @Transactional
    public void updateCampaignDetails(String userUid, String campaignUid, String name, String description, String mediaFileUid, boolean removeImage, Instant endDate, String newCode, String landingUrl, String petitionApi, List<String> joinTopics) {
        User user = userManager.load(Objects.requireNonNull(userUid));
        Campaign campaign = campaignRepository.findOneByUid(campaignUid);
        validateUserCanModifyCampaign(user, campaign);

        LogsAndNotificationsBundle bundle = new LogsAndNotificationsBundle();
        if (!StringUtils.isEmpty(name) && !campaign.getName().trim().equalsIgnoreCase(name)) {
            campaign.setName(name);
            bundle.addLog(new CampaignLog(user, CampaignLogType.CAMPAIGN_NAME_CHANGED, campaign, null, name));
        }

        if (!StringUtils.isEmpty(description)) {
            campaign.setDescription(description);
            bundle.addLog(new CampaignLog(user, CampaignLogType.CAMPAIGN_DESC_CHANGED, campaign, null, description));
        }

        if (!StringUtils.isEmpty(mediaFileUid)) {
            campaign.setCampaignImage(mediaFileBroker.load(mediaFileUid));
            bundle.addLog(new CampaignLog(user, CampaignLogType.CAMPAIGN_IMG_CHANGED, campaign, null, mediaFileUid));
        } else if (removeImage) {
            campaign.setCampaignImage(null);
            bundle.addLog(new CampaignLog(user, CampaignLogType.CAMPAIGN_IMG_REMOVED, campaign, null, null));
        }

        if (endDate != null) {
            if (campaign.isActive()) {
                campaign.setEndDateTime(endDate);
                bundle.addLog(new CampaignLog(user, CampaignLogType.CAMPAIGN_END_CHANGED, campaign, null, endDate.toString()));
            } else {
                log.info("campaign inactive, reactivating with end date: ", endDate);
                bundle.addLog(reactivateCampaign(campaign, user, endDate, newCode));
            }
        }

        if (!Objects.equals(landingUrl, campaign.getLandingUrl()) || !Objects.equals(petitionApi, campaign.getPetitionApi())) {
            campaign.setLandingUrl(landingUrl);
            campaign.setPetitionApi(petitionApi);
            bundle.addLog(new CampaignLog(user, CampaignLogType.CAMPAIGN_URLS_CHANGED, campaign, null,
                    landingUrl + "; " + petitionApi));
        }

        if (joinTopics != null) {
            campaign.setJoinTopics(joinTopics);
        }

        log.info("campaign still has account? {}", campaign.getAccount());

        AfterTxCommitTask task = () -> logsAndNotificationsBroker.storeBundle(bundle);
        eventPublisher.publishEvent(task);
    }

    private CampaignLog reactivateCampaign(Campaign campaign, User user, Instant newEndDate, String newCode) {
        if (campaign.isActive()) {
            throw new IllegalArgumentException("Error! Campaign already active");
        }

        Set<String> activeCodes = getActiveCampaignCodes();
        if (!StringUtils.isEmpty(newCode) && !activeCodes.contains(newCode)) {
            campaign.setCampaignCode(newCode);
        } else if (activeCodes.contains(campaign.getCampaignCode())) {
            campaign.setCampaignCode(null);
        }

        log.info("reactivating campaign, with new end date: {}", newEndDate);
        campaign.setEndDateTime(newEndDate);

        return new CampaignLog(user, CampaignLogType.CAMPAIGN_REACTIVATED, campaign, null, newEndDate + ";" + newCode);
    }

    @Override
    @Transactional
    public void alterSmsSharingSettings(String userUid, String campaignUid, boolean smsEnabled, Long smsBudget, Set<CampaignMessageDTO> sharingMessages) {
        User user = userManager.load(Objects.requireNonNull(userUid));
        Campaign campaign = campaignRepository.findOneByUid(campaignUid);

        validateUserCanModifyCampaign(user, campaign);

        boolean noSharingMsgs = sharingMessages.stream().noneMatch(msg -> CampaignActionType.SHARE_PROMPT.equals(msg.getLinkedActionType()));
        boolean noSharingMsgInCampaign = campaign.getCampaignMessages().stream().noneMatch(msg -> CampaignActionType.SHARE_PROMPT.equals(msg.getActionType()));
        if (smsEnabled && noSharingMsgs && noSharingMsgInCampaign) {
            throw new IllegalArgumentException("Attempting to enable with no prior or given sharing prompts");
        }

        campaign.setSharingEnabled(smsEnabled);
        campaign.setSharingBudget(smsBudget);

        campaign.addCampaignMessages(transformMessageDTOs(sharingMessages, campaign, user));

        persistCampaignLog(new CampaignLog(user, CampaignLogType.SHARING_SETTINGS_ALTERED, campaign, null,
                "Enabled: " + smsEnabled + ", budget = " + smsBudget));
    }

    @Override
    @Transactional
    public Campaign addUserToCampaignMasterGroup(String campaignUid, String userUid, UserInterfaceType channel){
        Objects.requireNonNull(campaignUid);
        Objects.requireNonNull(userUid);
        User user = userManager.load(userUid);
        Campaign campaign = campaignRepository.findOneByUid(campaignUid);
        groupBroker.addMemberViaCampaign(user.getUid(),campaign.getMasterGroup().getUid(),campaign.getCampaignCode());
        CampaignLog campaignLog = new CampaignLog(user, CampaignLogType.CAMPAIGN_USER_ADDED_TO_MASTER_GROUP, campaign, channel, null);
        persistCampaignLog(campaignLog);
        campaignStatsBroker.clearCampaignStatsCache(campaignUid);
        return campaign;
    }

    @Override
    @Transactional
    public void setUserJoinTopic(String campaignUid, String userUid, String joinTopic, UserInterfaceType channel) {
        User user = userManager.load(Objects.requireNonNull(userUid));
        Campaign campaign = campaignRepository.findOneByUid(Objects.requireNonNull(campaignUid));

        CampaignLog campaignLog = new CampaignLog(user, CampaignLogType.CAMPAIGN_USER_TAGGED, campaign, channel,
                Campaign.JOIN_TOPIC_PREFIX + joinTopic);

        groupBroker.assignMembershipTopics(userUid, campaign.getMasterGroup().getUid(), userUid, Collections.singleton(joinTopic), true);
        persistCampaignLog(campaignLog);
    }

    @Override
    @Transactional
    public void updateCampaignType(String userUid, String campaignUid, CampaignType newType, Set<CampaignMessageDTO> revisedMessages) {
        User user = userManager.load(Objects.requireNonNull(userUid));
        Campaign campaign = campaignRepository.findOneByUid(Objects.requireNonNull(campaignUid));

        validateUserCanModifyCampaign(user, campaign);

        CampaignType oldType = campaign.getCampaignType();
        campaign.setCampaignType(newType);

        if (revisedMessages != null && !revisedMessages.isEmpty()) {
            setCampaignMessages(userUid, campaignUid, revisedMessages);
        }

        persistCampaignLog(new CampaignLog(user, CampaignLogType.CAMPAIGN_TYPE_CHANGED, campaign, null,
                "From: " + oldType + ", to = " + newType));
    }

    @Override
    @Transactional
    public void setCampaignImage(String userUid, String campaignUid, String mediaFileKey) {
        User user = userManager.load(Objects.requireNonNull(userUid));
        Campaign campaign = campaignRepository.findOneByUid(Objects.requireNonNull(campaignUid));

        log.info("user = {}, campaign = {}", user, campaign);
        validateUserCanModifyCampaign(user, campaign);

        MediaFileRecord record = mediaFileBroker.load(MediaFunction.CAMPAIGN_IMAGE, mediaFileKey);
        campaign.setCampaignImage(record);
    }

    private Campaign getCampaignByCampaignCode(String campaignCode){
        Objects.requireNonNull(campaignCode);
        return campaignRepository.findByCampaignCodeAndEndDateTimeAfter(campaignCode, Instant.now());
    }

    // leave this here for a while as may come in handy in future, although not quite yet
    private String createSearchValue(String value, MessageVariationAssignment assignment, Locale locale, String tag){
        String AND = " and ";
        StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append(" search by ");
        stringBuilder.append((value != null)? value:"");
        stringBuilder.append((assignment != null)? AND.concat(assignment.name()):"");
        stringBuilder.append((locale !=  null)? AND.concat(locale.getDisplayLanguage()):"");
        stringBuilder.append((tag != null)? AND.concat(tag):"");
        return stringBuilder.toString();
    }

    private void createAndStoreCampaignLog(CampaignLog campaignLog) {
        LogsAndNotificationsBundle bundle = new LogsAndNotificationsBundle();
        bundle.addLog(campaignLog);
        logsAndNotificationsBroker.asyncStoreBundle(bundle);
    }

    private void persistCampaignLog(CampaignLog accountLog) {
        AfterTxCommitTask task = () -> createAndStoreCampaignLog(accountLog);
        eventPublisher.publishEvent(task);
    }

    private void validateUserCanModifyCampaign(User user, Campaign campaign) {
        // for the moment
        if (!campaign.getCreatedByUser().equals(user)) {
            permissionBroker.validateGroupPermission(user, campaign.getMasterGroup(), Permission.GROUP_PERMISSION_UPDATE_GROUP_DETAILS);
        }
    }

}
