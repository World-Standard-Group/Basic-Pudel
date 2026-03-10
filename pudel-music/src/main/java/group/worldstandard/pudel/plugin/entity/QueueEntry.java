package group.worldstandard.pudel.plugin.entity;

import group.worldstandard.pudel.api.database.Entity;

@Entity
public class QueueEntry {
    private Long id;
    private Long guildId;
    private Long userId;
    private String trackBlob;
    private String status;
    private String title;
    private Boolean isLooped;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getGuildId() { return guildId; }
    public void setGuildId(Long guildId) { this.guildId = guildId; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public String getTrackBlob() { return trackBlob; }
    public void setTrackBlob(String trackBlob) { this.trackBlob = trackBlob; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public Boolean getIsLooped() { return isLooped; }
    public void setIsLooped(Boolean looped) { isLooped = looped; }
}

