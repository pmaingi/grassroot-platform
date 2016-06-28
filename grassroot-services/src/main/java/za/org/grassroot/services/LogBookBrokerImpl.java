package za.org.grassroot.services;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.org.grassroot.core.domain.*;
import za.org.grassroot.core.domain.notification.LogBookInfoNotification;
import za.org.grassroot.core.domain.notification.LogBookReminderNotification;
import za.org.grassroot.core.repository.GroupRepository;
import za.org.grassroot.core.repository.LogBookRepository;
import za.org.grassroot.core.repository.UidIdentifiableRepository;
import za.org.grassroot.core.repository.UserRepository;
import za.org.grassroot.core.util.DateTimeUtil;
import za.org.grassroot.services.enums.LogBookStatus;
import za.org.grassroot.services.util.LogsAndNotificationsBroker;
import za.org.grassroot.services.util.LogsAndNotificationsBundle;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import static za.org.grassroot.core.util.DateTimeUtil.convertToSystemTime;
import static za.org.grassroot.core.util.DateTimeUtil.getSAST;

@Service
public class LogBookBrokerImpl implements LogBookBroker {

	private final Logger logger = LoggerFactory.getLogger(LogBookBrokerImpl.class);
	private static final int defaultReminders = 2; // todo: externalize this / base it on some logic

	@Autowired
	private UserRepository userRepository;
	@Autowired
	private UidIdentifiableRepository uidIdentifiableRepository;
	@Autowired
	private GroupRepository groupRepository;
	@Autowired
	private LogBookRepository logBookRepository;
	@Autowired
	private MessageAssemblingService messageAssemblingService;
	@Autowired
	private LogsAndNotificationsBroker logsAndNotificationsBroker;
	@Autowired
	private PermissionBroker permissionBroker;

	@Override
	public LogBook load(String logBookUid) {
		return logBookRepository.findOneByUid(logBookUid);
	}

	@Override
	@Transactional
	public LogBook create(String userUid, JpaEntityType parentType, String parentUid, String message, LocalDateTime actionByDate, int reminderMinutes,
						  boolean replicateToSubgroups, Set<String> assignedMemberUids) {

		Objects.requireNonNull(userUid);
		Objects.requireNonNull(parentType);
		Objects.requireNonNull(parentUid);
		Objects.requireNonNull(message);
		Objects.requireNonNull(actionByDate);
		Objects.requireNonNull(assignedMemberUids);

		User user = userRepository.findOneByUid(userUid);
		LogBookContainer parent = uidIdentifiableRepository.findOneByUid(LogBookContainer.class, parentType, parentUid);

		logger.info("Creating new log book: userUid={}, parentType={}, parentUid={}, message={}, actionByDate={}, reminderMinutes={}, assignedMemberUids={}, replicateToSubgroups={}",
				userUid, parentType, parentUid, message, actionByDate, reminderMinutes, assignedMemberUids, replicateToSubgroups);

		Instant convertedActionByDate = convertToSystemTime(actionByDate, getSAST());
		LogBook logBook = new LogBook(user, parent, message, convertedActionByDate, reminderMinutes, null, defaultReminders);
		logBook.assignMembers(assignedMemberUids);
		logBook = logBookRepository.save(logBook);

		LogsAndNotificationsBundle bundle = new LogsAndNotificationsBundle();
		LogBookLog logBookLog = new LogBookLog(user, logBook, null);
		bundle.addLog(logBookLog);

		Set<Notification> notifications = constructLogBookRecordedNotifications(logBook, logBookLog);
		bundle.addNotifications(notifications);

		logsAndNotificationsBroker.storeBundle(bundle);

		// replication means this could get _very_ expensive, so we probably want to move this to async once it starts being used
		if (replicateToSubgroups && parent.getJpaEntityType().equals(JpaEntityType.GROUP)) {
			replicateLogBookToSubgroups(user, logBook, actionByDate);
		}

		return logBook;
	}

	private Set<Notification> constructLogBookRecordedNotifications(LogBook logBook, LogBookLog logBookLog) {
		Set<Notification> notifications = new HashSet<>();
		// the "recorded" notification gets sent to all users in parent, not just assigned (to re-evaluate in future)
		for (User member : logBook.getParent().getMembers()) {
			String message = messageAssemblingService.createLogBookInfoNotificationMessage(member, logBook);
			Notification notification = new LogBookInfoNotification(member, message, logBookLog);
			notifications.add(notification);
		}
		return notifications;
	}

	private void replicateLogBookToSubgroups(User user, LogBook logBook, LocalDateTime actionByDate) {
		Group group = logBook.getAncestorGroup();
		// note: getGroupAndSubGroups is a much faster method (a recursive query) than getSubGroups, hence use it and just skip parent
		List<Group> groupAndSubGroups = groupRepository.findGroupAndSubGroupsById(group.getId());
		for (Group subGroup : groupAndSubGroups) {
			if (!group.equals(subGroup)) {
				create(user.getUid(), JpaEntityType.GROUP, subGroup.getUid(), logBook.getMessage(), actionByDate,
					   logBook.getReminderMinutes(), true, new HashSet<>());
			}
		}
	}

	@Override
	@Transactional
	public void assignMembers(String userUid, String logBookUid, Set<String> assignMemberUids) {
		Objects.requireNonNull(logBookUid);

		User user = userRepository.findOneByUid(userUid);
		LogBook logBook = logBookRepository.findOneByUid(logBookUid);

		logBook.assignMembers(assignMemberUids);
	}

	@Override
	@Transactional
	public void removeAssignedMembers(String userUid, String logBookUid, Set<String> memberUids) {
		Objects.requireNonNull(logBookUid);

		User user = userRepository.findOneByUid(userUid);
		LogBook logBook = logBookRepository.findOneByUid(logBookUid);

		logBook.removeAssignedMembers(memberUids);
	}

	@Override
	@Transactional
	public boolean confirmCompletion(String userUid, String logBookUid, LocalDateTime completionTime) {
		Objects.requireNonNull(userUid);
		Objects.requireNonNull(logBookUid);

		User user = userRepository.findOneByUid(userUid);
		LogBook logBook = logBookRepository.findOneByUid(logBookUid);

		logger.info("Confirming completion logbook={}, completion time={}, user={}", logBook, completionTime, user);

		Instant completionInstant = completionTime == null ? null : convertToSystemTime(completionTime, getSAST());
		boolean confirmationRegistered = logBook.addCompletionConfirmation(user, completionInstant);
		if (!confirmationRegistered) {
			// should error be raised when member already registered completions !?
			logger.info("Completion confirmation already exists for member {} and log book {}", user, logBook);
		}
		return confirmationRegistered;
	}


	@Override
	@Transactional
	public void sendScheduledReminder(String logBookUid) {
		Objects.requireNonNull(logBookUid);

		LogBook logBook = logBookRepository.findOneByUid(logBookUid);
		LogsAndNotificationsBundle bundle = new LogsAndNotificationsBundle();
		LogBookLog logBookLog = new LogBookLog(null, logBook, null);

		Set<User> members = logBook.isAllGroupMembersAssigned() ? logBook.getAncestorGroup().getMembers() : logBook.getAssignedMembers();
		for (User member : members) {
			String message = messageAssemblingService.createLogBookReminderMessage(member, logBook);
			Notification notification = new LogBookReminderNotification(member, message, logBookLog);
			bundle.addNotification(notification);
		}

		// we only want to include log if there are some notifications
		if (!bundle.getNotifications().isEmpty()) {
			bundle.addLog(logBookLog);
		}

		// reduce number of reminders to send and calculate new reminder minutes
		logBook.setNumberOfRemindersLeftToSend(logBook.getNumberOfRemindersLeftToSend() - 1);
		if (logBook.getReminderMinutes() < 0) {
			logBook.setReminderMinutes(DateTimeUtil.numberOfMinutesForDays(7));
		} else {
			logBook.setReminderMinutes(logBook.getReminderMinutes() + DateTimeUtil.numberOfMinutesForDays(7));
		}

		logsAndNotificationsBroker.storeBundle(bundle);
	}

	@Override
	@Transactional(readOnly = true)
	public Page<LogBook> retrieveGroupLogBooks(String userUid, String groupUid, boolean entriesComplete, int pageNumber, int pageSize) {
		Objects.requireNonNull(userUid);

		Page<LogBook> page;
		Pageable pageable = new PageRequest(pageNumber, pageSize);
		User user = userRepository.findOneByUid(userUid);

		if (groupUid != null) {
			Group group = groupRepository.findOneByUid(groupUid);
			permissionBroker.validateGroupPermission(user, group, null); // make sure user is part of group
			if (entriesComplete) {
				page = logBookRepository.findByParentGroupAndCompletionPercentageGreaterThanEqualOrderByActionByDateDesc(group, 50, pageable);
			} else {
				page = logBookRepository.findByParentGroupAndCompletionPercentageLessThanOrderByActionByDateDesc(group, 50, pageable);
			}
		} else {
			if (entriesComplete) {
				page = logBookRepository.findByParentGroupMembershipsUserAndCompletionPercentageGreaterThanEqualOrderByActionByDateDesc(user, 50, pageable);
			} else {
				page = logBookRepository.findByParentGroupMembershipsUserAndCompletionPercentageLessThanOrderByActionByDateDesc(user, 50, pageable);
			}
		}

		return page;
	}

	@Override
	public List<Group> retrieveGroupsFromLogBooks(List<LogBook> logBooks) {
		return logBooks.stream()
				.filter(logBook -> logBook.getParent().getJpaEntityType().equals(JpaEntityType.GROUP))
				.map(logBook -> (Group) logBook.getParent())
				.collect(Collectors.toList());
	}

	@Override
	public List<LogBook> loadGroupLogBooks(String groupUid, boolean futureLogBooksOnly, LogBookStatus status) {
		Objects.requireNonNull(groupUid);

		Group group = groupRepository.findOneByUid(groupUid);
		Instant start = futureLogBooksOnly ? Instant.now() : DateTimeUtil.getEarliestInstant();

		switch (status) {
			case COMPLETE:
				return logBookRepository.findByParentGroupAndCompletionPercentageGreaterThanEqualAndActionByDateGreaterThan(group, 50, start);
			case INCOMPLETE:
				return logBookRepository.findByParentGroupAndCompletionPercentageLessThanAndActionByDateGreaterThan(group, 50, start);
			case BOTH:
				return logBookRepository.findByParentGroupAndActionByDateGreaterThan(group, start);
			default:
				return logBookRepository.findByParentGroupAndActionByDateGreaterThan(group, start);
		}
	}

	@Override
	@Transactional(readOnly = true)
	public List<LogBook> loadUserLogBooks(String userUid, boolean assignedLogBooksOnly, boolean futureLogBooksOnly, LogBookStatus status) {
		Objects.requireNonNull(userUid);

		User user = userRepository.findOneByUid(userUid);
		Instant start = futureLogBooksOnly ? Instant.now() : DateTimeUtil.getEarliestInstant();
		Sort sort = new Sort(Sort.Direction.DESC, "createdDateTime");

		if (!assignedLogBooksOnly) {
			switch(status) {
				case COMPLETE:
					return logBookRepository.findByParentGroupMembershipsUserAndActionByDateBetweenAndCompletionPercentageGreaterThanEqual(user, start, Instant.MAX, 50, sort);
				case INCOMPLETE:
					return logBookRepository.findByParentGroupMembershipsUserAndActionByDateBetweenAndCompletionPercentageLessThan(user, start, Instant.MAX, 50, sort);
				case BOTH:
					return logBookRepository.findByParentGroupMembershipsUserAndActionByDateGreaterThan(user, start);
				default:
					return logBookRepository.findByParentGroupMembershipsUserAndActionByDateGreaterThan(user, start);
			}
		} else {
			switch (status) {
				case COMPLETE:
					return logBookRepository.findByAssignedMembersAndActionByDateBetweenAndCompletionPercentageGreaterThanEqual(user, start, Instant.MAX, 50, sort);
				case INCOMPLETE:
					return logBookRepository.findByAssignedMembersAndActionByDateBetweenAndCompletionPercentageLessThan(user, start, Instant.MAX, 50, sort);
				case BOTH:
					return logBookRepository.findByAssignedMembersAndActionByDateGreaterThan(user, start);
				default:
					return logBookRepository.findByParentGroupMembershipsUserAndActionByDateGreaterThan(user, start);
			}
		}
	}

	@Override
	@Transactional(readOnly = true)
	public LogBook fetchLogBookForUserResponse(String userUid, long daysInPast, boolean assignedLogBooksOnly) {
		LogBook lbToReturn;
		User user = userRepository.findOneByUid(userUid);
		Instant end = Instant.now();
		Instant start = Instant.now().minus(daysInPast, ChronoUnit.DAYS);
		Sort sort = new Sort(Sort.Direction.ASC, "actionByDate"); // so the most overdue come up first

		if (!assignedLogBooksOnly) {
			List<LogBook> userLbs = logBookRepository.
					findByParentGroupMembershipsUserAndActionByDateBetweenAndCompletionPercentageLessThan(user, start, end, 50, sort);
			lbToReturn = (userLbs.isEmpty()) ? null : userLbs.get(0);
		} else {
			List<LogBook> userLbs = logBookRepository.
					findByAssignedMembersAndActionByDateBetweenAndCompletionPercentageLessThan(user, start, end, 50, sort);
			lbToReturn = (userLbs.isEmpty()) ? null : userLbs.get(0);
		}
		return lbToReturn;
	}
}
