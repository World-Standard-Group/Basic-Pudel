package group.worldstandard.pudel.plugin.entity;

import group.worldstandard.pudel.api.database.Entity;

@Entity
public class WarnCollection {
    private Long id;
    private String guildId;
    private String userId;
    private boolean inCase;
    private String issuerId;
    private char resolveFlag;

    public WarnCollection(){}

    public WarnCollection(Long id, String guildId, String userId, boolean inCase, String issuerId, char resolveFlag) {
        this.id = id;
        this.guildId = guildId;
        this.userId = userId;
        this.inCase = inCase;
        this.issuerId = issuerId;
        this.resolveFlag = resolveFlag;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getGuildId() {
        return guildId;
    }

    public void setGuildId(String guildId) {
        this.guildId = guildId;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public boolean isInCase() {
        return inCase;
    }

    public void setInCase(boolean inCase) {
        this.inCase = inCase;
    }

    public String getIssuerId() {
        return issuerId;
    }

    public void setIssuerId(String issuerId) {
        this.issuerId = issuerId;
    }

    public char getResolveFlag() {
        return resolveFlag;
    }

    public void setResolveFlag(char resolveFlag) {
        this.resolveFlag = resolveFlag;
    }
}