package group.worldstandard.pudel.plugin.handler;

import group.worldstandard.pudel.plugin.builder.PanelBuilder;
import group.worldstandard.pudel.plugin.service.PermissionService;
import group.worldstandard.pudel.plugin.session.SessionManager;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;

/**
 * Handles modal interactions in a flat, non-waterfall structure.
 * Each modal ID maps to a single handler method with early returns.
 */
public class ModalHandler {

    private final String modalPrefix;
    private final PermissionService permissionService;
    private final SessionManager sessionManager;
    private final PanelBuilder panelBuilder;
    private final ModalLogger logger;

    public ModalHandler(String modalPrefix, PermissionService permissionService,
                        SessionManager sessionManager, PanelBuilder panelBuilder, ModalLogger logger) {
        this.modalPrefix = modalPrefix;
        this.permissionService = permissionService;
        this.sessionManager = sessionManager;
        this.panelBuilder = panelBuilder;
        this.logger = logger;
    }

    public void handle(ModalInteractionEvent event) {
        var guild = event.getGuild();
        var member = event.getMember();
        if (guild == null || member == null) return;

        var modalId = event.getModalId().substring(modalPrefix.length());
        var userId = event.getUser().getId();

        try {
            switch (modalId) {
                case "create_name" -> handleCreateName(event, userId, guild);
                case "priv_add" -> handleAddPrivilege(event, guild, member);
                case "profile_create" -> handleCreateProfile(event, guild, member);
                default -> logger.warn("Unknown modal: " + modalId);
            }
        } catch (Exception e) {
            logger.error("Modal error: " + e.getMessage(), e);
            event.reply("❌ An error occurred: " + e.getMessage())
                    .setEphemeral(true).queue(m -> m.deleteOriginal().queueAfter(5, java.util.concurrent.TimeUnit.SECONDS));
        }
    }

    // ==================== CREATE NAME ====================

    private void handleCreateName(ModalInteractionEvent event, String userId,
                                   Guild guild) {
        var name = getModalString(event, "name").trim();
        if (!name.isEmpty()) {
            var state = sessionManager.getCreateFormState(userId);
            if (state != null) {
                state.put("name", name);
            }
        }
        var msg = sessionManager.getControlMessage(userId);
        if (msg != null) {
            msg.editMessage(panelBuilder.buildCreatePanel(guild.getId(), userId).build()).queue();
        }
        event.deferEdit().queue();
    }

    // ==================== ADD PRIVILEGE ====================

    private void handleAddPrivilege(ModalInteractionEvent event,
                                     Guild guild,
                                     Member member) {
        var roleId = getModalFirstId(event, "role");
        if (roleId == null) {
            event.reply("❌ Please select a role!").setEphemeral(true)
                    .queue(m -> m.deleteOriginal().queueAfter(5, java.util.concurrent.TimeUnit.SECONDS));
            return;
        }

        if (!permissionService.addPrivilegeRole(guild.getId(), roleId)) {
            event.reply("❌ This role is already a privilege role!").setEphemeral(true)
                    .queue(m -> m.deleteOriginal().queueAfter(5, java.util.concurrent.TimeUnit.SECONDS));
            return;
        }

        var role = guild.getRoleById(roleId);
        var roleName = role != null ? role.getName() : roleId;

        event.reply("✅ Role **" + roleName + "** added as privilege role!").setEphemeral(true)
                .queue(m -> m.deleteOriginal().queueAfter(5, java.util.concurrent.TimeUnit.SECONDS));

        var msg = sessionManager.getControlMessage(member.getId());
        if (msg != null) {
            boolean hasAuth = member.hasPermission(Permission.ADMINISTRATOR);
            msg.editMessage(panelBuilder.buildPrivilegePanel(guild, hasAuth).build()).queue(null, _ -> {});
        }
    }

    // ==================== CREATE PROFILE ====================

    private void handleCreateProfile(ModalInteractionEvent event,
                                      Guild guild,
                                      Member member) {
        if (!permissionService.hasPermissionOrPrivilege(member, guild.getId())) {
            event.reply("❌ You do not have permission to create permission profiles!").setEphemeral(true)
                    .queue(m -> m.deleteOriginal().queueAfter(5, java.util.concurrent.TimeUnit.SECONDS));
            return;
        }

        var profileName = getModalString(event, "profile_name").trim();
        var guildId = guild.getId();

        if (profileName.isEmpty()) {
            event.reply("❌ Profile name cannot be empty!").setEphemeral(true)
                    .queue(m -> m.deleteOriginal().queueAfter(5, java.util.concurrent.TimeUnit.SECONDS));
            return;
        }

        if (!permissionService.createProfile(guildId, profileName)) {
            event.reply("❌ A profile with name **" + profileName + "** already exists!").setEphemeral(true)
                    .queue(m -> m.deleteOriginal().queueAfter(5, java.util.concurrent.TimeUnit.SECONDS));
            return;
        }

        event.reply("✅ Profile **" + profileName + "** created! Use **View Profile** to edit its permissions.")
                .setEphemeral(true).queue(m -> m.deleteOriginal().queueAfter(5, java.util.concurrent.TimeUnit.SECONDS));

        var msg = sessionManager.getControlMessage(member.getId());
        if (msg != null) {
            msg.editMessage(panelBuilder.buildSettingPanel(guild, member).build()).queue(null, _ -> {});
        }
    }

    // ==================== HELPERS ====================

    private String getModalString(ModalInteractionEvent event, String id) {
        var v = event.getValue(id);
        return v != null ? v.getAsString() : "";
    }

    private String getModalFirstId(ModalInteractionEvent event, String id) {
        var v = event.getValue(id);
        if (v == null) return null;
        var list = v.getAsStringList();
        return !list.isEmpty() ? list.getFirst() : null;
    }

    // Logger interface
    public interface ModalLogger {
        void warn(String message);
        void error(String message, Throwable t);
    }
}