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
 * Represents a privilege role entity that defines a role granted elevated permissions
 * within a specific guild context.
 * <p>
 * This entity maps a role identifier to a guild, indicating that members assigned
 * this role should be treated with elevated privileges for certain operations
 * within the categorization system.
 */
@Entity
public class PrivilegeRole{
    private Long id;
    private String guild_id;
    private String role_id;

    /**
     * No-arg constructor required by PluginRepository for entity deserialization.
     */
    public PrivilegeRole() {}

    /**
     * Constructs a new PrivilegeRole with the specified id, guild_id, and role_id.
     *
     * @param id the unique identifier for this privilege role entry
     * @param guild_id the ID of the guild to which this privilege role belongs
     * @param role_id the ID of the role that is granted elevated permissions
     */
    public PrivilegeRole(Long id, String guild_id, String role_id) {
        this.id = id;
        this.guild_id = guild_id;
        this.role_id = role_id;
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

    public String getRole_id() {
        return role_id;
    }

    public void setRole_id(String role_id) {
        this.role_id = role_id;
    }
}
