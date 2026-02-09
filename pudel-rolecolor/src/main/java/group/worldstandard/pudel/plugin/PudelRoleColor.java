package group.worldstandard.pudel.plugin;

import group.worldstandard.pudel.api.PluginContext;
import group.worldstandard.pudel.api.annotation.*;
import group.worldstandard.pudel.api.database.*;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;

import java.awt.Color;
import java.util.List;
import java.util.regex.Pattern;

/**
 * RoleColorPlugin - Allows users to set custom color roles.
 */
@Plugin(
        name = "Pudel's Role Color",
        version = "1.0.0",
        author = "Zazalng",
        description = "Custom color roles for users"
)
public class PudelRoleColor {

    private PluginContext context;
    private PluginDatabaseManager db;
    private PluginRepository<ManagedRole> roleRepo;
    private PluginRepository<UserAssignment> assignmentRepo;

    private static final Pattern HEX_PATTERN = Pattern.compile("^#?([A-Fa-f0-9]{6})$");

    @OnEnable
    public void onEnable(PluginContext ctx) {
        this.context = ctx;
        this.db = ctx.getDatabaseManager();

        // 1. Define table for roles managed by this plugin
        // We track these to ensure we don't accidentally delete manual server roles.
        TableSchema roleSchema = TableSchema.builder("managed_roles")
                .column("role_id", ColumnType.BIGINT, false) // Discord Role ID
                .column("guild_id", ColumnType.BIGINT, false)
                .column("hex_code", ColumnType.STRING, 7, false)
                .index("role_id")
                .uniqueIndex("guild_id", "hex_code") // One role per color per guild
                .build();

        // 2. Define table for user assignments
        // Tracks which user has which role to enforce the "1 color per user" rule
        TableSchema assignmentSchema = TableSchema.builder("user_assignments")
                .column("user_id", ColumnType.BIGINT, false)
                .column("guild_id", ColumnType.BIGINT, false)
                .column("role_id", ColumnType.BIGINT, false)
                .index("role_id")
                .uniqueIndex("user_id", "guild_id") // Enforce 1 role per user per guild
                .build();

        // Create tables
        db.createTable(roleSchema);
        db.createTable(assignmentSchema);

        // Initialize repositories
        this.roleRepo = db.getRepository("managed_roles", ManagedRole.class);
        this.assignmentRepo = db.getRepository("user_assignments", UserAssignment.class);

        ctx.log("info", "RoleColor plugin loaded & database initialized.");
    }

    @OnShutdown
    public boolean onShutdown(PluginContext ctx) {
        // Safe to shutdown
        return true;
    }

    @SlashCommand(
            name = "rolecolor",
            description = "Set your name color or reset it.",
            options = {
                    @CommandOption(
                            name = "input",
                            description = "Hex color code (e.g. FF0000) or 'reset'",
                            type = "STRING",
                            required = true
                    )
            }
    )
    public void onRoleColor(SlashCommandInteractionEvent event) {
        // Defer reply since Discord API calls might take time
        event.deferReply().setEphemeral(true).queue();

        Guild guild = event.getGuild();
        Member member = event.getMember();
        String input = event.getOption("input").getAsString().trim();

        if (guild == null || member == null) {
            event.getHook().editOriginal("❌ This command can only be used in a server.").queue();
            return;
        }

        // Check bot permissions
        if (!guild.getSelfMember().hasPermission(Permission.MANAGE_ROLES)) {
            event.getHook().editOriginal("❌ I need the **Manage Roles** permission to function.").queue();
            return;
        }

        try {
            if (input.equalsIgnoreCase("reset")) {
                handleReset(guild, member, event);
            } else {
                handleSetColor(guild, member, input, event);
            }
        } catch (Exception e) {
            context.log("error", "Error in rolecolor: " + e.getMessage());
            event.getHook().editOriginal("❌ An internal error occurred.").queue();
        }
    }

    private void handleReset(Guild guild, Member member, SlashCommandInteractionEvent event) {
        long userId = member.getIdLong();
        long guildId = guild.getIdLong();

        // 1. Check database for existing assignment
        UserAssignment existing = findAssignment(userId, guildId);

        if (existing == null) {
            event.getHook().editOriginal("ℹ️ You don't have a color role set.").queue();
            return;
        }

        // 2. Remove role from Discord user
        removeRoleFromMember(guild, member, existing.getRoleId());

        // 3. Remove assignment from DB
        assignmentRepo.deleteById(existing.getId());

        // 4. Check if role is empty (Database Authority)
        cleanupRoleIfEmpty(guild, existing.getRoleId());

        event.getHook().editOriginal("✅ Your color role has been reset.").queue();
    }

    private void handleSetColor(Guild guild, Member member, String hexInput, SlashCommandInteractionEvent event) {
        // Validate Hex
        String cleanHex = hexInput.replace("#", "").toUpperCase();
        if (!HEX_PATTERN.matcher(cleanHex).matches()) {
            event.getHook().editOriginal("❌ Invalid format. Please use a hex code like `FF0000`.").queue();
            return;
        }

        Color color;
        try {
            color = new Color(Integer.parseInt(cleanHex, 16));
        } catch (NumberFormatException e) {
            event.getHook().editOriginal("❌ Invalid color code.").queue();
            return;
        }

        long userId = member.getIdLong();
        long guildId = guild.getIdLong();

        // 1. Clean up OLD role if exists
        UserAssignment existing = findAssignment(userId, guildId);
        if (existing != null) {
            removeRoleFromMember(guild, member, existing.getRoleId());
            assignmentRepo.deleteById(existing.getId());
            cleanupRoleIfEmpty(guild, existing.getRoleId());
        }

        // 2. Get or Create NEW role
        ManagedRole targetRoleInfo = findRoleByColor(guildId, cleanHex);
        Role tempRole; // Temporary non-final variable

        if (targetRoleInfo != null) {
            tempRole = guild.getRoleById(targetRoleInfo.getRoleId());

            // If DB says it exists but Discord doesn't, recreate it
            if (tempRole == null) {
                roleRepo.deleteById(targetRoleInfo.getId());
                tempRole = createDiscordRole(guild, cleanHex, color);
                saveManagedRole(guildId, tempRole.getIdLong(), cleanHex);
            }
        } else {
            tempRole = createDiscordRole(guild, cleanHex, color);
            saveManagedRole(guildId, tempRole.getIdLong(), cleanHex);
        }

        // 3. Create a FINAL copy for the lambda
        final Role discordRole = tempRole;

        // 4. Assign NEW role
        guild.addRoleToMember(member, discordRole).queue(
                success -> {
                    // Now using the 'effectively final' variable
                    UserAssignment newAssignment = new UserAssignment();
                    newAssignment.setUserId(userId);
                    newAssignment.setGuildId(guildId);
                    newAssignment.setRoleId(discordRole.getIdLong());

                    assignmentRepo.save(newAssignment);

                    event.getHook().editOriginal("✅ Color set to `#" + cleanHex + "`!").queue();
                },
                failure -> {
                    event.getHook().editOriginal("❌ Failed to assign role. Please check my role hierarchy.").queue();
                }
        );
    }

    /**
     * Checks if a managed role has 0 assigned users in the DATABASE.
     * If so, deletes it from Discord and the Database.
     */
    private void cleanupRoleIfEmpty(Guild guild, long roleId) {
        // 1. Check DB count
        long userCount = assignmentRepo.query()
                .where("role_id", roleId)
                .count();

        if (userCount == 0) {
            // 2. Clean up DB immediately (So future checks see it as gone)
            List<ManagedRole> roles = roleRepo.findBy("role_id", roleId);
            for (ManagedRole r : roles) {
                roleRepo.deleteById(r.getId());
            }

            // 3. Delete from Discord with Error Handling
            Role role = guild.getRoleById(roleId);
            if (role != null) {
                // WE USE CALLBACKS HERE TO PREVENT STACK TRACES
                role.delete().reason("RoleColor: Unused managed role").queue(
                        success -> context.log("info", "Deleted unused role ID: " + roleId),
                        failure -> {
                            // If it fails (404 or 500), it's likely a race condition.
                            // Since we already cleaned the DB, we can ignore this.
                            context.log("debug", "Role cleanup race ignored: " + failure.getMessage());
                        }
                );
            }
        }
    }

    private Role createDiscordRole(Guild guild, String hex, Color color) {
        return guild.createRole()
                .setName("#" + hex)
                .setColor(color)
                .setPermissions() // Empty permissions
                .complete();
    }

    private void removeRoleFromMember(Guild guild, Member member, long roleId) {
        Role role = guild.getRoleById(roleId);
        if (role != null) {
            guild.removeRoleFromMember(member, role).reason("RoleColor: Changing color").queue();
        }
    }

    // --- DB Helper Methods ---

    private UserAssignment findAssignment(long userId, long guildId) {
        List<UserAssignment> list = assignmentRepo.query()
                .where("user_id", userId)
                .where("guild_id", guildId)
                .list();
        return list.isEmpty() ? null : list.getFirst();
    }

    private ManagedRole findRoleByColor(long guildId, String hex) {
        List<ManagedRole> list = roleRepo.query()
                .where("guild_id", guildId)
                .where("hex_code", hex)
                .list();
        return list.isEmpty() ? null : list.getFirst();
    }

    private void saveManagedRole(long guildId, long roleId, String hex) {
        ManagedRole mr = new ManagedRole();
        mr.setGuildId(guildId);
        mr.setRoleId(roleId);
        mr.setHexCode(hex);
        roleRepo.save(mr);
    }

    // --- Entities ---

    @Entity
    public static class ManagedRole {
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

    @Entity
    public static class UserAssignment {
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
}