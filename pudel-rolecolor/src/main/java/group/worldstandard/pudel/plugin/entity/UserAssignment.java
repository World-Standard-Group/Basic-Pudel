package group.worldstandard.pudel.plugin.entity;

import group.worldstandard.pudel.api.database.Entity;

@Entity
public class UserAssignment {
    private Long id;
    private Long userId;
    private Long guildId;
    private Long roleId;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public Long getGuildId() { return guildId; }
    public void setGuildId(Long guildId) { this.guildId = guildId; }
    public Long getRoleId() { return roleId; }
    public void setRoleId(Long roleId) { this.roleId = roleId; }
}

