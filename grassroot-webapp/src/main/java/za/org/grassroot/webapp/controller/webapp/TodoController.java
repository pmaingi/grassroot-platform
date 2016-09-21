package za.org.grassroot.webapp.controller.webapp;

import com.google.common.base.Joiner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import za.org.grassroot.core.domain.*;
import za.org.grassroot.core.enums.TodoCompletionConfirmType;
import za.org.grassroot.core.util.DateTimeUtil;
import za.org.grassroot.services.EventBroker;
import za.org.grassroot.services.GroupBroker;
import za.org.grassroot.services.TodoBroker;
import za.org.grassroot.services.enums.TodoStatus;
import za.org.grassroot.webapp.controller.BaseController;
import za.org.grassroot.webapp.model.web.MemberPicker;
import za.org.grassroot.webapp.model.web.TodoWrapper;

import javax.servlet.http.HttpServletRequest;
import java.time.LocalDateTime;
import java.util.*;

/**
 * Created by luke on 2016/01/02.
 */
@Controller
@RequestMapping("/todo/")
public class TodoController extends BaseController {

    private static final Logger log = LoggerFactory.getLogger(TodoController.class);

    @Value("${grassroot.todos.completion.threshold:20}") // defaults to 20 percent
    private double COMPLETION_PERCENTAGE_BOUNDARY;

    @Autowired
    private GroupBroker groupBroker;

    @Autowired
    private TodoBroker todoBroker;

    @Autowired
    private EventBroker eventBroker;

    /**
     * SECTION: Views and methods for creating logbook entries
     */

    @RequestMapping("create")
    public String createTodo(Model model, @RequestParam(value="groupUid", required=false) String parentUid,
                             @RequestParam(value="parentType", required=false) JpaEntityType parentType) {

        TodoWrapper entryWrapper;

        // todo: clean this up / consolidate it
        if (JpaEntityType.MEETING.equals(parentType)) {

            Meeting parent = eventBroker.loadMeeting(parentUid);
            TodoWrapper wrapper = new TodoWrapper(JpaEntityType.MEETING, parentUid, parent.getName());

            model.addAttribute("parent", parent);
            model.addAttribute("logBook", wrapper);

            return "todo/create_meeting";

        } else {

            if (parentUid == null || parentUid.trim().equals("")) {

                // reload user entity in case things have changed during session (else bug w/ list of possible groups)
                User userFromDb = userManagementService.load(getUserProfile().getUid());
                model.addAttribute("groupSpecified", false);
                model.addAttribute("userUid", userFromDb.getUid());
                model.addAttribute("possibleGroups", permissionBroker.
                        getActiveGroupsWithPermission(userFromDb, Permission.GROUP_PERMISSION_CREATE_LOGBOOK_ENTRY));
                entryWrapper = new TodoWrapper(JpaEntityType.GROUP);

            } else {

                model.addAttribute("groupSpecified", true);
                Group group = groupBroker.load(parentUid);
                model.addAttribute("group", group);
                entryWrapper = new TodoWrapper(JpaEntityType.GROUP, group.getUid(), group.getName(""));
                entryWrapper.setMemberPicker(new MemberPicker(group, false));
            }

            entryWrapper.setAssignmentType("group");
            entryWrapper.setReminderType(EventReminderType.GROUP_CONFIGURED);
            entryWrapper.setReminderMinutes(AbstractTodoEntity.DEFAULT_REMINDER_MINUTES);

            model.addAttribute("entry", entryWrapper);
            return "todo/create";

        }

    }

    @RequestMapping(value = "record", method = RequestMethod.POST)
    public String recordTodo(@ModelAttribute("entry") TodoWrapper todoEntry,
                             HttpServletRequest request, RedirectAttributes redirectAttributes) {

        log.info("TodoWrapper received, looks like: {}", todoEntry.toString());

        if (todoEntry.getReminderType().equals(EventReminderType.GROUP_CONFIGURED)) {
            int convertedMinutes = -(groupBroker.load(todoEntry.getParentUid()).getReminderMinutes());
            todoEntry.setReminderMinutes(convertedMinutes);
        }

        Set<String> assignedUids;
        if ("members".equals(todoEntry.getAssignmentType())) {
            MemberPicker listOfMembers = todoEntry.getMemberPicker();
            log.info("The memberUids are : ..." + Joiner.on(", ").join(listOfMembers.getSelectedUids()));
            assignedUids = listOfMembers.getSelectedUids();
        } else {
            assignedUids = Collections.emptySet();
        }

        Long startTime = System.currentTimeMillis();
        todoBroker.create(getUserProfile().getUid(), todoEntry.getParentEntityType(), todoEntry.getParentUid(),
                todoEntry.getMessage(), todoEntry.getActionByDate(), todoEntry.getReminderMinutes(),
                todoEntry.isReplicateToSubGroups(), assignedUids);

        log.info("Time to create, store, logbooks: {} msecs", System.currentTimeMillis() - startTime);

        addMessage(redirectAttributes, MessageType.SUCCESS, "todo.creation.success", request);
        // redirectAttributes.addAttribute("logBookUid", created.getUid());

        return "redirect:/home";
    }

    @RequestMapping(value = "record/meeting", method = RequestMethod.POST)
    public String recordEntryWithMeetingParent(Model model, @ModelAttribute("logBook") TodoWrapper todo,
                                               HttpServletRequest request, RedirectAttributes attributes) {

        Todo created = todoBroker.create(getUserProfile().getUid(), todo.getParentEntityType(),
                todo.getParentUid(), todo.getMessage(), todo.getActionByDate(),
                todo.getReminderMinutes(), false, Collections.emptySet());

        addMessage(attributes, MessageType.SUCCESS, "todo.creation.success", request);
        attributes.addAttribute("logBookUid", created.getUid());

        return "redirect:/todo/details";

    }

    /**
     * SECTION: Views and methods for examining a group's actions and todos
     * The standard view just looks at the entry as applied to the group ... There's a click through to check sub-group ones
     */
    @RequestMapping(value = "view")
    public String viewGroupLogBook(Model model, @RequestParam String groupUid) {

        log.info("Okay, pulling up logbook records ... primarily for the currently assigned group");

        Group group = groupBroker.load(groupUid);
        model.addAttribute("group", group);
        model.addAttribute("incompleteEntries", todoBroker.fetchTodosForGroupByStatus(group.getUid(), false, TodoStatus.INCOMPLETE));

        List<Todo> completedEntries = todoBroker.fetchTodosForGroupByStatus(group.getUid(), false, TodoStatus.COMPLETE);
        model.addAttribute("completedEntries", completedEntries);
        log.info("Got back this many complete entries ... " + completedEntries.size());

        return "todo/view";
    }

    @RequestMapping(value = "details")
    public String viewTodoDetails(Model model, @RequestParam String logBookUid) {

        // todo: be able to view "children" of the log book once design changed to allow it
        // (replicate by logbook rather than group)

        log.info("Finding details about logbook entry with Id ..." + logBookUid);

        Todo todoEntry = todoBroker.load(logBookUid);

        log.info("Retrieved logBook entry with these details ... " + todoEntry);

        model.addAttribute("entry", todoEntry);
        model.addAttribute("parent", todoEntry.getParent());
        model.addAttribute("creatingUser", todoEntry.getCreatedByUser());
        model.addAttribute("isComplete", todoEntry.isCompleted(COMPLETION_PERCENTAGE_BOUNDARY));

        if (todoBroker.hasReplicatedEntries(todoEntry)) {
            log.info("Found replicated entries ... adding them to model");
            List<Todo> replicatedEntries = todoBroker.getAllReplicatedEntriesFromParent(todoEntry);
            log.info("Here are the replicated entries ... " + replicatedEntries);
            List<Group> relevantSubGroups = todoBroker.retrieveGroupsFromTodos(replicatedEntries);
            model.addAttribute("hasReplicatedEntries", true);
            model.addAttribute("replicatedEntries", replicatedEntries);
            model.addAttribute("replicatedGroups", relevantSubGroups);
            log.info("Here are the groups ... " + relevantSubGroups);
        }

        if (todoEntry.getReplicatedGroup() != null) {
            log.info("This one is replicated from a parent logBook entry ...");
            Todo parentEntry = todoBroker.getParentTodoEntry(todoEntry);
            model.addAttribute("parentEntry", parentEntry);
            model.addAttribute("parentEntryGroup", todoEntry.getReplicatedGroup());
        }

        return "todo/details";
    }

    @RequestMapping("complete")
    public String completeTodoForm(Model model, @RequestParam String logBookUid) {

        Todo todoEntry = todoBroker.load(logBookUid);
        model.addAttribute("entry", todoEntry);
        Set<User> assignedMembers = (todoEntry.isAllGroupMembersAssigned()) ?
                todoEntry.getAncestorGroup().getMembers() : todoEntry.getAssignedMembers();
        model.addAttribute("assignedMembers", assignedMembers);

        return "todo/complete";
    }

    @RequestMapping(value = "complete-do", method = RequestMethod.POST)
    public String confirmTodoComplete(Model model, @RequestParam String logBookUid,
                                      @RequestParam(value="specifyCompletedDate", required=false) boolean setCompletedDate,
                                      @RequestParam(value="completedOnDate", required=false) String completedOnDate,
                                      HttpServletRequest request) {

        log.info("Marking logbook entry as completed ... ");

        LocalDateTime completedDate = (setCompletedDate) ? LocalDateTime.parse(completedOnDate, DateTimeUtil.getWebFormFormat())
		        : LocalDateTime.now();

	    String sessionUserUid = getUserProfile().getUid();
        Todo todo = todoBroker.load(logBookUid);

        if (setCompletedDate) {
	        todoBroker.confirmCompletion(sessionUserUid, todo.getUid(), TodoCompletionConfirmType.COMPLETED, completedDate);
        } else {
	        todoBroker.confirmCompletion(sessionUserUid, todo.getUid(), TodoCompletionConfirmType.COMPLETED, LocalDateTime.now());
        }

        addMessage(model, MessageType.SUCCESS, "todo.completed.done", request);
        Group group = (Group) todo.getParent();
        return viewGroupLogBook(model, group.getUid());
    }

    // todo : more permissions than just the below!
    @RequestMapping("modify")
    public String modifyTodo(Model model, @RequestParam(value="logBookUid") String logBookUid) {

        Todo todo = todoBroker.load(logBookUid);
        Group group = (Group) todo.getParent();
        if (!group.getMembers().contains(getUserProfile())) throw new AccessDeniedException("");

        model.addAttribute("todo", todo);
        model.addAttribute("group", group);
        model.addAttribute("groupMembers", group.getMembers());
        model.addAttribute("reminderTime", reminderTimeDescriptions().get(todo.getReminderMinutes()));
        model.addAttribute("reminderTimeOptions", reminderTimeDescriptions());

        // todo: implement this using new design
        /* if (to-do.getAssignedToUser() != null)
            model.addAttribute("assignedUser", to-do.getAssignedToUser());*/

        return "todo/modify";
    }

    // todo: permission checking
    @RequestMapping(value = "modify", method = RequestMethod.POST)
    public String changeTodoEntry(Model model, @ModelAttribute("todo") Todo todo,
                                  @RequestParam(value = "assignToUser", required = false) boolean assignToUser, HttpServletRequest request) {

        // may consider doing some of this in services layer, but main point is can't just use to-do entity passed
        // back from form as thymeleaf whacks all the attributes we don't explicitly code into hidden inputs

        Todo savedTodo = todoBroker.load(todo.getUid());
        if (!todo.getMessage().equals(savedTodo.getMessage()))
            savedTodo.setMessage(todo.getMessage());

        if (!todo.getActionByDate().equals(savedTodo.getActionByDate()))
            savedTodo.setActionByDate(todo.getActionByDate());

        if (todo.getReminderMinutes() != savedTodo.getReminderMinutes())
            savedTodo.setReminderMinutes(todo.getReminderMinutes());

        // todo: implement this using new design (and switch the update field)
        /* if (!assignToUser)
            savedTodo.setAssignedToUser(null);
        else
            savedTodo.setAssignedToUser(to-do.getAssignedToUser());
        */

        savedTodo = todoBroker.update(savedTodo);

        addMessage(model, MessageType.SUCCESS, "todo.modified.done", request);
        return viewTodoDetails(model, savedTodo.getUid());
    }

    private Map<Integer, String> reminderTimeDescriptions() {
        // could have created this once off, but doing it through function, for i18n later
        Map<Integer, String> descriptions = new LinkedHashMap<>();
        descriptions.put(-60, "On due date");
        descriptions.put(-1440, "One day before");
        descriptions.put(-2880, "Two days before");
        descriptions.put(-10080, "A week before deadline");
        return descriptions;
    }

}