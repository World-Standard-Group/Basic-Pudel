package group.worldstandard.pudel.plugin.entity;

import group.worldstandard.pudel.api.database.Entity;

@Entity
public class PermissionProfile {
    private Long id;
    private String guild_id;
    private String name;
    private String allow;
    private String deny;

    public PermissionProfile() {
    }

    public PermissionProfile(Long id, String guild_id, String name, String allow, String deny) {
        this.id = id;
        this.guild_id = guild_id;
        this.name = name;
        this.allow = allow;
        this.deny = deny;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getGuild_id() {
        return guild_id;
    }

    public void setGuild_id(String guild_id) {
        this.guild_id = guild_id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getAllow() {
        return allow;
    }

    public void setAllow(String allow) {
        this.allow = allow;
    }

    public String getDeny() {
        return deny;
    }

    public void setDeny(String deny) {
        this.deny = deny;
    }
}
