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
 * Represents a category entry entity that defines the management and permission settings
 * for a specific category within a guild.
 * <p>
 * This entity associates a category with a designated manager (user) and specifies
 * default role assignments along with their corresponding permission profiles.
 * It is used to persist configuration details about how categories are managed
 * and what default permissions apply to users interacting with them.
 */
@Entity
public class CategoryEntry{
    private Long id;
    private String guild_id;
    private String category_id;
    private String manager_id; // user_id
    private String manager_role_profile; // permission profile name for manager
    private String default_role; // role_id
    private String default_role_profile; // permission profile name for default role

    public CategoryEntry() {}

    public CategoryEntry(Long id, String guild_id, String category_id, String manager_id, String manager_role_profile, String default_role, String default_role_profile) {
        this.id = id;
        this.guild_id = guild_id;
        this.category_id = category_id;
        this.manager_id = manager_id;
        this.manager_role_profile = manager_role_profile;
        this.default_role = default_role;
        this.default_role_profile = default_role_profile;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getGuild_id() { return guild_id; }
    public void setGuild_id(String guild_id) { this.guild_id = guild_id; }

    public String getCategory_id() { return category_id; }
    public void setCategory_id(String category_id) { this.category_id = category_id; }

    public String getManager_id() { return manager_id; }
    public void setManager_id(String manager_id) { this.manager_id = manager_id; }

    public String getManager_role_profile() { return manager_role_profile; }
    public void setManager_role_profile(String manager_role_profile) { this.manager_role_profile = manager_role_profile; }

    public String getDefault_role() { return default_role; }
    public void setDefault_role(String default_role) { this.default_role = default_role; }

    public String getDefault_role_profile() { return default_role_profile; }
    public void setDefault_role_profile(String default_role_profile) { this.default_role_profile = default_role_profile; }
}
