/*
 * Advanced Pudel - Pudel's Category Management
 * Copyright (c) 2026 World Standard Group
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT.
 */
package group.worldstandard.pudel.plugin.entity;

import group.worldstandard.pudel.api.database.Entity;

/**
 * Persisted permission profile for a guild.
 * <p>
 * This entity stores the profile name together with the serialized permission
 * definitions to explicitly allow and deny. The values in {@code allow} and
 * {@code deny} are stored in their persisted string format and should be kept
 * consistent with the rest of the plugin to avoid migration and compatibility
 * issues.
 */
@Entity
public class PermissionProfile {
    private Long id;
    private String guild_id;
    private String name;
    /**
     * Serialized permissions that this profile explicitly allows.
     */
    private String allow;
    /**
     * Serialized permissions that this profile explicitly denies.
     */
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
