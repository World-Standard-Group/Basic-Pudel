package group.worldstandard.pudel.plugin.builder;

import group.worldstandard.pudel.plugin.entity.CategoryEntry;
import group.worldstandard.pudel.plugin.entity.PermissionProfile;
import group.worldstandard.pudel.plugin.entity.PrivilegeRole;
import group.worldstandard.pudel.plugin.service.PermissionService;
import group.worldstandard.pudel.plugin.session.SessionManager;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.*;
import net.dv8tion.jda.api.entities.channel.ChannelType;
import net.dv8tion.jda.api.entities.channel.attribute.ICategorizableChannel;
import net.dv8tion.jda.api.entities.channel.concrete.Category;
import net.dv8tion.jda.api.entities.channel.middleman.GuildChannel;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.components.container.Container;
import net.dv8tion.jda.api.components.container.ContainerChildComponent;
import net.dv8tion.jda.api.components.separator.Separator;
import net.dv8tion.jda.api.components.textdisplay.TextDisplay;
import net.dv8tion.jda.api.components.selections.EntitySelectMenu;
import net.dv8tion.jda.api.components.selections.StringSelectMenu;
import net.dv8tion.jda.api.utils.messages.MessageEditBuilder;

import java.awt.*;
import java.util.*;
import java.util.List;

/**
 * Builds UI panel components for the categorization plugin.
 * Separates UI construction logic from business logic and event handling.
 */
public class PanelBuilder {

    private static final Color ACCENT_MAIN = new Color(88, 101, 242);
    private static final Color ACCENT_SETTING = new Color(254, 231, 92);
    private static final Color ACCENT_PERM = new Color(87, 242, 135);
    private static final Color ACCENT_DANGER = new Color(237, 66, 69);

    private final String btnPrefix;
    private final String stringMenuPrefix;
    private final String entityMenuPrefix;
    private final PermissionService permissionService;
    private final SessionManager sessionManager;

    public PanelBuilder(String btnPrefix, String stringMenuPrefix, String entityMenuPrefix,
 PermissionService permissionService, SessionManager sessionManager) {
        this.btnPrefix = btnPrefix;
        this.stringMenuPrefix = stringMenuPrefix;
        this.entityMenuPrefix = entityMenuPrefix;
        this.permissionService = permissionService;
        this.sessionManager = sessionManager;
    }

    // ==================== MAIN PANEL ====================

    public Container buildMainPanel(Guild guild, Member member) {
        String guildId = guild.getId();
        boolean hasAuth = permissionService.hasPermissionOrPrivilege(member, guildId);
        int guildCatCount = guild.getCategories().size();
        int trackedCount = permissionService.getCategoryEntries(guildId).size();

        return Container.of(
                TextDisplay.of("# 📂 Category Management"),
                Separator.create(true, Separator.Spacing.SMALL),
                TextDisplay.of("📊 **Guild Categories:** " + guildCatCount +
                        "\n🤖 **Controlled by Pudel:** " + trackedCount),
                Separator.create(true, Separator.Spacing.SMALL),
                ActionRow.of(
                        Button.success(btnPrefix + "create", "+ Create").withDisabled(!hasAuth),
                        Button.success(btnPrefix + "import", "+ Import").withDisabled(!hasAuth),
                        Button.primary(btnPrefix + "setting", "⚙️ Setting")
                ),
                ActionRow.of(
                        Button.primary(btnPrefix + "view", "📋 View Category"),
                        Button.danger(btnPrefix + "unlink", "🔗 Unlink Category")
                )
        ).withAccentColor(ACCENT_MAIN);
    }

    public MessageEditBuilder editMainPanel(Guild guild, Member member) {
        return new MessageEditBuilder().useComponentsV2(true).setComponents(buildMainPanel(guild, member));
    }

    // ==================== CREATE PANEL ====================

    public MessageEditBuilder buildCreatePanel(String guildId, String userId) {
        List<PermissionProfile> profiles = permissionService.getProfiles(guildId);
        Map<String, String> state = sessionManager.getCreateFormState(userId) != null
                ? sessionManager.getCreateFormState(userId) : new LinkedHashMap<>();

        EntitySelectMenu managerMenu = EntitySelectMenu.create(entityMenuPrefix + "create_manager", EntitySelectMenu.SelectTarget.USER)
                .setRequiredRange(0, 1).setRequired(false).build();

        EntitySelectMenu roleMenu = EntitySelectMenu.create(entityMenuPrefix + "create_default_role", EntitySelectMenu.SelectTarget.ROLE)
                .setRequiredRange(0, 1).setRequired(false).build();

        boolean controlBy = Boolean.parseBoolean(state.getOrDefault("controlBy", "false"));
        String controlLabel = controlBy ? "✅ Pudel Control: ON" : "❌ Pudel Control: OFF";

        List<ContainerChildComponent> components = new ArrayList<>();
        components.add(TextDisplay.of("# ➕ Create New Category"));
        components.add(Separator.create(true, Separator.Spacing.SMALL));
        components.add(TextDisplay.of("**Category Name**\n" + (state.containsKey("name") ? "📝 " + state.get("name") : "_Not set_")));
        components.add(ActionRow.of(Button.primary(btnPrefix + "create_set_name", "📝 Set Category Name")));
        components.add(Separator.create(false, Separator.Spacing.SMALL));
        components.add(TextDisplay.of("**Manager User (optional)**"));
        components.add(ActionRow.of(managerMenu));
        components.add(Separator.create(false, Separator.Spacing.SMALL));
        components.add(TextDisplay.of("**Default Role (optional)**"));
        components.add(ActionRow.of(roleMenu));

        addProfileSelectors(components, profiles, state, "create", userId);

        components.add(Separator.create(true, Separator.Spacing.SMALL));
        components.add(TextDisplay.of("**Control this category via Pudel?**\n" + controlLabel));
        components.add(ActionRow.of(Button.primary(btnPrefix + "create_toggle_control", "⚙️ Toggle Pudel Control")));
        components.add(Separator.create(true, Separator.Spacing.SMALL));
        components.add(ActionRow.of(
                Button.success(btnPrefix + "create_confirm", "✅ Confirm"),
                Button.danger(btnPrefix + "create_cancel", "❌ Cancel")
        ));

        return new MessageEditBuilder().useComponentsV2(true).setComponents(
                Container.of(components).withAccentColor(ACCENT_MAIN)
        );
    }

    // ==================== IMPORT PANEL ====================

    public MessageEditBuilder buildImportPanel(String guildId, String userId) {
        List<PermissionProfile> profiles = permissionService.getProfiles(guildId);
        Map<String, String> state = sessionManager.getImportFormState(userId) != null
                ? sessionManager.getImportFormState(userId) : new LinkedHashMap<>();

        EntitySelectMenu categoryMenu = EntitySelectMenu.create(entityMenuPrefix + "import_category", EntitySelectMenu.SelectTarget.CHANNEL)
                .setChannelTypes(ChannelType.CATEGORY)
                .setRequiredRange(1, 1).build();

        EntitySelectMenu managerMenu = EntitySelectMenu.create(entityMenuPrefix + "import_manager", EntitySelectMenu.SelectTarget.USER)
                .setRequiredRange(0, 1).setRequired(false).build();

        EntitySelectMenu roleMenu = EntitySelectMenu.create(entityMenuPrefix + "import_default_role", EntitySelectMenu.SelectTarget.ROLE)
                .setRequiredRange(0, 1).setRequired(false).build();

        boolean acknowledged = Boolean.parseBoolean(state.getOrDefault("acknowledged", "false"));
        String ackLabel = acknowledged ? "✅ Acknowledged" : "❌ Not Acknowledged";
        boolean hasManagerOrRole = (state.get("manager") != null) || (state.get("default_role") != null);
        boolean confirmEnabled = !hasManagerOrRole || acknowledged;

        List<ContainerChildComponent> components = new ArrayList<>();
        components.add(TextDisplay.of("# 📥 Import Existing Category"));
        components.add(Separator.create(true, Separator.Spacing.SMALL));
        components.add(TextDisplay.of("**Category to Import ***"));
        components.add(ActionRow.of(categoryMenu));
        components.add(Separator.create(false, Separator.Spacing.SMALL));
        components.add(TextDisplay.of("**Manager User (optional)**"));
        components.add(ActionRow.of(managerMenu));
        components.add(Separator.create(false, Separator.Spacing.SMALL));
        components.add(TextDisplay.of("**Default Role (optional)**"));
        components.add(ActionRow.of(roleMenu));

        addProfileSelectors(components, profiles, state, "import", userId);

        components.add(Separator.create(true, Separator.Spacing.SMALL));
        components.add(TextDisplay.of("**Acknowledgement**\nSelecting Manager/Role will sync all child channel permissions & may make category private\n" + ackLabel));
        components.add(ActionRow.of(Button.primary(btnPrefix + "import_toggle_ack", "✅ Toggle Acknowledge")));
        components.add(Separator.create(true, Separator.Spacing.SMALL));
        components.add(ActionRow.of(
                Button.success(btnPrefix + "import_confirm", "✅ Confirm").withDisabled(!confirmEnabled),
                Button.danger(btnPrefix + "import_cancel", "❌ Cancel")
        ));

        return new MessageEditBuilder().useComponentsV2(true).setComponents(
                Container.of(components).withAccentColor(ACCENT_MAIN)
        );
    }

    // ==================== SETTING PANEL ====================

    public MessageEditBuilder buildSettingPanel(Guild guild, Member member) {
        String guildId = guild.getId();
        boolean hasAuth = permissionService.hasPermissionOrPrivilege(member, guildId);

        List<PermissionProfile> profiles = permissionService.getProfiles(guildId);
        StringBuilder profileSummary = new StringBuilder();
        if (profiles.isEmpty()) {
            profileSummary.append("_No permission profiles configured._");
        } else {
            for (PermissionProfile p : profiles) {
                String summary = permissionService.formatPermSummary(p.getAllow(), p.getDeny());
                profileSummary.append("• **").append(p.getName()).append("** — ").append(summary).append("\n");
            }
        }

        List<PrivilegeRole> privRoles = permissionService.getPrivilegeRoles(guildId);
        StringBuilder privSummary = new StringBuilder();
        if (privRoles.isEmpty()) {
            privSummary.append("_None configured_");
        } else {
            for (PrivilegeRole pr : privRoles) {
                Role role = guild.getRoleById(pr.getRole_id());
                privSummary.append("• ").append(role != null ? role.getName() : pr.getRole_id()).append("\n");
            }
        }

        return new MessageEditBuilder().useComponentsV2(true).setComponents(
                Container.of(
                        TextDisplay.of("# ⚙️ Permission Profile Settings"),
                        Separator.create(true, Separator.Spacing.SMALL),
                        TextDisplay.of("### 📋 Permission Profiles\n" + profileSummary),
                        Separator.create(false, Separator.Spacing.SMALL),
                        TextDisplay.of("### 🛡️ Privilege Roles\n" + privSummary),
                        Separator.create(true, Separator.Spacing.SMALL),
                        ActionRow.of(
                                Button.primary(btnPrefix + "profile_view", "👁️ View Profile"),
                                Button.success(btnPrefix + "profile_create", "➕ Create Profile").withDisabled(!hasAuth),
                                Button.danger(btnPrefix + "profile_remove", "➖ Remove Profile").withDisabled(!hasAuth || profiles.isEmpty())
                        ),
                        ActionRow.of(
                                Button.primary(btnPrefix + "privilege", "🛡️ Privilege Roles"),
                                Button.secondary(btnPrefix + "back_main", "⬅️ Back")
                        )
                ).withAccentColor(ACCENT_SETTING)
        );
    }

    // ==================== PERMISSION PANEL ====================

    public record PermInfo(Permission perm, String category, String displayName) {}

    public static final List<PermInfo> MANAGEABLE_PERMISSIONS = List.of(
            new PermInfo(Permission.MANAGE_CHANNEL, "Channel", "Manage Channel"),
            new PermInfo(Permission.MANAGE_PERMISSIONS, "Channel", "Manage Permission"),
            new PermInfo(Permission.MANAGE_WEBHOOKS, "Channel", "Manage Webhooks"),
            new PermInfo(Permission.MANAGE_EVENTS, "Channel", "Manage Events"),
            new PermInfo(Permission.CREATE_SCHEDULED_EVENTS, "Channel", "Create Events"),
            new PermInfo(Permission.CREATE_INSTANT_INVITE, "Channel", "Create Invite"),
            new PermInfo(Permission.MESSAGE_MENTION_EVERYONE, "Message", "Mention Ability `@`"),
            new PermInfo(Permission.MESSAGE_MANAGE, "Message", "Manage Messages"),
            new PermInfo(Permission.MANAGE_THREADS, "Message", "Manage Threads & Posts"),
            new PermInfo(Permission.MESSAGE_SEND_POLLS, "Message", "Create Polls"),
            new PermInfo(Permission.VOICE_MUTE_OTHERS, "Voice", "Mute Members"),
            new PermInfo(Permission.VOICE_DEAF_OTHERS, "Voice", "Deafen Members"),
            new PermInfo(Permission.VOICE_MOVE_OTHERS, "Voice", "Move Members"),
            new PermInfo(Permission.MESSAGE_SEND, "Message", "Send Message & Posts"),
            new PermInfo(Permission.CREATE_PUBLIC_THREADS, "Message", "Create Public Threads"),
            new PermInfo(Permission.MESSAGE_EXT_EMOJI, "Message", "Use External Emojis"),
            new PermInfo(Permission.MESSAGE_EXT_STICKER, "Message", "Use External Stickers"),
            new PermInfo(Permission.MESSAGE_SEND_IN_THREADS, "Message", "Send in Threads & Posts"),
            new PermInfo(Permission.CREATE_PRIVATE_THREADS, "Message", "Create Private Threads"),
            new PermInfo(Permission.MESSAGE_EMBED_LINKS, "Message", "Embed Links"),
            new PermInfo(Permission.MESSAGE_ATTACH_FILES, "Message", "Attach Files"),
            new PermInfo(Permission.MESSAGE_ADD_REACTION, "Message", "Add Reactions"),
            new PermInfo(Permission.MESSAGE_HISTORY, "Message", "Read Message History"),
            new PermInfo(Permission.MESSAGE_TTS, "Message", "Send TTS Message"),
            new PermInfo(Permission.MESSAGE_ATTACH_VOICE_MESSAGE, "Message", "Send Voice Message"),
            new PermInfo(Permission.VOICE_CONNECT, "Voice", "Connect"),
            new PermInfo(Permission.VOICE_SPEAK, "Voice", "Speak"),
            new PermInfo(Permission.VOICE_STREAM, "Voice", "Video"),
            new PermInfo(Permission.VOICE_USE_SOUNDBOARD, "Voice", "Use Soundboard"),
            new PermInfo(Permission.VOICE_USE_EXTERNAL_SOUNDS, "Voice", "Use External Sounds"),
            new PermInfo(Permission.REQUEST_TO_SPEAK, "Voice", "Request to Speak"),
            new PermInfo(Permission.USE_APPLICATION_COMMANDS, "Channel", "Use App Commands"),
            new PermInfo(Permission.USE_EXTERNAL_APPLICATIONS, "Channel", "Use External Apps"),
            new PermInfo(Permission.USE_EMBEDDED_ACTIVITIES, "Voice", "Use Activities"),
            new PermInfo(Permission.VOICE_USE_VAD, "Voice", "Use Voice Activity"),
            new PermInfo(Permission.PRIORITY_SPEAKER, "Voice", "Priority Speaker"),
            new PermInfo(Permission.VOICE_SET_STATUS, "Voice", "Set Voice Status")
    );

    private static final int PERM_PAGE_SIZE = 10;

    public MessageEditBuilder buildPermissionPanel(String userId, boolean hasAuth) {
        String profileName = sessionManager.getEditingProfileName(userId) != null
                ? sessionManager.getEditingProfileName(userId) : "Unknown";
        LinkedHashMap<String, String> state = sessionManager.getTempPermState(userId);

        if (state == null) {
            return new MessageEditBuilder().useComponentsV2(true).setComponents(
                    Container.of(TextDisplay.of("❌ Session expired. Use `/categorizement` again."))
                            .withAccentColor(ACCENT_DANGER));
        }

        int total = MANAGEABLE_PERMISSIONS.size();
        int totalPages = Math.max(1, (int) Math.ceil((double) total / PERM_PAGE_SIZE));
        int page = sessionManager.getActivePermPage(userId);
        if (page < 0) page = 0;
        if (page >= totalPages) page = totalPages - 1;

        int start = page * PERM_PAGE_SIZE;
        int end = Math.min(start + PERM_PAGE_SIZE, total);

        List<ContainerChildComponent> components = new ArrayList<>();
        components.add(TextDisplay.of("# 📋 Profile: **" + profileName + "**"));
        components.add(Separator.create(true, Separator.Spacing.SMALL));
        components.add(TextDisplay.of("### Permissions — Page %d / %d".formatted(page + 1, totalPages)));
        components.add(Separator.create(false, Separator.Spacing.SMALL));

        // Waterfall list: render the current page of permissions
        for (int i = start; i < end; i++) {
            PermInfo pi = MANAGEABLE_PERMISSIONS.get(i);
            String permName = pi.perm().name();
            String stateStr = state.getOrDefault(permName, "INHERIT");
            Button btn = switch (stateStr) {
                case "ALLOW" -> Button.success(btnPrefix + "perm_" + permName, "✅ (%s) %s".formatted(pi.category(),pi.displayName())).withDisabled(!hasAuth);
                case "DENY" -> Button.danger(btnPrefix + "perm_" + permName, "❌ (%s) %s".formatted(pi.category(),pi.displayName())).withDisabled(!hasAuth);
                default -> Button.secondary(btnPrefix + "perm_" + permName, "⬜ (%s) %s".formatted(pi.category(),pi.displayName())).withDisabled(!hasAuth);
            };
            components.add(ActionRow.of(btn));
        }

        // Tooltip for colors
        components.add(Separator.create(true, Separator.Spacing.SMALL));
        components.add(TextDisplay.of("-# 🟢 Green = Allowed · 🔴 Red = Denied · ⬜ Gray = Inherited"));

        // Pagination buttons (previous / current / next)
        components.add(ActionRow.of(
                Button.secondary(btnPrefix + "perm_prev", "⬅️ Previous").withDisabled(page <= 0),
                Button.secondary(btnPrefix + "perm_page", "📄 %d / %d".formatted(page + 1, totalPages)).withDisabled(true),
                Button.primary(btnPrefix + "perm_next", "Next ➡️").withDisabled(page >= totalPages - 1)
        ));
        components.add(ActionRow.of(
                Button.secondary(btnPrefix + "perm_back", "⬅️ Back"),
                Button.success(btnPrefix + "confirm", "✅ Confirm").withDisabled(!hasAuth),
                Button.danger(btnPrefix + "perm_reset", "🔃 Reset").withDisabled(!hasAuth)
        ));

        return new MessageEditBuilder().useComponentsV2(true).setComponents(
                Container.of(components).withAccentColor(ACCENT_PERM)
        );
    }

    public MessageEditBuilder buildPermissionConfirmPanel(String userId, boolean hasAuth) {
        String profileName = sessionManager.getEditingProfileName(userId) != null
                ? sessionManager.getEditingProfileName(userId) : "Unknown";
        LinkedHashMap<String, String> original = sessionManager.getOriginalPermState(userId);
        LinkedHashMap<String, String> current = sessionManager.getTempPermState(userId);

        if (original == null || current == null) {
            return new MessageEditBuilder().useComponentsV2(true).setComponents(
                    Container.of(TextDisplay.of("❌ Session expired. Use `/categorizement` again."))
                            .withAccentColor(ACCENT_DANGER));
        }

        StringBuilder sb = new StringBuilder();
        sb.append("# 📋 Profile: **").append(profileName).append("**\n\n");
        sb.append("### Permission Changes\n\n");

        boolean hasChanges = false;
        for (PermInfo pi : MANAGEABLE_PERMISSIONS) {
            String permName = pi.perm().name();
            String origState = original.getOrDefault(permName, "INHERIT");
            String currState = current.getOrDefault(permName, "INHERIT");
            if (!origState.equals(currState)) {
                hasChanges = true;
                String origIcon = switch (origState) {
                    case "ALLOW" -> "✅";
                    case "DENY" -> "❌";
                    default -> "⬜";
                };
                String currIcon = switch (currState) {
                    case "ALLOW" -> "✅";
                    case "DENY" -> "❌";
                    default -> "⬜";
                };
                sb.append("• **").append(" (%s) ".formatted(pi.category())).append(pi.displayName().replace("`@", "@")).append("**: ")
                        .append(origIcon).append(" ").append(origState)
                        .append(" → ").append(currIcon).append(" ").append(currState).append("\n");
            }
        }

        if (!hasChanges) {
            sb.append("_No changes detected._");
        }

        return new MessageEditBuilder().useComponentsV2(true).setComponents(
                Container.of(
                        TextDisplay.of(sb.toString()),
                        Separator.create(true, Separator.Spacing.SMALL),
                        ActionRow.of(
                                Button.success(btnPrefix + "confirm_save", "✅ Confirm").withDisabled(!hasAuth),
                                Button.secondary(btnPrefix + "confirm_cancel", "❌ Cancel")
                        )
                ).withAccentColor(ACCENT_PERM)
        );
    }

    // ==================== PRIVILEGE PANEL ====================

    public MessageEditBuilder buildPrivilegePanel(Guild guild, boolean hasAuth) {
        String guildId = guild.getId();
        List<PrivilegeRole> roles = permissionService.getPrivilegeRoles(guildId);

        StringBuilder sb = new StringBuilder("# 🛡️ Privilege Roles\n\n");
        if (roles.isEmpty()) {
            sb.append("_No privilege roles configured._");
        } else {
            for (PrivilegeRole pr : roles) {
                Role role = guild.getRoleById(pr.getRole_id());
                String name = role != null ? role.getName() : "Unknown (" + pr.getRole_id() + ")";
                sb.append("• **").append(name).append("** (<@&").append(pr.getRole_id()).append(">)\n");
            }
        }
        sb.append("\n-# Privilege roles can use Create, Import & Edit permissions without MANAGE_CHANNEL");

        return new MessageEditBuilder().useComponentsV2(true).setComponents(
                Container.of(
                        TextDisplay.of(sb.toString()),
                        Separator.create(true, Separator.Spacing.SMALL),
                        ActionRow.of(
                                Button.success(btnPrefix + "priv_add", "➕ Add Role").withDisabled(!hasAuth),
                                Button.danger(btnPrefix + "priv_remove", "➖ Remove Role").withDisabled(!hasAuth || roles.isEmpty()),
                                Button.secondary(btnPrefix + "back_setting", "⬅️ Back")
                        )
                ).withAccentColor(ACCENT_SETTING)
        );
    }

    // ==================== VIEW PANELS ====================

    public MessageEditBuilder buildViewCategoryPanel(Guild guild) {
        String guildId = guild.getId();
        List<CategoryEntry> entries = permissionService.getCategoryEntries(guildId);

        if (entries.isEmpty()) {
            return new MessageEditBuilder().useComponentsV2(true).setComponents(
                    Container.of(
                            TextDisplay.of("### 📋 View Category"),
                            TextDisplay.of("_No categories are currently tracked by Pudel._"),
                            Separator.create(true, Separator.Spacing.SMALL),
                            ActionRow.of(Button.secondary(btnPrefix + "back_main", "⬅️ Back"))
                    ).withAccentColor(ACCENT_MAIN)
            );
        }

        StringSelectMenu.Builder menuBuilder = StringSelectMenu.create(stringMenuPrefix + "view_cat")
                .setPlaceholder("Select a category to view");

        for (CategoryEntry entry : entries) {
            Category cat = guild.getCategoryById(entry.getCategory_id());
            String catName = cat != null ? cat.getName() : "Deleted (" + entry.getCategory_id() + ")";
            menuBuilder.addOption(catName, entry.getCategory_id());
        }

        return new MessageEditBuilder().useComponentsV2(true).setComponents(
                Container.of(
                        TextDisplay.of("### 📋 View Category"),
                        TextDisplay.of("Select a category controlled by Pudel:"),
                        Separator.create(true, Separator.Spacing.SMALL),
                        ActionRow.of(menuBuilder.build()),
                        ActionRow.of(Button.secondary(btnPrefix + "back_main", "⬅️ Back"))
                ).withAccentColor(ACCENT_MAIN)
        );
    }

    public MessageEditBuilder buildCategoryDetailPanel(Guild guild, String categoryId) {
        CategoryEntry entry = permissionService.findCategoryEntry(categoryId);
        Category cat = guild.getCategoryById(categoryId);

        if (entry == null || cat == null) {
            return new MessageEditBuilder().useComponentsV2(true).setComponents(
                    Container.of(
                            TextDisplay.of("### ❌ Category Not Found"),
                            TextDisplay.of("_This category may have been deleted or unlinked._"),
                            Separator.create(true, Separator.Spacing.SMALL),
                            ActionRow.of(Button.secondary(btnPrefix + "back_view", "⬅️ Back"))
                    ).withAccentColor(ACCENT_DANGER)
            );
        }

        int textCount = 0, voiceCount = 0, forumCount = 0;
        for (GuildChannel ch : cat.getChannels()) {
            switch (ch.getType()) {
                case TEXT, NEWS -> textCount++;
                case VOICE, STAGE -> voiceCount++;
                case FORUM, MEDIA -> forumCount++;
                default -> {}
            }
        }

        StringBuilder unsyncChStr = new StringBuilder();
        for (GuildChannel ch : cat.getChannels()) {
            if (ch instanceof ICategorizableChannel categorizableChannel && !categorizableChannel.isSynced()) {
                unsyncChStr.append("- [%s](%s)\n".formatted(ch.getName(), ch.getJumpUrl()));
            }
        }
        if (unsyncChStr.isEmpty()) {
            unsyncChStr.append("_*empty*_");
        }

        String managerStr = entry.getManager_id() != null ? "<@" + entry.getManager_id() + ">" : "_Not set_";
        String mgrProfileStr = entry.getManager_role_profile() != null ? "**" + entry.getManager_role_profile() + "**" : "_Not set_";
        String roleStr = entry.getDefault_role() != null ? "<@&" + entry.getDefault_role() + ">" : "_Not set_";
        String roleProfileStr = entry.getDefault_role_profile() != null ? "**" + entry.getDefault_role_profile() + "**" : "_Not set_";

        String sb = """
                ### 📂 %s
                📝 **Text Channels:** %d
                🔊 **Voice Channels:** %d
                💬 **Forum Channels:** %d

                👤 **Managed by:** %s
                📋 **Manager Profile:** %s
                🎭 **Default Role:** %s
                📋 **Role Profile:** %s

                ### Unsync Channels
                %s
                """.formatted(cat.getName(), textCount, voiceCount, forumCount, managerStr, mgrProfileStr, roleStr, roleProfileStr, unsyncChStr);

        return new MessageEditBuilder().useComponentsV2(true).setComponents(
                Container.of(
                        TextDisplay.of(sb),
                        Separator.create(true, Separator.Spacing.SMALL),
                        ActionRow.of(Button.secondary(btnPrefix + "back_view", "⬅️ Back"))
                ).withAccentColor(ACCENT_MAIN)
        );
    }

    // ==================== UNLINK PANEL ====================

    public MessageEditBuilder buildUnlinkPanel(Guild guild, Member member) {
        String guildId = guild.getId();
        String userId = member.getId();
        boolean hasAuth = permissionService.hasPermissionOrPrivilege(member, guildId);

        List<CategoryEntry> entries = permissionService.getCategoryEntries(guildId);

        List<CategoryEntry> visible;
        if (hasAuth) {
            visible = entries;
        } else {
            visible = entries.stream()
                    .filter(e -> e.getManager_id() != null && e.getManager_id().equals(userId))
                    .toList();
        }

        if (visible.isEmpty()) {
            String message = entries.isEmpty()
                    ? "_No categories are currently tracked by Pudel._"
                    : "_You don't have permission to unlink any categories._";
            return new MessageEditBuilder().useComponentsV2(true).setComponents(
                    Container.of(
                            TextDisplay.of("### 🔗 Unlink Category"),
                            TextDisplay.of(message),
                            Separator.create(true, Separator.Spacing.SMALL),
                            ActionRow.of(Button.secondary(btnPrefix + "back_main", "⬅️ Back"))
                    ).withAccentColor(ACCENT_DANGER)
            );
        }

        StringSelectMenu.Builder menuBuilder = StringSelectMenu.create(stringMenuPrefix + "unlink_cat")
                .setPlaceholder("Select a category to unlink");

        for (CategoryEntry entry : visible) {
            Category cat = guild.getCategoryById(entry.getCategory_id());
            String catName = cat != null ? cat.getName() : "Deleted (" + entry.getCategory_id() + ")";
            menuBuilder.addOption(catName, entry.getCategory_id());
        }

        return new MessageEditBuilder().useComponentsV2(true).setComponents(
                Container.of(
                        TextDisplay.of("### 🔗 Unlink Category"),
                        TextDisplay.of("Select a category to unlink from Pudel (category remains in guild):"),
                        Separator.create(true, Separator.Spacing.SMALL),
                        ActionRow.of(menuBuilder.build()),
                        ActionRow.of(Button.secondary(btnPrefix + "back_main", "⬅️ Back"))
                ).withAccentColor(ACCENT_DANGER)
        );
    }

    // ==================== PROFILE SELECT PANELS ====================

    public MessageEditBuilder buildViewProfileSelectPanel(String guildId) {
        List<PermissionProfile> profiles = permissionService.getProfiles(guildId);

        if (profiles.isEmpty()) {
            return new MessageEditBuilder().useComponentsV2(true).setComponents(
                    Container.of(
                            TextDisplay.of("### 👁️ View Permission Profile"),
                            TextDisplay.of("_No permission profiles configured. Create one first._"),
                            Separator.create(true, Separator.Spacing.SMALL),
                            ActionRow.of(Button.secondary(btnPrefix + "back_setting", "⬅️ Back"))
                    ).withAccentColor(ACCENT_SETTING)
            );
        }

        StringSelectMenu.Builder menuBuilder = StringSelectMenu.create(stringMenuPrefix + "profile_view")
                .setPlaceholder("Select a profile to view/edit");
        for (PermissionProfile p : profiles) {
            menuBuilder.addOption(p.getName(), p.getName());
        }

        return new MessageEditBuilder().useComponentsV2(true).setComponents(
                Container.of(
                        TextDisplay.of("### 👁️ View Permission Profile"),
                        TextDisplay.of("Select a profile to view or edit its permissions:"),
                        Separator.create(true, Separator.Spacing.SMALL),
                        ActionRow.of(menuBuilder.build()),
                        ActionRow.of(Button.secondary(btnPrefix + "back_setting", "⬅️ Back"))
                ).withAccentColor(ACCENT_SETTING)
        );
    }

    public MessageEditBuilder buildRemoveProfileSelectPanel(String guildId) {
        List<PermissionProfile> profiles = permissionService.getProfiles(guildId);

        if (profiles.isEmpty()) {
            return new MessageEditBuilder().useComponentsV2(true).setComponents(
                    Container.of(
                            TextDisplay.of("### ➖ Remove Permission Profile"),
                            TextDisplay.of("_No permission profiles to remove._"),
                            Separator.create(true, Separator.Spacing.SMALL),
                            ActionRow.of(Button.secondary(btnPrefix + "back_setting", "⬅️ Back"))
                    ).withAccentColor(ACCENT_DANGER)
            );
        }

        StringSelectMenu.Builder menuBuilder = StringSelectMenu.create(stringMenuPrefix + "profile_rm")
                .setPlaceholder("Select a profile to remove");
        for (PermissionProfile p : profiles) {
            menuBuilder.addOption(p.getName(), p.getName());
        }

        return new MessageEditBuilder().useComponentsV2(true).setComponents(
                Container.of(
                        TextDisplay.of("### ➖ Remove Permission Profile"),
                        TextDisplay.of("Select a profile to permanently remove:"),
                        Separator.create(true, Separator.Spacing.SMALL),
                        ActionRow.of(menuBuilder.build()),
                        ActionRow.of(Button.secondary(btnPrefix + "back_setting", "⬅️ Back"))
                ).withAccentColor(ACCENT_DANGER)
        );
    }

    public MessageEditBuilder buildPrivilegeRemovePanel(Guild guild) {
        String guildId = guild.getId();
        List<PrivilegeRole> roles = permissionService.getPrivilegeRoles(guildId);

        StringSelectMenu.Builder menuBuilder = StringSelectMenu.create(stringMenuPrefix + "priv_rm")
                .setPlaceholder("Select a role to remove");

        for (PrivilegeRole pr : roles) {
            Role role = guild.getRoleById(pr.getRole_id());
            String name = role != null ? role.getName() : "Unknown (" + pr.getRole_id() + ")";
            menuBuilder.addOption(name, pr.getRole_id());
        }

        return new MessageEditBuilder().useComponentsV2(true).setComponents(
                Container.of(
                        TextDisplay.of("### ➖ Remove Privilege Role"),
                        TextDisplay.of("Select a role to remove from the privilege list:"),
                        Separator.create(true, Separator.Spacing.SMALL),
                        ActionRow.of(menuBuilder.build()),
                        ActionRow.of(Button.secondary(btnPrefix + "back_privilege", "⬅️ Back"))
                ).withAccentColor(ACCENT_DANGER)
        );
    }

    // ==================== ACCESSORS ====================

    public List<Permission> getManageablePermissions() {
        return MANAGEABLE_PERMISSIONS.stream()
                .map(PermInfo::perm)
                .toList();
    }

    // ==================== HELPER METHODS ====================

    private void addProfileSelectors(List<ContainerChildComponent> components, List<PermissionProfile> profiles,
                                     Map<String, String> state, String prefix, String userId) {
        if (!profiles.isEmpty()) {
            StringSelectMenu.Builder mgrProfileMenu = StringSelectMenu.create(stringMenuPrefix + prefix + "_manager_profile")
                    .setPlaceholder("Select manager permission profile")
                    .setRequiredRange(0, 1);
            StringSelectMenu.Builder roleProfileMenu = StringSelectMenu.create(stringMenuPrefix + prefix + "_role_profile")
                    .setPlaceholder("Select default role permission profile")
                    .setRequiredRange(0, 1);
            for (PermissionProfile p : profiles) {
                try {
                    mgrProfileMenu.addOption(p.getName(), p.getName());
                    if (prefix.equals("create") && state.containsKey("manager_profile")) {
                        if (p.getName().equals(state.get("manager_profile"))) {
                            mgrProfileMenu.setDefaultOptions(mgrProfileMenu.getOptions().getLast());
                        }
                    } else if (prefix.equals("import") && state.containsKey("import_manager_profile")) {
                        if (p.getName().equals(state.get("import_manager_profile"))) {
                            mgrProfileMenu.setDefaultOptions(mgrProfileMenu.getOptions().getLast());
                        }
                    }
                    roleProfileMenu.addOption(p.getName(), p.getName());
                    if (prefix.equals("create") && state.containsKey("role_profile")) {
                        if (p.getName().equals(state.get("role_profile"))) {
                            roleProfileMenu.setDefaultOptions(roleProfileMenu.getOptions().getLast());
                        }
                    } else if (prefix.equals("import") && state.containsKey("import_role_profile")) {
                        if (p.getName().equals(state.get("import_role_profile"))) {
                            roleProfileMenu.setDefaultOptions(roleProfileMenu.getOptions().getLast());
                        }
                    }
                } catch (IllegalArgumentException ex) {
                    // Profile options reached limit
                }
            }

            components.add(Separator.create(false, Separator.Spacing.SMALL));
            components.add(TextDisplay.of("**Manager Role Profile (optional)**"));
            components.add(ActionRow.of(mgrProfileMenu.build()));
            components.add(Separator.create(false, Separator.Spacing.SMALL));
            components.add(TextDisplay.of("**Default Role Profile (optional)**"));
            components.add(ActionRow.of(roleProfileMenu.build()));
        }
    }
}