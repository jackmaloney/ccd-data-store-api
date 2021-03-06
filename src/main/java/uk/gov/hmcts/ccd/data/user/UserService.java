package uk.gov.hmcts.ccd.data.user;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import javax.inject.Inject;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import uk.gov.hmcts.ccd.data.casedetails.JurisdictionMapper;
import uk.gov.hmcts.ccd.data.definition.CachedCaseDefinitionRepository;
import uk.gov.hmcts.ccd.data.definition.CaseDefinitionRepository;
import uk.gov.hmcts.ccd.domain.model.aggregated.IDAMProperties;
import uk.gov.hmcts.ccd.domain.model.aggregated.JurisdictionDisplayProperties;
import uk.gov.hmcts.ccd.domain.model.aggregated.UserDefault;
import uk.gov.hmcts.ccd.domain.model.aggregated.UserProfile;
import uk.gov.hmcts.ccd.domain.model.aggregated.WorkbasketDefault;
import uk.gov.hmcts.ccd.domain.model.definition.Jurisdiction;

@Service
public class UserService {

    private static final Logger LOG = LoggerFactory.getLogger(UserService.class);

    private UserRepository userRepository;
    private CaseDefinitionRepository caseDefinitionRepository;
    private JurisdictionMapper jurisdictionMapper;

    @Inject
    public UserService(@Qualifier(DefaultUserRepository.QUALIFIER) UserRepository userRepository,
                       @Qualifier(CachedCaseDefinitionRepository.QUALIFIER) CaseDefinitionRepository caseDefinitionRepository,
                       JurisdictionMapper jurisdictionMapper) {
        this.userRepository = userRepository;
        this.caseDefinitionRepository = caseDefinitionRepository;
        this.jurisdictionMapper = jurisdictionMapper;
    }

    public CompletableFuture<UserProfile> getUserProfileAsync() {

        CompletableFuture<List<Jurisdiction>> jurisdictionDefsFuture = caseDefinitionRepository.getAllJurisdictionsAsync();
        CompletableFuture<IDAMProperties> idamPropsFuture = userRepository.getUserDetailsAsync();
        CompletableFuture<UserDefault> userDefaultFuture = idamPropsFuture
            .thenCompose(props -> userRepository.getUserDefaultSettingsAsync(props.getEmail()));
        return userDefaultFuture.thenCombine(jurisdictionDefsFuture, ((userDefault, jurisdictions) ->
                createUserProfile(idamPropsFuture.join(), userDefault, jurisdictions)));
    }

    private UserProfile createUserProfile(IDAMProperties idamProperties, UserDefault userDefault, List<Jurisdiction> jurisdictions) {

        UserProfile userProfile = new UserProfile();

        userProfile.getUser().setIdamProperties(idamProperties);

        List<String> userJurisdictions = userDefault.getJurisdictionsId();
        JurisdictionDisplayProperties[] resultJurisdictions = toResponse(userJurisdictions, jurisdictions);
        userProfile.setJurisdictions(resultJurisdictions);

        WorkbasketDefault workbasketDefault = new WorkbasketDefault();
        workbasketDefault.setJurisdictionId(userDefault.getWorkBasketDefaultJurisdiction());
        workbasketDefault.setCaseTypeId(userDefault.getWorkBasketDefaultCaseType());
        workbasketDefault.setStateId(userDefault.getWorkBasketDefaultState());
        userProfile.getDefaultSettings().setWorkbasketDefault(workbasketDefault);

        return userProfile;
    }

    private JurisdictionDisplayProperties[] toResponse(List<String> userJurisdictions, List<Jurisdiction> jurisdictions) {
        return userJurisdictions.stream().map(id -> {
            Optional<Jurisdiction> definition = jurisdictions.stream().filter(def -> def.getId().equals(id)).findAny();
            if (!definition.isPresent()) {
                LOG.warn("Could not retrieve definition of jurisdiction '{}'", id);
            }
            return definition.map(jurisdictionMapper::toResponse);
        }).filter(Optional::isPresent).map(Optional::get).toArray(JurisdictionDisplayProperties[]::new);
    }
}
