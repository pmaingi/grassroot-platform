package za.org.grassroot.webapp.controller.rest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import za.org.grassroot.core.domain.*;
import za.org.grassroot.core.domain.account.AccountLog;
import za.org.grassroot.core.domain.geo.AddressLog;
import za.org.grassroot.core.domain.livewire.LiveWireLog;
import za.org.grassroot.core.domain.task.Event;
import za.org.grassroot.core.domain.task.EventLog;
import za.org.grassroot.core.domain.task.TodoLog;
import za.org.grassroot.core.enums.*;
import za.org.grassroot.core.repository.GroupLogRepository;
import za.org.grassroot.core.repository.UserLogRepository;
import za.org.grassroot.integration.NotificationService;
import za.org.grassroot.integration.messaging.MessagingServiceBroker;
import za.org.grassroot.services.MessageAssemblingService;
import za.org.grassroot.services.task.EventBroker;
import za.org.grassroot.services.task.EventLogBroker;
import za.org.grassroot.services.task.VoteBroker;
import za.org.grassroot.services.user.UserManagementService;
import za.org.grassroot.webapp.model.AatMsgStatus;
import za.org.grassroot.webapp.model.SMSDeliveryStatus;

import java.text.MessageFormat;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Created by paballo on 2016/02/17.
 */

@RestController
@RequestMapping("/sms/")
public class IncomingSMSController {

    private static final Logger log = LoggerFactory.getLogger(IncomingSMSController.class);

    private final EventBroker eventBroker;
    private final VoteBroker voteBroker;
    private final UserLogRepository userLogRepository;
    private final NotificationService notificationService;
    private final GroupLogRepository groupLogRepository;
    private final UserManagementService userManager;
    private final EventLogBroker eventLogManager;

    private final MessageAssemblingService messageAssemblingService;
    private final MessagingServiceBroker messagingServiceBroker;

    private static final String FROM_PARAMETER ="fn";
    private static final String MESSAGE_TEXT_PARAM ="ms";

    private static final String TO_PARAMETER = "tn";
    private static final String SUCCESS_PARAMETER = "sc";
    private static final String REF_PARAMETER = "rf";
    private static final String STATUS_PARAMETER = "st";
    private static final String TIME_PARAMETER = "ts";


    private static final Duration NOTIFICATION_WINDOW = Duration.of(6, ChronoUnit.HOURS);

    @Autowired
    public IncomingSMSController(EventBroker eventBroker, UserManagementService userManager, EventLogBroker eventLogManager,
                                 MessageAssemblingService messageAssemblingService, MessagingServiceBroker messagingServiceBroker,
                                 VoteBroker voteBroker, UserLogRepository userLogRepository, NotificationService notificationService,
                                 GroupLogRepository groupLogRepository) {

        this.eventBroker = eventBroker;
        this.userManager = userManager;
        this.eventLogManager = eventLogManager;
        this.messageAssemblingService = messageAssemblingService;
        this.messagingServiceBroker = messagingServiceBroker;
        this.voteBroker = voteBroker;
        this.userLogRepository = userLogRepository;
        this.notificationService = notificationService;
        this.groupLogRepository = groupLogRepository;
    }


    @RequestMapping(value = "incoming", method = RequestMethod.GET)
    public void receiveSms(@RequestParam(value = FROM_PARAMETER) String phoneNumber,
                           @RequestParam(value = MESSAGE_TEXT_PARAM) String msg) {


        log.info("Inside AATIncomingSMSController -" + " following param values were received + ms ="+msg+ " fn= "+phoneNumber);

        User user = userManager.findByInputNumber(phoneNumber);
        String trimmedMsg =  msg.toLowerCase().trim();

        if (user == null) {
            log.warn("Message from unknown user: " + phoneNumber);
            return;
        }

        EventRSVPResponse responseType = EventRSVPResponse.fromString(trimmedMsg);
        boolean isYesNoResponse = responseType == EventRSVPResponse.YES || responseType == EventRSVPResponse.NO || responseType == EventRSVPResponse.MAYBE;

        List<Event> outstandingVotes = eventBroker.getOutstandingResponseForUser(user, EventType.VOTE);
        List<Event> outstandingYesNoVotes = outstandingVotes.stream()
                .filter(vote -> vote.getTags() == null || vote.getTags().length == 0)
                .collect(Collectors.toList());

        List<Event> outstandingOptionsVotes = outstandingVotes.stream()
                .filter(vote -> hasVoteOption(trimmedMsg, vote))
                .collect(Collectors.toList());

        List<Event> outstandingMeetings = eventBroker.getOutstandingResponseForUser(user, EventType.MEETING);

        if (isYesNoResponse && !outstandingMeetings.isEmpty()) {  // user sent yes-no response and there is a meeting awaiting yes-no response
            log.info("User response is {}, type {} and there are outstanding meetings for that user. Recording RSVP...", trimmedMsg, responseType);
            eventLogManager.rsvpForEvent(outstandingMeetings.get(0).getUid(), user.getUid(), responseType); // recording rsvp for meeting
        } else if (isYesNoResponse && !outstandingYesNoVotes.isEmpty()) { // user sent yes-no response and there is a vote awaiting yes-no response
            log.info("User response is {}, type {} and there are outstanding YES_NO votes for that user. Recording vote...", trimmedMsg, responseType);
            voteBroker.recordUserVote(user.getUid(), outstandingYesNoVotes.get(0).getUid(), trimmedMsg); // recording user vote
        }

        else if (!outstandingOptionsVotes.isEmpty()) { // user sent something other then yes-no, and there is a vote that has this option (tag)
            log.info("User response is {}, type {} and there are outstanding votes with custom option matching user's answer. Recording vote...", trimmedMsg, responseType);
            Event vote = outstandingOptionsVotes.get(0);
            String option = getVoteOption(trimmedMsg, vote);
            voteBroker.recordUserVote(user.getUid(), vote.getUid(), option); // recording user vote
        } else {// we have not found any meetings or votes that this could be response to
            log.info("User response is {}, type {} and there are no outstanding meetings or votes this answer is. Recording vote...", trimmedMsg, responseType);
            handleUnknownResponse(user, trimmedMsg);
        }

    }


    @RequestMapping(value = "receipt")
    public void deliveryReceipt(
            @RequestParam(value = FROM_PARAMETER) String fromNumber,
            @RequestParam(value = TO_PARAMETER, required = false) String toNumber,
            @RequestParam(value = SUCCESS_PARAMETER, required = false) String success,
            @RequestParam(value = REF_PARAMETER) String msgKey,
            @RequestParam(value = STATUS_PARAMETER) Integer status,
            @RequestParam(value = TIME_PARAMETER, required = false) String time) {

        log.info("AATIncomingSMSController -" + " message delivery receipt from number: {}, message key: {}", fromNumber, msgKey);

        Notification notification = notificationService.loadBySeningKey(msgKey);
        if (notification != null) {
            AatMsgStatus aatMsgStatus = AatMsgStatus.fromCode(status);
            SMSDeliveryStatus deliveryStatus = aatMsgStatus.toSMSDeliveryStatus();
            if (deliveryStatus == SMSDeliveryStatus.DELIVERED)
                notificationService.updateNotificationStatus(notification.getUid(), NotificationStatus.DELIVERED, null, null);
            else if (deliveryStatus == SMSDeliveryStatus.DELIVERY_FAILED)
                notificationService.updateNotificationStatus(notification.getUid(), NotificationStatus.DELIVERY_FAILED, "Message delivery failed: " + aatMsgStatus.name(), null);
        }

    }


    private void handleUnknownResponse(User user, String trimmedMsg) {

        log.info("Handling unexpected user SMS message");
        notifyUnableToProcessReply(user);

        log.info("Recording  unexpected user SMS message user log.");
        UserLog userLog = new UserLog(user.getUid(), UserLogType.SENT_UNEXPECTED_SMS_MESSAGE,
                trimmedMsg,
                UserInterfaceType.INCOMING_SMS);

        userLogRepository.save(userLog);

        List<Notification> recentNotifications = notificationService
                .fetchAndroidNotificationsSince(user.getUid(), Instant.now().minus(NOTIFICATION_WINDOW));

        for (Notification notification : recentNotifications) {

            Map<ActionLog, Group> logs = getNotificationLog(notification);

            for (Map.Entry<ActionLog, Group> entry : logs.entrySet()) {
                ActionLog aLog = entry.getKey();

                Group group = entry.getValue();

                // String notificationType = getNotificationType(aLog);
                String description = MessageFormat.format("{0}; {1}", trimmedMsg, notification.getMessage());
                GroupLog groupLog = new GroupLog(group, user, GroupLogType.USER_SENT_UNKNOWN_RESPONSE, user.getId(), description);

                log.info("Recording group log for unexpected user SMS message after notification has been sent to him/her. " +
                        "Group {}, notification uid: {}, user: {}, message: {} ", group.getGroupName(), notification.getUid(), user.getDisplayName(), trimmedMsg);
                groupLogRepository.save(groupLog);
            }
        }
    }

    // might need this in future so just leaving it here
    private String getNotificationType(ActionLog aLog) {
        if (aLog instanceof EventLog)
            return "Event log: " + ((EventLog) aLog).getEventLogType().name();
        else if (aLog instanceof TodoLog)
            return "ToDo log: " + ((TodoLog) aLog).getType().name();
        else if (aLog instanceof GroupLog)
            return "Group log: " + ((GroupLog) aLog).getGroupLogType().name();
        else if (aLog instanceof UserLog)
            return "User log: " + ((UserLog) aLog).getUserLogType().name();
        else if (aLog instanceof AccountLog)
            return "Account log: " + ((AccountLog) aLog).getAccountLogType().name();
        else if (aLog instanceof AddressLog)
            return "Address log: " + ((AddressLog) aLog).getType().name();
        else if (aLog instanceof LiveWireLog)
            return "LiveWire log: " + ((LiveWireLog) aLog).getType().name();
        else return "Unknown notification type";
    }

    private Map<ActionLog, Group> getNotificationLog(Notification notification) {

        Map<ActionLog, Group> logGroupMap = new HashMap<>();

        if (notification.getEventLog() != null)
            logGroupMap.put(notification.getEventLog(), notification.getEventLog().getEvent().getAncestorGroup());

        else if (notification.getTodoLog() != null)
            logGroupMap.put(notification.getTodoLog(), notification.getTodoLog().getTodo().getAncestorGroup());

        else if (notification.getGroupLog() != null)
            logGroupMap.put(notification.getGroupLog(), notification.getGroupLog().getGroup());

        else if (notification.getLiveWireLog() != null)
            logGroupMap.put(notification.getLiveWireLog(), notification.getLiveWireLog().getAlert().getGroup());

        else if (notification.getAccountLog() != null)
            logGroupMap.put(notification.getAccountLog(), notification.getAccountLog().getGroup());

        // note: user log and address are not address related, so just watch the overall user logs for those

        return logGroupMap;
    }

    private boolean hasVoteOption(String option, Event vote) {
        if (vote.getTags() != null) {
            for (String tag : vote.getTags()) {
                if (tag.equalsIgnoreCase(option))
                    return true;
            }
        }
        return false;
    }

    private String getVoteOption(String option, Event vote) {
        if (vote.getTags() != null) {
            for (String tag : vote.getTags()) {
                if (tag.equalsIgnoreCase(option))
                    return tag;
            }
        }
        return null;
    }


    private void notifyUnableToProcessReply(User user) {
        String message = messageAssemblingService.createReplyFailureMessage(user);
        messagingServiceBroker.sendSMS(message, user.getPhoneNumber(), true);
    }

}
