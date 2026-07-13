package group.worldstandard.pudel.plugin.handler;

import group.worldstandard.pudel.plugin.builder.PanelBuilder;
import group.worldstandard.pudel.plugin.entity.CategoryEntry;
import group.worldstandard.pudel.plugin.service.PermissionService;
import group.worldstandard.pudel.plugin.session.SessionManager;
import java.util.LinkedHashMap;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.channel.concrete.Category;
import net.dv8tion.jda.api.events.interaction.component.StringSelectInteractionEvent;

import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Handles string select menu interactions with flat dispatch.
 */
public class SelectMenuHandler {

    private final String stringMenuPrefix;
    private final PermissionService permissionService;
    private final SessionManager sessionManager;
    private final PanelBuilder panelBuilder;

    public SelectMenuHandler(String stringMenuPrefix, PermissionService permissionService,
                             SessionManager sessionManager, PanelBuilder panelBuilder) {
        this.stringMenuPrefix = stringMenuPrefix;
        this.permissionService = permissionService;
        this.sessionManager = sessionManager;
        this.panelBuilder = panelBuilder;
    }

    public void handle(StringSelectInteractionEvent event) {
        Guild guild = event.getGuild();
        Member member = event.getMember();
        if (guild == null || member == null) return;

        String menuId = event.getComponentId().substring(stringMenuPrefix.length());
        String selected = event.getValues().getFirst();
        String userId = member.getId();
        String guildId = guild.getId();
        boolean hasAuth = permissionService.hasPermissionOrPrivilege(member, guildId);

        switch (menuId) {
            case "view_cat" -> event.editMessage(panelBuilder.buildCategoryDetailPanel(guild, selected).build()).queue();
            case "unlink_cat" -> handleUnlinkCategory(event, guild, member, selected);
            case "priv_rm" -> handleRemovePrivilege(event, guild, member, selected);
            case "profile_view" -> handleProfileView(event, userId, guildId, hasAuth);
            case "profile_rm" -> handleProfileRemove(event, guild, member, guildId, selected);
            case "create_manager_profile" -> handleCreateManagerProfile(event, userId, guildId, selected);
            case "create_role_profile" -> handleCreateRoleProfile(event, userId, guildId, selected);
            case "import_manager_profile" -> handleImportManagerProfile(event, userId, guildId, selected);
            case "import_role_profile" -> handleImportRoleProfile(event, userId, guildId, selected);
        }
    }

    // ==================== VIEW CATEGORY ====================

    private void handleProfileView(StringSelectInteractionEvent event, String userId,
                                    String guildId, boolean hasAuth) {
        sessionManager.setEditingProfileName(userId, event.getValues().getFirst());
        sessionManager.setActivePermPage(userId, 0);
        String profileName = event.getValues().getFirst();
        LinkedHashMap<String, String> state = permissionService.loadProfilePermState(guildId, profileName);
        sessionManager.putTempPermState(userId, state);
        // Store original state for diff comparison
        sessionManager.putOriginalPermState(userId, new LinkedHashMap<>(state));
        event.editMessage(panelBuilder.buildPermissionPanel(userId, hasAuth).build()).queue();
    }

    // ==================== UNLINK CATEGORY ====================

    private void handleUnlinkCategory(StringSelectInteractionEvent event,
                                       Guild guild,
                                       Member member,
                                       String categoryId) {
        CategoryEntry entry = permissionService.findCategoryEntry(categoryId);
        if (entry == null) {
            event.reply("❌ Category record not found!").setEphemeral(true)
                    .queue(m -> m.deleteOriginal().queueAfter(5, TimeUnit.SECONDS));
            return;
        }

        String userId = member.getId();
        boolean isManager = entry.getManager_id() != null && entry.getManager_id().equals(userId);
        if (!permissionService.hasPermissionOrPrivilege(member, guild.getId()) && !isManager) {
            event.reply("❌ You don't have permission to perform this action!")
                    .setEphemeral(true).queue(m -> m.deleteOriginal().queueAfter(5, TimeUnit.SECONDS));
            return;
        }

        permissionService.deleteCategoryEntry(entry.getId());

        Category cat = guild.getCategoryById(categoryId);
        String catName = cat != null ? cat.getName() : categoryId;

        event.reply("✅ Category **" + catName + "** unlinked from Pudel (category kept in guild).")
                .setEphemeral(true).queue(m -> m.deleteOriginal().queueAfter(5, TimeUnit.SECONDS));

        Message msg = sessionManager.getControlMessage(userId);
        if (msg != null) {
            msg.editMessage(panelBuilder.editMainPanel(guild, member).build()).queue(null, _ -> {});
        }
    }

    // ==================== REMOVE PRIVILEGE ====================

    private void handleRemovePrivilege(StringSelectInteractionEvent event,
                                         Guild guild,
                                         Member member,
                                         String roleId) {
        permissionService.removePrivilegeRole(guild.getId(), roleId);
        boolean hasAuth = member.hasPermission(Permission.ADMINISTRATOR);
        event.editMessage(panelBuilder.buildPrivilegePanel(guild, hasAuth).build()).queue();
    }

    // ==================== REMOVE PROFILE ====================

    private void handleProfileRemove(StringSelectInteractionEvent event,
                                      Guild guild,
                                      Member member,
                                      String guildId, String selected) {
        if (!permissionService.hasPermissionOrPrivilege(member, guildId)) {
            event.reply("❌ You do not have permission to remove permission profiles!").setEphemeral(true)
                    .queue(m -> m.deleteOriginal().queueAfter(5, TimeUnit.SECONDS));
            return;
        }

        permissionService.deleteProfile(guildId, selected);
        event.editMessage(panelBuilder.buildSettingPanel(guild, member).build()).queue();
    }

    // ==================== CREATE PANEL PROFILE SELECTORS ====================

    private void handleCreateManagerProfile(StringSelectInteractionEvent event, String userId,
                                             String guildId, String selected) {
        Map<String, String> state = sessionManager.getCreateFormState(userId);
        if (state != null) {
            state.put("manager_profile", selected);
            event.editMessage(panelBuilder.buildCreatePanel(guildId, userId).build()).queue();
        }
    }

    private void handleCreateRoleProfile(StringSelectInteractionEvent event, String userId,
                                          String guildId, String selected) {
        Map<String, String> state = sessionManager.getCreateFormState(userId);
        if (state != null) {
            state.put("role_profile", selected);
            event.editMessage(panelBuilder.buildCreatePanel(guildId, userId).build()).queue();
        }
    }

    // ==================== IMPORT PANEL PROFILE SELECTORS ====================

    private void handleImportManagerProfile(StringSelectInteractionEvent event, String userId,
                                              String guildId, String selected) {
        Map<String, String> state = sessionManager.getImportFormState(userId);
        if (state != null) {
            state.put("manager_profile", selected);
            event.editMessage(panelBuilder.buildImportPanel(guildId, userId).build()).queue();
        }
    }

    private void handleImportRoleProfile(StringSelectInteractionEvent event, String userId,
                                           String guildId, String selected) {
        Map<String, String> state = sessionManager.getImportFormState(userId);
        if (state != null) {
            state.put("role_profile", selected);
            event.editMessage(panelBuilder.buildImportPanel(guildId, userId).build()).queue();
        }
    }
}