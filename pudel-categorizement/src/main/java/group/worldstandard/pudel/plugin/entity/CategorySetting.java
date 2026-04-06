package group.worldstandard.pudel.plugin.entity;

import group.worldstandard.pudel.api.database.Entity;

/**
 * Persists the default permission settings used for categories in a guild.
 * <p>
 * This entity stores the guild identifier in {@code guild_id} and the configured
 * permission overrides for managers and roles in the {@code manager_allow},
 * {@code manager_deny}, {@code role_allow}, and {@code role_deny} fields.
 * <p>
 * Each allow/deny field is stored as a comma-separated list of permission names,
 * where each value corresponds to a {@code Permission} enum constant name. An
 * empty or null value indicates that no permissions are configured for that list.
 */
@Entity
public class CategorySetting{
    private Long id;
    private String guild_id;
    private String manager_allow;
    private String manager_deny;
    private String role_allow;
    private String role_deny;

    public CategorySetting() {}

    public CategorySetting(Long id, String guild_id, String manager_allow, String manager_deny, String role_allow, String role_deny) {
        this.id = id;
        this.guild_id = guild_id;
        this.manager_allow = manager_allow;
        this.manager_deny = manager_deny;
        this.role_allow = role_allow;
        this.role_deny = role_deny;
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

    public String getManager_allow() {
        return manager_allow;
    }

    public void setManager_allow(String manager_allow) {
        this.manager_allow = manager_allow;
    }

    public String getManager_deny() {
        return manager_deny;
    }

    public void setManager_deny(String manager_deny) {
        this.manager_deny = manager_deny;
    }

    public String getRole_allow() {
        return role_allow;
    }

    public void setRole_allow(String role_allow) {
        this.role_allow = role_allow;
    }

    public String getRole_deny() {
        return role_deny;
    }

    public void setRole_deny(String role_deny) {
        this.role_deny = role_deny;
    }
}
