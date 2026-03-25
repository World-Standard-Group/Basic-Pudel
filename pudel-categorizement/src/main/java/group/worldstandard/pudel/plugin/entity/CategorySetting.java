package group.worldstandard.pudel.plugin.entity;

import group.worldstandard.pudel.api.database.Entity;

/**
 *
 * @param id auto manage by Core
 * @param guild_id guild id that hold this setting (unique)
 * @param permissions a default permission that user_id from {@link CategoryEntry#manager_id()} gonna had when create category
 */
@Entity
public record CategorySetting(
        Long id,
        String guild_id,
        String permissions // Using Permission.*.name() (eg. "MANAGE_CHANNELS,VIEW_CHANNELS") split by ','
) {
}
