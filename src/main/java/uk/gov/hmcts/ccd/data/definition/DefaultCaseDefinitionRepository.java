package uk.gov.hmcts.ccd.data.definition;

import static uk.gov.hmcts.ccd.ApplicationParams.encodeBase64;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.inject.Inject;

import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.cache.annotation.CacheConfig;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;
import uk.gov.hmcts.ccd.ApplicationParams;
import uk.gov.hmcts.ccd.data.SecurityUtils;
import uk.gov.hmcts.ccd.domain.model.definition.CaseType;
import uk.gov.hmcts.ccd.domain.model.definition.FieldType;
import uk.gov.hmcts.ccd.domain.model.definition.UserRole;
import uk.gov.hmcts.ccd.endpoint.exceptions.ResourceNotFoundException;
import uk.gov.hmcts.ccd.endpoint.exceptions.ServiceException;

@Service
@Qualifier(DefaultCaseDefinitionRepository.QUALIFIER)
public class DefaultCaseDefinitionRepository implements CaseDefinitionRepository {

    private static final org.slf4j.Logger LOG = LoggerFactory.getLogger(DefaultCaseDefinitionRepository.class);

    public static final String QUALIFIER = "default";
    private static final int RESOURCE_NOT_FOUND = 404;

    private final ApplicationParams applicationParams;
    private final SecurityUtils securityUtils;
    private final RestTemplate restTemplate;

    @Inject
    public DefaultCaseDefinitionRepository(final ApplicationParams applicationParams,
                                           final SecurityUtils securityUtils,
                                           final RestTemplate restTemplate) {
        this.applicationParams = applicationParams;
        this.securityUtils = securityUtils;
        this.restTemplate = restTemplate;
    }

    @Override
    public List<CaseType> getCaseTypesForJurisdiction(final String jurisdictionId) {
        try {
            final HttpEntity requestEntity = new HttpEntity(securityUtils.authorizationHeaders());
            return Arrays.asList(restTemplate.exchange(applicationParams.jurisdictionCaseTypesDefURL(jurisdictionId), HttpMethod.GET, requestEntity, CaseType[].class).getBody());
        } catch (Exception e) {
            LOG.warn("Error while retrieving base type", e);
            if (e instanceof HttpClientErrorException
                && ((HttpClientErrorException)e).getRawStatusCode() == RESOURCE_NOT_FOUND) {
                throw new ResourceNotFoundException("Resource not found when getting case types for Jurisdiction:" + jurisdictionId + " because of " + e.getMessage());
            } else {
                throw new ServiceException("Problem getting case types for the Jurisdiction:" + jurisdictionId + " because of " + e.getMessage());
            }
        }
    }

    @Override
    public CaseType getCaseType(final String caseTypeId) {
        LOG.debug("retrieving case type definition for case type:" + caseTypeId);
        try {
            final HttpEntity requestEntity = new HttpEntity<CaseType>(securityUtils.authorizationHeaders());
            return restTemplate.exchange(applicationParams.caseTypeDefURL(caseTypeId), HttpMethod.GET, requestEntity, CaseType.class).getBody();

        } catch (Exception e) {
            LOG.warn("Error while retrieving case type", e);
            if (e instanceof HttpClientErrorException
                && ((HttpClientErrorException)e).getRawStatusCode() == RESOURCE_NOT_FOUND) {
                throw new ResourceNotFoundException("Resource not found when getting case type definition for " + caseTypeId + " because of " + e.getMessage());
            } else {
                throw new ServiceException("Problem getting case type definition for " + caseTypeId + " because of " + e.getMessage());
            }
        }
    }

    @Override
    public List<FieldType> getBaseTypes() {
        try {
            final HttpEntity requestEntity = new HttpEntity<CaseType>(securityUtils.authorizationHeaders());
            return Arrays.asList(restTemplate.exchange(applicationParams.baseTypesURL(), HttpMethod.GET, requestEntity, FieldType[].class).getBody());
        } catch (Exception e) {
            LOG.warn("Error while retrieving base types", e);
            if (e instanceof HttpClientErrorException
                && ((HttpClientErrorException)e).getRawStatusCode() == RESOURCE_NOT_FOUND) {
                throw new ResourceNotFoundException("Problem getting base types definition from definition store because of " + e.getMessage());
            } else {
                throw new ServiceException("Problem getting base types definition from definition store because of " + e.getMessage());
            }
        }
    }

    @Override
    public UserRole getUserRoleClassifications(String userRole) {
        try {
            final HttpEntity requestEntity = new HttpEntity<CaseType>(securityUtils.authorizationHeaders());
            final Map<String, String> queryParams = new HashMap<>();
            queryParams.put("userRole", encodeBase64(userRole));
            return restTemplate.exchange(applicationParams.userRoleClassification(), HttpMethod.GET, requestEntity, UserRole.class, queryParams).getBody();
        } catch (Exception e) {
            if (e instanceof HttpClientErrorException
                && ((HttpClientErrorException)e).getRawStatusCode() == RESOURCE_NOT_FOUND) {
                LOG.debug("No classification found for user role " + userRole + " because of ", e);
                return null;
            } else {
                LOG.warn("Error while retrieving classification for user role " + userRole + " because of ", e);
                throw new ServiceException("Error while retrieving classification for user role " + userRole + " because of " + e.getMessage());
            }
        }
    }

    @Override
    public CaseTypeVersionInformation getLatestVersion(String caseTypeId) {
        try {
            final HttpEntity requestEntity = new HttpEntity<CaseType>(securityUtils.authorizationHeaders());
//            System.out.println("returning url:" + applicationParams.caseTypeLatestVersionUrl(caseTypeId));
            CaseTypeVersionInformation version = restTemplate.exchange(applicationParams.caseTypeLatestVersionUrl
                    (caseTypeId), HttpMethod.GET, requestEntity, CaseTypeVersionInformation.class).getBody();
            LOG.debug("retrieved latest version for case type: " + caseTypeId + ": " + version);
            return version;

        } catch (Exception e) {
            LOG.warn("Error while retrieving case type version", e);
            if (e instanceof HttpClientErrorException
                    && ((HttpClientErrorException)e).getRawStatusCode() == RESOURCE_NOT_FOUND) {
                throw new ResourceNotFoundException("Resource not found when getting case type version for " + caseTypeId + " because of " + e.getMessage());
            } else {
                throw new ServiceException("Problem getting case type version for " + caseTypeId + " because of " + e.getMessage());
            }
        }
    }

    @Override
    @Cacheable("caseTypeDefinitions")
    public CaseType getCaseType(int latestVersion, String caseTypeId) {
        return this.getCaseType(caseTypeId);
    }

}