package uk.gov.hmcts.ccd.data.caseaccess;

import org.hibernate.annotations.CreationTimestamp;

import javax.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "case_users_audit")
public class CaseUserAuditEntity {

    public enum Action {
        GRANT, REVOKE
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "case_data_id")
    private long caseDataId;
    @Column(name = "user_id")
    private String userId;
    @Column(name = "changed_by_id")
    private String changedById;
    @Column(name = "changed_at")
    @CreationTimestamp
    private LocalDateTime changedAt;
    @Enumerated(EnumType.STRING)
    private Action action;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public long getCaseDataId() {
        return caseDataId;
    }

    public void setCaseDataId(long caseDataId) {
        this.caseDataId = caseDataId;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getChangedById() {
        return changedById;
    }

    public void setChangedById(String changedById) {
        this.changedById = changedById;
    }

    public LocalDateTime getChangedAt() {
        return changedAt;
    }

    public void setChangedAt(LocalDateTime changedAt) {
        this.changedAt = changedAt;
    }

    public Action getAction() {
        return action;
    }

    public void setAction(Action action) {
        this.action = action;
    }
}
