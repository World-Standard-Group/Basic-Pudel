package group.worldstandard.pudel.plugin.entity;

import group.worldstandard.pudel.api.database.Entity;

@Entity
public class PrankCollection {
    private Long id;
    private String prankId;
    private String containerId;
    private String url;
    private String placeholder;

    public PrankCollection() {}

    public PrankCollection(String prankId, String containerId, String url, String placeholder) {
        this.prankId = prankId;
        this.containerId = containerId;
        this.url = url;
        this.placeholder = placeholder;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getPrankId() { return prankId; }
    public void setPrankId(String prankId) { this.prankId = prankId; }
    public String getContainerId() { return containerId; }
    public void setContainerId(String containerId) { this.containerId = containerId; }
    public String getUrl() { return url; }
    public void setUrl(String url) { this.url = url; }
    public String getPlaceholder() { return placeholder; }
    public void setPlaceholder(String placeholder) { this.placeholder = placeholder; }
}
