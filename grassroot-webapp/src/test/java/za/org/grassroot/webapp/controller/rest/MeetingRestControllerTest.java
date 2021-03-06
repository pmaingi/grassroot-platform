package za.org.grassroot.webapp.controller.rest;

import org.junit.Before;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import za.org.grassroot.core.domain.GroupRole;
import za.org.grassroot.core.domain.JpaEntityType;
import za.org.grassroot.core.domain.group.GroupJoinMethod;
import za.org.grassroot.core.domain.task.EventLog;
import za.org.grassroot.core.domain.task.EventReminderType;
import za.org.grassroot.core.dto.ResponseTotalsDTO;
import za.org.grassroot.core.enums.EventLogType;
import za.org.grassroot.core.enums.EventRSVPResponse;
import za.org.grassroot.core.enums.UserInterfaceType;
import za.org.grassroot.services.task.MeetingBuilderHelper;
import za.org.grassroot.webapp.controller.android1.MeetingRestController;

import java.time.format.DateTimeFormatter;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Created by Siyanda Mzam on 2016/03/22.
 */
public class MeetingRestControllerTest extends RestAbstractUnitTest {

    private static final Logger logger = LoggerFactory.getLogger(MeetingRestControllerTest.class);

    @InjectMocks
    private MeetingRestController meetingRestController;

    private static final String path = "/api/meeting";

    @Before
    public void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(meetingRestController).build();
    }

    @Test
    public void creatingAMeetingShouldWork() throws Exception {
        Set<String> membersToAdd = new HashSet<>();
        MeetingBuilderHelper helper = new MeetingBuilderHelper()
                .userUid(sessionTestUser.getUid())
                .startDateTime(testDateTime)
                .parentUid(testGroup.getUid())
                .parentType(JpaEntityType.GROUP)
                .name(testEventTitle)
                .description(testEventDescription)
                .location(testEventLocation)
                .reminderType(EventReminderType.GROUP_CONFIGURED)
                .customReminderMinutes(-1)
                .assignedMemberUids(membersToAdd);

        when(userManagementServiceMock.findByInputNumber(testUserPhone)).thenReturn(sessionTestUser);
        logger.debug("meetingHelperTest: {}", helper);
        when(eventBrokerMock.createMeeting(helper, UserInterfaceType.ANDROID)).thenReturn(meetingEvent);

        mockMvc.perform(post(path + "/create/{phoneNumber}/{code}/{parentUid}", testUserPhone, testUserCode, testGroup.getUid())
                                .param("title", testEventTitle)
                                .param("description", testEventDescription)
                                .param("eventStartDateTime", testDateTime.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME))
                                .param("reminderMinutes", String.valueOf(-1))
                                .param("location", testEventLocation))
                .andExpect(status().is2xxSuccessful());

        verify(userManagementServiceMock).findByInputNumber(testUserPhone);
        verify(eventBrokerMock).createMeeting(helper, UserInterfaceType.ANDROID);
    }

    @Test
    public void updatingAMeetingShoulWork() throws Exception {
        when(userManagementServiceMock.findByInputNumber(testUserPhone)).thenReturn(sessionTestUser);
        mockMvc.perform(post(path + "/update/{phoneNumber}/{code}/{meetingUid}", testUserPhone, testUserCode, meetingEvent.getUid())
                .param("title", testEventTitle)
                .param("description", testEventDescription)
                .param("startTime", testDateTime.format(DateTimeFormatter.ISO_DATE_TIME))
                .param("location", testEventLocation))
                .andExpect(status().is2xxSuccessful());
        verify(userManagementServiceMock).findByInputNumber(testUserPhone);
    }

    @Test
    public void rsvpingShouldWork() throws Exception {

        when(userManagementServiceMock.findByInputNumber(testUserPhone)).thenReturn(sessionTestUser);
        when(eventBrokerMock.loadMeeting(meetingEvent.getUid())).thenReturn(meetingEvent);
        mockMvc.perform(get(path + "/rsvp/{id}/{phoneNumber}/{code}", meetingEvent.getUid(), testUserPhone, testUserCode)
                                .param("response", "Yes"))
                .andExpect(status().is2xxSuccessful());
        verify(userManagementServiceMock).findByInputNumber(testUserPhone);
        verify(eventBrokerMock).loadMeeting(meetingEvent.getUid());
    }

    @Test
    public void viewRsvpingShouldWork() throws Exception {
        testGroup.addMember(sessionTestUser, GroupRole.ROLE_GROUP_ORGANIZER, GroupJoinMethod.ADDED_BY_OTHER_MEMBER, null);
        EventLog testLog = new EventLog(sessionTestUser, meetingEvent, EventLogType.RSVP, EventRSVPResponse.YES);
        ResponseTotalsDTO testResponseTotalsDTO = ResponseTotalsDTO.makeForTest(40, 20, 10, 0, 70);

        when(userManagementServiceMock.findByInputNumber(testUserPhone)).thenReturn(sessionTestUser);
        when(eventBrokerMock.loadMeeting(meetingEvent.getUid())).thenReturn(meetingEvent);
        when(eventLogRepositoryMock.findOne(any(Specification.class))).thenReturn(Optional.of(testLog));
        when(eventLogBrokerMock.getResponseCountForEvent(meetingEvent)).thenReturn(testResponseTotalsDTO);
        mockMvc.perform(get(path + "/view/{id}/{phoneNumber}/{code}", meetingEvent.getUid(), testUserPhone, testUserCode))
                .andExpect(status().is2xxSuccessful());
        verify(userManagementServiceMock).findByInputNumber(testUserPhone);
        verify(eventBrokerMock).loadMeeting(meetingEvent.getUid());
        verify(eventLogRepositoryMock).findOne(any(Specification.class));
        verify(eventLogBrokerMock).getResponseCountForEvent(meetingEvent);

    }
}
