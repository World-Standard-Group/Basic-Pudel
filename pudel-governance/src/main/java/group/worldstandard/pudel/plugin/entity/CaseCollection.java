package group.worldstandard.pudel.plugin.entity;

import group.worldstandard.pudel.api.database.Entity;

@Entity
public class CaseCollection {
    private Long id;
    private String userId;
    private String issuerId;
    private String judgeId;
    private String reportId;
    private char resolveFlag;

    public CaseCollection() {}

    public CaseCollection(Long id, String userId, String issuerId, String judgeId, String reportId, char resolveFlag) {
        this.id = id;
        this.userId = userId;
        this.issuerId = issuerId;
        this.judgeId = judgeId;
        this.reportId = reportId;
        this.resolveFlag = resolveFlag;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getIssuerId() {
        return issuerId;
    }

    public void setIssuerId(String issuerId) {
        this.issuerId = issuerId;
    }

    public String getJudgeId() {
        return judgeId;
    }

    public void setJudgeId(String judgeId) {
        this.judgeId = judgeId;
    }

    public String getReportId() {
        return reportId;
    }

    public void setReportId(String reportId) {
        this.reportId = reportId;
    }

    public char getResolveFlag() {
        return resolveFlag;
    }

    public void setResolveFlag(char resolveFlag) {
        this.resolveFlag = resolveFlag;
    }
}