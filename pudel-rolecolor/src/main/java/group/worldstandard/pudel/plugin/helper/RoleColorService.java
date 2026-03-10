package group.worldstandard.pudel.plugin.helper;

import group.worldstandard.pudel.api.PluginContext;
import group.worldstandard.pudel.api.database.PluginRepository;
import group.worldstandard.pudel.plugin.entity.ManagedRole;
import group.worldstandard.pudel.plugin.entity.UserAssignment;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;

import java.awt.Color;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Service layer for role color management.
 * Handles role creation, assignment, cleanup, and database operations.
 */
public class RoleColorService {

    private final PluginContext context;
    private final PluginRepository<ManagedRole> roleRepo;
    private final PluginRepository<UserAssignment> assignmentRepo;

    public RoleColorService(PluginContext context,
                            PluginRepository<ManagedRole> roleRepo,
                            PluginRepository<UserAssignment> assignmentRepo) {
        this.context = context;
        this.roleRepo = roleRepo;
        this.assignmentRepo = assignmentRepo;
    }

    public void handleReset(Guild guild, Member member, SlashCommandInteractionEvent event) {
        long userId = member.getIdLong();
        long guildId = guild.getIdLong();

        UserAssignment existing = findAssignment(userId, guildId);

        if (existing == null) {
            event.getHook().editOriginal("ℹ️ You don't have a color role set.").queue();
            return;
        }

        removeRoleFromMember(guild, member, existing.getRoleId());
        assignmentRepo.deleteById(existing.getId());
        cleanupRoleIfEmpty(guild, existing.getRoleId());

        event.getHook().editOriginal("✅ Your color role has been reset.").queue();
    }

    public void handleSetColor(Guild guild, Member member, String cleanHex, Color color,
                                SlashCommandInteractionEvent event) {
        long userId = member.getIdLong();
        long guildId = guild.getIdLong();

        // Clean up OLD role if exists
        UserAssignment existing = findAssignment(userId, guildId);
        if (existing != null) {
            removeRoleFromMember(guild, member, existing.getRoleId());
            assignmentRepo.deleteById(existing.getId());
            cleanupRoleIfEmpty(guild, existing.getRoleId());
        }

        // Get or Create NEW role
        ManagedRole targetRoleInfo = findRoleByColor(guildId, cleanHex);
        Role tempRole;

        if (targetRoleInfo != null) {
            tempRole = guild.getRoleById(targetRoleInfo.getRoleId());

            if (tempRole == null) {
                roleRepo.deleteById(targetRoleInfo.getId());
                tempRole = createDiscordRole(guild, cleanHex, color);
                saveManagedRole(guildId, tempRole.getIdLong(), cleanHex);
            }
        } else {
            tempRole = createDiscordRole(guild, cleanHex, color);
            saveManagedRole(guildId, tempRole.getIdLong(), cleanHex);
        }

        final Role discordRole = tempRole;

        guild.addRoleToMember(member, discordRole).queue(
                success -> {
                    UserAssignment newAssignment = new UserAssignment();
                    newAssignment.setUserId(userId);
                    newAssignment.setGuildId(guildId);
                    newAssignment.setRoleId(discordRole.getIdLong());
                    assignmentRepo.save(newAssignment);

                    event.getHook().editOriginal("✅ Color set to `#" + cleanHex + "`!").queue(m -> m.delete().queueAfter(5, TimeUnit.SECONDS));
                },
                failure -> event.getHook().editOriginal("❌ Failed to assign role. Please check my role hierarchy.").queue(m -> m.delete().queueAfter(5, TimeUnit.SECONDS))
        );
    }

    /**
     * Checks if a managed role has 0 assigned users in the DATABASE.
     * If so, deletes it from Discord and the Database.
     */
    private void cleanupRoleIfEmpty(Guild guild, long roleId) {
        long userCount = assignmentRepo.query()
                .where("role_id", roleId)
                .count();

        if (userCount == 0) {
            List<ManagedRole> roles = roleRepo.findBy("role_id", roleId);
            for (ManagedRole r : roles) {
                roleRepo.deleteById(r.getId());
            }

            Role role = guild.getRoleById(roleId);
            if (role != null) {
                role.delete().reason("RoleColor: Unused managed role").queue(
                        success -> context.log("info", "Deleted unused role ID: " + roleId),
                        failure -> context.log("debug", "Role cleanup race ignored: " + failure.getMessage())
                );
            }
        }
    }

    private Role createDiscordRole(Guild guild, String hex, Color color) {
        return guild.createRole()
                .setName("#" + hex)
                .setColor(color)
                .setPermissions()
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
}

