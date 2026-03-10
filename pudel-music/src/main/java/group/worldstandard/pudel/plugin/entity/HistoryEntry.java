package group.worldstandard.pudel.plugin.entity;

import group.worldstandard.pudel.api.database.Entity;

@Entity
public class HistoryEntry {
    private Long id;
    private Long guildId;
    private Long userId;
    private String trackTitle;
    private String trackUrl;
    private Long playedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getGuildId() { return guildId; }
    public void setGuildId(Long guildId) { this.guildId = guildId; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public String getTrackTitle() { return trackTitle; }
    public void setTrackTitle(String trackTitle) { this.trackTitle = trackTitle; }
    public String getTrackUrl() { return trackUrl; }
    public void setTrackUrl(String trackUrl) { this.trackUrl = trackUrl; }
    public Long getPlayedAt() { return playedAt; }
    public void setPlayedAt(Long playedAt) { this.playedAt = playedAt; }
}

