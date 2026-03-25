package group.worldstandard.pudel.plugin.entity;

import group.worldstandard.pudel.api.database.Entity;

/**
 * An entity of category record with {@param guild_id}
 *
 * @param id auto manage by Core
 * @param guild_id guild id that hold this category
 * @param category_id category id of this guild_id (unique to guild_id)
 * @param manager_id user id that gonna had all permission from this category
 * @param default_role role id that gonna had default permission to this category
 */
@Entity
public record CategoryEntry(
        Long id,
        String guild_id,
        String category_id,
        String manager_id, // user_id
        String default_role // role_id that had default permission set from modal
) {
}
