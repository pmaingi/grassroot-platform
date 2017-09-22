package za.org.grassroot.services.campaign;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.org.grassroot.core.domain.Campaign;
import za.org.grassroot.core.domain.CampaignMessage;
import za.org.grassroot.core.domain.Group;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.enums.MessageVariationAssignment;
import za.org.grassroot.core.repository.CampaignRepository;
import za.org.grassroot.core.repository.GroupRepository;
import za.org.grassroot.services.campaign.util.CampaignUtil;
import za.org.grassroot.services.exception.CampaignNotFoundException;
import za.org.grassroot.services.exception.GroupNotFoundException;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

@Service
public class CampaignBrokerImpl implements CampaignBroker {

    private final CampaignRepository campaignRepository;
    private final GroupRepository groupRepository;

    @Autowired
    public CampaignBrokerImpl(CampaignRepository campaignRepository, GroupRepository groupRepository){
        this.campaignRepository = campaignRepository;
        this.groupRepository = groupRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public Campaign getCampaignDetailsByCode(String campaignCode){
        return getCampaignByCampaignCode(campaignCode);
    }

    @Override
    @Transactional(readOnly = true)
    public Campaign getCampaignDetailsByName(String campaignName){
        return getCampaignByCampaignName(campaignName);
    }

    @Override
    @Transactional(readOnly = true)
    public Set<CampaignMessage> getCampaignMessagesByCampaignCode(String campaignCode, MessageVariationAssignment assignment){
        return findMessagesByCampaignCodeAndVariation(campaignCode,assignment);
    }

    @Override
    @Transactional(readOnly = true)
    public Set<CampaignMessage> getCampaignMessagesByCampaignName(String campaignName, MessageVariationAssignment assignment){
        return findMessagesByCampaignNameAndVariation(campaignName,assignment);
    }

    @Override
    @Transactional(readOnly = true)
    public Set<CampaignMessage> getCampaignMessagesByCampaignCodeAndLocale(String campaignCode, MessageVariationAssignment assignment, String locale){
        Objects.requireNonNull(locale);
        Set<CampaignMessage> messageSet = findMessagesByCampaignCodeAndVariation(campaignCode,assignment);
        return  CampaignUtil.processCampaignMessagesByLocale(messageSet,locale);
    }

    @Override
    @Transactional(readOnly = true)
    public Set<CampaignMessage> getCampaignMessagesByCampaignNameAndLocale(String campaignName, MessageVariationAssignment assignment, String locale){
        Objects.requireNonNull(locale);
        Set<CampaignMessage>messageSet = findMessagesByCampaignNameAndVariation(campaignName,assignment);
        return CampaignUtil.processCampaignMessagesByLocale(messageSet,locale);
    }

    @Override
    @Transactional(readOnly = true)
    public Set<CampaignMessage> getCampaignMessagesByCampaignCodeAndMessageTag(String campaignCode, MessageVariationAssignment assignment, String messageTag){
        Objects.requireNonNull(messageTag);
        Set<CampaignMessage> messageSet = findMessagesByCampaignCodeAndVariation(campaignCode, assignment);
        return CampaignUtil.processCampaignMessagesByTag(messageSet,messageTag);
    }

    @Override
    @Transactional(readOnly = true)
    public Set<CampaignMessage> getCampaignMessagesByCampaignNameAndMessageTag(String campaignName, MessageVariationAssignment assignment, String messageTag){
        Objects.requireNonNull(messageTag);
        Set<CampaignMessage> messageSet = findMessagesByCampaignNameAndVariation(campaignName, assignment);
        return CampaignUtil.processCampaignMessagesByTag(messageSet, messageTag);
    }

    @Override
    @Transactional
    public Campaign createCampaign(String campaignName, String campaignCode, String description, User createUser, Instant startDate, Instant endDate, List<String> campaignTags){
        Campaign newCampaign = new Campaign(campaignName, campaignCode, description,createUser, startDate, endDate);
        if(campaignTags != null && !campaignTags.isEmpty()){
            newCampaign.getTagList().addAll(campaignTags);
        }
        return campaignRepository.saveAndFlush(newCampaign);
    }

    @Override
    @Transactional
    public Campaign createCampaign(String campaignName, String campaignCode, String description, User createUser, Long groupId, Instant startDate, Instant endDate, List<String> campaignTags){
        Objects.requireNonNull(groupId);
        Group group = groupRepository.findOne(groupId);
        if(group != null){
            Campaign newCampaign = new Campaign(campaignName, campaignCode, description,createUser, startDate, endDate);
            newCampaign.setMasterGroup(group);
            if(campaignTags != null && !campaignTags.isEmpty()){
                newCampaign.getTagList().addAll(campaignTags);
            }
            return campaignRepository.saveAndFlush(newCampaign);
        }
        throw new GroupNotFoundException();
    }

    @Override
    @Transactional
    public Campaign addCampaignMessage(String campaignCode, String campaignMessage, Locale messageLocale, MessageVariationAssignment assignment, Integer sequenceNumber, User createUser, List<String> messageTags){
        Objects.requireNonNull(campaignCode);
        Objects.requireNonNull(campaignMessage);
        Objects.requireNonNull(messageLocale);
        Objects.requireNonNull(sequenceNumber);
        Objects.requireNonNull(createUser);
        Objects.requireNonNull(assignment);
        Objects.requireNonNull(messageTags);
        Campaign campaign = campaignRepository.findByCampaignCodeAndEndDateTimeAfter(campaignCode,Instant.now());
        if(campaign != null){
            CampaignMessage message = new CampaignMessage(campaignMessage, createUser, assignment, messageLocale,sequenceNumber);
            message.getTagList().addAll(messageTags);
            campaign.getCampaignMessages().add(message);
            return campaignRepository.saveAndFlush(campaign);
        }
        throw new CampaignNotFoundException("No Campaign found for code "+campaignCode);
    }

    @Override
    @Transactional
    public Campaign addCampaignTags(String campaignCode, List<String> tags){
        Objects.requireNonNull(campaignCode);
        Objects.requireNonNull(tags);
        Campaign campaign = campaignRepository.findByCampaignCodeAndEndDateTimeAfter(campaignCode,Instant.now());
        if(campaign != null){
            campaign.getTagList().addAll(tags);
            return campaignRepository.saveAndFlush(campaign);
        }
        throw new CampaignNotFoundException("No Campaign found for code "+campaignCode);
    }

    @Override
    @Transactional
    public Campaign linkCampaignToMasterGroup(String campaignCode, Long groupId){
        Objects.requireNonNull(campaignCode);
        Objects.requireNonNull(groupId);
        Group group = groupRepository.findOne(groupId);
        Campaign campaign = campaignRepository.findByCampaignCodeAndEndDateTimeAfter(campaignCode,Instant.now());
        if(group != null && campaign != null){
            campaign.setMasterGroup(group);
            return campaignRepository.saveAndFlush(campaign);
        }
        //throw exception
        return null;

    }



    private Campaign getCampaignByCampaignCode(String campaignCode){
        Objects.requireNonNull(campaignCode);
        return campaignRepository.findByCampaignCodeAndEndDateTimeAfter(campaignCode, Instant.now());
    }

    private Campaign getCampaignByCampaignName(String campaignName){
        Objects.requireNonNull(campaignName);
        return campaignRepository.findByCampaignNameAndEndDateTimeAfter(campaignName, Instant.now());
    }

    private Set<CampaignMessage> findMessagesByCampaignCodeAndVariation(String campaignCode, MessageVariationAssignment assignment){
        Objects.requireNonNull(assignment);
        Campaign campaign = getCampaignByCampaignCode(campaignCode);
        return CampaignUtil.processCampaignMessageByAssignmentVariation(campaign,assignment);
    }

    private Set<CampaignMessage> findMessagesByCampaignNameAndVariation(String campaignName, MessageVariationAssignment assignment){
        Objects.requireNonNull(assignment);
        Campaign campaign = getCampaignByCampaignName(campaignName);
        return CampaignUtil.processCampaignMessageByAssignmentVariation(campaign, assignment);
    }

    public Campaign getCampaignByTag(String tag){
        Objects.requireNonNull(tag);
        return campaignRepository.findActiveCampaignByTag(tag);
    }

}
