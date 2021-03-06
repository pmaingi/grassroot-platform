package za.org.grassroot.webapp.controller.ussd;

import org.junit.Before;
import org.junit.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.util.StringUtils;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.domain.group.Group;
import za.org.grassroot.core.domain.group.GroupPermissionTemplate;
import za.org.grassroot.core.domain.task.Todo;
import za.org.grassroot.core.domain.task.TodoAssignment;
import za.org.grassroot.core.domain.task.TodoRequest;
import za.org.grassroot.core.domain.task.TodoType;
import za.org.grassroot.webapp.util.USSDUrlUtil;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.mockito.Mockito.*;

/**
 * Created by luke on 2015/12/18.
 */
public class UssdTodoServiceTest extends UssdUnitTest {

    public static final String assignUserID = "assignUserUid";

    private static final String testUserPhone = "0601110001";
    private static final String phoneParam = "msisdn";
    private static final String todoUidParam = "todoUid";
    private static final String dummyUserInput = "blah blah blah blah";
    private static final String testMessage = "Test message";
    private static final String testUserResponse = "yes";
    private static final String groupMenu = "group",
            subjectMenu = "subject",
            dueDateMenu = "due_date",
            confirmMenu = "confirm",
            send = "send";
    private static final String listEntriesMenu = "list",
            viewEntryMenu = "view",
            setCompleteMenu = "complete";

    private User testUser;
    private Todo testTodo;
    private Group testGroup;

    private UssdTodoService ussdTodoService;

    @Before
    public void setUp() {
        testUser = new User(testUserPhone,"Test User", null);
        testGroup = new Group("Test Group", GroupPermissionTemplate.DEFAULT_GROUP, testUser);
        testTodo = new Todo(testUser,testGroup, TodoType.ACTION_REQUIRED,testMessage, Instant.now());

        this.ussdTodoService = new UssdTodoServiceImpl(ussdSupport, ussdMessageAssembler, userManagementServiceMock, todoBrokerMock, todoRequestBrokerMock, cacheUtilManagerMock, ussdGroupUtil, accountFeaturesBrokerMock, learningServiceMock, memberDataExportBrokerMock);
    }

    @Test
    public void startMenuShouldWork() throws Exception {
        when(userManagementServiceMock.findByInputNumber(testUserPhone)).thenReturn(testUser);

        this.ussdTodoService.processStart(testUserPhone);

        verify(userManagementServiceMock, times(1)).findByInputNumber(testUserPhone);
        verifyNoMoreInteractions(userManagementServiceMock);
    }

    @Test
    public void volunteerResponseShouldWork()throws Exception{
        when(userManagementServiceMock.findByInputNumber(testUserPhone)).thenReturn(testUser);

        this.ussdTodoService.processVolunteerResponse(testUserPhone, testTodo.getUid(), testUserResponse);

        verify(userManagementServiceMock,times(1)).findByInputNumber(testUserPhone);
    }

    @Test
    public void confirmInfoResponseShouldWork()throws Exception{
        when(userManagementServiceMock.findByInputNumber(testUserPhone,
                saveUrl("/respond/info", testTodo.getUid(), testUserResponse))).thenReturn(testUser);

        this.ussdTodoService.processConfirmInfoResponse(testUserPhone, testTodo.getUid(), testUserResponse, null);

        verify(userManagementServiceMock,times(1)).findByInputNumber(testUserPhone,saveUrl("/respond/info", testTodo.getUid(), testUserResponse));
    }

    private String saveUrl(String menu, String todoUid, String userInput) {
        return "todo" + menu + "?todoUid=" + todoUid + "&priorInput=" + USSDUrlUtil.encodeParameter(userInput);
    }

    @Test
    public void recordInfoResponseShouldWork()throws Exception{
        when(userManagementServiceMock.findByInputNumber(testUserPhone,null)).thenReturn(testUser);

        this.ussdTodoService.processRecordInfoResponse(testUserPhone, testTodo.getUid(), testUserResponse);

        verify(userManagementServiceMock,times(1)).findByInputNumber(testUserPhone,null);
    }


    @Test
    public void reviseInfoRequestShouldWork()throws Exception{
        when(userManagementServiceMock.findByInputNumber(testUserPhone,
                saveUrl("/respond/info/revise", testTodo.getUid(), testUserResponse))).thenReturn(testUser);

        this.ussdTodoService.processReviseInfoRequest(testUserPhone, testTodo.getUid(), testUserResponse, null);

        verify(userManagementServiceMock,times(1)).findByInputNumber(testUserPhone,saveUrl("/respond/info/revise", testTodo.getUid(), testUserResponse));
    }

    @Test
    public void validateTodoCompletionShouldWork()throws Exception{
        when(userManagementServiceMock.findByInputNumber(testUserPhone)).thenReturn(testUser);

        this.ussdTodoService.processValidateTodoCompletion(testUserPhone, testTodo.getUid(), testUserResponse);

        verify(userManagementServiceMock,times(1)).findByInputNumber(testUserPhone);
    }

    @Test
    public void createShouldWork()throws Exception{
        when(userManagementServiceMock.findByInputNumber(testUserPhone)).thenReturn(testUser);
        when(todoRequestBrokerMock.create(testUser.getUid(),
                TodoType.ACTION_REQUIRED)).thenReturn(new TodoRequest(testUser,TodoType.ACTION_REQUIRED));

        this.ussdTodoService.processCreate(testUserPhone, TodoType.ACTION_REQUIRED, null);

        verify(userManagementServiceMock,times(1)).findByInputNumber(testUserPhone);
        verify(todoRequestBrokerMock,times(1)).create(testUser.getUid(),TodoType.ACTION_REQUIRED);
    }

    @Test
    public void askForSubjectShouldWork()throws Exception{
        String testStoreUid = testTodo.getUid();
        when(userManagementServiceMock.findByInputNumber(testUserPhone,
                saveRequestUrl("/create/subject", testStoreUid, null))).thenReturn(testUser);
        TodoRequest testTodoRequest = new TodoRequest(testUser,TodoType.ACTION_REQUIRED);
        when(todoRequestBrokerMock.load(testStoreUid)).thenReturn(testTodoRequest);

        this.ussdTodoService.processAskForSubject(testUserPhone, testStoreUid, null, null);

        verify(userManagementServiceMock,times(1))
                .findByInputNumber(testUserPhone,saveRequestUrl("/create/subject", testStoreUid, null));
        verify(todoRequestBrokerMock,times(1)).load(testStoreUid);
    }

    @Test
    public void askForDeadlineShouldWork()throws Exception{
        String testStoreUid = testTodo.getUid();
        TodoRequest testTodoRequest = new TodoRequest(testUser,TodoType.ACTION_REQUIRED);
        when(userManagementServiceMock.findByInputNumber(testUserPhone,
                saveRequestUrl("/create/deadline", testStoreUid, null))).thenReturn(testUser);

        when(todoRequestBrokerMock.load(testStoreUid)).thenReturn(testTodoRequest);

        this.ussdTodoService.processAskForDeadline(testUserPhone, testStoreUid, testUserResponse, null, false);

        verify(userManagementServiceMock,times(1))
                .findByInputNumber(testUserPhone,saveRequestUrl("/create/deadline", testStoreUid, null));
        verify(todoRequestBrokerMock,times(1)).load(testStoreUid);
    }

    @Test
    public void askForResponseTagShouldWork()throws Exception{
        String testStoreUid = testTodo.getUid();
        when(userManagementServiceMock.findByInputNumber(testUserPhone,
                saveRequestUrl("/create/tag", testStoreUid, null))).thenReturn(testUser);

        this.ussdTodoService.processAskForResponseTag(testUserPhone, testStoreUid, testUserResponse, null, null);

        verify(userManagementServiceMock,times(1))
                .findByInputNumber(testUserPhone,saveRequestUrl("/create/tag", testStoreUid, null));
    }

    @Test
    public void creationDateInPastShouldReturnNoticeToUser()throws Exception{
        String testStoreUid = testTodo.getUid();
        TodoRequest testTodoRequest = new TodoRequest(testUser,TodoType.ACTION_REQUIRED);
        testTodoRequest.setActionByDate(Instant.now().minus(1, ChronoUnit.DAYS));
        when(userManagementServiceMock.findByInputNumber(testUserPhone,
                saveRequestUrl("/confirm", testStoreUid, null))).thenReturn(testUser);
        when(todoRequestBrokerMock.load(testStoreUid)).thenReturn(testTodoRequest);

        this.ussdTodoService.processConfirmTodoCreation(testUserPhone, testStoreUid, testUserResponse, null, null);

        verify(userManagementServiceMock,times(1))
                .findByInputNumber(testUserPhone,saveRequestUrl("/confirm", testStoreUid, null));
        verify(todoRequestBrokerMock,times(1)).load(testStoreUid);
    }

    @Test
    public void confirmTodoCreationShouldWork()throws Exception{
        String testStoreUid = testTodo.getUid();
        TodoRequest testTodoRequest = new TodoRequest(testUser,TodoType.ACTION_REQUIRED);
        testTodoRequest.setActionByDate(Instant.now().plus(1, ChronoUnit.DAYS));
        testTodoRequest.setParent(testGroup);
        when(userManagementServiceMock.findByInputNumber(testUserPhone,
                saveRequestUrl("/confirm", testStoreUid, null))).thenReturn(testUser);
        when(todoRequestBrokerMock.load(testStoreUid)).thenReturn(testTodoRequest);

        this.ussdTodoService.processConfirmTodoCreation(testUserPhone, testStoreUid, testUserResponse, null, null);

        verify(userManagementServiceMock,times(1))
                .findByInputNumber(testUserPhone,saveRequestUrl("/confirm", testStoreUid, null));
        verify(todoRequestBrokerMock,times(1)).load(testStoreUid);
    }

    @Test
    public void finishTodoEntryShouldWork()throws Exception{
        String testStoreUid = testTodo.getUid();
        when(userManagementServiceMock.findByInputNumber(testUserPhone,null)).thenReturn(testUser);

        this.ussdTodoService.processFinishTodoEntry(testUserPhone, testStoreUid);

        verify(userManagementServiceMock,times(1)).findByInputNumber(testUserPhone,null);
    }

    @Test
    public void viewExistingTodosShouldWork()throws Exception{

        List<Todo> testTodos = Arrays.asList(
                new Todo(testUser, testGroup, TodoType.ACTION_REQUIRED, testMessage, Instant.now()),
                new Todo(testUser, testGroup, TodoType.ACTION_REQUIRED, testMessage, Instant.now()),
                new Todo(testUser, testGroup, TodoType.ACTION_REQUIRED, testMessage, Instant.now()));

        Page<Todo> testPage = new PageImpl<>(testTodos);
        PageRequest testPageRequest = PageRequest.of(0, 3, new Sort(Sort.Direction.DESC, "createdDateTime"));

        when(userManagementServiceMock.findByInputNumber(testUserPhone,
                "todo/existing?page=" + 0 + "&fetchAll=" + true)).thenReturn(testUser);

        when(todoBrokerMock.fetchPageOfTodosForUser(testUser.getUid(),
                false, testPageRequest)).thenReturn(testPage);

        this.ussdTodoService.processViewExistingTodos(testUserPhone, 0, true);
    }

    @Test
    public void viewTodoEntryShouldWork()throws Exception{
        Todo testTodo = new Todo(testUser, testGroup, TodoType.ACTION_REQUIRED, "Some todo subject", Instant.now());

        TodoAssignment todoAssignment = new TodoAssignment(testTodo,testUser,false,false,true);
        List<TodoAssignment> todoAssignments = new ArrayList<>();
        todoAssignments.add(todoAssignment);

        when(userManagementServiceMock.findByInputNumber(testUserPhone)).thenReturn(testUser);
        when(todoBrokerMock.load(testTodo.getUid())).thenReturn(testTodo);
        when(todoBrokerMock.fetchAssignedUserResponses(testUser.getUid(),testTodo.getUid(),
                true,false,false)).thenReturn(todoAssignments);

        this.ussdTodoService.processViewTodoEntry(testUserPhone, testTodo.getUid());

        verify(userManagementServiceMock,times(1)).findByInputNumber(testUserPhone);
        verify(todoBrokerMock,times(1)).load(testTodo.getUid());
        verify(todoBrokerMock,times(1)).fetchAssignedUserResponses(testUser.getUid(),testTodo.getUid(),
                true,false,false);
    }

    @Test
    public void emailTodoResponsesShouldWork()throws Exception{
        Todo testTodo = new Todo(testUser, testGroup, TodoType.ACTION_REQUIRED, "Some todo subject", Instant.now());
        when(userManagementServiceMock.findByInputNumber(testUserPhone,
                "todo/view/email?todoUid=" + testTodo.getUid())).thenReturn(testUser);

        this.ussdTodoService.processEmailTodoResponses(testUserPhone, testTodo.getUid());

        verify(userManagementServiceMock,times(1)).findByInputNumber(testUserPhone,
                "todo/view/email?todoUid=" + testTodo.getUid());
    }

    @Test
    public void emailResponsesDoShouldWork()throws Exception{
        when(userManagementServiceMock.findByInputNumber(testUserPhone,null)).thenReturn(testUser);
        String testEmail = "test@grassroot.org.za";
        testUser.setEmailAddress("testUser@grassroot.org.za");

        this.ussdTodoService.processEmailResponseDo(testUserPhone, testTodo.getUid(), testEmail);

        verify(userManagementServiceMock,times(1)).findByInputNumber(testUserPhone,null);
    }

    @Test
    public void markTodoCompletePromptShouldWork()throws Exception{
        when(userManagementServiceMock.findByInputNumber(testUserPhone)).thenReturn(testUser);

        this.ussdTodoService.processMarkTodoCompletePrompt(testUserPhone, testTodo.getUid());

        verify(userManagementServiceMock,times(1)).findByInputNumber(testUserPhone);
    }

    @Test
    public void markTodoCompleteDoneShouldWork() throws Exception{
        when(userManagementServiceMock.findByInputNumber(testUserPhone,null)).thenReturn(testUser);

        this.ussdTodoService.processMarkTodoCompleteDone(testUserPhone, testTodo.getUid());

        verify(userManagementServiceMock,times(1)).findByInputNumber(testUserPhone,null);
    }

    @Test
    public void alterTodoMenuShouldWork()throws Exception{
        when(userManagementServiceMock.findByInputNumber(testUserPhone,
                saveModifyUrl("/", testTodo.getUid(), null))).thenReturn(testUser);
        when(todoBrokerMock.load(testTodo.getUid())).thenReturn(testTodo);

        this.ussdTodoService.processAlterTodoMenu(testUserPhone, testTodo.getUid());

        verify(userManagementServiceMock,times(1)).findByInputNumber(testUserPhone,
                saveModifyUrl("/", testTodo.getUid(), null));
        verify(todoBrokerMock,times(1)).load(testTodo.getUid());
    }

    @Test
    public void alterTodoSubjectShouldWork()throws Exception{
        when(userManagementServiceMock.findByInputNumber(testUserPhone,
                saveModifyUrl("/subject", testTodo.getUid(), null))).thenReturn(testUser);
        when(todoBrokerMock.load(testTodo.getUid())).thenReturn(testTodo);

        this.ussdTodoService.processAlterTodoSubject(testUserPhone, testTodo.getUid());

        verify(userManagementServiceMock,times(1)).findByInputNumber(testUserPhone,
                saveModifyUrl("/subject", testTodo.getUid(), null));
        verify(todoBrokerMock,times(1)).load(testTodo.getUid());
    }

    @Test
    public void confirmSubjectModificationShouldWork()throws Exception{
        when(userManagementServiceMock.findByInputNumber(testUserPhone,null)).thenReturn(testUser);
        String testSubject = "test subject";

        this.ussdTodoService.processConfirmSubjectModification(testUserPhone, testTodo.getUid(), testSubject);

        verify(userManagementServiceMock,times(1)).findByInputNumber(testUserPhone,null);
    }

    @Test
    public void alterTodoDateShouldWork()throws Exception{
        when(userManagementServiceMock.findByInputNumber(testUserPhone,
                saveModifyUrl("/date", testTodo.getUid(), null))).thenReturn(testUser);
        when(todoBrokerMock.load(testTodo.getUid())).thenReturn(testTodo);

        this.ussdTodoService.processAlterTodoDate(testUserPhone, testTodo.getUid());

        verify(userManagementServiceMock,times(1)).findByInputNumber(testUserPhone,
                saveModifyUrl("/date", testTodo.getUid(), null));
        verify(todoBrokerMock,times(1)).load(testTodo.getUid());
    }

    @Test
    public void alterDateConfirmShouldWork()throws Exception{
        String testUserInput = "tomorrow at 5pm";
        when(userManagementServiceMock.findByInputNumber(testUserPhone,
                saveModifyUrl("/date/confirm", testTodo.getUid(), testUserInput))).thenReturn(testUser);
        when(learningServiceMock.parse(testUserInput)).thenReturn(LocalDateTime.now().plusDays(1).plusHours(1));

        this.ussdTodoService.processAlterDateConfirm(testUserPhone, testTodo.getUid(), testUserInput, null);

        verify(userManagementServiceMock,times(1)).findByInputNumber(testUserPhone,
                saveModifyUrl("/date/confirm", testTodo.getUid(), testUserInput));
    }

    @Test
    public void changeEntryConfirmedShouldWork()throws Exception{
        when(userManagementServiceMock.findByInputNumber(testUserPhone,null)).thenReturn(testUser);

        this.ussdTodoService.processChangeEntryConfirmed(testUserPhone, testTodo.getUid(), Instant.now().toEpochMilli());

        verify(userManagementServiceMock,times(1)).findByInputNumber(testUserPhone,null);
    }

    @Test
    public void cancelTodoShouldWork()throws Exception{
        when(userManagementServiceMock.findByInputNumber(testUserPhone,
                saveModifyUrl("/cancel", testTodo.getUid(), null))).thenReturn(testUser);
        when(todoBrokerMock.load(testTodo.getUid())).thenReturn(testTodo);

        this.ussdTodoService.processCancelTodo(testUserPhone, testTodo.getUid());

        verify(userManagementServiceMock,times(1)).findByInputNumber(testUserPhone,
                saveModifyUrl("/cancel", testTodo.getUid(), null));
        verify(todoBrokerMock,times(1)).load(testTodo.getUid());
    }

    @Test
    public void cancelTodoConfirmedShouldWork()throws Exception{
        when(userManagementServiceMock.findByInputNumber(testUserPhone,null)).thenReturn(testUser);

        this.ussdTodoService.processCancelTodoConfirmed(testUserPhone, testTodo.getUid(), true);

        verify(userManagementServiceMock,times(1)).findByInputNumber(testUserPhone,null);
    }

    private String saveRequestUrl(String menu, String requestUid, String priorInput) {
        return "todo" + menu + "?storedUid=" + requestUid + (priorInput == null ? "" :
                "&priorInput=" + USSDUrlUtil.encodeParameter(priorInput));
    }

    private String saveModifyUrl(String modifyMenu, String requestUid, String priorInput) {
        return "todo/modify" + modifyMenu + "?requestUid=" + requestUid +
                (StringUtils.isEmpty(priorInput) ? "" : "&priorInput=" + USSDUrlUtil.encodeParameter(priorInput));
    }

    // todo : fix the below, refactoring to new design
    /* @Test
    public void groupSelectMenuShouldWorkWithGroup() throws Exception {

        List<Group> testGroups = Arrays.asList(new Group("tg1", testUser),
                new Group("tg2", testUser),
                new Group("tg3", testUser));
        testGroups.stream().forEach(tg -> tg.addMember(testUser, GroupRole.ROLE_ORDINARY_MEMBER, GroupJoinMethod.ADDED_BY_OTHER_MEMBER));

        when(userManagementServiceMock.findByInputNumber(testUserPhone)).thenReturn(testUser);
        when(permissionBrokerMock.countActiveGroupsWithPermission(testUser, GROUP_PERMISSION_CREATE_LOGBOOK_ENTRY)).thenReturn(3);
        when(permissionBrokerMock.getPageOfGroups(testUser, GROUP_PERMISSION_CREATE_LOGBOOK_ENTRY, 0, 3)).thenReturn(testGroups);

        mockMvc.perform(get(path + groupMenu).param(phoneParam, testUserPhone).param("new", "1")).andExpect(status().isOk());

        verify(userManagementServiceMock, times(1)).findByInputNumber(testUserPhone);
        verifyNoMoreInteractions(userManagementServiceMock);
        verify(permissionBrokerMock, times(1)).countActiveGroupsWithPermission(testUser, GROUP_PERMISSION_CREATE_LOGBOOK_ENTRY);
        verify(permissionBrokerMock, times(1)).getPageOfGroups(testUser, GROUP_PERMISSION_CREATE_LOGBOOK_ENTRY, 0, 3);
        verifyNoMoreInteractions(permissionBrokerMock);
        verifyNoMoreInteractions(groupBrokerMock);

    }

    @Test
    public void groupSelectMenuShouldWorkWithNoGroup() throws Exception {

        when(userManagementServiceMock.findByInputNumber(testUserPhone)).thenReturn(testUser);
        when(permissionBrokerMock.countActiveGroupsWithPermission(testUser, GROUP_PERMISSION_CREATE_LOGBOOK_ENTRY)).thenReturn(0);
        mockMvc.perform(get(path + groupMenu).param(phoneParam, testUserPhone).param("new", "1")).
                andExpect(status().isOk());
        verify(userManagementServiceMock, times(1)).findByInputNumber(testUserPhone);
        verifyNoMoreInteractions(userManagementServiceMock);
        verify(permissionBrokerMock, times(1)).countActiveGroupsWithPermission(testUser, GROUP_PERMISSION_CREATE_LOGBOOK_ENTRY);
        verifyNoMoreInteractions(permissionBrokerMock);
        verifyNoMoreInteractions(groupBrokerMock);

    }

    @Test
    public void listEntriesMenuShouldWork() throws Exception {
        String message = "some message about meeting some other people to discuss something important about the community";
        Instant now = Instant.now();

        Group testGroup = new Group("somegroup", testUser);
        List<Todo> testTodos = Arrays.asList(
                new Todo(testUser, testGroup, TodoType.ACTION_REQUIRED, message, now),
                new Todo(testUser, testGroup, TodoType.ACTION_REQUIRED, message, now),
                new Todo(testUser, testGroup, TodoType.ACTION_REQUIRED, message, now));

        Page<Todo> dummyPage = new PageImpl<>(testTodos);
        PageRequest pageRequest = new PageRequest(0, 3, new Sort(Sort.Direction.DESC, "actionByDate"));
        String urlToSave = todosViewGroupCompleteEntries(listEntriesMenu, testGroup.getUid(), 0);
        when(userManagementServiceMock.findByInputNumber(testUserPhone, urlToSave)).thenReturn(testUser);
        when(todoBrokerMock.fetchPageOfTodosForUser(testUser.getUid(), true, false, pageRequest)).thenReturn(dummyPage);

        mockMvc.perform(get(path + listEntriesMenu).param(phoneParam, testUserPhone)
                .param("groupUid", testGroup.getUid())
                .param("pageNumber", String.valueOf(0))).andExpect(status().isOk());

        verify(userManagementServiceMock, times(1)).findByInputNumber(testUserPhone, urlToSave);
        verifyNoMoreInteractions(userManagementServiceMock);
        verify(todoBrokerMock, times(1)).fetchPageOfTodosForUser(testUser.getUid(), true, false, pageRequest);
        verifyNoMoreInteractions(todoBrokerMock);
    }

    @Test
    public void askForSubjectShouldWork() throws Exception {
        Group dummyGroup = new Group("", testUser);
        TodoRequest dummyLogBook = TodoRequest.makeEmpty(testUser, dummyGroup);

        when(userManagementServiceMock.findByInputNumber(testUserPhone)).thenReturn(testUser);
        // when(todoRequestBrokerMock.create(testUser.getUid(), dummyGroup.getUid())).thenReturn(dummyLogBook);

        mockMvc.perform(get(path + "subject").param(phoneParam, testUserPhone).param("groupUid", dummyGroup.getUid()))
                .andExpect(status().isOk());

        verify(userManagementServiceMock, times(1)).findByInputNumber(testUserPhone);
        verifyNoMoreInteractions(userManagementServiceMock);
        verify(cacheUtilManagerMock, times(1)).putUssdMenuForUser(testUserPhone, saveToDoMenu("subject", dummyLogBook.getUid()));
        verifyNoMoreInteractions(cacheUtilManagerMock);
        // verify(todoRequestBrokerMock, times(1)).create(testUser.getUid(), dummyGroup.getUid());
        verifyNoMoreInteractions(todoRequestBrokerMock);
    }

    @Test
    public void askForDueDateShouldWorkAfterInterruption() throws Exception {

        TodoRequest dummyLogBook = TodoRequest.makeEmpty(testUser);
        String urlToSave = saveToDoMenu("due_date", dummyLogBook.getUid(), dummyUserInput);
        when(userManagementServiceMock.findByInputNumber(testUserPhone, urlToSave)).
                thenReturn(testUser);

        mockMvc.perform(get(path + dueDateMenu).param(todoUidParam, dummyLogBook.getUid()).param(phoneParam, testUserPhone)
                .param("prior_input", dummyUserInput).param("interrupted", String.valueOf(true))
                .param("revising", String.valueOf(false)).param("request", "1")).andExpect(status().isOk());

        verify(userManagementServiceMock, times(1)).findByInputNumber(testUserPhone, urlToSave);
        verifyNoMoreInteractions(userManagementServiceMock);
        // todo : restore when build test for instant todo
        // verify(todoRequestBrokerMock, times(1)).updateMessage(testUser.getUid(), dummyLogBook.getUid(), dummyUserInput);
        verifyNoMoreInteractions(todoRequestBrokerMock);

    }

    @Test
    public void confirmTodoWorksWhenAssignedToUser() throws Exception {

        Group testGroup = new Group("testGroup", testUser);
        testGroup.addMember(testUser, GroupRole.ROLE_ORDINARY_MEMBER, GroupJoinMethod.ADDED_BY_OTHER_MEMBER);
        TodoRequest dummyLogBook = TodoRequest.makeEmpty(testUser, testGroup);

        testUser.setDisplayName("Paballo");

        dummyLogBook.setMessage(dummyUserInput);
        dummyLogBook.setActionByDate(Instant.now());

        String urlToSave = saveToDoMenu(confirmMenu, dummyLogBook.getUid(), subjectMenu, "revised message", true);

        when(userManagementServiceMock.findByInputNumber(testUserPhone, urlToSave)).thenReturn(testUser);
        when(todoRequestBrokerMock.load(dummyLogBook.getUid())).thenReturn(dummyLogBook);

        mockMvc.perform(get(path + confirmMenu).param(todoUidParam, dummyLogBook.getUid())
                .param(phoneParam, testUserPhone).param("request", "revised message").param("prior_menu", subjectMenu))
                .andExpect(status().isOk());
        mockMvc.perform(get(base + urlToSave).param(phoneParam, testUserPhone).param("request", "1"))
                .andExpect(status().isOk());

        verify(userManagementServiceMock, times(2)).findByInputNumber(testUserPhone, urlToSave);
        verify(todoRequestBrokerMock, times(2)).load(dummyLogBook.getUid());
        verify(todoRequestBrokerMock, times(1)).updateMessage(testUser.getUid(), dummyLogBook.getUid(), "revised message");
        verifyNoMoreInteractions(userManagementServiceMock);
        verifyNoMoreInteractions(todoRequestBrokerMock);
        verifyNoMoreInteractions(groupBrokerMock);
    }

    @Test
    public void dateProcessingShouldWork() throws Exception {

        Group testGroup = new Group("test testGroup", testUser);
        TodoRequest dummyLogBook = TodoRequest.makeEmpty(testUser, testGroup);

        LocalDateTime correctDueDate = LocalDateTime.of(testYear.getValue(), testDay.getMonthValue(), testDay.getDayOfMonth(), 13, 0);
        List<String> bloomVariations = Arrays.asList("%02d-%02d", "%02d %02d", "%02d/%02d", "%d-%d", "%d %d", "%d/%d",
                "%02d-%02d-%d", "%02d %02d %d", "%02d/%02d/%d", "%d-%d-%d", "%d/%d/%d");

	    for (String format : bloomVariations) {
	        String date = String.format(format, testDay.getDayOfMonth(), testDay.getMonthValue(), testYear.getValue());
		    String urlToSave = USSDUrlUtil.saveToDoMenu(confirmMenu, dummyLogBook.getUid(), dueDateMenu, date, true);
	        String formattedDateString = reformatDateInput(date).trim();

            dummyLogBook.setActionByDate(convertDateStringToLocalDateTime(formattedDateString, 13, 0).toInstant(ZoneOffset.UTC));
            when(userManagementServiceMock.findByInputNumber(testUserPhone, urlToSave)).thenReturn(testUser);
            when(todoRequestBrokerMock.load(dummyLogBook.getUid())).thenReturn(dummyLogBook);

            mockMvc.perform(get(path + confirmMenu).param(phoneParam, testUserPhone).param(todoUidParam, dummyLogBook.getUid())
                    .param("prior_input", date).param("prior_menu", "due_date").param("request", "1"))
                    .andExpect(status().isOk());
        }

        verify(userManagementServiceMock, times(bloomVariations.size()))
                .findByInputNumber(eq(testUserPhone), nullable(String.class));
        verifyNoMoreInteractions(userManagementServiceMock);
        verify(todoRequestBrokerMock, times(bloomVariations.size()))
                .load(dummyLogBook.getUid());
        verify(todoRequestBrokerMock, times(bloomVariations.size()))
                .updateDueDate(testUser.getUid(), dummyLogBook.getUid(), correctDueDate);
        verifyNoMoreInteractions(todoRequestBrokerMock);

    }

    @Test
    public void finishTodoShouldWork() throws Exception {
        TodoRequest dummyLogBook = TodoRequest.makeEmpty(testUser);
        when(userManagementServiceMock.findByInputNumber(testUserPhone, null)).thenReturn(testUser);

        mockMvc.perform(get(path + send).param(todoUidParam, dummyLogBook.getUid())
                .param(phoneParam, testUserPhone)).andExpect(status().isOk());

        verify(userManagementServiceMock, times(1)).findByInputNumber(testUserPhone, null);
        verifyNoMoreInteractions(userManagementServiceMock);
        verify(todoRequestBrokerMock, times(1)).finish(dummyLogBook.getUid());
        verifyNoMoreInteractions(todoRequestBrokerMock);
    }

    @Test
    public void viewEntryMenuWorks() throws Exception {
        Group testGroup = new Group("test testGroup", testUser);
        Todo dummyTodo = new Todo(testUser, testGroup, TodoType.ACTION_REQUIRED, "Some todo subject", Instant.now());
        dummyTodo.getAncestorGroup().addMember(testUser, GroupRole.ROLE_ORDINARY_MEMBER, GroupJoinMethod.ADDED_BY_OTHER_MEMBER);
        dummyTodo.addCompletionConfirmation(testUser, TodoCompletionConfirmType.COMPLETED, Instant.now());

        when(userManagementServiceMock.findByInputNumber(testUserPhone, saveToDoMenu(viewEntryMenu, dummyTodo.getUid()))).thenReturn(testUser);
        when(todoBrokerMock.load(dummyTodo.getUid())).thenReturn(dummyTodo);

        mockMvc.perform(get(path + viewEntryMenu).param(todoUidParam, dummyTodo.getUid())
                .param(phoneParam, testUserPhone)).andExpect(status().isOk());

        verify(userManagementServiceMock, times(1)).findByInputNumber(testUserPhone, saveToDoMenu(viewEntryMenu, dummyTodo.getUid()));
        verify(todoBrokerMock, times(1)).load(dummyTodo.getUid());
        verifyNoMoreInteractions(userManagementServiceMock);
        verifyNoMoreInteractions(todoBrokerMock);
    }

    @Test
    public void todoCompleteMenuWorks() throws Exception {

        Group testGroup = new Group("tg2", testUser);
        testGroup.addMember(testUser, GroupRole.ROLE_ORDINARY_MEMBER, GroupJoinMethod.ADDED_BY_OTHER_MEMBER);
        Todo dummyTodo = new Todo(testUser, testGroup, TodoType.ACTION_REQUIRED, "test todo", Instant.now().minus(7, ChronoUnit.DAYS));

        dummyTodo.addCompletionConfirmation(testUser, TodoCompletionConfirmType.COMPLETED, Instant.now());
        dummyTodo.assignMembers(Collections.singleton(testUser.getUid()));

        String urlToSave = saveToDoMenu(setCompleteMenu, dummyTodo.getUid());

        when(userManagementServiceMock.findByInputNumber(testUserPhone, urlToSave)).thenReturn(testUser);
        when(todoBrokerMock.load(dummyTodo.getUid())).thenReturn(dummyTodo);

        mockMvc.perform(get(path + setCompleteMenu).param(phoneParam, testUserPhone)
                .param(todoUidParam, dummyTodo.getUid())).andExpect(status().isOk());
        mockMvc.perform(get(base + urlToSave).param(phoneParam, testUserPhone)).andExpect(status().isOk());

        verify(userManagementServiceMock, times(2)).findByInputNumber(testUserPhone, urlToSave);
        verifyNoMoreInteractions(userManagementServiceMock);
        verify(todoBrokerMock, times(2)).load(dummyTodo.getUid());
        verifyNoMoreInteractions(todoBrokerMock);

    }

    @Test
    public void setActionTodoComplete() throws Exception {

        Group testGroup = new Group("tg2", testUser);
        testGroup.addMember(testUser, GroupRole.ROLE_ORDINARY_MEMBER, GroupJoinMethod.ADDED_BY_OTHER_MEMBER);
        Todo dummyTodo = new Todo(testUser, testGroup, TodoType.ACTION_REQUIRED, "test logbook", Instant.now().plus(1, ChronoUnit.DAYS));
        dummyTodo.assignMembers(Collections.singleton(testUser.getUid()));
        dummyTodo.addCompletionConfirmation(testUser, TodoCompletionConfirmType.COMPLETED, Instant.now());

        when(userManagementServiceMock.findByInputNumber(testUserPhone, null)).thenReturn(testUser);
        when(todoBrokerMock.load(dummyTodo.getUid())).thenReturn(dummyTodo);

        mockMvc.perform(get(path + setCompleteMenu + "-do")
                .param(phoneParam, testUserPhone)
                .param(todoUidParam, dummyTodo.getUid())
                .param("prior_input", TodoCompletionConfirmType.COMPLETED.name())).andExpect(status().isOk());

        verify(userManagementServiceMock, times(1)).findByInputNumber(testUserPhone, null);

        verify(todoBrokerMock, times(1)).load(dummyTodo.getUid());
        verify(todoBrokerMock, times(1)).recordValidation(eq(testUser.getUid()), eq(dummyTodo.getUid()), eq(TodoCompletionConfirmType.COMPLETED),
                any(LocalDateTime.class));
        verifyNoMoreInteractions(userManagementServiceMock);
        verifyNoMoreInteractions(todoBrokerMock);

    }*/

}
