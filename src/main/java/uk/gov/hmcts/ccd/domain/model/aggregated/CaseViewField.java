package uk.gov.hmcts.ccd.domain.model.aggregated;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import uk.gov.hmcts.ccd.domain.model.definition.FieldType;

public class CaseViewField {
    private String id;
    private String label;
    @JsonProperty("hint_text")
    private String hintText;
    @JsonProperty("field_type")
    private FieldType fieldType;
    private Boolean hidden;
    @JsonProperty("validation_expr")
    private String validationExpression;
    @JsonProperty("security_label")
    private String securityLabel;
    @JsonProperty("order")
    private Integer order;
    private JsonNode value;
    @JsonProperty("display_context")
    private String displayContext;
    @JsonProperty("show_condition")
    private String showCondition;
    @JsonProperty("show_summary_change_option")
    private Boolean showSummaryChangeOption;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    public String getHintText() {
        return hintText;
    }

    public void setHintText(String hintText) {
        this.hintText = hintText;
    }

    public FieldType getFieldType() {
        return fieldType;
    }

    public void setFieldType(FieldType fieldType) {
        this.fieldType = fieldType;
    }

    public Boolean isHidden() {
        return hidden;
    }

    public void setHidden(Boolean hidden) {
        this.hidden = hidden;
    }

    public String getValidationExpression() {
        return validationExpression;
    }

    public void setValidationExpression(String validationExpression) {
        this.validationExpression = validationExpression;
    }

    public String getSecurityLabel() {
        return securityLabel;
    }

    public void setSecurityLabel(String securityLabel) {
        this.securityLabel = securityLabel;
    }

    public Integer getOrder() {
        return order;
    }

    public void setOrder(Integer order) {
        this.order = order;
    }

    public JsonNode getValue() {
        return value;
    }

    public void setValue(JsonNode value) {
        this.value = value;
    }

    public String getDisplayContext() {
        return displayContext;
    }

    public void setDisplayContext(String displayContext) {
        this.displayContext = displayContext;
    }

    public void setShowCondition(String showCondition) {
        this.showCondition = showCondition;
    }

    public String getShowCondition() {
        return this.showCondition;
    }

    public Boolean getShowSummaryChangeOption() {
        return showSummaryChangeOption;
    }

    public void setShowSummaryChangeOption(final Boolean showSummaryChangeOption) {
        this.showSummaryChangeOption = showSummaryChangeOption;
    }
}
