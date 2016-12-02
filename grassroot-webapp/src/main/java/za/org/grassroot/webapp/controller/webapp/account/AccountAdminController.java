package za.org.grassroot.webapp.controller.webapp.account;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import za.org.grassroot.core.domain.Account;
import za.org.grassroot.integration.payments.PaymentServiceBroker;
import za.org.grassroot.services.account.AccountBillingBroker;
import za.org.grassroot.services.account.AccountBroker;
import za.org.grassroot.webapp.controller.BaseController;

import javax.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;

/**
 * Created by luke on 2016/12/01.
 */
@Controller
@RequestMapping("/admin/accounts/")
@PreAuthorize("hasRole('ROLE_SYSTEM_ADMIN')")
public class AccountAdminController extends BaseController {

    private static final Logger log = LoggerFactory.getLogger(AccountAdminController.class);

    private final AccountBroker accountBroker;
    private final AccountBillingBroker billingBroker;
    private final Environment environment;

    private PaymentServiceBroker paymentServiceBroker;

    @Autowired
    public AccountAdminController(AccountBroker accountBroker, AccountBillingBroker billingBroker, Environment environment) {
        this.accountBroker = accountBroker;
        this.billingBroker = billingBroker;
        this.environment = environment;
    }

    @Autowired(required = false)
    public void setPaymentServiceBroker(PaymentServiceBroker paymentServiceBroker) {
        this.paymentServiceBroker = paymentServiceBroker;
    }

    /**
     * Methods to create institutional accounts and designate their administrators
     */
    @PreAuthorize("hasRole('ROLE_SYSTEM_ADMIN')")
    @RequestMapping("/home")
    public String listAccounts(Model model) {
        model.addAttribute("accounts", new ArrayList<>(accountBroker.loadAllAccounts(true)));
        return "admin/accounts/home";
    }

    @PreAuthorize("hasRole('ROLE_SYSTEM_ADMIN')")
    @RequestMapping(value = "/disable")
    public String disableAccount(@RequestParam("accountUid") String accountUid, RedirectAttributes attributes, HttpServletRequest request) {
        accountBroker.disableAccount(getUserProfile().getUid(), accountUid, "disabled by admin user", true, false); // todo : have a form to input this
        addMessage(attributes, MessageType.INFO, "admin.accounts.disabled", request);
        return "redirect:home";
    }

    @PreAuthorize("hasRole('ROLE_SYSTEM_ADMIN')")
    @RequestMapping(value = "/enable")
    public String enableAccount(@RequestParam("accountUid") String accountUid, RedirectAttributes attributes, HttpServletRequest request) {
        // todo : send an email notifying them it's enabled but have to add payment within a month
        accountBroker.enableAccount(getUserProfile().getUid(), accountUid, LocalDate.now().plus(1, ChronoUnit.MONTHS), null);
        addMessage(attributes, MessageType.INFO, "admin.accounts.disabled", request);
        return "redirect:home";
    }

    @PreAuthorize("hasRole('ROLE_SYSTEM_ADMIN')")
    @RequestMapping(value = "/close", method = RequestMethod.GET)
    public String closeAccount(@RequestParam("accountUid") String accountUid, RedirectAttributes attributes, HttpServletRequest request) {
        accountBroker.closeAccount(getUserProfile().getUid(), accountUid, false);
        addMessage(attributes, MessageType.INFO, "admin.accounts.invisible", request);
        return "redirect:home";
    }

    @PreAuthorize("hasRole('ROLE_SYSTEM_ADMIN')")
    @RequestMapping(value = "/change/subscription", method = RequestMethod.POST)
    public String changeSubcription(@RequestParam String accountUid, @RequestParam Integer newSubscriptionFee,
                                    RedirectAttributes attributes, HttpServletRequest request) {
        accountBroker.updateAccountFee(getUserProfile().getUid(), accountUid, newSubscriptionFee);
        addMessage(attributes, MessageType.INFO, "admin.accounts.fee.changed", request);
        return "redirect:/admin/accounts/home";
    }

    @PreAuthorize("hasRole('ROLE_SYSTEM_ADMIN')")
    @RequestMapping(value = "/change/balance", method = RequestMethod.POST)
    public String changeBalance(@RequestParam String accountUid, @RequestParam Integer newBalance,
                                RedirectAttributes attributes, HttpServletRequest request) {
        accountBroker.updateAccountBalance(getUserProfile().getUid(), accountUid, newBalance);
        addMessage(attributes, MessageType.INFO, "admin.accounts.balance.changed", request);
        return "redirect:/admin/accounts/home";
    }

    @PreAuthorize("hasRole('ROLE_SYSTEM_ADMIN')")
    @RequestMapping(value = "/bill", method = RequestMethod.POST)
    public String generateBill(@RequestParam String accountUid, @RequestParam(required = false) Long billAmount,
                               @RequestParam(required = false) boolean generateStatement, @RequestParam(required = false) boolean triggerPayment,
                               RedirectAttributes attributes, HttpServletRequest request) {
        billingBroker.generateBillOutOfCycle(accountUid, generateStatement, triggerPayment, billAmount, true);
        addMessage(attributes, MessageType.INFO, "admin.accounts.bill.generated", request);
        return "redirect:home";
    }

    @PreAuthorize("hasRole('ROLE_SYSTEM_ADMIN')")
    @RequestMapping(value = "/change/billingdate", method = RequestMethod.POST)
    public String toggleBilling(@RequestParam String accountUid, @RequestParam(required = false) boolean stopBilling,
                                @RequestParam(required = false) LocalDateTime billingDateTime,
                                RedirectAttributes attributes, HttpServletRequest request) {
        log.info("stopBilling: {}", stopBilling);
        billingBroker.forceUpdateBillingCycle(getUserProfile().getUid(), accountUid, stopBilling ? null : billingDateTime);
        addMessage(attributes, MessageType.INFO, "admin.accounts.billingdate.changed", request);
        return "redirect:/admin/accounts/home";
    }

    @PreAuthorize("hasRole('ROLE_SYSTEM_ADMIN')")
    @RequestMapping(value = "/payments/stop", method = RequestMethod.GET)
    public String togglePayments(@RequestParam String accountUid, RedirectAttributes attributes, HttpServletRequest request) {
        billingBroker.haltAccountPayments(getUserProfile().getUid(), accountUid);
        addMessage(attributes, MessageType.INFO, "admin.accounts.payment.stopped", request);
        return "redirect:/admin/accounts/home";
    }

    @PreAuthorize("hasRole('ROLE_SYSTEM_ADMIN')")
    @RequestMapping(value = "/records/list", method = RequestMethod.GET)
    public String seeUnpaidBills(@RequestParam String accountUid, @RequestParam(required = false) boolean unpaidOnly, Model model) {
        model.addAttribute("records", billingBroker.loadBillingRecordsForAccount(accountUid, unpaidOnly,
                new Sort(Sort.Direction.DESC, "createdDateTime")));
        model.addAttribute("account", accountBroker.loadAccount(accountUid));
        return "admin/accounts/records";
    }

    @PreAuthorize("hasRole('ROLE_SYSTEM_ADMIN')")
    @RequestMapping(value = "/bill/paydate/change", method = RequestMethod.POST)
    public String changeBillPaymentDate(@RequestParam String recordUid, LocalDateTime newDate,
                                        RedirectAttributes attributes, HttpServletRequest request) {
        billingBroker.changeBillPaymentDate(getUserProfile().getUid(), recordUid, newDate);
        addMessage(attributes, MessageType.INFO, "admin.accounts.billpaydate.changed", request);
        return "redirect:/admin/accounts/bill/list/unpaid";
    }

    @PreAuthorize("hasRole('ROLE_SYSTEM_ADMIN')")
    @RequestMapping(value = "/reset/dates", method = RequestMethod.GET)
    public String resetAccountBillingDates(RedirectAttributes redirectAttributes, HttpServletRequest request) {
        if (!environment.acceptsProfiles("production")) {
            accountBroker.resetAccountBillingDates(Instant.now());
            addMessage(redirectAttributes, MessageType.INFO, "admin.accounts.reset", request);
        } else {
            addMessage(redirectAttributes, MessageType.ERROR, "admin.accounts.production", request);
        }
        return "redirect:/admin/accounts/home";
    }

    @PreAuthorize("hasRole('ROLE_SYSTEM_ADMIN')")
    @RequestMapping(value = "/reset/billing", method = RequestMethod.GET)
    public String triggerBilling(@RequestParam(required = false) boolean sendEmails, @RequestParam(required = false) boolean sendNotifications,
                                 RedirectAttributes attributes, HttpServletRequest request) {
        if (!environment.acceptsProfiles("production")) {
            billingBroker.calculateStatementsForDueAccounts(sendEmails, sendNotifications);
            addMessage(attributes, MessageType.INFO, "admin.accounts.billing.done", request);
        } else {
            addMessage(attributes, MessageType.ERROR, "admin.accounts.production", request);
        }
        return "redirect:/admin/accounts/home";
    }

    @PreAuthorize("hasRole('ROLE_SYSTEM_ADMIN')")
    @RequestMapping(value = "/reset/payments", method = RequestMethod.GET)
    public String triggerPayments(@RequestParam RedirectAttributes attributes, @RequestParam HttpServletRequest request) {
        if (!environment.acceptsProfiles("production") && paymentServiceBroker != null) {
            paymentServiceBroker.processAccountPaymentsOutstanding();
            addMessage(attributes, MessageType.INFO, "admin.accounts.payments.done", request);
        } else {
            addMessage(attributes, MessageType.ERROR, "admin.accounts.production", request);
        }
        return "redirect:/admin/accounts/home";
    }

    // todo: wire this up properly
    public void changeAccountSettings(Account account) {
        accountBroker.updateBillingEmail(getUserProfile().getUid(), account.getUid(), account.getBillingUser().getEmailAddress());
        accountBroker.updateAccountGroupLimits(getUserProfile().getUid(), account.getUid(), account.getMaxNumberGroups(),
                account.getMaxSizePerGroup(), account.getMaxSubGroupDepth());
        accountBroker.updateAccountMessageSettings(getUserProfile().getUid(), account.getUid(), account.getFreeFormMessages(),
                account.getFreeFormCost());
    }

}