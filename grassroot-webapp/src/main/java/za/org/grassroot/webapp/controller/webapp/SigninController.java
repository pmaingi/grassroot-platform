package za.org.grassroot.webapp.controller.webapp;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.ModelAndView;

import java.util.Optional;

/**
 * @author Lesetse Kimwaga
 */
@Controller
public class SigninController {

    private static final Logger logger = LoggerFactory.getLogger(LoginController.class);


//    @RequestMapping(value = "/logon", method = RequestMethod.GET)
//    public ModelAndView getLoginPage(@RequestParam Optional<String> error) {
//        logger.debug("Getting login page, error={}", error);
//
//        return new ModelAndView("signin", "error", error);
//    }
    @RequestMapping(value = "/login", method = RequestMethod.GET)
    public ModelAndView getLoginPage(@RequestParam (required = false) String error) {
        logger.debug("Getting login page, error={}", error);

        return new ModelAndView("signin", "error", error);
    }

    @RequestMapping(value = "/signin", method = RequestMethod.GET)
    public ModelAndView getSignnPage(@RequestParam(required = false) String error) {
        logger.debug("Getting login page, error={}", error);

        return new ModelAndView("signin", "error", error);
    }

    // Login form with error
    @RequestMapping("/signin-error")
    public String loginError(Model model) {
        model.addAttribute("loginError", true);
        return "signin";
    }
}
