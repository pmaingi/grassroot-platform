package za.org.grassroot.services.campaign;


import za.org.grassroot.core.domain.Campaign;
import za.org.grassroot.core.domain.CampaignMessage;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.enums.MessageVariationAssignment;

import java.time.Instant;
import java.util.Set;

public interface CampaignService {
    /**
     * Get Campaign information by campaign code
     * @param campaignCode
     * @return
     */
    Campaign getCampaignDetailsByCode(String campaignCode);

    /**
     * Get Campaign information by campaign name
     * @param campaignName
     * @return
     */
    Campaign getCampaignDetailsByName(String campaignName);


    /**
     * Get Campaign message by campaign code
     * @param campaignCode
     * @param assignment
     * @return
     */
    Set<CampaignMessage> getCampaignMessagesByCampaignCode(String campaignCode, MessageVariationAssignment assignment);

    /**
     *
     * @param campaignName
     * @param assignment
     * @return
     */
    Set<CampaignMessage> getCampaignMessagesByCampaignName(String campaignName, MessageVariationAssignment assignment);

    /**
     *
     * @param campaignCode
     * @param assignment
     * @param locale
     * @return
     */
    Set<CampaignMessage> getCampaignMessagesByCampaignCodeAndLocale(String campaignCode, MessageVariationAssignment assignment, String locale);

    /**
     *
     * @param campaignCode - campaign code
     * @param assignment
     * @param locale
     * @return
     */
    Set<CampaignMessage> getCampaignMessagesByCampaignNameAndLocale(String campaignCode, MessageVariationAssignment assignment, String locale);

    /**
     *
     * @param campaignCode
     * @param assignment
     * @param messageTag
     * @return
     */
    Set<CampaignMessage> getCampaignMessagesByCampaignCodeAndMessageTag(String campaignCode, MessageVariationAssignment assignment, String messageTag);

    /**
     *
     * @param campaignName
     * @param assignment
     * @param messageTag
     * @return
     */
    Set<CampaignMessage> getCampaignMessagesByCampaignNameAndMessageTag(String campaignName, MessageVariationAssignment assignment, String messageTag);

    /**
     *
     * @param campaignName
     * @param campaignCode
     * @param campaign
     * @param createUser
     * @param startDate
     * @param endDate
     * @return
     */
    Campaign createCampaign(String campaignName, String campaignCode, String campaign, User createUser, Instant startDate, Instant endDate);

}
