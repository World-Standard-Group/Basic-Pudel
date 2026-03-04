package group.worldstandard.pudel.plugin.entity;

import group.worldstandard.pudel.api.database.Entity;

@Entity
public class PrankContainer {
    private Long id;
    private String userId;
    private String containerId;
    private String name;
    private int usage;

    public PrankContainer() {}

    public PrankContainer(String userId, String containerId, String name) {
        this.userId = userId;
        this.containerId = containerId;
        this.name = name;
        this.usage = 0;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }
    public String getContainerId() { return containerId; }
    public void setContainerId(String containerId) { this.containerId = containerId; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public int getUsage() { return usage; }
    public void setUsage(int usage) { this.usage = usage; }
}
