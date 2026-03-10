package group.worldstandard.pudel.plugin.entity;

import group.worldstandard.pudel.api.database.Entity;

@Entity
public class ManagedRole {
    private Long id;
    private Long roleId;
    private Long guildId;
    private String hexCode;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getRoleId() { return roleId; }
    public void setRoleId(Long roleId) { this.roleId = roleId; }
    public Long getGuildId() { return guildId; }
    public void setGuildId(Long guildId) { this.guildId = guildId; }
    public String getHexCode() { return hexCode; }
    public void setHexCode(String hexCode) { this.hexCode = hexCode; }
}

