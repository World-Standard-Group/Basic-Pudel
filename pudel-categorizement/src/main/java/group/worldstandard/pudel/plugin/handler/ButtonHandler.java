package group.worldstandard.pudel.plugin.handler;

import group.worldstandard.pudel.plugin.builder.PanelBuilder;
import group.worldstandard.pudel.plugin.entity.CategoryEntry;
import group.worldstandard.pudel.plugin.entity.PermissionProfile;
import group.worldstandard.pudel.plugin.service.PermissionService;
import group.worldstandard.pudel.plugin.session.SessionManager;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.components.label.Label;
import net.dv8tion.jda.api.components.selections.EntitySelectMenu;
import net.dv8tion.jda.api.components.textdisplay.TextDisplay;
import net.dv8tion.jda.api.components.textinput.TextInput;
import net.dv8tion.jda.api.components.textinput.TextInputStyle;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.channel.attribute.ICategorizableChannel;
import net.dv8tion.jda.api.entities.channel.concrete.Category;
import net.dv8tion.jda.api.entities.channel.middleman.GuildChannel;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.modals.Modal;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Handles button interactions using the strategy pattern.
 * Each logical group of buttons is delegated to a focused handler method.
 */
public class ButtonHandler {

    private final String btnPrefix;
    private final PermissionService permissionService;
    private final SessionManager sessionManager;
    private final PanelBuilder panelBuilder;

    public ButtonHandler(String btnPrefix, PermissionService permissionService,
                         SessionManager sessionManager, PanelBuilder panelBuilder) {
        this.btnPrefix = btnPrefix;
        this.permissionService = permissionService;
        this.sessionManager = sessionManager;
        this.panelBuilder = panelBuilder;
    }

    /**
     * Main entry point — dispatches to sub-handlers based on button ID prefix groups.
     */
    public void handle(ButtonInteractionEvent event) {
        Guild guild = event.getGuild();
        Member member = event.getMember();
        if (guild == null || member == null) return;

        String userId = event.getUser().getId();
        String guildId = guild.getId();
        String buttonId = event.getComponentId().substring(btnPrefix.length());
        boolean hasAuth = permissionService.hasPermissionOrPrivilege(member, guildId);

        // Route to appropriate sub-handler
        if (isMainPanelButton(buttonId)) {
            handleMainPanel(event, userId, guildId, guild, member, hasAuth);
        } else if (isCreatePanelButton(buttonId)) {
            handleCreatePanel(event, userId, guildId, guild, member, hasAuth);
        } else if (isImportPanelButton(buttonId)) {
            handleImportPanel(event, userId, guildId, guild, member);
        } else if (isSettingPanelButton(buttonId)) {
            handleSettingPanel(event, userId, guildId, guild, member, hasAuth);
        } else if (isPermissionPanelButton(buttonId)) {
            handlePermissionPanel(event, userId, guildId, guild, member, hasAuth);
        } else if (isPrivilegePanelButton(buttonId)) {
            handlePrivilegePanel(event, guild, member, hasAuth);
        } else if (buttonId.equals("back_main")) {
            handleBackMain(event, userId, guild, member);
        }
    }

    // ==================== BUTTON ID CLASSIFICATION ====================

    private boolean isMainPanelButton(String id) {
        return id.equals("create") || id.equals("import") || id.equals("setting")
                || id.equals("back_setting") || id.equals("view") || id.equals("back_view") || id.equals("unlink");
    }

    private boolean isCreatePanelButton(String id) {
        return id.startsWith("create_");
    }

    private boolean isImportPanelButton(String id) {
        return id.startsWith("import_");
    }

    private boolean isSettingPanelButton(String id) {
        return id.startsWith("profile_") || id.equals("privilege") || id.equals("back_privilege");
    }

    private boolean isPermissionPanelButton(String id) {
        return id.startsWith("perm_")
                || id.equals("confirm") || id.equals("perm_reset") || id.equals("perm_back")
                || id.equals("confirm_save") || id.equals("confirm_cancel");
    }

    private boolean isPrivilegePanelButton(String id) {
        return id.equals("priv_add") || id.equals("priv_remove");
    }

    // ==================== MAIN PANEL ====================

    private void handleMainPanel(ButtonInteractionEvent event, String userId, String guildId,
                                  Guild guild,
                                  Member member, boolean hasAuth) {
        switch (event.getComponentId().substring(btnPrefix.length())) {
            case "create" -> {
                if (!hasAuth) { replyNoPermission(event); return; }
                sessionManager.putCreateFormState(userId, new LinkedHashMap<>());
                sessionManager.getCreateFormState(userId).put("controlBy", "false");
                event.editMessage(panelBuilder.buildCreatePanel(guildId, userId).build()).queue();
            }
            case "import" -> {
                if (!hasAuth) { replyNoPermission(event); return; }
                sessionManager.putImportFormState(userId, new LinkedHashMap<>());
                sessionManager.getImportFormState(userId).put("acknowledged", "false");
                event.editMessage(panelBuilder.buildImportPanel(guildId, userId).build()).queue();
            }
            case "setting", "back_setting" -> event.editMessage(panelBuilder.buildSettingPanel(guild, member).build()).queue();
            case "view", "back_view" -> event.editMessage(panelBuilder.buildViewCategoryPanel(guild).build()).queue();
            case "unlink" -> event.editMessage(panelBuilder.buildUnlinkPanel(guild, member).build()).queue();
        }
    }

    // ==================== CREATE PANEL ====================

    private void handleCreatePanel(ButtonInteractionEvent event, String userId, String guildId,
                                    Guild guild,
                                    Member member, boolean hasAuth) {
        String buttonId = event.getComponentId().substring(btnPrefix.length());
        switch (buttonId) {
            case "create_set_name" -> handleCreateSetName(event);
            case "create_toggle_control" -> handleCreateToggleControl(event, userId, guildId);
            case "create_confirm" -> handleCreateConfirm(event, userId, guildId, guild, member);
            case "create_cancel" -> handleCreateCancel(event, userId, guild, member);
        }
    }

    private void handleCreateSetName(ButtonInteractionEvent event) {
        TextInput nameInput = TextInput.create("name",
                TextInputStyle.SHORT)
                .setPlaceholder("e.g. Staff Area, Private Channels")
                .setMinLength(1).setMaxLength(100).setRequired(true).build();
        event.replyModal(Modal.create(
                btnPrefix.replace("button", "modal") + "create_name", "Set Category Name")
                .addComponents(Label.of("Category Name", nameInput)).build()
        ).queue();
    }

    private void handleCreateToggleControl(ButtonInteractionEvent event, String userId, String guildId) {
        Map<String, String> state = sessionManager.getCreateFormState(userId);
        if (state != null) {
            boolean current = Boolean.parseBoolean(state.getOrDefault("controlBy", "false"));
            state.put("controlBy", String.valueOf(!current));
            event.editMessage(panelBuilder.buildCreatePanel(guildId, userId).build()).queue();
        }
    }

    private void handleCreateConfirm(ButtonInteractionEvent event, String userId, String guildId,
                                      Guild guild,
                                      Member member) {
        Map<String, String> state = sessionManager.getCreateFormState(userId);
        if (state == null) return;
        String name = state.getOrDefault("name", "").trim();
        if (name.isEmpty()) {
            event.reply("❌ Category name cannot be empty!").setEphemeral(true)
                    .queue(m -> m.deleteOriginal().queueAfter(5, TimeUnit.SECONDS));
            return;
        }
        String managerId = state.get("manager");
        String roleId = state.get("default_role");
        String managerProfileName = state.get("manager_profile");
        String roleProfileName = state.get("role_profile");
        boolean controlPudel = Boolean.parseBoolean(state.getOrDefault("controlBy", "false"));

        PermissionProfile managerProfile = permissionService.findProfile(guildId, managerProfileName);
        PermissionProfile roleProfile = permissionService.findProfile(guildId, roleProfileName);

        event.deferEdit().queue(hook -> guild.createCategory(name).queue(category -> {
            permissionService.applyPermissions(category, guild, managerId, roleId, managerProfile, roleProfile);
            if (controlPudel) {
                permissionService.saveCategoryEntry(
                        new group.worldstandard.pudel.plugin.entity.CategoryEntry(
                                null, guildId, category.getId(), managerId, managerProfileName, roleId, roleProfileName));
            }
            sessionManager.removeCreateFormState(userId);
            hook.editOriginalComponents(
                    TextDisplay.of(
                            "✅ Category **" + name + "** imported and tracked!" + (controlPudel ? " (Tracked by Pudel)" : "")))
                    .queue(m -> m.delete().queueAfter(5, TimeUnit.SECONDS));
            refreshMainPanel(userId, guild, member);
        }, err -> hook.editOriginal("❌ Failed to create category: " + err.getMessage())
                .queue(m -> m.delete().queueAfter(5, TimeUnit.SECONDS))));
    }

    private void handleCreateCancel(ButtonInteractionEvent event, String userId,
                                     Guild guild,
                                     Member member) {
        sessionManager.removeCreateFormState(userId);
        event.editMessage(panelBuilder.editMainPanel(guild, member).build()).queue();
    }

    // ==================== IMPORT PANEL ====================

    private void handleImportPanel(ButtonInteractionEvent event, String userId, String guildId,
                                    Guild guild,
                                    Member member) {
        String buttonId = event.getComponentId().substring(btnPrefix.length());
        switch (buttonId) {
            case "import_toggle_ack" -> handleImportToggleAck(event, userId, guildId);
            case "import_confirm" -> handleImportConfirm(event, userId, guildId, guild, member);
            case "import_cancel" -> handleImportCancel(event, userId, guild, member);
        }
    }

    private void handleImportToggleAck(ButtonInteractionEvent event, String userId, String guildId) {
        var state = sessionManager.getImportFormState(userId);
        if (state != null) {
            boolean current = Boolean.parseBoolean(state.getOrDefault("acknowledged", "false"));
            state.put("acknowledged", String.valueOf(!current));
            event.editMessage(panelBuilder.buildImportPanel(guildId, userId).build()).queue();
        }
    }

    private void handleImportConfirm(ButtonInteractionEvent event, String userId, String guildId,
                                      Guild guild,
                                      Member member) {
        Map<String, String> state = sessionManager.getImportFormState(userId);
        if (state == null) return;
        String categoryId = state.get("category");
        if (categoryId == null) {
            event.reply("❌ Please select a category!").setEphemeral(true)
                    .queue(m -> m.deleteOriginal().queueAfter(5, TimeUnit.SECONDS));
            return;
        }
        if (permissionService.isCategoryTracked(categoryId)) {
            event.reply("❌ This category is already tracked by Pudel!").setEphemeral(true)
                    .queue(m -> m.deleteOriginal().queueAfter(5, TimeUnit.SECONDS));
            return;
        }
        String managerId = state.get("manager");
        String roleId = state.get("default_role");
        String managerProfileName = state.get("manager_profile");
        String roleProfileName = state.get("role_profile");
        boolean acknowledged = Boolean.parseBoolean(state.getOrDefault("acknowledged", "false"));
        boolean hasManagerOrRole = (managerId != null) || (roleId != null);
        if (hasManagerOrRole && !acknowledged) {
            event.reply("❌ You must acknowledge the warning when setting a Manager User or Default Role!")
                    .setEphemeral(true).queue(m -> m.deleteOriginal().queueAfter(5, TimeUnit.SECONDS));
            return;
        }

        Category category = guild.getCategoryById(categoryId);
        if (category == null) {
            event.reply("❌ Category not found in this server!").setEphemeral(true)
                    .queue(m -> m.deleteOriginal().queueAfter(5, TimeUnit.SECONDS));
            return;
        }

        PermissionProfile managerProfile = permissionService.findProfile(guildId, managerProfileName);
        PermissionProfile roleProfile = permissionService.findProfile(guildId, roleProfileName);
        String catName = category.getName();

        event.deferEdit().queue(hook -> {
            permissionService.applyPermissions(category, guild, managerId, roleId, managerProfile, roleProfile);
            if (hasManagerOrRole) {
                for (GuildChannel ch : category.getChannels()) {
                    if (ch instanceof ICategorizableChannel catCh) {
                        catCh.getManager().sync(category).queue(null, _ -> {});
                    }
                }
            }
            permissionService.saveCategoryEntry(
                    new CategoryEntry(
                            null, guildId, categoryId, managerId, managerProfileName, roleId, roleProfileName));
            sessionManager.removeImportFormState(userId);
            hook.editOriginalComponents(TextDisplay.of("✅ Category **" + catName + "** imported and tracked!"))
                    .queue(m -> event.editMessage(panelBuilder.editMainPanel(guild, member).build()).queueAfter(5, TimeUnit.SECONDS));
            refreshMainPanel(userId, guild, member);
        });
    }

    private void handleImportCancel(ButtonInteractionEvent event, String userId, Guild guild, Member member) {
        sessionManager.removeImportFormState(userId);
        event.editMessage(panelBuilder.editMainPanel(guild, member).build()).queue();
    }

    // ==================== SETTING PANEL ====================

    private void handleSettingPanel(ButtonInteractionEvent event, String userId, String guildId,
                                    Guild guild,
                                    Member member, boolean hasAuth) {
        String buttonId = event.getComponentId().substring(btnPrefix.length());
        switch (buttonId) {
            case "profile_view" -> event.editMessage(panelBuilder.buildViewProfileSelectPanel(guildId).build()).queue();
            case "profile_create" -> {
                if (!hasAuth) { replyNoPermission(event); return; }
                handleCreateProfileModal(event);
            }
            case "profile_remove" -> {
                if (!hasAuth) { replyNoPermission(event); return; }
                event.editMessage(panelBuilder.buildRemoveProfileSelectPanel(guildId).build()).queue();
            }
            case "privilege", "back_privilege" -> event.editMessage(panelBuilder.buildPrivilegePanel(guild, hasAuth).build()).queue();
        }
    }

    private void handleCreateProfileModal(ButtonInteractionEvent event) {
        TextInput nameInput = TextInput.create("profile_name",
                TextInputStyle.SHORT)
                .setPlaceholder("e.g. Manager Default, Read Only")
                .setMinLength(1).setMaxLength(50).setRequired(true).build();
        event.replyModal(Modal.create(
                btnPrefix.replace("button", "modal") + "profile_create", "Create Permission Profile")
                .addComponents(Label.of("Profile Name", nameInput)).build()
        ).queue();
    }

    // ==================== PERMISSION PANEL ====================

    private void handlePermissionPanel(ButtonInteractionEvent event, String userId, String guildId,
                                        Guild guild,
                                        Member member, boolean hasAuth) {
        String buttonId = event.getComponentId().substring(btnPrefix.length());

        // Handle pagination: previous / next page
        if (buttonId.equals("perm_prev") || buttonId.equals("perm_next")) {
            int page = sessionManager.getActivePermPage(userId);
            sessionManager.setActivePermPage(userId, page + (buttonId.equals("perm_next") ? 1 : -1));
            event.editMessage(panelBuilder.buildPermissionPanel(userId, hasAuth).build()).queue();
            return;
        }

        // Handle permission state toggle (perm_<PERM_NAME>)
        if (buttonId.startsWith("perm_")) {
            if (!hasAuth) { replyNoPermission(event); return; }
            String permName = buttonId.substring("perm_".length());
            togglePermState(userId, permName);
            event.editMessage(panelBuilder.buildPermissionPanel(userId, hasAuth).build()).queue();
            return;
        }

        switch (buttonId) {
            case "confirm" -> {
                if (!hasAuth) { replyNoPermission(event); return; }
                // Show confirmation panel with diff
                event.editMessage(panelBuilder.buildPermissionConfirmPanel(userId, hasAuth).build()).queue();
            }
            case "perm_reset" -> {
                if (!hasAuth) { replyNoPermission(event); return; }
                // Reset all permissions back to original state
                String profileName = sessionManager.getEditingProfileName(userId);
                if (profileName != null) {
                    LinkedHashMap<String, String> originalState = permissionService.loadProfilePermState(guildId, profileName);
                    LinkedHashMap<String, String> currentState = sessionManager.getTempPermState(userId);
                    if (currentState != null) {
                        currentState.clear();
                        currentState.putAll(originalState);
                    }
                }
                event.editMessage(panelBuilder.buildPermissionPanel(userId, hasAuth).build()).queue();
            }
            case "perm_back" -> {
                sessionManager.removeActivePermPage(userId);
                sessionManager.removeEditingProfileName(userId);
                sessionManager.removeTempPermState(userId);
                sessionManager.removeOriginalPermState(userId);
                event.editMessage(panelBuilder.buildSettingPanel(guild, member).build()).queue();
            }
            case "confirm_save" -> {
                if (!hasAuth) { replyNoPermission(event); return; }
                saveProfilePermState(userId, guildId);
                event.editMessage(panelBuilder.buildSettingPanel(guild, member).build()).queue();
            }
            case "confirm_cancel" -> // Go back to permission page
                    event.editMessage(panelBuilder.buildPermissionPanel(userId, hasAuth).build()).queue();
        }
    }

    // ==================== PRIVILEGE PANEL ====================

    private void handlePrivilegePanel(ButtonInteractionEvent event,
                                       Guild guild,
                                       Member member, boolean hasAuth) {
        String buttonId = event.getComponentId().substring(btnPrefix.length());
        if (buttonId.equals("priv_add")) {
            if (!member.hasPermission(Permission.ADMINISTRATOR)) {
                replyNoPermission(event);
                return;
            }
            showAddPrivilegeModal(event);
        } else if (buttonId.equals("priv_remove")) {
            if (!member.hasPermission(Permission.ADMINISTRATOR)) {
                replyNoPermission(event);
                return;
            }
            event.editMessage(panelBuilder.buildPrivilegeRemovePanel(guild).build()).queue();
        }
    }

    private void showAddPrivilegeModal(ButtonInteractionEvent event) {
        var roleMenu = EntitySelectMenu.create("role",
                EntitySelectMenu.SelectTarget.ROLE)
                .setRequiredRange(1, 1).build();
        event.replyModal(Modal.create(
                btnPrefix.replace("button", "modal") + "priv_add", "Add Privilege Role")
                .addComponents(Label.of("Select Role to Grant Privilege", roleMenu)).build()
        ).queue();
    }

    // ==================== NAVIGATION ====================

    private void handleBackMain(ButtonInteractionEvent event, String userId,
                                 Guild guild,
                                 Member member) {
        sessionManager.removeActivePermPage(userId);
        sessionManager.removeEditingProfileName(userId);
        sessionManager.removeTempPermState(userId);
        sessionManager.removeOriginalPermState(userId);
        sessionManager.removeCreateFormState(userId);
        sessionManager.removeImportFormState(userId);
        event.editMessage(panelBuilder.editMainPanel(guild, member).build()).queue();
    }

    // ==================== HELPERS ====================

    private void togglePermState(String userId, String permName) {
        LinkedHashMap<String, String> state = sessionManager.getTempPermState(userId);
        if (state == null) return;
        String currentState = state.getOrDefault(permName, "INHERIT");
        // Cycle: INHERIT -> ALLOW -> DENY -> INHERIT
        String nextState = switch (currentState) {
            case "INHERIT" -> "ALLOW";
            case "ALLOW" -> "DENY";
            default -> "INHERIT";
        };
        state.put(permName, nextState);
    }

    private void saveProfilePermState(String userId, String guildId) {
        LinkedHashMap<String, String> state = sessionManager.getTempPermState(userId);
        String profileName = sessionManager.getEditingProfileName(userId);
        if (state == null || profileName == null) return;
        permissionService.saveProfilePermState(guildId, profileName, state);
        sessionManager.removeActivePermPage(userId);
        sessionManager.removeEditingProfileName(userId);
        sessionManager.removeTempPermState(userId);
        sessionManager.removeOriginalPermState(userId);
    }

    private void replyNoPermission(ButtonInteractionEvent event) {
        event.reply("❌ You need **Manage Channels** permission or a Privilege Role to do this!")
                .setEphemeral(true).queue(m -> m.deleteOriginal().queueAfter(5, TimeUnit.SECONDS));
    }

    private void refreshMainPanel(String userId, Guild guild,
                                   Member member) {
        var msg = sessionManager.getControlMessage(userId);
        if (msg != null) {
            msg.editMessage(panelBuilder.editMainPanel(guild, member).build()).queue(null, _ -> {});
        }
    }
}