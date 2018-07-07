package za.org.grassroot.services.account;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import za.org.grassroot.core.domain.BaseRoles;
import za.org.grassroot.core.domain.Permission;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.domain.account.Account;
import za.org.grassroot.core.domain.account.AccountLog;
import za.org.grassroot.core.domain.group.Group;
import za.org.grassroot.core.domain.group.GroupLog;
import za.org.grassroot.core.enums.AccountLogType;
import za.org.grassroot.core.enums.AccountType;
import za.org.grassroot.core.enums.GroupLogType;
import za.org.grassroot.core.repository.AccountRepository;
import za.org.grassroot.core.repository.GroupRepository;
import za.org.grassroot.core.repository.UserRepository;
import za.org.grassroot.core.specifications.GroupSpecifications;
import za.org.grassroot.core.util.AfterTxCommitTask;
import za.org.grassroot.core.util.DateTimeUtil;
import za.org.grassroot.core.util.DebugUtil;
import za.org.grassroot.services.PermissionBroker;
import za.org.grassroot.services.exception.AdminRemovalException;
import za.org.grassroot.services.util.LogsAndNotificationsBroker;
import za.org.grassroot.services.util.LogsAndNotificationsBundle;

import javax.persistence.EntityManager;
import javax.persistence.TypedQuery;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Created by luke on 2015/11/12.
 */
@Service @Slf4j
public class AccountBrokerImpl implements AccountBroker {

    @Value("${accounts.freeform.cost.standard:22}")
    private int additionalMessageCost;

    private final AccountRepository accountRepository;
    private final UserRepository userRepository;
    private final GroupRepository groupRepository;

    private final LogsAndNotificationsBroker logsAndNotificationsBroker;
    private final PermissionBroker permissionBroker;
    private final ApplicationEventPublisher eventPublisher;

    private EntityManager entityManager;

    @Autowired
    public AccountBrokerImpl(AccountRepository accountRepository, UserRepository userRepository, GroupRepository groupRepository, PermissionBroker permissionBroker,
                             LogsAndNotificationsBroker logsAndNotificationsBroker, ApplicationEventPublisher eventPublisher) {
        this.accountRepository = accountRepository;
        this.userRepository = userRepository;
        this.groupRepository = groupRepository;
        this.permissionBroker = permissionBroker;
        this.logsAndNotificationsBroker = logsAndNotificationsBroker;
        this.eventPublisher = eventPublisher;
    }

    @Autowired
    public void setEntityManager(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    @Override
    public Account loadAccount(String accountUid) {
        return accountRepository.findOneByUid(accountUid);
    }

    private void validateAdmin(User user, Account account) {
        Objects.requireNonNull(user);
        Objects.requireNonNull(account);

        if (account.getAdministrators() == null || !account.getAdministrators().contains(user)) {
            permissionBroker.validateSystemRole(user, BaseRoles.ROLE_SYSTEM_ADMIN);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public Account loadPrimaryAccountForUser(String userUid, boolean loadEvenIfDisabled) {
        User user = userRepository.findOneByUid(userUid);
        Account account = user.getPrimaryAccount();
        return (account != null && (loadEvenIfDisabled || account.isEnabled())) ? account : null;
    }

    @Override
    @Transactional
    public String createAccount(String userUid, String accountName, String billedUserUid, String ongoingPaymentRef) {
        Objects.requireNonNull(userUid);
        Objects.requireNonNull(billedUserUid);
        Objects.requireNonNull(accountName);

        User creatingUser = userRepository.findOneByUid(userUid);
        User billedUser = userRepository.findOneByUid(billedUserUid);

        Account account = new Account(creatingUser, accountName, AccountType.STANDARD, billedUser);

        accountRepository.saveAndFlush(account);

        account.addAdministrator(billedUser);
        billedUser.setPrimaryAccount(account);
        permissionBroker.addSystemRole(billedUser, BaseRoles.ROLE_ACCOUNT_ADMIN);

        log.info("Created account, now looks like: " + account);

        account.setFreeFormCost(additionalMessageCost);

        LogsAndNotificationsBundle bundle = new LogsAndNotificationsBundle();
        bundle.addLog(new AccountLog.Builder(account)
                .user(creatingUser)
                .accountLogType(AccountLogType.ACCOUNT_CREATED)
                .description(accountName + ":" + billedUserUid).build());

        bundle.addLog(new AccountLog.Builder(account)
                .user(billedUser)
                .accountLogType(AccountLogType.ADMIN_ADDED)
                .description("billed user set as admin").build());

        if (!StringUtils.isEmpty(ongoingPaymentRef)) {
            account.setEnabled(true);
            account.setEnabledDateTime(Instant.now());
            account.setEnabledByUser(creatingUser);
            account.setPaymentRef(ongoingPaymentRef);

            bundle.addLog(new AccountLog.Builder(account)
                    .accountLogType(AccountLogType.ACCOUNT_ENABLED)
                    .user(creatingUser)
                    .description("account enabled for free trial, at creation")
                    .build());

        }

        AfterTxCommitTask afterTxCommitTask = () -> logsAndNotificationsBroker.asyncStoreBundle(bundle);
        eventPublisher.publishEvent(afterTxCommitTask);

        return account.getUid();
    }

    @Override
    @Transactional
    public void setAccountSubscriptionRef(String userUid, String accountUid, String subscriptionId) {
        User user = userRepository.findOneByUid(Objects.requireNonNull(userUid));
        Account account = accountRepository.findOneByUid(Objects.requireNonNull(accountUid));

        validateAdmin(user, account);

        account.setSubscriptionRef(subscriptionId);

        createAndStoreSingleAccountLog(new AccountLog.Builder(account).user(user)
                .accountLogType(AccountLogType.ACCOUNT_SUB_ID_CHANGED)
                .description(subscriptionId).build());
    }

    @Override
    public void setAccountPaymentRef(String userUid, String accountUid, String paymentRef) {
        User user = userRepository.findOneByUid(Objects.requireNonNull(userUid));
        Account account = accountRepository.findOneByUid(Objects.requireNonNull(accountUid));

        validateAdmin(user, account);

        account.setPaymentRef(paymentRef);

        if (!account.isEnabled())
            account.setEnabled(true);

        createAndStoreSingleAccountLog(new AccountLog.Builder(account).user(user)
                .accountLogType(AccountLogType.PAYMENT_CHANGED)
                .description(paymentRef).build());
    }

    @Override
    @Transactional
    public void enableAccount(String userUid, String accountUid, String ongoingPaymentRef, boolean ensureUserAddedToAdmin, boolean setBillingUser) {
        Objects.requireNonNull(userUid);
        Objects.requireNonNull(accountUid);

        User user = userRepository.findOneByUid(userUid);
        Account account = accountRepository.findOneByUid(accountUid);

        if (!account.getAdministrators().contains(user)) {
            permissionBroker.validateSystemRole(user, BaseRoles.ROLE_SYSTEM_ADMIN);
        }

        account.setEnabled(true);
        account.setEnabledDateTime(Instant.now());
        account.setEnabledByUser(user);
        account.setDisabledDateTime(DateTimeUtil.getVeryLongAwayInstant());

        LogsAndNotificationsBundle bundle = new LogsAndNotificationsBundle();
        bundle.addLog(new AccountLog.Builder(account)
                .accountLogType(AccountLogType.ACCOUNT_ENABLED)
                .user(user)
                .description("account enabled")
                .build());

        // since the user that enables may be different to user that creates, and leaving out this role breaks a lot of UI, just make sure role is added (no regret)
        if (ensureUserAddedToAdmin && !account.equals(user.getPrimaryAccount())) {
            account.addAdministrator(user);
            user.addAccountAdministered(account);
            permissionBroker.addSystemRole(user, BaseRoles.ROLE_SYSTEM_ADMIN);

            bundle.addLog(new AccountLog.Builder(account)
                    .accountLogType(AccountLogType.ADMIN_ADDED)
                    .user(user)
                    .description("account admin added during enabling")
                    .build());
        }

        logsAndNotificationsBroker.asyncStoreBundle(bundle);
    }

    @Override
    @Transactional
    public void setAccountPrimary(String userUid, String accountUid) {
        DebugUtil.transactionRequired("");
        Objects.requireNonNull(userUid);
        Objects.requireNonNull(accountUid);

        User user = userRepository.findOneByUid(userUid);
        Account account = accountRepository.findOneByUid(accountUid);

        if (!account.getAdministrators().contains(user)) {
            throw new IllegalArgumentException("Error! User must be an administrator of their primary account");
        }

        user.setPrimaryAccount(account);
    }

    @Override
    @Transactional
    public void disableAccount(String administratorUid, String accountUid, String reasonToRecord, boolean removeAdminRole, boolean generateClosingBill) {
        Objects.requireNonNull(administratorUid);
        Objects.requireNonNull(accountUid);

        User user = userRepository.findOneByUid(administratorUid);
        Account account = accountRepository.findOneByUid(accountUid);
        validateAdmin(user, account);

        account.setEnabled(false);
        account.setDisabledDateTime(Instant.now());
        account.setDisabledByUser(user);

        for (Group paidGroup : account.getPaidGroups()) {
            paidGroup.setPaidFor(false);
        }

        if (removeAdminRole) {
            for (User admin : account.getAdministrators()) {
                admin.setPrimaryAccount(null);
                permissionBroker.removeSystemRole(admin, BaseRoles.ROLE_ACCOUNT_ADMIN);
            }
        }

        createAndStoreSingleAccountLog(new AccountLog.Builder(account)
                .user(user)
                .accountLogType(AccountLogType.ACCOUNT_DISABLED)
                .description(reasonToRecord).build());
    }

    @Override
    @Transactional
    public void addAdministrator(String userUid, String accountUid, String administratorUid) {
        Objects.requireNonNull(userUid);
        Objects.requireNonNull(accountUid);
        Objects.requireNonNull(administratorUid);

        User changingUser = userRepository.findOneByUid(userUid);
        Account account = accountRepository.findOneByUid(accountUid);
        User administrator = userRepository.findOneByUid(administratorUid);

        validateAdmin(changingUser, account);

        account.addAdministrator(administrator);
        administrator.addAccountAdministered(account);
        permissionBroker.addSystemRole(administrator, BaseRoles.ROLE_ACCOUNT_ADMIN);

        if (administrator.getPrimaryAccount() == null) {
            administrator.setPrimaryAccount(account);
        }

        createAndStoreSingleAccountLog(new AccountLog.Builder(account)
                .user(changingUser)
                .accountLogType(AccountLogType.ADMIN_ADDED)
                .description(administrator.getUid()).build());
    }

    @Override
    @Transactional
    public void removeAdministrator(String userUid, String accountUid, String adminToRemoveUid, boolean preventRemovingSelfOrBilling) {
        Objects.requireNonNull(userUid);
        Objects.requireNonNull(accountUid);
        Objects.requireNonNull(adminToRemoveUid);
        DebugUtil.transactionRequired("");

        log.info("removing admin, user: {}, admin to remove uid: {}, account uid: {}", userUid, adminToRemoveUid, accountUid);

        if (preventRemovingSelfOrBilling && userUid.equals(adminToRemoveUid)) {
            throw new AdminRemovalException("account.admin.remove.error.same");
        }

        User changingUser = userRepository.findOneByUid(userUid);
        Account account = accountRepository.findOneByUid(accountUid);
        User administrator = userRepository.findOneByUid(adminToRemoveUid);

        validateAdmin(changingUser, account);

        account.removeAdministrator(administrator);
        administrator.removeAccountAdministered(account);

        if (account.equals(administrator.getPrimaryAccount())) {
            administrator.setPrimaryAccount(null);
        }

        if (administrator.getAccountsAdministered() == null || administrator.getAccountsAdministered().isEmpty()) {
            permissionBroker.removeSystemRole(administrator, BaseRoles.ROLE_ACCOUNT_ADMIN);
        }

        createAndStoreSingleAccountLog(new AccountLog.Builder(account)
                .user(changingUser)
                .accountLogType(AccountLogType.ADMIN_REMOVED)
                .description(administrator.getUid()).build());
    }

    @Override
    @Transactional
    public void addGroupsToAccount(String accountUid, Set<String> groupUids, String userUid) {
        Objects.requireNonNull(accountUid);
        Objects.requireNonNull(groupUids);
        Objects.requireNonNull(userUid);

        Account account = accountRepository.findOneByUid(accountUid);
        User user = userRepository.findOneByUid(userUid);
        validateAdmin(user, account);

        List<Group> groups = groupRepository
                .findAll(Specification.where(GroupSpecifications.uidIn(groupUids)));
        log.info("number of groups matching list: {}", groups.size());

        LogsAndNotificationsBundle bundle = new LogsAndNotificationsBundle();

        groups.stream().filter(group -> !group.isPaidFor()).forEach(group -> {
            group.setAccount(account);
            group.setPaidFor(true);

            log.info("Added group {} to account {}", group.getName(), account.getName());

            bundle.addLog(new AccountLog.Builder(account)
                    .user(user)
                    .accountLogType(AccountLogType.GROUP_ADDED)
                    .group(group)
                    .paidGroupUid(group.getUid())
                    .description(group.getName()).build());

            bundle.addLog(new GroupLog(group, user, GroupLogType.ADDED_TO_ACCOUNT,
                    null, null, account, "Group added to Grassroot Extra accounts"));
        });

        logsAndNotificationsBroker.asyncStoreBundle(bundle);
    }

    @Override
    @Transactional
    public void addAllUserCreatedGroupsToAccount(String accountUid, String userUid) {
        Objects.requireNonNull(accountUid);
        Objects.requireNonNull(userUid);

        Account account = accountRepository.findOneByUid(accountUid);
        User user = userRepository.findOneByUid(userUid);

        validateAdmin(user, account);

        Set<String> groups = groupRepository.findByCreatedByUserAndActiveTrueOrderByCreatedDateTimeDesc(user)
                .stream().map(Group::getUid).collect(Collectors.toSet());

        addGroupsToAccount(accountUid, groups, userUid);
    }

    @Override
    @Transactional(readOnly = true)
    public Set<Group> fetchGroupsUserCanAddToAccount(String accountUid, String userUid) {
        User user = userRepository.findOneByUid(Objects.requireNonNull(userUid));
        Account account = accountRepository.findOneByUid(Objects.requireNonNull(accountUid));
        validateAdmin(user, account); // should never need to call this witohut admin

        // for the moment, just doing it based on the most general organizer permission
        Set<Group> userGroups = permissionBroker.getActiveGroupsWithPermission(user, Permission.GROUP_PERMISSION_UPDATE_GROUP_DETAILS);
        log.debug("User has {} groups with organizer permission", userGroups.size());
        Set<Group> userGroupsUnpaidFor = userGroups.stream().filter(group -> !group.isPaidFor()).collect(Collectors.toSet());
        log.debug("After removing paid groups, {} left", userGroupsUnpaidFor.size());

        return userGroupsUnpaidFor;
    }

    @Override
    @Transactional
    public void removeGroupsFromAccount(String accountUid, Set<String> groupUids, String removingUserUid) {
        Objects.requireNonNull(accountUid);
        Objects.requireNonNull(groupUids);
        Objects.requireNonNull(removingUserUid);

        Account account = accountRepository.findOneByUid(accountUid);

        LogsAndNotificationsBundle bundle = new LogsAndNotificationsBundle();

        for (String groupUid : groupUids) {
            Group group = groupRepository.findOneByUid(groupUid);
            User user = userRepository.findOneByUid(removingUserUid);

            if (!account.getAdministrators().contains(user)) {
                permissionBroker.validateSystemRole(user, BaseRoles.ROLE_SYSTEM_ADMIN);
            }

            account.removePaidGroup(group);
            group.setPaidFor(false);

            bundle.addLog(new AccountLog.Builder(account)
                    .user(user)
                    .accountLogType(AccountLogType.GROUP_REMOVED)
                    .group(group)
                    .paidGroupUid(group.getUid())
                    .description(group.getName()).build());

            bundle.addLog(new GroupLog(group,
                    user,
                    GroupLogType.REMOVED_FROM_ACCOUNT,
                    null, null, account, "Group removed from Grassroot Extra"));
        }

        logsAndNotificationsBroker.asyncStoreBundle(bundle);
    }

    @Override
    @Transactional
    public void modifyAccount(String adminUid, String accountUid, String accountName, String billingEmail) {
        Objects.requireNonNull(adminUid);
        Objects.requireNonNull(accountUid);

        User user = userRepository.findOneByUid(adminUid);
        Account account = accountRepository.findOneByUid(accountUid);

        validateAdmin(user, account);

        StringBuilder sb = new StringBuilder("Changes: ");

        if(!account.getAccountName().equals(accountName)) {
            account.setAccountName(accountName);
            sb.append(" account name = ").append(accountName).append(";");
        }

        createAndStoreSingleAccountLog(new AccountLog.Builder(account)
                .accountLogType(AccountLogType.SYSADMIN_MODIFIED_MULTIPLE)
                .user(user)
                .description(sb.toString()).build());
    }

    @Override
    @Transactional
    public void closeAccount(String userUid, String accountUid, String closingReason) {
        Account account = accountRepository.findOneByUid(Objects.requireNonNull(userUid));
        User user = userRepository.findOneByUid(Objects.requireNonNull(accountUid));

        validateAdmin(user, account); // note: this allows non-billing admin to close account, leaving for now but may revisit

        account.setEnabled(false);

        log.info("removing {} paid groups", account.getPaidGroups().size());
        Set<String> paidGroupUids = account.getPaidGroups().stream().map(Group::getUid).collect(Collectors.toSet());
        removeGroupsFromAccount(accountUid, paidGroupUids, userUid);

        createAndStoreSingleAccountLog(new AccountLog.Builder(account)
                .user(user)
                .accountLogType(AccountLogType.ACCOUNT_INVISIBLE)
                .description(closingReason).build());

        log.info("removing {} administrators", account.getAdministrators().size());
        account.getAdministrators().stream().filter(u -> !u.getUid().equals(userUid))
                .forEach(a -> removeAdministrator(userUid, accountUid, a.getUid(), false));

        removeAdministrator(userUid, accountUid, userUid, false); // at the end, remove self
    }

    @Override
    @Transactional(readOnly = true)
    public long countAccountNotifications(String accountUid, Instant startTime, Instant endTime) {
        Account account = accountRepository.findOneByUid(accountUid);
        return countAllForGroups(account.getPaidGroups(), startTime, endTime);
    }

    @Override
    @Transactional(readOnly = true)
    public long countChargedNotificationsForGroup(String accountUid, String groupUid, Instant start, Instant end) {
        Account account = accountRepository.findOneByUid(accountUid);
        Group group = groupRepository.findOneByUid(groupUid);
        return countAllForGroups(Collections.singleton(group), start, end);
    }

    private long countAllForGroups(Set<Group> groups, Instant start, Instant end) {
        long startTime = System.currentTimeMillis();
        long groupLogCount = executeCountQuery("(n.groupLog in (select gl from GroupLog gl where gl.group in :groups))",
                start, end, groups);

        long eventLogCount = executeCountQuery("(n.eventLog in (select el from EventLog el where el.event.ancestorGroup in :groups))",
                start, end, groups);

        long todoLogCount = executeCountQuery("(n.todoLog in (select tl from TodoLog tl where tl.todo.ancestorGroup in :groups))",
                start, end, groups);

        long campaignLogCount = executeCountQuery("(n.campaignLog in (select cl from CampaignLog cl where cl.campaign.masterGroup in :groups))",
                start, end, groups);

        log.info("In {} msecs, for {} groups, counted {} from group logs, {} from event logs, {} from todo logs, {} from campaign logs",
                System.currentTimeMillis() - startTime, groups.size(), groupLogCount, eventLogCount, todoLogCount, campaignLogCount);

        return groupLogCount + eventLogCount + todoLogCount + campaignLogCount;

    }

    private long executeCountQuery(String querySuffix, Instant start, Instant end, Set<Group> groups) {
        TypedQuery<Long> countQuery = entityManager.createQuery(countQueryOpening() + querySuffix, Long.class)
                .setParameter("start", start).setParameter("end", end)
                .setParameter("groups", groups);

        return countQuery.getSingleResult();
    }

    private String countQueryOpening() {
        return "select count(n) from Notification n " +
                "where n.createdDateTime between :start and :end and " +
                "n.status in ('DELIVERED', 'READ') and ";
    }

    private void createAndStoreSingleAccountLog(AccountLog accountLog) {
        LogsAndNotificationsBundle bundle = new LogsAndNotificationsBundle();
        bundle.addLog(accountLog);
        logsAndNotificationsBroker.asyncStoreBundle(bundle);
    }
}