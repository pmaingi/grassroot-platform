package za.org.grassroot.webapp.controller.webapp.todo;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import za.org.grassroot.core.domain.task.Todo;
import za.org.grassroot.core.util.DateTimeUtil;
import za.org.grassroot.services.task.TodoBroker;
import za.org.grassroot.webapp.controller.BaseController;
import za.org.grassroot.webapp.model.web.MemberPicker;

import javax.servlet.http.HttpServletRequest;
import java.time.LocalDateTime;

@Controller @Slf4j
@RequestMapping("/todo/")
public class TodoWebViewModifyController extends BaseController {

    private final TodoBroker todoBroker;

    @Autowired
    public TodoWebViewModifyController(TodoBroker todoBroker) {
        this.todoBroker = todoBroker;
    }

    @RequestMapping(value = "view", method = RequestMethod.GET)
    public String viewTodoDetails(Model model, @RequestParam String todoUid) {
        Todo todo = todoBroker.load(todoUid);
        model.addAttribute("todo", todo);

        final String userUid = getUserProfile().getUid();
        if (todoBroker.canUserViewResponses(userUid, todoUid)) {
            model.addAttribute("responses", todoBroker.fetchAssignedUserResponses(userUid, todoUid, true, false, false));
            model.addAttribute("userResponse", todoBroker.fetchUserTodoDetails(userUid, todoUid));
        }

        if (todoBroker.canUserModify(userUid, todoUid)) {
            model.addAttribute("canModify", true);
            model.addAttribute("memberPicker", MemberPicker.taskAssigned(todo));
        } else {
            model.addAttribute("canModify", false);
        }

        return "todo/view_new";
    }

    @RequestMapping(value = "cancel", method = RequestMethod.POST)
    public String cancelTodo(@RequestParam String todoUid, @RequestParam String parentUid,
                             RedirectAttributes redirectAttributes, HttpServletRequest request) {
        // service layer will test for permission etc and throw errors
        todoBroker.cancel(getUserProfile().getUid(), todoUid, null);
        addMessage(redirectAttributes, MessageType.INFO, "todo.cancelled.done", request);
        // for now, assuming it's a group
        redirectAttributes.addAttribute("groupUid", parentUid);
        return "redirect:/group/view";
    }

    @RequestMapping(value = "explanation", method = RequestMethod.POST)
    public String changeExplanation(@RequestParam String todoUid,
                                    @RequestParam String explanation,
                                    RedirectAttributes attributes, HttpServletRequest request) {
        todoBroker.updateSubject(getUserProfile().getUid(), todoUid, explanation);
        addMessage(attributes, MessageType.SUCCESS, "todo.description.updated", request);
        attributes.addAttribute("todoUid", todoUid);
        return "redirect:/todo/view";
    }

    @RequestMapping(value = "extend", method = RequestMethod.POST)
    public String changeDueDate(@RequestParam String todoUid,
                                @RequestParam LocalDateTime actionByDate,
                                RedirectAttributes attributes, HttpServletRequest request) {
        todoBroker.extend(getUserProfile().getUid(), todoUid, DateTimeUtil.convertToSystemTime(actionByDate, DateTimeUtil.getSAST()));
        addMessage(attributes, MessageType.SUCCESS, "todo.deadline.updated", request);
        attributes.addAttribute("todoUid", todoUid);
        return "redirect:/todo/view";
    }



}
