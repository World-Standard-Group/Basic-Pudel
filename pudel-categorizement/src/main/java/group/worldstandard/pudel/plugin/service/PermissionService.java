package group.worldstandard.pudel.plugin.service;

import group.worldstandard.pudel.api.database.PluginRepository;
import group.worldstandard.pudel.plugin.entity.CategoryEntry;
import group.worldstandard.pudel.plugin.entity.PermissionProfile;
import group.worldstandard.pudel.plugin.entity.PrivilegeRole;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.channel.concrete.Category;

import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Service for managing permission profiles, privilege roles, and applying permissions to categories.
 * Encapsulates all permission-related business logic.
 */
public class PermissionService {

    private final PluginRepository<PermissionProfile> profileRepo;
    private final PluginRepository<PrivilegeRole> privilegeRepo;
    private final PluginRepository<CategoryEntry> categoryRepo;
    private final PluginContextLogger logger;

    // Manageable permissions (VIEW_CHANNEL excluded per spec)
    private static final List<Permission> MANAGEABLE_PERMISSIONS = List.of(
            // Management
            Permission.MANAGE_CHANNEL, Permission.MANAGE_PERMISSIONS, Permission.MANAGE_WEBHOOKS,
            Permission.MANAGE_EVENTS, Permission.CREATE_SCHEDULED_EVENTS, Permission.CREATE_INSTANT_INVITE,
            Permission.USE_APPLICATION_COMMANDS, Permission.USE_EMBEDDED_ACTIVITIES, Permission.USE_EXTERNAL_APPLICATIONS,
            // Messaging
            Permission.MESSAGE_SEND, Permission.CREATE_PUBLIC_THREADS, Permission.MESSAGE_EXT_EMOJI,
            Permission.MESSAGE_EXT_STICKER, Permission.MESSAGE_SEND_IN_THREADS, Permission.CREATE_PRIVATE_THREADS,
            Permission.MESSAGE_EMBED_LINKS, Permission.MESSAGE_ATTACH_FILES, Permission.MESSAGE_ADD_REACTION,
            Permission.MESSAGE_MENTION_EVERYONE, Permission.MESSAGE_MANAGE, Permission.MANAGE_THREADS,
            Permission.MESSAGE_HISTORY, Permission.MESSAGE_TTS, Permission.MESSAGE_ATTACH_VOICE_MESSAGE,
            Permission.MESSAGE_SEND_POLLS,
            // Voice
            Permission.VOICE_CONNECT, Permission.VOICE_SPEAK, Permission.VOICE_STREAM,
            Permission.VOICE_USE_SOUNDBOARD, Permission.VOICE_USE_EXTERNAL_SOUNDS, Permission.VOICE_USE_VAD,
            Permission.PRIORITY_SPEAKER, Permission.VOICE_MUTE_OTHERS, Permission.VOICE_DEAF_OTHERS,
            Permission.VOICE_MOVE_OTHERS, Permission.VOICE_SET_STATUS, Permission.REQUEST_TO_SPEAK
    );

    public PermissionService(PluginRepository<PermissionProfile> profileRepo,
                             PluginRepository<PrivilegeRole> privilegeRepo,
                             PluginRepository<CategoryEntry> categoryRepo,
                             PluginContextLogger logger) {
        this.profileRepo = profileRepo;
        this.privilegeRepo = privilegeRepo;
        this.categoryRepo = categoryRepo;
        this.logger = logger;
    }

    // ==================== PERMISSION PROFILE MANAGEMENT ====================

    /**
     * Finds a permission profile by guild ID and name.
     */
    public PermissionProfile findProfile(String guildId, String profileName) {
        if (profileName == null || profileName.isBlank()) return null;
        List<PermissionProfile> list = profileRepo.query()
                .where("guild_id", guildId).where("name", profileName).list();
        return list.isEmpty() ? null : list.getFirst();
    }

    /**
     * Gets all permission profiles for a guild.
     */
    public List<PermissionProfile> getProfiles(String guildId) {
        return profileRepo.query().where("guild_id", guildId).list();
    }

    /**
     * Creates a new permission profile.
     */
    public boolean createProfile(String guildId, String profileName) {
        if (profileName == null || profileName.isBlank()) return false;
        if (!profileRepo.query().where("guild_id", guildId).where("name", profileName).list().isEmpty()) {
            return false; // duplicate
        }
        profileRepo.save(new PermissionProfile(null, guildId, profileName, "", ""));
        return true;
    }

    /**
     * Deletes a permission profile by name.
     */
    public boolean deleteProfile(String guildId, String profileName) {
        List<PermissionProfile> profiles = profileRepo.query()
                .where("guild_id", guildId).where("name", profileName).list();
        if (!profiles.isEmpty()) {
            profileRepo.deleteById(profiles.getFirst().getId());
            return true;
        }
        return false;
    }

    /**
     * Saves permission state to a named profile.
     */
    public void saveProfilePermState(String guildId, String profileName, java.util.LinkedHashMap<String, String> state) {
        String allow = state.entrySet().stream()
                .filter(e -> e.getValue().equals("ALLOW"))
                .map(Map.Entry::getKey)
                .collect(Collectors.joining(","));
        String deny = state.entrySet().stream()
                .filter(e -> e.getValue().equals("DENY"))
                .map(Map.Entry::getKey)
                .collect(Collectors.joining(","));

        List<PermissionProfile> existing = profileRepo.query()
                .where("guild_id", guildId).where("name", profileName).list();
        if (!existing.isEmpty()) {
            PermissionProfile p = existing.getFirst();
            profileRepo.save(new PermissionProfile(p.getId(), guildId, profileName, allow, deny));
        }
    }

    /**
     * Loads permission state from a named profile for the permission editor.
     */
    public java.util.LinkedHashMap<String, String> loadProfilePermState(String guildId, String profileName) {
        PermissionProfile profile = findProfile(guildId, profileName);
        String allowStr = profile != null ? profile.getAllow() : "";
        String denyStr = profile != null ? profile.getDeny() : "";

        Set<String> allowSet = (allowStr != null && !allowStr.isBlank())
                ? Set.of(allowStr.split(",")) : Set.of();
        Set<String> denySet = (denyStr != null && !denyStr.isBlank())
                ? Set.of(denyStr.split(",")) : Set.of();

        java.util.LinkedHashMap<String, String> state = new java.util.LinkedHashMap<>();
        for (Permission perm : MANAGEABLE_PERMISSIONS) {
            String name = perm.name();
            if (allowSet.contains(name)) state.put(name, "ALLOW");
            else if (denySet.contains(name)) state.put(name, "DENY");
            else state.put(name, "INHERIT");
        }
        return state;
    }

    /**
     * Formats a summary of allowed and denied permissions.
     */
    public String formatPermSummary(String allow, String deny) {
        boolean hasAllow = allow != null && !allow.isBlank();
        boolean hasDeny = deny != null && !deny.isBlank();
        if (!hasAllow && !hasDeny) return "_All permissions set to Inherit (default)_";

        StringBuilder sb = new StringBuilder();
        if (hasAllow) {
            long count = allow.split(",").length;
            sb.append("✅ **").append(count).append("** allowed");
        }
        if (hasDeny) {
            if (hasAllow) sb.append(" · ");
            long count = deny.split(",").length;
            sb.append("❌ **").append(count).append("** denied");
        }
        return sb.toString();
    }

    // ==================== PRIVILEGE ROLE MANAGEMENT ====================

    /**
     * Gets all privilege roles for a guild.
     */
    public List<PrivilegeRole> getPrivilegeRoles(String guildId) {
        return privilegeRepo.query().where("guild_id", guildId).list();
    }

    /**
     * Adds a privilege role.
     */
    public boolean addPrivilegeRole(String guildId, String roleId) {
        if (!privilegeRepo.query().where("guild_id", guildId).where("role_id", roleId).list().isEmpty()) {
            return false; // duplicate
        }
        privilegeRepo.save(new PrivilegeRole(null, guildId, roleId));
        return true;
    }

    /**
     * Removes a privilege role.
     */
    public boolean removePrivilegeRole(String guildId, String roleId) {
        List<PrivilegeRole> roles = privilegeRepo.query()
                .where("guild_id", guildId).where("role_id", roleId).list();
        if (!roles.isEmpty()) {
            privilegeRepo.deleteById(roles.getFirst().getId());
            return true;
        }
        return false;
    }

    /**
     * Checks if a member has MANAGE_CHANNEL permission or a Privilege Role.
     */
    public boolean hasPermissionOrPrivilege(Member member, String guildId) {
        if (member.hasPermission(Permission.MANAGE_CHANNEL)) return true;
        List<PrivilegeRole> privRoles = getPrivilegeRoles(guildId);
        Set<String> privRoleIds = privRoles.stream().map(PrivilegeRole::getRole_id).collect(Collectors.toSet());
        return member.getRoles().stream().anyMatch(r -> privRoleIds.contains(r.getId()));
    }

    // ==================== PERMISSION APPLICATION ====================

    /**
     * Applies permission overrides to a Discord category based on settings.
     */
    public void applyPermissions(Category category, Guild guild, String managerId, String roleId,
                                 PermissionProfile managerProfile, PermissionProfile roleProfile) {
        if (managerId != null) {
            Member manager = guild.getMemberById(managerId);
            if (manager != null) {
                EnumSet<Permission> allow = parsePermissions(managerProfile != null ? managerProfile.getAllow() : "");
                EnumSet<Permission> deny = parsePermissions(managerProfile != null ? managerProfile.getDeny() : "");
                category.upsertPermissionOverride(manager)
                        .setAllowed(allow)
                        .setDenied(deny)
                        .queue(null, err -> logger.warn("Failed to set manager override: " + err.getMessage()));
            }
        }

        if (roleId != null) {
            Role role = guild.getRoleById(roleId);
            if (role != null) {
                EnumSet<Permission> allow = parsePermissions(roleProfile != null ? roleProfile.getAllow() : "");
                EnumSet<Permission> deny = parsePermissions(roleProfile != null ? roleProfile.getDeny() : "");
                // Spec: Default Role always gets VIEW_CHANNEL
                allow.add(Permission.VIEW_CHANNEL);
                category.upsertPermissionOverride(role)
                        .setAllowed(allow)
                        .setDenied(deny)
                        .queue(null, err -> logger.warn("Failed to set role override: " + err.getMessage()));

                // Spec: Deny VIEW_CHANNEL for @everyone to make category private
                if (!roleId.equals(guild.getPublicRole().getId())) {
                    category.upsertPermissionOverride(guild.getPublicRole())
                            .deny(Permission.VIEW_CHANNEL)
                            .queue(null, err -> logger.warn("Failed to set @everyone override: " + err.getMessage()));
                }
            }
        }
    }

    /**
     * Parses a comma-separated string of permission names into an EnumSet.
     */
    public EnumSet<Permission> parsePermissions(String permString) {
        if (permString == null || permString.isBlank()) return EnumSet.noneOf(Permission.class);
        EnumSet<Permission> set = EnumSet.noneOf(Permission.class);
        for (String name : permString.split(",")) {
            try {
                set.add(Permission.valueOf(name.trim()));
            } catch (IllegalArgumentException ignored) {}
        }
        return set;
    }

    // ==================== CATEGORY ENTRY MANAGEMENT ====================

    /**
     * Saves a category entry.
     */
    public void saveCategoryEntry(CategoryEntry entry) {
        categoryRepo.save(entry);
    }

    /**
     * Finds a category entry by category ID.
     */
    public CategoryEntry findCategoryEntry(String categoryId) {
        List<CategoryEntry> entries = categoryRepo.query().where("category_id", categoryId).list();
        return entries.isEmpty() ? null : entries.getFirst();
    }

    /**
     * Gets all category entries for a guild.
     */
    public List<CategoryEntry> getCategoryEntries(String guildId) {
        return categoryRepo.query().where("guild_id", guildId).list();
    }

    /**
     * Deletes a category entry by ID.
     */
    public void deleteCategoryEntry(Long entryId) {
        categoryRepo.deleteById(entryId);
    }

    /**
     * Checks if a category is already tracked.
     */
    public boolean isCategoryTracked(String categoryId) {
        return !categoryRepo.query().where("category_id", categoryId).list().isEmpty();
    }

    // ==================== UTILITY ====================

    public List<Permission> getManageablePermissions() {
        return MANAGEABLE_PERMISSIONS;
    }

    // Logger interface to avoid direct dependency on PluginContext
    public interface PluginContextLogger {
        void warn(String message);
    }
}