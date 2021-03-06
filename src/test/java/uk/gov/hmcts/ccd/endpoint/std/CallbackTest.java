package uk.gov.hmcts.ccd.endpoint.std;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.github.tomakehurst.wiremock.junit.WireMockClassRule;
import org.hamcrest.Matchers;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Ignore;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import uk.gov.hmcts.ccd.MockUtils;
import uk.gov.hmcts.ccd.WireMockBaseTest;
import uk.gov.hmcts.ccd.data.casedetails.SecurityClassification;
import uk.gov.hmcts.ccd.domain.model.callbacks.CallbackResponse;
import uk.gov.hmcts.ccd.domain.model.callbacks.StartEventTrigger;
import uk.gov.hmcts.ccd.domain.model.definition.CaseDetails;
import uk.gov.hmcts.ccd.domain.model.definition.CaseType;
import uk.gov.hmcts.ccd.domain.model.definition.Jurisdiction;
import uk.gov.hmcts.ccd.domain.model.std.AuditEvent;
import uk.gov.hmcts.ccd.domain.model.std.CaseDataContent;
import uk.gov.hmcts.ccd.domain.model.std.Event;
import uk.gov.hmcts.ccd.endpoint.CallbackTestData;

import javax.inject.Inject;
import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.hamcrest.collection.IsIn.isIn;
import static org.hamcrest.core.Every.everyItem;
import static org.junit.Assert.*;
import static org.mockito.Mockito.doReturn;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

public class CallbackTest extends WireMockBaseTest {

    private static final int WIREMOCK_PORT = 10000;
    private  static final String URL_BEFORE_COMMIT = "/before-commit.*";

    @ClassRule
    public static WireMockClassRule DM_API_RULE = new WireMockClassRule(new WireMockConfiguration().port(WIREMOCK_PORT).notifier(slf4jNotifier));

    private final JsonNode DATA = mapper.readTree(
        "{\n" +
        "  \"PersonFirstName\": \"ccd-First Name\",\n" +
        "  \"PersonLastName\": \"Last Name\",\n" +
        "  \"PersonAddress\": {\n" +
        "    \"AddressLine1\": \"Address Line 1\",\n" +
        "    \"AddressLine2\": \"Address Line 2\"\n" +
        "  }\n" +
        "}\n"
    );

    private final String MODIFIED_DATA_STRING = "{\n" +
        "  \"PersonFirstName\": \"ccd-First Name\",\n" +
        "  \"PersonLastName\": \"Last Name\",\n" +
        "  \"PersonAddress\": {\n" +
        "    \"AddressLine1\": \"Address Line 11\",\n" +
        "    \"AddressLine2\": \"Address Line 12\"\n" +
        "  },\n" +
        "  \"D8Document\":{" +
        "    \"document_url\": \"http://localhost:%s/documents/05e7cd7e-7041-4d8a-826a-7bb49dfd83d0\"" +
        "  }\n" +
        "}\n";

    private JsonNode MODIFIED_DATA = null;

    private final String EXPECTED_MODIFIED_DATA_AFTER_AUTH_STRING = "{\n" +
        "  \"PersonLastName\": \"Last Name\",\n" +
        "  \"PersonAddress\": {\n" +
        "    \"AddressLine1\": \"Address Line 11\",\n" +
        "    \"AddressLine2\": \"Address Line 12\"\n" +
        "  },\n" +
        "  \"D8Document\":{" +
        "    \"document_url\": \"http://localhost:" + DM_API_RULE.port() + "/documents/05e7cd7e-7041-4d8a-826a-7bb49dfd83d0\",\n" +
        "    \"document_binary_url\": \"http://localhost:%s/documents/05e7cd7e-7041-4d8a-826a-7bb49dfd83d0/binary\",\n" +
        "    \"document_filename\": \"Seagulls_Square.jpg\"" +
        "  }\n" +
        "}\n";

    private JsonNode EXPECTED_SAVED_DATA = null;

    private final String EXPECTED_SAVED_DATA_STRING = "{\n" +
        "  \"PersonFirstName\": \"ccd-First Name\",\n" +
        "  \"PersonLastName\": \"Last Name\",\n" +
        "  \"PersonAddress\": {\n" +
        "    \"AddressLine1\": \"Address Line 11\",\n" +
        "    \"AddressLine2\": \"Address Line 12\"\n" +
        "  },\n" +
        "  \"D8Document\":{" +
        "    \"document_url\": \"http://localhost:" + DM_API_RULE.port() + "/documents/05e7cd7e-7041-4d8a-826a-7bb49dfd83d0\",\n" +
        "    \"document_binary_url\": \"http://localhost:%s/documents/05e7cd7e-7041-4d8a-826a-7bb49dfd83d0/binary\",\n" +
        "    \"document_filename\": \"Seagulls_Square.jpg\"" +
        "  }\n" +
        "}\n";

    private JsonNode EXPECTED_MODIFIED_DATA = null;

    private final String SANITIZED_MODIFIED_DATA_WITH_MISSING_BINARY_LINK_STRING = "{\n" +
        "  \"PersonLastName\": \"Last Name\",\n" +
        "  \"PersonAddress\": {\n" +
        "    \"AddressLine1\": \"Address Line 11\",\n" +
        "    \"AddressLine2\": \"Address Line 12\"\n" +
        "  },\n" +
        "  \"D8Document\":{" +
        "    \"document_url\": \"http://localhost:%s/documents/05e7cd7e-7041-4d8a-826a-7bb49dfd83d1\"\n" +
        "  }\n" +
        "}\n";

    private JsonNode SANITIZED_MODIFIED_DATA_WITH_MISSING_BINARY_LINK = null;

    private final JsonNode INVALID_CALLBACK_DATA = mapper.readTree(
        "{\n" +
            "  \"PersonFirstName\": \"First Name\",\n" +
            "  \"PersonLastName\": \"Last Name\",\n" +
            "  \"PersonAddress\": {\n" +
            "    \"AddressLine1\": \"Address Line 11\",\n" +
            "    \"AddressLine2\": \"Address Line 12\"\n" +
            "  }\n" +
            "}\n"
    );

    private final JsonNode MODIFIED_CORRUPTED_DATA = mapper.readTree(
        "{\n" +
        "  \"adsdsassdasad\": \"First Name\",\n" +
        "  \"PersonLastName\": \"Last Name\",\n" +
        "  \"PersonAddress\": {\n" +
        "    \"AddressLine1\": \"Address Line 11\",\n" +
        "    \"AddressLine2\": \"Address Line 12\"\n" +
        "  }\n" +
        "}\n"
    );

    @Inject
    private WebApplicationContext wac;
    private MockMvc mockMvc;
    private JdbcTemplate jdbcTemplate;

    @Mock
    private Authentication authentication;

    @Mock
    private SecurityContext securityContext;

    private static final String CREATE_CASE_EVENT_ID = "CREATE-CASE";
    private static final String UPDATE_EVENT_ID = "UPDATE-EVENT";
    private static final String UPDATE_EVENT_TRIGGER_ID = UPDATE_EVENT_ID;
    private static final String CREATE_CASE_EVENT_TRIGGER_ID = CREATE_CASE_EVENT_ID;
    private static final String CASE_TYPE_ID = "CallbackCase";
    private static final String JURISDICTION_ID = "TEST";
    private static final Integer USER_ID = 123;
    private static final Long CASE_REFERENCE = 1504259907353545L;
    private static final Long INVALID_REFERENCE = 1504259907L;

    public CallbackTest() throws IOException {
    }

    @Before
    public void setUp() throws IOException {
        MockitoAnnotations.initMocks(this);

        doReturn(authentication).when(securityContext).getAuthentication();
        SecurityContextHolder.setContext(securityContext);

        MockUtils.setSecurityAuthorities(authentication, MockUtils.ROLE_TEST_PUBLIC);

        mockMvc = MockMvcBuilders.webAppContextSetup(wac).build();
        jdbcTemplate = new JdbcTemplate(db);
        wireMockRule.stubFor(get(urlMatching("/api/data/case-type/CallbackCase"))
            .willReturn(okJson(CallbackTestData.getTestDefinition(wireMockRule.port())).withStatus(200)));
        MODIFIED_DATA = mapper.readTree(String.format(MODIFIED_DATA_STRING, DM_API_RULE.port()));
        EXPECTED_MODIFIED_DATA = mapper.readTree(String.format(EXPECTED_MODIFIED_DATA_AFTER_AUTH_STRING, DM_API_RULE.port()));
        EXPECTED_SAVED_DATA = mapper.readTree(String.format(EXPECTED_SAVED_DATA_STRING, DM_API_RULE.port()));
        SANITIZED_MODIFIED_DATA_WITH_MISSING_BINARY_LINK = mapper.readTree(String.format(SANITIZED_MODIFIED_DATA_WITH_MISSING_BINARY_LINK_STRING, DM_API_RULE.port()));
    }

    @Test
    public void shouldReturn201WhenPostCreateCaseWithModifiedDataForCaseworker() throws Exception {
        final String URL = String.format("/caseworkers/%d/jurisdictions/%s/case-types/%s/cases", USER_ID, JURISDICTION_ID, CASE_TYPE_ID);
        final CaseDataContent caseDetailsToSave = new CaseDataContent();
        caseDetailsToSave.setEvent(new Event());
        caseDetailsToSave.getEvent().setEventId(CREATE_CASE_EVENT_ID);
        caseDetailsToSave.setData(mapper.convertValue(DATA, STRING_NODE_TYPE));
        caseDetailsToSave.setToken(generateEventTokenNewCase(USER_ID, JURISDICTION_ID, CASE_TYPE_ID, CREATE_CASE_EVENT_ID));

        final CallbackResponse callbackResponse = new CallbackResponse();
        callbackResponse.setData(mapper.convertValue(MODIFIED_DATA, STRING_NODE_TYPE));

        wireMockRule.stubFor(WireMock.post(urlMatching("/before-commit.*"))
            .willReturn(okJson(mapper.writeValueAsString(callbackResponse)).withStatus(200)));

        wireMockRule.stubFor(WireMock.post(urlMatching("/after-commit.*"))
            .willReturn(ok()));

        final MvcResult mvcResult = mockMvc.perform(post(URL)
            .contentType(JSON_CONTENT_TYPE)
            .content(mapper.writeValueAsBytes(caseDetailsToSave))
        ).andReturn();

        assertEquals(mvcResult.getResponse().getContentAsString(), 201, mvcResult.getResponse().getStatus());
        Map expectedSanitizedData = mapper.readValue(EXPECTED_MODIFIED_DATA.toString(), Map.class);
        Map actualData = mapper.readValue(mapper.readTree(mvcResult.getResponse().getContentAsString()).get("case_data").toString(), Map.class);
        assertThat( "Incorrect Response Content", actualData.entrySet(), everyItem(isIn(expectedSanitizedData.entrySet())));

        final List<CaseDetails> caseDetailsList = jdbcTemplate.query("SELECT * FROM case_data", this::mapCaseData);
        assertEquals("Incorrect number of cases", 1, caseDetailsList.size());

        final CaseDetails savedCaseDetails = caseDetailsList.get(0);
        assertEquals("Incorrect Case Type", CASE_TYPE_ID, savedCaseDetails.getCaseTypeId());
        Map sanitizedData = mapper.convertValue(EXPECTED_SAVED_DATA, new TypeReference<HashMap<String, JsonNode>>() {
        });
        assertThat("Incorrect Data content", savedCaseDetails.getData().entrySet(), everyItem(isIn(sanitizedData.entrySet())));
        assertEquals("CaseCreated", savedCaseDetails.getState());
        assertThat(savedCaseDetails.getSecurityClassification(), Matchers.equalTo(SecurityClassification.PUBLIC));

        final List<AuditEvent> caseAuditEventList = jdbcTemplate.query("SELECT * FROM case_event", this::mapAuditEvent);
        assertEquals("Incorrect number of case events", 1, caseAuditEventList.size());

        // Assertion belows are for creation event
        final AuditEvent caseAuditEvent = caseAuditEventList.get(0);
        assertEquals(USER_ID.intValue(), caseAuditEvent.getUserId().intValue());
        assertEquals("Strife", caseAuditEvent.getUserLastName());
        assertEquals("Cloud", caseAuditEvent.getUserFirstName());
        assertEquals(CREATE_CASE_EVENT_ID, caseAuditEvent.getEventId());
        assertEquals(savedCaseDetails.getId(), caseAuditEvent.getCaseDataId());
        assertEquals(savedCaseDetails.getCaseTypeId(), caseAuditEvent.getCaseTypeId());
        assertEquals(1, caseAuditEvent.getCaseTypeVersion().intValue());
        assertEquals(savedCaseDetails.getState(), caseAuditEvent.getStateId());
        assertEquals(savedCaseDetails.getCreatedDate(), caseAuditEvent.getCreatedDate());
        assertEquals(savedCaseDetails.getData(), caseAuditEvent.getData());
        assertThat(caseAuditEvent.getSecurityClassification(), Matchers.equalTo(SecurityClassification.PUBLIC));
    }

    @Test
    public void shouldReturn201WhenPostCreateCaseForCitizenWithModifiedData() throws Exception {
        final String URL = String.format("/citizens/%d/jurisdictions/%s/case-types/%s/cases", USER_ID, JURISDICTION_ID, CASE_TYPE_ID);
        final CaseDataContent caseDetailsToSave = new CaseDataContent();
        caseDetailsToSave.setEvent(new Event());
        caseDetailsToSave.getEvent().setEventId(CREATE_CASE_EVENT_ID);
        caseDetailsToSave.setData(mapper.convertValue(DATA, STRING_NODE_TYPE));
        caseDetailsToSave.setToken(generateEventTokenNewCase(USER_ID, JURISDICTION_ID, CASE_TYPE_ID, CREATE_CASE_EVENT_ID));

        final CallbackResponse callbackResponse = new CallbackResponse();
        callbackResponse.setData(mapper.convertValue(MODIFIED_DATA, STRING_NODE_TYPE));

        wireMockRule.stubFor(WireMock.post(urlMatching("/before-commit.*"))
            .willReturn(okJson(mapper.writeValueAsString(callbackResponse)).withStatus(200)));

        wireMockRule.stubFor(WireMock.post(urlMatching("/after-commit.*"))
            .willReturn(ok()));

        final MvcResult mvcResult = mockMvc.perform(post(URL)
            .contentType(JSON_CONTENT_TYPE)
            .content(mapper.writeValueAsBytes(caseDetailsToSave))
        ).andReturn();

        assertEquals(mvcResult.getResponse().getContentAsString(), 201, mvcResult.getResponse().getStatus());
        Map expectedSanitizedData = mapper.readValue(EXPECTED_MODIFIED_DATA.toString(), Map.class);
        Map actualData = mapper.readValue(mapper.readTree(mvcResult.getResponse().getContentAsString()).get("case_data").toString(), Map.class);
        assertThat( "Incorrect Response Content", actualData.entrySet(), everyItem(isIn(expectedSanitizedData.entrySet())));

        final List<CaseDetails> caseDetailsList = jdbcTemplate.query("SELECT * FROM case_data", this::mapCaseData);
        assertEquals("Incorrect number of cases", 1, caseDetailsList.size());

        final CaseDetails savedCaseDetails = caseDetailsList.get(0);
        assertEquals("Incorrect Case Type", CASE_TYPE_ID, savedCaseDetails.getCaseTypeId());
        Map sanitizedData = mapper.convertValue(EXPECTED_SAVED_DATA, new TypeReference<HashMap<String, JsonNode>>() {
        });
        assertThat("Incorrect Data content", savedCaseDetails.getData().entrySet(), everyItem(isIn(sanitizedData.entrySet())));

        assertEquals("CaseCreated", savedCaseDetails.getState());

        final List<AuditEvent> caseAuditEventList = jdbcTemplate.query("SELECT * FROM case_event", this::mapAuditEvent);
        assertEquals("Incorrect number of case events", 1, caseAuditEventList.size());

        // Assertion belows are for creation event
        final AuditEvent caseAuditEvent = caseAuditEventList.get(0);
        assertEquals(USER_ID.intValue(), caseAuditEvent.getUserId().intValue());
        assertEquals("Strife", caseAuditEvent.getUserLastName());
        assertEquals("Cloud", caseAuditEvent.getUserFirstName());
        assertEquals(CREATE_CASE_EVENT_ID, caseAuditEvent.getEventId());
        assertEquals(savedCaseDetails.getId(), caseAuditEvent.getCaseDataId());
        assertEquals(savedCaseDetails.getCaseTypeId(), caseAuditEvent.getCaseTypeId());
        assertEquals(1, caseAuditEvent.getCaseTypeVersion().intValue());
        assertEquals(savedCaseDetails.getState(), caseAuditEvent.getStateId());
        assertEquals(savedCaseDetails.getCreatedDate(), caseAuditEvent.getCreatedDate());
        assertEquals(savedCaseDetails.getData(), caseAuditEvent.getData());
        assertThat(caseAuditEvent.getSecurityClassification(), Matchers.equalTo(SecurityClassification.PUBLIC));
    }

    @Test
    public void shouldReturn422WhenPostCreateCaseWithInvalidModifiedDataFromBeforeCommitForCaseworker() throws Exception {
        final String URL = String.format("/caseworkers/%d/jurisdictions/%s/case-types/%s/cases", USER_ID, JURISDICTION_ID, CASE_TYPE_ID);
        final CaseDataContent caseDetailsToSave = new CaseDataContent();
        caseDetailsToSave.setEvent(new Event());
        caseDetailsToSave.getEvent().setEventId(CREATE_CASE_EVENT_ID);
        caseDetailsToSave.setData(mapper.convertValue(DATA, STRING_NODE_TYPE));
        caseDetailsToSave.setToken(generateEventTokenNewCase(USER_ID, JURISDICTION_ID, CASE_TYPE_ID, CREATE_CASE_EVENT_ID));

        final CallbackResponse callbackResponse = new CallbackResponse();
        callbackResponse.setData(mapper.convertValue(MODIFIED_CORRUPTED_DATA, STRING_NODE_TYPE));

        wireMockRule.stubFor(WireMock.post(urlMatching("/before-commit.*"))
            .willReturn(okJson(mapper.writeValueAsString(callbackResponse)).withStatus(200)));

        wireMockRule.stubFor(WireMock.post(urlMatching("/after-commit.*"))
            .willReturn(ok()));

        final MvcResult mvcResult = mockMvc.perform(post(URL)
            .contentType(JSON_CONTENT_TYPE)
            .content(mapper.writeValueAsBytes(caseDetailsToSave))
        ).andReturn();

        assertEquals("Invalid modified content were not caught by validators", 422, mvcResult.getResponse().getStatus());
    }

    @Test
    public void shouldReturn422WhenPostCreateCaseWithInvalidModifiedDataFromBeforeCommitForCitizen() throws Exception {
        final String URL = String.format("/citizens/%d/jurisdictions/%s/case-types/%s/cases", USER_ID, JURISDICTION_ID, CASE_TYPE_ID);
        final CaseDataContent caseDetailsToSave = new CaseDataContent();
        caseDetailsToSave.setEvent(new Event());
        caseDetailsToSave.getEvent().setEventId(CREATE_CASE_EVENT_ID);
        caseDetailsToSave.setData(mapper.convertValue(DATA, STRING_NODE_TYPE));
        caseDetailsToSave.setToken(generateEventTokenNewCase(USER_ID, JURISDICTION_ID, CASE_TYPE_ID, CREATE_CASE_EVENT_ID));

        final CallbackResponse callbackResponse = new CallbackResponse();
        callbackResponse.setData(mapper.convertValue(MODIFIED_CORRUPTED_DATA, STRING_NODE_TYPE));

        wireMockRule.stubFor(WireMock.post(urlMatching("/before-commit.*"))
            .willReturn(okJson(mapper.writeValueAsString(callbackResponse)).withStatus(200)));


        final MvcResult mvcResult = mockMvc.perform(post(URL)
            .contentType(JSON_CONTENT_TYPE)
            .content(mapper.writeValueAsBytes(caseDetailsToSave))
        ).andReturn();

        assertEquals("Invalid modified content were not caught by validators", 422, mvcResult.getResponse().getStatus());
    }
    @Test
    public void shouldReturn422WhenPostCreateCaseWithInvalidModifiedMissingDocumentDataFromBeforeCommitForCaseworker() throws Exception {
        final String URL = String.format("/caseworkers/%d/jurisdictions/%s/case-types/%s/cases", USER_ID, JURISDICTION_ID, CASE_TYPE_ID);
        final CaseDataContent caseDetailsToSave = new CaseDataContent();
        caseDetailsToSave.setEvent(new Event());
        caseDetailsToSave.getEvent().setEventId(CREATE_CASE_EVENT_ID);
        caseDetailsToSave.setData(mapper.convertValue(DATA, STRING_NODE_TYPE));
        caseDetailsToSave.setToken(generateEventTokenNewCase(USER_ID, JURISDICTION_ID, CASE_TYPE_ID, CREATE_CASE_EVENT_ID));

        final CallbackResponse callbackResponse = new CallbackResponse();
        callbackResponse.setData(mapper.convertValue(SANITIZED_MODIFIED_DATA_WITH_MISSING_BINARY_LINK, STRING_NODE_TYPE));

        wireMockRule.stubFor(WireMock.post(urlMatching("/before-commit.*"))
            .willReturn(okJson(mapper.writeValueAsString(callbackResponse)).withStatus(200)));

        wireMockRule.stubFor(WireMock.post(urlMatching("/after-commit.*"))
            .willReturn(ok()));

        final MvcResult mvcResult = mockMvc.perform(post(URL)
            .contentType(JSON_CONTENT_TYPE)
            .content(mapper.writeValueAsBytes(caseDetailsToSave))
        ).andReturn();

        assertEquals("Invalid modified content were not caught by validators", 422, mvcResult.getResponse().getStatus());
    }

    @Test
    public void shouldReturn422WhenPostCreateCaseWithInvalidModifiedMissingDocumentDataFromBeforeCommitForCitizen() throws Exception {
        final String URL = String.format("/citizens/%d/jurisdictions/%s/case-types/%s/cases", USER_ID, JURISDICTION_ID, CASE_TYPE_ID);
        final CaseDataContent caseDetailsToSave = new CaseDataContent();
        caseDetailsToSave.setEvent(new Event());
        caseDetailsToSave.getEvent().setEventId(CREATE_CASE_EVENT_ID);
        caseDetailsToSave.setData(mapper.convertValue(DATA, STRING_NODE_TYPE));
        caseDetailsToSave.setToken(generateEventTokenNewCase(USER_ID, JURISDICTION_ID, CASE_TYPE_ID, CREATE_CASE_EVENT_ID));

        final CallbackResponse callbackResponse = new CallbackResponse();
        callbackResponse.setData(mapper.convertValue(SANITIZED_MODIFIED_DATA_WITH_MISSING_BINARY_LINK, STRING_NODE_TYPE));

        wireMockRule.stubFor(WireMock.post(urlMatching("/before-commit.*"))
            .willReturn(okJson(mapper.writeValueAsString(callbackResponse)).withStatus(200)));


        final MvcResult mvcResult = mockMvc.perform(post(URL)
            .contentType(JSON_CONTENT_TYPE)
            .content(mapper.writeValueAsBytes(caseDetailsToSave))
        ).andReturn();

        assertEquals("Invalid modified content were not caught by validators", 422, mvcResult.getResponse().getStatus());
    }

    @Test
    public void shouldReturn422WhenPostCreateCaseWithErrorsFromBeforeCommitForCaseworker() throws Exception {
        final String URL = String.format("/caseworkers/%d/jurisdictions/%s/case-types/%s/cases", USER_ID, JURISDICTION_ID, CASE_TYPE_ID);
        final CaseDataContent caseDetailsToSave = new CaseDataContent();
        caseDetailsToSave.setEvent(new Event());
        caseDetailsToSave.getEvent().setEventId(CREATE_CASE_EVENT_ID);
        caseDetailsToSave.setData(mapper.convertValue(DATA, STRING_NODE_TYPE));
        caseDetailsToSave.setToken(generateEventTokenNewCase(USER_ID, JURISDICTION_ID, CASE_TYPE_ID, CREATE_CASE_EVENT_ID));

        stubForErrorCallbackResponse(URL_BEFORE_COMMIT);

        final MvcResult mvcResult = mockMvc.perform(post(URL)
            .contentType(JSON_CONTENT_TYPE)
            .content(mapper.writeValueAsBytes(caseDetailsToSave))
        ).andReturn();

        assertEquals("Callback did not catch block", 422, mvcResult.getResponse().getStatus());
    }

    @Test
    public void shouldReturn422WhenPostCreateCaseWithErrorsFromBeforeCommitForCitizen() throws Exception {
        final String URL = String.format("/citizens/%d/jurisdictions/%s/case-types/%s/cases", USER_ID, JURISDICTION_ID, CASE_TYPE_ID);
        final CaseDataContent caseDetailsToSave = new CaseDataContent();
        caseDetailsToSave.setEvent(new Event());
        caseDetailsToSave.getEvent().setEventId(CREATE_CASE_EVENT_ID);
        caseDetailsToSave.setData(mapper.convertValue(DATA, STRING_NODE_TYPE));
        caseDetailsToSave.setToken(generateEventTokenNewCase(USER_ID, JURISDICTION_ID, CASE_TYPE_ID, CREATE_CASE_EVENT_ID));

        stubForErrorCallbackResponse("/before-commit.*");

        final MvcResult mvcResult = mockMvc.perform(post(URL)
            .contentType(JSON_CONTENT_TYPE)
            .content(mapper.writeValueAsBytes(caseDetailsToSave))
        ).andReturn();

        assertEquals("Callback did not catch block", 422, mvcResult.getResponse().getStatus());
    }

    @Test
    @Sql(executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD, scripts = {"classpath:sql/insert_callback_cases.sql"})
    public void shouldReturn422WhenGetEventTokenForCaseWithCallbackErrorsForCaseworker() throws Exception {
        final String URL = String.format("/caseworkers/%d/jurisdictions/%s/case-types/%s/cases/%d/event-triggers/%s/token", USER_ID, JURISDICTION_ID, CASE_TYPE_ID, CASE_REFERENCE, UPDATE_EVENT_TRIGGER_ID);

        final CallbackResponse callbackResponse = new CallbackResponse();
        callbackResponse.setErrors(Collections.singletonList("Just a test"));

        wireMockRule.stubFor(WireMock.post(urlMatching("/before-start.*"))
            .willReturn(okJson(mapper.writeValueAsString(callbackResponse)).withStatus(200)));

        final MvcResult mvcResult = mockMvc
            .perform(MockMvcRequestBuilders.get(URL).contentType(MediaType.APPLICATION_JSON))
            .andReturn();

        assertEquals(mvcResult.getResponse().getContentAsString(), 422, mvcResult.getResponse().getStatus());
    }

    @Test
    @Sql(executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD, scripts = {"classpath:sql/insert_callback_cases.sql"})
    public void shouldReturn422WhenGetEventTokenForCaseWithCallbackErrorsForCitizen() throws Exception {
        final String URL = String.format("/citizens/%d/jurisdictions/%s/case-types/%s/cases/%d/event-triggers/%s/token", USER_ID, JURISDICTION_ID, CASE_TYPE_ID, CASE_REFERENCE, UPDATE_EVENT_TRIGGER_ID);

        final CallbackResponse callbackResponse = new CallbackResponse();
        callbackResponse.setErrors(Collections.singletonList("Just a test"));

        wireMockRule.stubFor(WireMock.post(urlMatching("/before-start.*"))
            .willReturn(okJson(mapper.writeValueAsString(callbackResponse)).withStatus(200)));


        final MvcResult mvcResult = mockMvc
            .perform(MockMvcRequestBuilders.get(URL).contentType(MediaType.APPLICATION_JSON))
            .andReturn();

        assertEquals(mvcResult.getResponse().getContentAsString(), 422, mvcResult.getResponse().getStatus());
    }

    @Test
    @Sql(executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD, scripts = {"classpath:sql/insert_callback_cases.sql"})
    public void shouldReturn200WhenGetEventTokenForCaseWithValidCallbackDataForCaseworker() throws Exception {
        final String URL = String.format("/caseworkers/%d/jurisdictions/%s/case-types/%s/cases/%d/event-triggers/%s/token", USER_ID, JURISDICTION_ID, CASE_TYPE_ID, CASE_REFERENCE, UPDATE_EVENT_TRIGGER_ID);

        final CallbackResponse callbackResponse = new CallbackResponse();
        callbackResponse.setData(mapper.convertValue(MODIFIED_DATA, STRING_NODE_TYPE));

        wireMockRule.stubFor(WireMock.post(urlMatching("/before-start.*"))
            .willReturn(okJson(mapper.writeValueAsString(callbackResponse)).withStatus(200)));

        final MvcResult mvcResult = mockMvc
            .perform(MockMvcRequestBuilders.get(URL).contentType(MediaType.APPLICATION_JSON))
            .andReturn();

        assertEquals(mvcResult.getResponse().getContentAsString(), 200, mvcResult.getResponse().getStatus());

        final StartEventTrigger startEventTrigger = mapper.readValue(mvcResult.getResponse().getContentAsString(), StartEventTrigger.class);
        assertEquals("Incorrect Data content", mapper.convertValue(EXPECTED_MODIFIED_DATA, STRING_NODE_TYPE), startEventTrigger.getCaseDetails().getData());
        assertTrue("No token", !startEventTrigger.getToken().isEmpty());
    }

    @Test
    @Sql(executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD, scripts = {"classpath:sql/insert_callback_cases.sql"})
    public void shouldReturn422WhenGetEventTokenForCaseWithCallbackDataWithValidationErrorsForCaseworker() throws Exception {
        final String URL = String.format("/caseworkers/%d/jurisdictions/%s/case-types/%s/cases/%d/event-triggers/%s/token", USER_ID, JURISDICTION_ID, CASE_TYPE_ID, CASE_REFERENCE, CREATE_CASE_EVENT_TRIGGER_ID);

        final CallbackResponse callbackResponse = new CallbackResponse();
        callbackResponse.setData(mapper.convertValue(INVALID_CALLBACK_DATA, STRING_NODE_TYPE));

        wireMockRule.stubFor(WireMock.post(urlMatching("/before-start.*"))
            .willReturn(okJson(mapper.writeValueAsString(callbackResponse)).withStatus(200)));

        final MvcResult mvcResult = mockMvc
            .perform(MockMvcRequestBuilders.get(URL).contentType(MediaType.APPLICATION_JSON))
            .andReturn();

        assertEquals("Invalid callback data should have caused UNPROCESSABLE_ENTITY response", 422, mvcResult.getResponse().getStatus());
    }

    @Test
    @Sql(executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD, scripts = {"classpath:sql/insert_callback_cases.sql"})
    public void shouldReturn422WhenGetEventTokenForCaseWithCallbackDataWithValidationErrorsForCitizen() throws Exception {
        final String URL = String.format("/citizens/%d/jurisdictions/%s/case-types/%s/cases/%d/event-triggers/%s/token", USER_ID, JURISDICTION_ID, CASE_TYPE_ID, CASE_REFERENCE, CREATE_CASE_EVENT_TRIGGER_ID);

        final CallbackResponse callbackResponse = new CallbackResponse();
        callbackResponse.setData(mapper.convertValue(INVALID_CALLBACK_DATA, STRING_NODE_TYPE));

        wireMockRule.stubFor(WireMock.post(urlMatching("/before-start.*"))
            .willReturn(okJson(mapper.writeValueAsString(callbackResponse)).withStatus(200)));

        final MvcResult mvcResult = mockMvc
            .perform(MockMvcRequestBuilders.get(URL).contentType(MediaType.APPLICATION_JSON))
            .andReturn();

        assertEquals("Invalid callback data should have caused UNPROCESSABLE_ENTITY response", 422, mvcResult.getResponse().getStatus());
    }

    @Test
    @Sql(executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD, scripts = {"classpath:sql/insert_callback_cases.sql"})
    public void shouldReturn400WhenGetEventTokenForCaseWithInvalidCaseReferenceForCaseworker() throws Exception {
        final String URL = String.format("/caseworkers/%d/jurisdictions/%s/case-types/%s/cases/%d/event-triggers/%s/token", USER_ID, JURISDICTION_ID, CASE_TYPE_ID, INVALID_REFERENCE, CREATE_CASE_EVENT_TRIGGER_ID);

        final MvcResult mvcResult = mockMvc
            .perform(MockMvcRequestBuilders.get(URL).contentType(MediaType.APPLICATION_JSON))
            .andReturn();

        assertEquals("Invalid case reference data should have caused BAD_REQUEST response", 400, mvcResult.getResponse().getStatus());
    }

    @Test
    @Sql(executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD, scripts = {"classpath:sql/insert_callback_cases.sql"})
    public void shouldReturn400WhenGetEventTokenForCaseWithInvalidCaseReferenceForCitizen() throws Exception {
        final String URL = String.format("/citizens/%d/jurisdictions/%s/case-types/%s/cases/%d/event-triggers/%s/token", USER_ID, JURISDICTION_ID, CASE_TYPE_ID, INVALID_REFERENCE, CREATE_CASE_EVENT_TRIGGER_ID);

        final MvcResult mvcResult = mockMvc
            .perform(MockMvcRequestBuilders.get(URL).contentType(MediaType.APPLICATION_JSON))
            .andReturn();

        assertEquals("Invalid case reference data should have caused BAD_REQUEST response", 400, mvcResult.getResponse().getStatus());
    }

    @Test
    @Sql(executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD, scripts = {"classpath:sql/insert_callback_cases.sql"})
    public void shouldReturn200WhenGetEventTokenForCaseWithValidCallbackDataForCitizen() throws Exception {
        final String URL = String.format("/citizens/%d/jurisdictions/%s/case-types/%s/cases/%d/event-triggers/%s/token", USER_ID, JURISDICTION_ID, CASE_TYPE_ID, CASE_REFERENCE, UPDATE_EVENT_TRIGGER_ID);

        final CallbackResponse callbackResponse = new CallbackResponse();
        callbackResponse.setData(mapper.convertValue(MODIFIED_DATA, STRING_NODE_TYPE));

        wireMockRule.stubFor(WireMock.post(urlMatching("/before-start.*"))
            .willReturn(okJson(mapper.writeValueAsString(callbackResponse)).withStatus(200)));


        final MvcResult mvcResult = mockMvc
            .perform(MockMvcRequestBuilders.get(URL).contentType(MediaType.APPLICATION_JSON))
            .andReturn();

        assertEquals(mvcResult.getResponse().getContentAsString(), 200, mvcResult.getResponse().getStatus());

        final StartEventTrigger startEventTrigger = mapper.readValue(mvcResult.getResponse().getContentAsString(), StartEventTrigger.class);
        assertEquals("Incorrect Data content", mapper.convertValue(EXPECTED_MODIFIED_DATA, STRING_NODE_TYPE), startEventTrigger.getCaseDetails().getData());
        assertTrue("No token", !startEventTrigger.getToken().isEmpty());
    }

    @Test
    @Sql(executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD, scripts = {"classpath:sql/insert_callback_cases.sql"})
    public void shouldReturn422WhenGetEventTokenForCaseTypeWithCallbackErrorsForCaseworker() throws Exception {
        final String URL = String.format("/caseworkers/%d/jurisdictions/%s/case-types/%s/event-triggers/%s/token", USER_ID, JURISDICTION_ID, CASE_TYPE_ID, UPDATE_EVENT_TRIGGER_ID);

        final CallbackResponse callbackResponse = new CallbackResponse();
        callbackResponse.setErrors(Collections.singletonList("Just a test"));

        wireMockRule.stubFor(WireMock.post(urlMatching("/before-start.*"))
            .willReturn(okJson(mapper.writeValueAsString(callbackResponse)).withStatus(200)));

        final MvcResult mvcResult = mockMvc
            .perform(MockMvcRequestBuilders.get(URL).contentType(MediaType.APPLICATION_JSON))
            .andReturn();

        assertEquals(mvcResult.getResponse().getContentAsString(), 422, mvcResult.getResponse().getStatus());
    }

    @Test
    @Sql(executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD, scripts = {"classpath:sql/insert_callback_cases.sql"})
    public void shouldReturn422WhenGetEventTokenForCaseTypeWithCallbackErrorsForCitizen() throws Exception {
        final String URL = String.format("/citizens/%d/jurisdictions/%s/case-types/%s/event-triggers/%s/token", USER_ID, JURISDICTION_ID, CASE_TYPE_ID, UPDATE_EVENT_TRIGGER_ID);

        final CallbackResponse callbackResponse = new CallbackResponse();
        callbackResponse.setErrors(Collections.singletonList("Just a test"));

        wireMockRule.stubFor(WireMock.post(urlMatching("/before-start.*"))
            .willReturn(okJson(mapper.writeValueAsString(callbackResponse)).withStatus(200)));

        final MvcResult mvcResult = mockMvc
            .perform(MockMvcRequestBuilders.get(URL).contentType(MediaType.APPLICATION_JSON))
            .andReturn();

        assertEquals(mvcResult.getResponse().getContentAsString(), 422, mvcResult.getResponse().getStatus());
    }

    @Test
    @Sql(executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD, scripts = {"classpath:sql/insert_callback_cases.sql"})
    public void shouldReturn200WhenGetEventTokenForCaseTypeWithValidModifiedDataForCaseworker() throws Exception {
        final String URL = String.format("/caseworkers/%d/jurisdictions/%s/case-types/%s/event-triggers/%s/token", USER_ID, JURISDICTION_ID, CASE_TYPE_ID, CREATE_CASE_EVENT_TRIGGER_ID);

        final CallbackResponse callbackResponse = new CallbackResponse();
        callbackResponse.setData(mapper.convertValue(MODIFIED_DATA, STRING_NODE_TYPE));

        wireMockRule.stubFor(WireMock.post(urlMatching("/before-start.*"))
            .willReturn(okJson(mapper.writeValueAsString(callbackResponse)).withStatus(200)));

        final MvcResult mvcResult = mockMvc
            .perform(MockMvcRequestBuilders.get(URL).contentType(MediaType.APPLICATION_JSON))
            .andReturn();

        assertEquals(mvcResult.getResponse().getContentAsString(), 200, mvcResult.getResponse().getStatus());

        final StartEventTrigger startEventTrigger = mapper.readValue(mvcResult.getResponse().getContentAsString(), StartEventTrigger.class);
        assertEquals("Incorrect Data content", mapper.convertValue(EXPECTED_MODIFIED_DATA, STRING_NODE_TYPE), startEventTrigger.getCaseDetails().getData());
        assertTrue("No token", !startEventTrigger.getToken().isEmpty());
    }

    @Test
    @Sql(executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD, scripts = {"classpath:sql/insert_callback_cases.sql"})
    public void shouldReturn200WhenGetEventTokenForCaseTypeWithValidModifiedDataForCitizen() throws Exception {
        final String URL = String.format("/citizens/%d/jurisdictions/%s/case-types/%s/event-triggers/%s/token", USER_ID, JURISDICTION_ID, CASE_TYPE_ID, CREATE_CASE_EVENT_TRIGGER_ID);

        final CallbackResponse callbackResponse = new CallbackResponse();
        callbackResponse.setData(mapper.convertValue(MODIFIED_DATA, STRING_NODE_TYPE));

        wireMockRule.stubFor(WireMock.post(urlMatching("/before-start.*"))
            .willReturn(okJson(mapper.writeValueAsString(callbackResponse)).withStatus(200)));

        final MvcResult mvcResult = mockMvc
            .perform(MockMvcRequestBuilders.get(URL).contentType(MediaType.APPLICATION_JSON))
            .andReturn();

        assertEquals(mvcResult.getResponse().getContentAsString(), 200, mvcResult.getResponse().getStatus());

        final StartEventTrigger startEventTrigger = mapper.readValue(mvcResult.getResponse().getContentAsString(), StartEventTrigger.class);
        assertEquals("Incorrect Data content", mapper.convertValue(EXPECTED_MODIFIED_DATA, STRING_NODE_TYPE), startEventTrigger.getCaseDetails().getData());
        assertTrue("No token", !startEventTrigger.getToken().isEmpty());
    }

    @Test
    @Sql(executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD, scripts = {"classpath:sql/insert_callback_cases.sql"})
    public void shouldReturn422WhenGetEventTokenForCaseTypeWithCallbackDataWithValidationErrorsForCaseworker() throws Exception {
        final String URL = String.format("/caseworkers/%d/jurisdictions/%s/case-types/%s/event-triggers/%s/token", USER_ID, JURISDICTION_ID, CASE_TYPE_ID, CREATE_CASE_EVENT_TRIGGER_ID);

        final CallbackResponse callbackResponse = new CallbackResponse();
        callbackResponse.setData(mapper.convertValue(INVALID_CALLBACK_DATA, STRING_NODE_TYPE));

        wireMockRule.stubFor(WireMock.post(urlMatching("/before-start.*"))
            .willReturn(okJson(mapper.writeValueAsString(callbackResponse)).withStatus(200)));

        final MvcResult mvcResult = mockMvc
            .perform(MockMvcRequestBuilders.get(URL).contentType(MediaType.APPLICATION_JSON))
            .andReturn();

        assertEquals("Invalid callback data should have caused UNPROCESSABLE_ENTITY response", 422, mvcResult.getResponse().getStatus());
    }

    @Test
    @Sql(executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD, scripts = {"classpath:sql/insert_callback_cases.sql"})
    public void shouldReturn422WhenGetEventTokenForCaseTypeWithCallbackDataWithValidationErrorsForCitizen() throws Exception {
        final String URL = String.format("/citizens/%d/jurisdictions/%s/case-types/%s/event-triggers/%s/token", USER_ID, JURISDICTION_ID, CASE_TYPE_ID, CREATE_CASE_EVENT_TRIGGER_ID);

        final CallbackResponse callbackResponse = new CallbackResponse();
        callbackResponse.setData(mapper.convertValue(INVALID_CALLBACK_DATA, STRING_NODE_TYPE));

        wireMockRule.stubFor(WireMock.post(urlMatching("/before-start.*"))
            .willReturn(okJson(mapper.writeValueAsString(callbackResponse)).withStatus(200)));

        final MvcResult mvcResult = mockMvc
            .perform(MockMvcRequestBuilders.get(URL).contentType(MediaType.APPLICATION_JSON))
            .andReturn();

        assertEquals("Invalid callback data should have caused UNPROCESSABLE_ENTITY response", 422, mvcResult.getResponse().getStatus());
    }

    @Test
    @Sql(executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD, scripts = {"classpath:sql/insert_callback_cases.sql"})
    public void shouldReturn201WhenPostCreateEventWithValidDataForCaseworker() throws Exception {
        final String SUMMARY = "Case event summary";
        final String DESCRIPTION = "Case event description";
        final String URL = String.format("/caseworkers/%d/jurisdictions/%s/case-types/%s/cases/%d/events", USER_ID, JURISDICTION_ID, CASE_TYPE_ID, CASE_REFERENCE);

        final CaseDataContent caseDetailsToSave = new CaseDataContent();
        final Event event = new Event();
        event.setEventId(UPDATE_EVENT_ID);
        event.setSummary(SUMMARY);
        event.setDescription(DESCRIPTION);

        final String token = generateEventToken(jdbcTemplate, USER_ID, JURISDICTION_ID, CASE_TYPE_ID, CASE_REFERENCE, UPDATE_EVENT_ID);
        caseDetailsToSave.setToken(token);
        caseDetailsToSave.setEvent(event);
        caseDetailsToSave.setData(mapper.convertValue(DATA, STRING_NODE_TYPE));

        final CallbackResponse callbackResponse = new CallbackResponse();
        callbackResponse.setData(mapper.convertValue(MODIFIED_DATA, STRING_NODE_TYPE));

        wireMockRule.stubFor(WireMock.post(urlMatching("/before-commit.*"))
            .willReturn(okJson(mapper.writeValueAsString(callbackResponse)).withStatus(200)));

        wireMockRule.stubFor(WireMock.post(urlMatching("/after-commit.*"))
            .willReturn(ok()));

        final MvcResult mvcResult = mockMvc.perform(post(URL)
            .contentType(JSON_CONTENT_TYPE)
            .content(mapper.writeValueAsBytes(caseDetailsToSave))
        ).andReturn();

        assertEquals(mvcResult.getResponse().getContentAsString(), 201, mvcResult.getResponse().getStatus());
        Map expectedSanitizedData = mapper.readValue(EXPECTED_MODIFIED_DATA.toString(), Map.class);
        Map actualData = mapper.readValue(mapper.readTree(mvcResult.getResponse().getContentAsString()).get("case_data").toString(), Map.class);
        assertThat( "Incorrect Response Content", actualData.entrySet(), everyItem(isIn(expectedSanitizedData.entrySet())));

        final List<CaseDetails> caseDetailsList = jdbcTemplate.query("SELECT * FROM case_data", this::mapCaseData);
        assertEquals("Incorrect number of cases", 1, caseDetailsList.size());

        final CaseDetails savedCaseDetails = caseDetailsList.get(0);
        assertEquals("Incorrect Case Type", CASE_TYPE_ID, savedCaseDetails.getCaseTypeId());
        Map sanitizedData = mapper.convertValue(EXPECTED_SAVED_DATA, new TypeReference<HashMap<String, JsonNode>>() {
        });
        assertThat("Incorrect Data content", savedCaseDetails.getData().entrySet(), everyItem(isIn(sanitizedData.entrySet())));
        assertEquals("CaseUpdated", savedCaseDetails.getState());

        final List<AuditEvent> caseAuditEventList = jdbcTemplate.query("SELECT * FROM case_event", this::mapAuditEvent);
        assertEquals("Incorrect number of case events", 1, caseAuditEventList.size());

        // Assertion belows are for creation event
        final AuditEvent caseAuditEvent = caseAuditEventList.get(0);
        assertEquals(USER_ID.intValue(), caseAuditEvent.getUserId().intValue());
        assertEquals("Strife", caseAuditEvent.getUserLastName());
        assertEquals("Cloud", caseAuditEvent.getUserFirstName());
        assertEquals(UPDATE_EVENT_ID, caseAuditEvent.getEventId());
        assertEquals(savedCaseDetails.getId(), caseAuditEvent.getCaseDataId());
        assertEquals(savedCaseDetails.getCaseTypeId(), caseAuditEvent.getCaseTypeId());
        assertEquals(1, caseAuditEvent.getCaseTypeVersion().intValue());
        assertEquals(savedCaseDetails.getState(), caseAuditEvent.getStateId());
        assertEquals(savedCaseDetails.getData(), caseAuditEvent.getData());
    }

    @Test
    @Sql(executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD, scripts = {"classpath:sql/insert_callback_cases.sql"})
    public void shouldReturn201WhenPostCreateEventWithValidDataForCitizen() throws Exception {
        final String SUMMARY = "Case event summary";
        final String DESCRIPTION = "Case event description";
        final String URL = String.format("/citizens/%d/jurisdictions/%s/case-types/%s/cases/%d/events", USER_ID, JURISDICTION_ID, CASE_TYPE_ID, CASE_REFERENCE);

        final CaseDataContent caseDetailsToSave = new CaseDataContent();
        final Event event = new Event();
        event.setEventId(UPDATE_EVENT_ID);
        event.setSummary(SUMMARY);
        event.setDescription(DESCRIPTION);

        final String token = generateEventToken(jdbcTemplate, USER_ID, JURISDICTION_ID, CASE_TYPE_ID, CASE_REFERENCE, UPDATE_EVENT_ID);
        caseDetailsToSave.setToken(token);
        caseDetailsToSave.setEvent(event);
        caseDetailsToSave.setData(mapper.convertValue(DATA, STRING_NODE_TYPE));

        final CallbackResponse callbackResponse = new CallbackResponse();
        callbackResponse.setData(mapper.convertValue(MODIFIED_DATA, STRING_NODE_TYPE));

        wireMockRule.stubFor(WireMock.post(urlMatching("/before-commit.*"))
            .willReturn(okJson(mapper.writeValueAsString(callbackResponse)).withStatus(200)));

        wireMockRule.stubFor(WireMock.post(urlMatching("/after-commit.*"))
            .willReturn(ok()));

        final MvcResult mvcResult = mockMvc.perform(post(URL)
            .contentType(JSON_CONTENT_TYPE)
            .content(mapper.writeValueAsBytes(caseDetailsToSave))
        ).andReturn();

        assertEquals(mvcResult.getResponse().getContentAsString(), 201, mvcResult.getResponse().getStatus());
        Map expectedSanitizedData = mapper.readValue(EXPECTED_MODIFIED_DATA.toString(), Map.class);
        Map actualData = mapper.readValue(mapper.readTree(mvcResult.getResponse().getContentAsString()).get("case_data").toString(), Map.class);
        assertThat( "Incorrect Response Content", actualData.entrySet(), everyItem(isIn(expectedSanitizedData.entrySet())));

        final List<CaseDetails> caseDetailsList = jdbcTemplate.query("SELECT * FROM case_data", this::mapCaseData);
        assertEquals("Incorrect number of cases", 1, caseDetailsList.size());

        final CaseDetails savedCaseDetails = caseDetailsList.get(0);
        assertEquals("Incorrect Case Type", CASE_TYPE_ID, savedCaseDetails.getCaseTypeId());
        Map sanitizedData = mapper.convertValue(EXPECTED_SAVED_DATA, new TypeReference<HashMap<String, JsonNode>>() {
        });
        assertThat("Incorrect Data content", savedCaseDetails.getData().entrySet(), everyItem(isIn(sanitizedData.entrySet())));
        assertEquals("CaseUpdated", savedCaseDetails.getState());

        final List<AuditEvent> caseAuditEventList = jdbcTemplate.query("SELECT * FROM case_event", this::mapAuditEvent);
        assertEquals("Incorrect number of case events", 1, caseAuditEventList.size());

        // Assertion belows are for creation event
        final AuditEvent caseAuditEvent = caseAuditEventList.get(0);
        assertEquals(USER_ID.intValue(), caseAuditEvent.getUserId().intValue());
        assertEquals("Strife", caseAuditEvent.getUserLastName());
        assertEquals("Cloud", caseAuditEvent.getUserFirstName());
        assertEquals(UPDATE_EVENT_ID, caseAuditEvent.getEventId());
        assertEquals(savedCaseDetails.getId(), caseAuditEvent.getCaseDataId());
        assertEquals(savedCaseDetails.getCaseTypeId(), caseAuditEvent.getCaseTypeId());
        assertEquals(1, caseAuditEvent.getCaseTypeVersion().intValue());
        assertEquals(savedCaseDetails.getState(), caseAuditEvent.getStateId());
        assertEquals(savedCaseDetails.getData(), caseAuditEvent.getData());
    }

    @Test
    @Sql(executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD, scripts = {"classpath:sql/insert_callback_cases.sql"})
    public void shouldReturn422WhenPostCreateEventWithCallbackErrorForCaseworker() throws Exception {
        final String EVENT_ID = "UPDATE-EVENT";
        final String CASE_TYPE_ID = "CallbackCase";
        final String JURISDICTION_ID = "TEST";
        final Integer USER_ID = 123;
        final String SUMMARY = "Case event summary";
        final String DESCRIPTION = "Case event description";
        final String URL = String.format("/caseworkers/%d/jurisdictions/%s/case-types/%s/cases/%d/events", USER_ID, JURISDICTION_ID, CASE_TYPE_ID, CASE_REFERENCE);

        final CaseDataContent caseDetailsToSave = new CaseDataContent();
        final Event event = new Event();
        event.setEventId(EVENT_ID);
        event.setSummary(SUMMARY);
        event.setDescription(DESCRIPTION);

        final String token = generateEventToken(jdbcTemplate, USER_ID, JURISDICTION_ID, CASE_TYPE_ID, CASE_REFERENCE, EVENT_ID);
        caseDetailsToSave.setToken(token);
        caseDetailsToSave.setEvent(event);
        caseDetailsToSave.setData(mapper.convertValue(DATA, STRING_NODE_TYPE));

        stubForErrorCallbackResponse("/before-commit.*");

        final MvcResult mvcResult = mockMvc.perform(post(URL)
            .contentType(JSON_CONTENT_TYPE)
            .content(mapper.writeValueAsBytes(caseDetailsToSave))
        ).andReturn();

        assertEquals(mvcResult.getResponse().getContentAsString(), 422, mvcResult.getResponse().getStatus());
    }

    @Test
    @Sql(executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD, scripts = {"classpath:sql/insert_callback_cases.sql"})
    public void shouldReturn422WhenPostCreateEventWithCallbackErrorForCitizen() throws Exception {
        final String EVENT_ID = "UPDATE-EVENT";
        final String CASE_TYPE_ID = "CallbackCase";
        final String JURISDICTION_ID = "TEST";
        final Integer USER_ID = 123;
        final Long CASE_REFERENCE = 1504259907353545L;
        final String SUMMARY = "Case event summary";
        final String DESCRIPTION = "Case event description";
        final String URL = String.format("/citizens/%d/jurisdictions/%s/case-types/%s/cases/%d/events", USER_ID, JURISDICTION_ID, CASE_TYPE_ID, CASE_REFERENCE);

        final CaseDataContent caseDetailsToSave = new CaseDataContent();
        final Event event = new Event();
        event.setEventId(EVENT_ID);
        event.setSummary(SUMMARY);
        event.setDescription(DESCRIPTION);

        final String token = generateEventToken(jdbcTemplate, USER_ID, JURISDICTION_ID, CASE_TYPE_ID, CASE_REFERENCE, EVENT_ID);
        caseDetailsToSave.setToken(token);
        caseDetailsToSave.setEvent(event);
        caseDetailsToSave.setData(mapper.convertValue(DATA, STRING_NODE_TYPE));

        stubForErrorCallbackResponse("/before-commit.*");

        final MvcResult mvcResult = mockMvc.perform(post(URL)
            .contentType(JSON_CONTENT_TYPE)
            .content(mapper.writeValueAsBytes(caseDetailsToSave))
        ).andReturn();

        assertEquals(mvcResult.getResponse().getContentAsString(), 422, mvcResult.getResponse().getStatus());
    }

    @Test
    @Sql(executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD, scripts = {"classpath:sql/insert_callback_cases.sql"})
    public void shouldReturn422WhenPostCreateEventWithInvalidCallbackDataForCaseworker() throws Exception {
        final String EVENT_ID = "UPDATE-EVENT";
        final String CASE_TYPE_ID = "CallbackCase";
        final CaseType caseType = new CaseType();
        caseType.setId(CASE_TYPE_ID);
        final String JURISDICTION_ID = "TEST";
        final Jurisdiction jurisdiction = new Jurisdiction();
        jurisdiction.setId(JURISDICTION_ID);
        final Integer USER_ID = 123;
        final String SUMMARY = "Case event summary";
        final String DESCRIPTION = "Case event description";
        final String URL = String.format("/caseworkers/%d/jurisdictions/%s/case-types/%s/cases/%d/events", USER_ID, JURISDICTION_ID, CASE_TYPE_ID, CASE_REFERENCE);

        final CaseDataContent caseDetailsToSave = new CaseDataContent();
        final Event event = new Event();
        event.setEventId(EVENT_ID);
        event.setSummary(SUMMARY);
        event.setDescription(DESCRIPTION);


        final String token = generateEventToken(jdbcTemplate, USER_ID, JURISDICTION_ID, CASE_TYPE_ID, CASE_REFERENCE,  EVENT_ID);
        caseDetailsToSave.setToken(token);
        caseDetailsToSave.setEvent(event);
        caseDetailsToSave.setData(mapper.convertValue(DATA, STRING_NODE_TYPE));

        final CallbackResponse callbackResponse = new CallbackResponse();
        callbackResponse.setData(mapper.convertValue(MODIFIED_CORRUPTED_DATA, STRING_NODE_TYPE));

        wireMockRule.stubFor(WireMock.post(urlMatching("/before-commit.*"))
            .willReturn(okJson(mapper.writeValueAsString(callbackResponse)).withStatus(200)));
        wireMockRule.stubFor(WireMock.post(urlMatching("/before-commit.*"))
            .willReturn(okJson(mapper.writeValueAsString(callbackResponse)).withStatus(200)));

        final MvcResult mvcResult = mockMvc.perform(post(URL)
            .contentType(JSON_CONTENT_TYPE)
            .content(mapper.writeValueAsBytes(caseDetailsToSave))
        ).andReturn();

        assertEquals(mvcResult.getResponse().getContentAsString(), 422, mvcResult.getResponse().getStatus());
    }

    @Test
    @Sql(executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD, scripts = {"classpath:sql/insert_callback_cases.sql"})
    public void shouldReturn422WhenPostCreateEventWithInvalidCallbackDataForCitizen() throws Exception {
        final String EVENT_ID = "UPDATE-EVENT";
        final String CASE_TYPE_ID = "CallbackCase";
        final CaseType caseType = new CaseType();
        caseType.setId(CASE_TYPE_ID);
        final String JURISDICTION_ID = "TEST";
        final Jurisdiction jurisdiction = new Jurisdiction();
        jurisdiction.setId(JURISDICTION_ID);
        final Integer USER_ID = 123;
        final String SUMMARY = "Case event summary";
        final String DESCRIPTION = "Case event description";
        final String URL = String.format("/citizens/%d/jurisdictions/%s/case-types/%s/cases/%d/events", USER_ID, JURISDICTION_ID, CASE_TYPE_ID, CASE_REFERENCE);

        final CaseDataContent caseDetailsToSave = new CaseDataContent();
        final Event event = new Event();
        event.setEventId(EVENT_ID);
        event.setSummary(SUMMARY);
        event.setDescription(DESCRIPTION);


        final String token = generateEventToken(jdbcTemplate, USER_ID, JURISDICTION_ID, CASE_TYPE_ID, CASE_REFERENCE,  EVENT_ID);
        caseDetailsToSave.setToken(token);
        caseDetailsToSave.setEvent(event);
        caseDetailsToSave.setData(mapper.convertValue(DATA, STRING_NODE_TYPE));

        final CallbackResponse callbackResponse = new CallbackResponse();
        callbackResponse.setData(mapper.convertValue(MODIFIED_CORRUPTED_DATA, STRING_NODE_TYPE));

        wireMockRule.stubFor(WireMock.post(urlMatching("/before-commit.*"))
            .willReturn(okJson(mapper.writeValueAsString(callbackResponse)).withStatus(200)));
        wireMockRule.stubFor(WireMock.post(urlMatching("/before-commit.*"))
            .willReturn(okJson(mapper.writeValueAsString(callbackResponse)).withStatus(200)));

        final MvcResult mvcResult = mockMvc.perform(post(URL)
            .contentType(JSON_CONTENT_TYPE)
            .content(mapper.writeValueAsBytes(caseDetailsToSave))
        ).andReturn();

        assertEquals(mvcResult.getResponse().getContentAsString(), 422, mvcResult.getResponse().getStatus());
    }

    @Test
    @Sql(executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD, scripts = {"classpath:sql/insert_callback_cases.sql"})
    public void shouldReturn404WhenPostCreateEventWithInvalidEventTokenForCaseworker() throws Exception {
        final String EVENT_ID = "UPDATE-EVENT";
        final String CASE_TYPE_ID = "CallbackCase";
        final String JURISDICTION_ID = "TEST";
        final Integer USER_ID = 123;
        final String SUMMARY = "Case event summary";
        final String DESCRIPTION = "Case event description";
        final String URL = String.format("/caseworkers/%d/jurisdictions/%s/case-types/%s/cases/%d/events", USER_ID, JURISDICTION_ID, CASE_TYPE_ID, CASE_REFERENCE);

        final CaseDataContent caseDetailsToSave = new CaseDataContent();
        final Event event = new Event();
        event.setEventId(EVENT_ID);
        event.setSummary(SUMMARY);
        event.setDescription(DESCRIPTION);

        final String token = generateEventToken(jdbcTemplate, 231321, JURISDICTION_ID, CASE_TYPE_ID, CASE_REFERENCE, EVENT_ID);
        caseDetailsToSave.setToken(token);
        caseDetailsToSave.setEvent(event);
        caseDetailsToSave.setData(mapper.convertValue(DATA, STRING_NODE_TYPE));

        final MvcResult mvcResult = mockMvc.perform(post(URL)
            .contentType(JSON_CONTENT_TYPE)
            .content(mapper.writeValueAsBytes(caseDetailsToSave))
        ).andReturn();

        assertEquals("Did not catch invalid token", 404, mvcResult.getResponse().getStatus());
    }

    @Test
    @Sql(executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD, scripts = {"classpath:sql/insert_callback_cases.sql"})
    public void shouldReturn404WhenPostCreateEventWithInvalidEventTokenForCitizen() throws Exception {
        final String EVENT_ID = "UPDATE-EVENT";
        final String CASE_TYPE_ID = "CallbackCase";
        final String JURISDICTION_ID = "TEST";
        final Integer USER_ID = 123;
        final String SUMMARY = "Case event summary";
        final String DESCRIPTION = "Case event description";
        final String URL = String.format("/citizens/%d/jurisdictions/%s/case-types/%s/cases/%d/events", USER_ID, JURISDICTION_ID, CASE_TYPE_ID, CASE_REFERENCE);

        final CaseDataContent caseDetailsToSave = new CaseDataContent();
        final Event event = new Event();
        event.setEventId(EVENT_ID);
        event.setSummary(SUMMARY);
        event.setDescription(DESCRIPTION);

        final String token = generateEventToken(jdbcTemplate, 231321, JURISDICTION_ID, CASE_TYPE_ID, CASE_REFERENCE, EVENT_ID);
        caseDetailsToSave.setToken(token);
        caseDetailsToSave.setEvent(event);
        caseDetailsToSave.setData(mapper.convertValue(DATA, STRING_NODE_TYPE));

        final MvcResult mvcResult = mockMvc.perform(post(URL)
            .contentType(JSON_CONTENT_TYPE)
            .content(mapper.writeValueAsBytes(caseDetailsToSave))
        ).andReturn();

        assertEquals("Did not catch invalid token", 404, mvcResult.getResponse().getStatus());
    }

    private void stubForErrorCallbackResponse(final String url) throws JsonProcessingException {
        final CallbackResponse callbackResponse = new CallbackResponse();
        callbackResponse.setErrors(Collections.singletonList("Just a test"));

        wireMockRule.stubFor(WireMock.post(urlMatching(url))
                                     .willReturn(okJson(mapper.writeValueAsString(callbackResponse)).withStatus(200)));
    }

}
