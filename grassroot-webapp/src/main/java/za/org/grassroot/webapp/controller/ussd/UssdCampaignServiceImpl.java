package za.org.grassroot.webapp.controller.ussd;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.org.grassroot.core.domain.RoleName;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.domain.campaign.Campaign;
import za.org.grassroot.core.domain.campaign.CampaignMessage;
import za.org.grassroot.core.domain.group.Group;
import za.org.grassroot.core.domain.group.GroupJoinMethod;
import za.org.grassroot.core.domain.group.Membership;
import za.org.grassroot.services.campaign.CampaignBroker;
import za.org.grassroot.services.user.UserManager;

import java.util.Optional;

@Service
public class UssdCampaignServiceImpl implements UssdCampaignService {
	private final CampaignBroker campaignBroker;
	private final UserManager userManager;

	public UssdCampaignServiceImpl(CampaignBroker campaignBroker, UserManager userManager) {
		this.campaignBroker = campaignBroker;
		this.userManager = userManager;
	}

	@Override
	@Transactional
	public CampaignMessage tagMembership(String inputNumber, String messageUid, String parentMsgUid) {
		final User user = userManager.findByInputNumber(inputNumber);
		final CampaignMessage message = campaignBroker.loadCampaignMessage(messageUid, user.getUid());
		final CampaignMessage parentMessage = campaignBroker.loadCampaignMessage(parentMsgUid, user.getUid());

		final Campaign campaign = message.getCampaign();
		final Group masterGroup = campaign.getMasterGroup();
		if (parentMessage != null && parentMessage.getTagList() != null && parentMessage.getTagList().isEmpty()) {
			final Membership membership = user.getGroupMembership(masterGroup.getUid())
					.orElseGet(() -> {
						// todo: VJERAN: - Is this bug in commented old line where Role's group UID is null? Test this new code!!!
						return campaign.getMasterGroup().addMember(user, RoleName.ROLE_ORDINARY_MEMBER, GroupJoinMethod.SELF_JOINED, null);
						//                membership = new Membership(campaign.getMasterGroup(), user, new Role(RoleName.ROLE_ORDINARY_MEMBER, null), Instant.now(), GroupJoinMethod.SELF_JOINED, null);
						//                user.getMemberships().add(membership);
						//                userManager.createUserProfile(user);
					}
			);
			for (String tag : parentMessage.getTagList()) {
				membership.addTag(tag);
			}
		}
		return message;
	}
}
