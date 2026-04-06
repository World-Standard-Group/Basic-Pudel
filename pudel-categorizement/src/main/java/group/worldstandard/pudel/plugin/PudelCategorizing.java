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
package group.worldstandard.pudel.plugin;

import group.worldstandard.pudel.api.PluginContext;
import group.worldstandard.pudel.api.annotation.*;
import group.worldstandard.pudel.api.database.ColumnType;
import group.worldstandard.pudel.api.database.PluginDatabaseManager;
import group.worldstandard.pudel.api.database.PluginRepository;
import group.worldstandard.pudel.api.database.TableSchema;
import group.worldstandard.pudel.plugin.entity.CategoryEntry;
import group.worldstandard.pudel.plugin.entity.CategorySetting;
import group.worldstandard.pudel.plugin.entity.PrivilegeRole;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.components.checkbox.Checkbox;
import net.dv8tion.jda.api.components.checkboxgroup.CheckboxGroup;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.channel.ChannelType;
import net.dv8tion.jda.api.entities.channel.concrete.Category;
import net.dv8tion.jda.api.entities.channel.middleman.GuildChannel;
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.StringSelectInteractionEvent;
import net.dv8tion.jda.api.interactions.IntegrationType;
import net.dv8tion.jda.api.interactions.InteractionContextType;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.components.container.Container;
import net.dv8tion.jda.api.components.separator.Separator;
import net.dv8tion.jda.api.components.textdisplay.TextDisplay;
import net.dv8tion.jda.api.components.textinput.TextInput;
import net.dv8tion.jda.api.components.textinput.TextInputStyle;
import net.dv8tion.jda.api.components.selections.EntitySelectMenu;
import net.dv8tion.jda.api.components.selections.StringSelectMenu;
import net.dv8tion.jda.api.entities.channel.attribute.ICategorizableChannel;
import net.dv8tion.jda.api.components.label.Label;
import net.dv8tion.jda.api.modals.Modal;
import net.dv8tion.jda.api.utils.messages.MessageCreateBuilder;
import net.dv8tion.jda.api.utils.messages.MessageEditBuilder;

import java.awt.*;
import java.util.*;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * Pudel's Category Management Plugin.
 *
 * <p>Provides a Components V2 control panel for creating, importing,
 * managing permissions, and unlinking Discord categories via slash command.
 *
 * @author Zazalng
 * @since 1.0.0
 */
@Plugin(
        name = "Pudel's Category Management",
        author = "Zazalng",
        version = "1.0.0-rc1",
        description = "Manages Discord categories and lets administrators assign category managers."
)
public class PudelCategorizing {

    // ==================== HANDLER IDS (compile-time, used in annotations) ====================
    private static final String BTN_HANDLER = "button:";
    private static final String MODAL_HANDLER = "modal:";
    private static final String MENU_HANDLER = "menu:";

    // ==================== RUNTIME PREFIXED IDS (initialized in onEnable) ====================
    private String btnPrefix;
    private String modalPrefix;
    private String menuPrefix;

    private static final Color ACCENT_MAIN = new Color(88,101,242);
    private static final Color ACCENT_SETTING = new Color(254,231,92);
    private static final Color ACCENT_PERM = new Color(87, 242, 135);
    private static final Color ACCENT_DANGER = new Color(237, 66, 69);

    /** Describes a single manageable permission for the NES-style cursor UI. */
    private record PermInfo(Permission perm, String displayName, String section) {}

    /**
     * Ordered list of all manageable category permissions.
     * VIEW_CHANNEL is excluded per spec (Manager=Inherit, DefaultRole=Always Allow, non-manageable).
     */
    private static final List<PermInfo> MANAGEABLE_PERMISSIONS = List.of(
            // Management
            new PermInfo(Permission.MANAGE_CHANNEL, "Manage Channel", "Management"),
            new PermInfo(Permission.MANAGE_PERMISSIONS, "Manage Permission", "Management"),
            new PermInfo(Permission.MANAGE_WEBHOOKS, "Manage Webhooks", "Management"),
            new PermInfo(Permission.MANAGE_EVENTS, "Manage Events", "Management"),
            new PermInfo(Permission.CREATE_SCHEDULED_EVENTS, "Create Events", "Management"),
            new PermInfo(Permission.CREATE_INSTANT_INVITE, "Create Invite", "Management"),
            new PermInfo(Permission.USE_APPLICATION_COMMANDS, "Use App Commands", "Management"),
            new PermInfo(Permission.USE_EMBEDDED_ACTIVITIES, "Use Activities", "Management"),
            new PermInfo(Permission.USE_EXTERNAL_APPLICATIONS, "Use External Apps", "Management"),
            // Messaging
            new PermInfo(Permission.MESSAGE_SEND, "Send Message & Posts", "Messaging"),
            new PermInfo(Permission.CREATE_PUBLIC_THREADS, "Create Public Threads", "Messaging"),
            new PermInfo(Permission.MESSAGE_EXT_EMOJI, "Use External Emojis", "Messaging"),
            new PermInfo(Permission.MESSAGE_EXT_STICKER, "Use External Stickers", "Messaging"),
            new PermInfo(Permission.MESSAGE_SEND_IN_THREADS, "Send in Threads & Posts", "Messaging"),
            new PermInfo(Permission.CREATE_PRIVATE_THREADS, "Create Private Threads", "Messaging"),
            new PermInfo(Permission.MESSAGE_EMBED_LINKS, "Embed Links", "Messaging"),
            new PermInfo(Permission.MESSAGE_ATTACH_FILES, "Attach Files", "Messaging"),
            new PermInfo(Permission.MESSAGE_ADD_REACTION, "Add Reactions", "Messaging"),
            new PermInfo(Permission.MESSAGE_MENTION_EVERYONE, "Mention @everyone", "Messaging"),
            new PermInfo(Permission.MESSAGE_MANAGE, "Manage Messages", "Messaging"),
            new PermInfo(Permission.MANAGE_THREADS, "Manage Threads & Posts", "Messaging"),
            new PermInfo(Permission.MESSAGE_HISTORY, "Read Message History", "Messaging"),
            new PermInfo(Permission.MESSAGE_TTS, "Send TTS Message", "Messaging"),
            new PermInfo(Permission.MESSAGE_ATTACH_VOICE_MESSAGE, "Send Voice Message", "Messaging"),
            new PermInfo(Permission.MESSAGE_SEND_POLLS, "Create Polls", "Messaging"),
            // Voice Channel
            new PermInfo(Permission.VOICE_CONNECT, "Connect", "Voice"),
            new PermInfo(Permission.VOICE_SPEAK, "Speak", "Voice"),
            new PermInfo(Permission.VOICE_STREAM, "Video", "Voice"),
            new PermInfo(Permission.VOICE_USE_SOUNDBOARD, "Use Soundboard", "Voice"),
            new PermInfo(Permission.VOICE_USE_EXTERNAL_SOUNDS, "Use External Sounds", "Voice"),
            new PermInfo(Permission.VOICE_USE_VAD, "Use Voice Activity", "Voice"),
            new PermInfo(Permission.PRIORITY_SPEAKER, "Priority Speaker", "Voice"),
            new PermInfo(Permission.VOICE_MUTE_OTHERS, "Mute Members", "Voice"),
            new PermInfo(Permission.VOICE_DEAF_OTHERS, "Deafen Members", "Voice"),
            new PermInfo(Permission.VOICE_MOVE_OTHERS, "Move Members", "Voice"),
            new PermInfo(Permission.VOICE_SET_STATUS, "Set Voice Status", "Voice"),
            new PermInfo(Permission.REQUEST_TO_SPEAK, "Request to Speak", "Voice")
    );

    // ==================== STATE ====================
    private PluginContext context;
    private PluginRepository<CategoryEntry> categoryRepo;
    private PluginRepository<CategorySetting> settingRepo;
    private PluginRepository<PrivilegeRole> privilegeRepo;

    /** Ephemeral control panel message per user (userId -> Message). */
    private final Map<String, Message> controlMessages = new ConcurrentHashMap<>();
    /** Permission cursor index per user. */
    private final Map<String, Integer> permCursor = new ConcurrentHashMap<>();
    /** Permission target type per user: "manager" or "role". */
    private final Map<String, String> permTarget = new ConcurrentHashMap<>();
    /** Temporary permission state per user: permEnumName -> "ALLOW"/"INHERIT"/"DENY". */
    private final Map<String, LinkedHashMap<String, String>> tempPermState = new ConcurrentHashMap<>();

    // ==================== LIFECYCLE ====================

    @OnEnable
    public void onEnable(PluginContext ctx) {
        this.context = ctx;
        String prefix = ctx.getDatabaseManager().getPrefix();
        this.btnPrefix = prefix + BTN_HANDLER;
        this.modalPrefix = prefix + MODAL_HANDLER;
        this.menuPrefix = prefix + MENU_HANDLER;
        initializeDatabase(ctx.getDatabaseManager());
        ctx.log("info", "%s (v%s) has initialized on '%s'".formatted(
                ctx.getInfo().getName(), ctx.getInfo().getVersion(), ctx.getPudel().getUserAgent()));
    }

    @OnShutdown
    public boolean onShutdown(PluginContext ctx) {
        try {
            controlMessages.values().forEach(m -> {
                try { m.delete().queue(null, _ -> {}); } catch (Exception _) {}
            });
            controlMessages.clear();
            permCursor.clear();
            permTarget.clear();
            tempPermState.clear();
            ctx.log("info", "%s (v%s) graceful shutdown on '%s'".formatted(
                    ctx.getInfo().getName(), ctx.getInfo().getVersion(), ctx.getPudel().getUserAgent()));
            context = null;
            return true;
        } catch (Exception e) {
            ctx.log("error", "Unable to shutdown gracefully: '%s'".formatted(e.getMessage()), e);
            return false;
        }
    }

    // ==================== DATABASE ====================

    private void initializeDatabase(PluginDatabaseManager db) {
        TableSchema categorySchema = TableSchema.builder("category")
                .column("guild_id", ColumnType.TEXT, false)
                .column("category_id", ColumnType.TEXT, false)
                .column("manager_id", ColumnType.TEXT, true)
                .column("default_role", ColumnType.TEXT, true)
                .uniqueIndex("category_id")
                .build();
        context.log("info", "Creating table '%s': %s".formatted(categorySchema.getTableName(), db.createTable(categorySchema)));

        TableSchema settingSchema = TableSchema.builder("setting")
                .column("guild_id", ColumnType.TEXT, false)
                .column("manager_allow", ColumnType.TEXT, false, "''")
                .column("manager_deny", ColumnType.TEXT, false, "''")
                .column("role_allow", ColumnType.TEXT, false, "''")
                .column("role_deny", ColumnType.TEXT, false, "''")
                .uniqueIndex("guild_id")
                .build();
        context.log("info", "Creating table '%s': %s".formatted(settingSchema.getTableName(), db.createTable(settingSchema)));

        TableSchema privilegeSchema = TableSchema.builder("privilege_role")
                .column("guild_id", ColumnType.TEXT, false)
                .column("role_id", ColumnType.TEXT, false)
                .index("guild_id")
                .build();
        context.log("info", "Creating table '%s': %s".formatted(privilegeSchema.getTableName(), db.createTable(privilegeSchema)));

        this.categoryRepo = db.getRepository("category", CategoryEntry.class);
        this.settingRepo = db.getRepository("setting", CategorySetting.class);
        this.privilegeRepo = db.getRepository("privilege_role", PrivilegeRole.class);
    }

    // ==================== SLASH COMMAND ====================

    @SlashCommand(
            name = "categorizement",
            description = "Open Control Panel for management category channel",
            nsfw = false,
            global = false,
            integrationTo = IntegrationType.GUILD_INSTALL,
            integrationContext = {InteractionContextType.GUILD}
    )
    public void onOpenControlPanel(SlashCommandInteractionEvent event) {
        Guild guild = event.getGuild();
        Member member = event.getMember();

        if (guild == null || member == null) {
            event.reply("❌ This command can only be used in a server!").setEphemeral(true)
                    .queue(m -> m.deleteOriginal().queueAfter(5, TimeUnit.SECONDS));
            return;
        }

        String userId = member.getId();

        // Clean old control message
        Message old = controlMessages.remove(userId);
        if (old != null) {
            try { old.delete().queue(null, _ -> {}); } catch (Exception _) {}
        }

        // Clean session state
        permCursor.remove(userId);
        permTarget.remove(userId);
        tempPermState.remove(userId);

        event.reply(
                new MessageCreateBuilder()
                        .useComponentsV2(true)
                        .setComponents(buildMainPanel(guild, member))
                        .build()
        ).setEphemeral(true).queue(hook -> hook.retrieveOriginal().queue(msg -> controlMessages.put(userId, msg)));
    }

    // ==================== BUTTON HANDLER ====================

    @ButtonHandler(BTN_HANDLER)
    public void handleButton(ButtonInteractionEvent event) {
        Guild guild = event.getGuild();
        Member member = event.getMember();
        if (guild == null || member == null) return;

        String userId = event.getUser().getId();
        String guildId = guild.getId();
        String buttonId = event.getComponentId().substring(btnPrefix.length());
        boolean hasAuth = hasPermissionOrPrivilege(member, guildId);

        switch (buttonId) {
            // ── Main Panel ──
            case "create" -> {
                if (!hasAuth) { replyNoPermission(event); return; }
                showCreateModal(event);
            }
            case "import" -> {
                if (!hasAuth) { replyNoPermission(event); return; }
                showImportModal(event);
            }
            case "setting", "back_setting" -> event.editMessage(buildSettingPanel(guild, member).build()).queue();
            case "view", "back_view" -> event.editMessage(buildViewCategoryPanel(guild).build()).queue();
            case "unlink" -> event.editMessage(buildUnlinkPanel(guild, member).build()).queue();

            // ── Setting Panel ──
            case "perm_manager" -> {
                permTarget.put(userId, "manager");
                permCursor.put(userId, 0);
                loadPermStateFromDb(userId, guildId, "manager");
                event.editMessage(buildPermissionPanel(userId, hasAuth).build()).queue();
            }
            case "perm_role" -> {
                permTarget.put(userId, "role");
                permCursor.put(userId, 0);
                loadPermStateFromDb(userId, guildId, "role");
                event.editMessage(buildPermissionPanel(userId, hasAuth).build()).queue();
            }
            case "privilege", "back_privilege" -> event.editMessage(buildPrivilegePanel(guild, hasAuth).build()).queue();

            // ── Permission Panel (NES cursor) ──
            case "up" -> {
                int cur = permCursor.getOrDefault(userId, 0);
                permCursor.put(userId, Math.max(0, cur - 1));
                event.editMessage(buildPermissionPanel(userId, hasAuth).build()).queue();
            }
            case "down" -> {
                int cur = permCursor.getOrDefault(userId, 0);
                permCursor.put(userId, Math.min(MANAGEABLE_PERMISSIONS.size() - 1, cur + 1));
                event.editMessage(buildPermissionPanel(userId, hasAuth).build()).queue();
            }
            case "swap" -> {
                if (!hasAuth) { replyNoPermission(event); return; }
                swapCurrentPermState(userId);
                event.editMessage(buildPermissionPanel(userId, hasAuth).build()).queue();
            }
            case "confirm" -> {
                if (!hasAuth) { replyNoPermission(event); return; }
                savePermStateToDb(userId, guildId);
                event.editMessage(buildSettingPanel(guild, member).build()).queue();
            }
            case "perm_reset" -> {
                if (!hasAuth) { replyNoPermission(event); return; }
                String target = permTarget.getOrDefault(userId, "manager");
                loadPermStateFromDb(userId, guildId, target);
                event.editMessage(buildPermissionPanel(userId, hasAuth).build()).queue();
            }

            // ── Privilege Panel ──
            case "priv_add" -> {
                if (!member.hasPermission(Permission.MANAGE_CHANNEL)) { replyNoPermission(event); return; }
                showAddPrivilegeModal(event);
            }
            case "priv_remove" -> {
                if (!member.hasPermission(Permission.MANAGE_CHANNEL)) { replyNoPermission(event); return; }
                event.editMessage(buildPrivilegeRemovePanel(guild).build()).queue();
            }

            // ── Navigation ──
            case "back_main" -> {
                permCursor.remove(userId);
                permTarget.remove(userId);
                tempPermState.remove(userId);
                event.editMessage(editMainPanel(guild, member).build()).queue();
            }
        }
    }

    // ==================== MODAL HANDLER ====================

    @ModalHandler(MODAL_HANDLER)
    public void handleModal(ModalInteractionEvent event) {
        Guild guild = event.getGuild();
        Member member = event.getMember();
        if (guild == null || member == null) return;

        String modalId = event.getModalId().substring(modalPrefix.length());

        try {
            switch (modalId) {
                case "create" -> handleCreateModal(event, guild, member);
                case "import" -> handleImportModal(event, guild, member);
                case "priv_add" -> handleAddPrivilegeModal(event, guild, member);
            }
        } catch (Exception e) {
            context.log("error", "Modal error: " + e.getMessage(), e);
            event.reply("❌ An error occurred: " + e.getMessage())
                    .setEphemeral(true).queue(m -> m.deleteOriginal().queueAfter(5, TimeUnit.SECONDS));
        }
    }

    // ==================== SELECT MENU HANDLER ====================

    @SelectMenuHandler(MENU_HANDLER)
    public void handleSelectMenu(StringSelectInteractionEvent event) {
        Guild guild = event.getGuild();
        Member member = event.getMember();
        if (guild == null || member == null) return;

        String menuId = event.getComponentId().substring(menuPrefix.length());
        String selected = event.getValues().getFirst();

        switch (menuId) {
            case "view_cat" -> event.editMessage(buildCategoryDetailPanel(guild, selected).build()).queue();
            case "unlink_cat" -> handleUnlinkCategory(event, guild, member, selected);
            case "priv_rm" -> handleRemovePrivilege(event, guild, member, selected);
        }
    }

    // ==================== MODAL SHOW METHODS ====================

    private void showCreateModal(ButtonInteractionEvent event) {
        TextInput nameInput = TextInput.create("name", TextInputStyle.SHORT)
                .setPlaceholder("e.g. Staff Area, Private Channels")
                .setMinLength(1).setMaxLength(100).setRequired(true).build();

        EntitySelectMenu managerMenu = EntitySelectMenu.create("manager", EntitySelectMenu.SelectTarget.USER)
                .setRequiredRange(0, 1).setRequired(false).build();

        EntitySelectMenu roleMenu = EntitySelectMenu.create("default_role", EntitySelectMenu.SelectTarget.ROLE)
                .setRequiredRange(0, 1).setRequired(false).build();

        event.replyModal(Modal.create(modalPrefix + "create", "Create New Category")
                .addComponents(
                        Label.of("Category Name *", nameInput),
                        Label.of("Manager User (optional)", managerMenu),
                        Label.of("Default Role (optional)", roleMenu),
                        Label.of("Control this category via Pudel?", Checkbox.of("controlBy"))
                ).build()
        ).queue();
    }

    private void showImportModal(ButtonInteractionEvent event) {
        EntitySelectMenu categoryMenu = EntitySelectMenu.create("category", EntitySelectMenu.SelectTarget.CHANNEL)
                .setChannelTypes(ChannelType.CATEGORY)
                .setRequiredRange(1, 1).build();

        EntitySelectMenu managerMenu = EntitySelectMenu.create("manager", EntitySelectMenu.SelectTarget.USER)
                .setRequiredRange(0, 1).setRequired(false).build();

        EntitySelectMenu roleMenu = EntitySelectMenu.create("default_role", EntitySelectMenu.SelectTarget.ROLE)
                .setRequiredRange(0, 1).setRequired(false).build();

        CheckboxGroup ackGroup = CheckboxGroup.create("acknowledged")
                .addOption("Selecting Manager/Role will sync all child channel permissions & may make category private", "ack")
                .setMinValues(1)
                .setRequired(true)
                .build();

        event.replyModal(Modal.create(modalPrefix + "import", "Import Existing Category")
                .addComponents(
                        Label.of("Category to Import *", categoryMenu),
                        Label.of("Manager User (optional)", managerMenu),
                        Label.of("Default Role (optional)", roleMenu),
                        Label.of("Acknowledged", ackGroup)
                ).build()
        ).queue();
    }

    private void showAddPrivilegeModal(ButtonInteractionEvent event) {
        EntitySelectMenu roleMenu = EntitySelectMenu.create("role", EntitySelectMenu.SelectTarget.ROLE)
                .setRequiredRange(1, 1).build();

        event.replyModal(Modal.create(modalPrefix + "priv_add", "Add Privilege Role")
                .addComponents(Label.of("Select Role to Grant Privilege", roleMenu)).build()
        ).queue();
    }

    // ==================== MODAL HANDLE METHODS ====================

    private void handleCreateModal(ModalInteractionEvent event, Guild guild, Member member) {
        String name = getModalString(event, "name").trim();
        String managerId = getModalFirstId(event, "manager");
        String roleId = getModalFirstId(event, "default_role");
        boolean controlPudel = getModalBoolean(event, "controlBy");
        String guildId = guild.getId();

        if (name.isEmpty()) {
            event.reply("❌ Category name cannot be empty!").setEphemeral(true)
                    .queue(m -> m.deleteOriginal().queueAfter(5, TimeUnit.SECONDS));
            return;
        }

        CategorySetting settings = getOrCreateSetting(guildId);

        event.deferReply(true).queue(hook -> {
            guild.createCategory(name).queue(category -> {
                applyPermissions(category, guild, managerId, roleId, settings);

                if (controlPudel) {
                    categoryRepo.save(new CategoryEntry(null, guildId, category.getId(), managerId, roleId));
                }

                hook.editOriginal("✅ Category **" + name + "** created!" +
                                (controlPudel ? " (Tracked by Pudel)" : ""))
                        .queue(m -> m.delete().queueAfter(5, TimeUnit.SECONDS));

                refreshMainPanel(member.getId(), guild, member);
            }, err -> hook.editOriginal("❌ Failed to create category: " + err.getMessage())
                    .queue(m -> m.delete().queueAfter(5, TimeUnit.SECONDS)));
        });
    }

    private void handleImportModal(ModalInteractionEvent event, Guild guild, Member member) {
        String categoryId = getModalFirstId(event, "category");
        String managerId = getModalFirstId(event, "manager");
        String roleId = getModalFirstId(event, "default_role");
        List<String> ackValues = getModalIdList(event, "acknowledged");
        boolean acknowledged = ackValues.contains("ack");
        String guildId = guild.getId();

        if (categoryId == null) {
            event.reply("❌ Please select a category!").setEphemeral(true)
                    .queue(m -> m.deleteOriginal().queueAfter(5, TimeUnit.SECONDS));
            return;
        }

        // Check duplicate
        if (!categoryRepo.query().where("category_id", categoryId).list().isEmpty()) {
            event.reply("❌ This category is already tracked by Pudel!").setEphemeral(true)
                    .queue(m -> m.deleteOriginal().queueAfter(5, TimeUnit.SECONDS));
            return;
        }

        // Acknowledgement check when manager or role is set
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

        CategorySetting settings = getOrCreateSetting(guildId);

        event.deferReply(true).queue(hook -> {
            applyPermissions(category, guild, managerId, roleId, settings);

            // Sync child channels to updated category permissions
            if (hasManagerOrRole) {
                for (GuildChannel ch : category.getChannels()) {
                    if (ch instanceof ICategorizableChannel catCh) {
                        catCh.getManager().sync(category).queue(null, _ -> {});
                    }
                }
            }

            categoryRepo.save(new CategoryEntry(null, guildId, categoryId, managerId, roleId));

            hook.editOriginal("✅ Category **" + category.getName() + "** imported and tracked!")
                    .queue(m -> m.delete().queueAfter(5, TimeUnit.SECONDS));

            refreshMainPanel(member.getId(), guild, member);
        });
    }

    private void handleAddPrivilegeModal(ModalInteractionEvent event, Guild guild, Member member) {
        String roleId = getModalFirstId(event, "role");
        String guildId = guild.getId();

        if (roleId == null) {
            event.reply("❌ Please select a role!").setEphemeral(true)
                    .queue(m -> m.deleteOriginal().queueAfter(5, TimeUnit.SECONDS));
            return;
        }

        // Check duplicate
        if (!privilegeRepo.query().where("guild_id", guildId).where("role_id", roleId).list().isEmpty()) {
            event.reply("❌ This role is already a privilege role!").setEphemeral(true)
                    .queue(m -> m.deleteOriginal().queueAfter(5, TimeUnit.SECONDS));
            return;
        }

        privilegeRepo.save(new PrivilegeRole(null, guildId, roleId));

        Role role = guild.getRoleById(roleId);
        String roleName = role != null ? role.getName() : roleId;

        event.reply("✅ Role **" + roleName + "** added as privilege role!").setEphemeral(true)
                .queue(m -> m.deleteOriginal().queueAfter(5, TimeUnit.SECONDS));

        // Refresh privilege panel on control message
        Message msg = controlMessages.get(member.getId());
        if (msg != null) {
            boolean hasAuth = member.hasPermission(Permission.MANAGE_CHANNEL);
            msg.editMessage(buildPrivilegePanel(guild, hasAuth).build()).queue(null, _ -> {});
        }
    }

    // ==================== UNLINK / PRIVILEGE REMOVE ====================

    private void handleUnlinkCategory(StringSelectInteractionEvent event, Guild guild, Member member, String categoryId) {
        List<CategoryEntry> entries = categoryRepo.query().where("category_id", categoryId).list();
        if (entries.isEmpty()) {
            event.reply("❌ Category record not found!").setEphemeral(true)
                    .queue(m -> m.deleteOriginal().queueAfter(5, TimeUnit.SECONDS));
            return;
        }

        CategoryEntry entry = entries.getFirst();

        // Permission check: MANAGE_CHANNEL, Privilege Role, or matching manager_id
        String userId = member.getId();
        boolean isManager = entry.getManager_id() != null && entry.getManager_id().equals(userId);
        if (!hasPermissionOrPrivilege(member, guild.getId()) && !isManager) {
            replyNoPermissionSelect(event);
            return;
        }

        categoryRepo.deleteById(entry.getId());

        Category cat = guild.getCategoryById(categoryId);
        String catName = cat != null ? cat.getName() : categoryId;

        event.reply("✅ Category **" + catName + "** unlinked from Pudel (category kept in guild).")
                .setEphemeral(true).queue(m -> m.deleteOriginal().queueAfter(5, TimeUnit.SECONDS));

        // Refresh to main panel
        Message msg = controlMessages.get(userId);
        if (msg != null) msg.editMessage(editMainPanel(guild, member).build()).queue(null, _ -> {});
    }

    private void handleRemovePrivilege(StringSelectInteractionEvent event, Guild guild, Member member, String roleId) {
        List<PrivilegeRole> roles = privilegeRepo.query()
                .where("guild_id", guild.getId()).where("role_id", roleId).list();
        if (!roles.isEmpty()) {
            privilegeRepo.deleteById(roles.getFirst().getId());
        }

        boolean hasAuth = member.hasPermission(Permission.MANAGE_CHANNEL);
        event.editMessage(buildPrivilegePanel(guild, hasAuth).build()).queue();
    }

    // ==================== PANEL BUILDERS ====================

    private Container buildMainPanel(Guild guild, Member member) {
        String guildId = guild.getId();
        boolean hasAuth = hasPermissionOrPrivilege(member, guildId);

        int guildCatCount = guild.getCategories().size();
        int trackedCount = categoryRepo.query().where("guild_id", guildId).list().size();

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

    private MessageEditBuilder editMainPanel(Guild guild, Member member) {
        return new MessageEditBuilder().useComponentsV2(true).setComponents(buildMainPanel(guild, member));
    }

    private MessageEditBuilder buildSettingPanel(Guild guild, Member member) {
        String guildId = guild.getId();
        CategorySetting settings = getOrCreateSetting(guildId);

        String managerSummary = formatPermSummary(settings.getManager_allow(), settings.getManager_deny());
        String roleSummary = formatPermSummary(settings.getRole_allow(), settings.getRole_deny());

        List<PrivilegeRole> privRoles = privilegeRepo.query().where("guild_id", guildId).list();
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
                        TextDisplay.of("# ⚙️ Default Permission Settings"),
                        Separator.create(true, Separator.Spacing.SMALL),
                        TextDisplay.of("### 👤 Manager Default Permission\n" + managerSummary),
                        Separator.create(false, Separator.Spacing.SMALL),
                        TextDisplay.of("### 🎭 Default Role Permission\n" + roleSummary),
                        Separator.create(false, Separator.Spacing.SMALL),
                        TextDisplay.of("### 🛡️ Privilege Roles\n" + privSummary),
                        Separator.create(true, Separator.Spacing.SMALL),
                        ActionRow.of(
                                Button.primary(btnPrefix + "perm_manager", "👤 Edit Manager Perm"),
                                Button.primary(btnPrefix + "perm_role", "🎭 Edit Role Perm")
                        ),
                        ActionRow.of(
                                Button.primary(btnPrefix + "privilege", "🛡️ Privilege Roles"),
                                Button.secondary(btnPrefix + "back_main", "⬅️ Back")
                        )
                ).withAccentColor(ACCENT_SETTING)
        );
    }

    private MessageEditBuilder buildPermissionPanel(String userId, boolean hasAuth) {
        String target = permTarget.getOrDefault(userId, "manager");
        int cursor = permCursor.getOrDefault(userId, 0);
        LinkedHashMap<String, String> state = tempPermState.get(userId);

        if (state == null) {
            return new MessageEditBuilder().useComponentsV2(true).setComponents(
                    Container.of(TextDisplay.of("❌ Session expired. Use `/categorizement` again."))
                            .withAccentColor(ACCENT_DANGER));
        }

        String title = target.equals("manager")
                ? "### 👤 Manager Default Permission"
                : "### 🎭 Default Role Permission";

        StringBuilder sb = new StringBuilder();
        sb.append(title).append("\n\n");

        String lastSection = "";
        int index = 0;
        for (PermInfo pi : MANAGEABLE_PERMISSIONS) {
            if (!pi.section().equals(lastSection)) {
                if (!lastSection.isEmpty()) sb.append("\n");
                sb.append("**").append(pi.section()).append("**\n");
                lastSection = pi.section();
            }

            String indicator = index == cursor ? "▸ " : "　";
            String permName = pi.perm().name();
            String stateStr = state.getOrDefault(permName, "INHERIT");
            String stateIcon = switch (stateStr) {
                case "ALLOW" -> "✅";
                case "DENY" -> "❌";
                default -> "⬜";
            };

            sb.append(indicator).append(stateIcon).append(" ").append(pi.displayName())
                    .append(" — ").append(stateStr).append("\n");
            index++;
        }

        sb.append("\n-# ↕️ Navigate • 🔄 Swap state • ✅ Confirm to save");

        return new MessageEditBuilder().useComponentsV2(true).setComponents(
                Container.of(
                        TextDisplay.of(sb.toString()),
                        Separator.create(true, Separator.Spacing.SMALL),
                        ActionRow.of(
                                Button.primary(btnPrefix + "up", "⬆️ Up").withDisabled(!hasAuth),
                                Button.primary(btnPrefix + "down", "⬇️ Down").withDisabled(!hasAuth),
                                Button.success(btnPrefix + "swap", "🔄 Swap").withDisabled(!hasAuth)
                        ),
                        ActionRow.of(
                                Button.secondary(btnPrefix + "back_setting", "⬅️ Back"),
                                Button.success(btnPrefix + "confirm", "✅ Confirm").withDisabled(!hasAuth),
                                Button.danger(btnPrefix + "perm_reset", "🔃 Reset").withDisabled(!hasAuth)
                        )
                ).withAccentColor(ACCENT_PERM)
        );
    }

    private MessageEditBuilder buildPrivilegePanel(Guild guild, boolean hasAuth) {
        String guildId = guild.getId();
        List<PrivilegeRole> roles = privilegeRepo.query().where("guild_id", guildId).list();

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

    private MessageEditBuilder buildPrivilegeRemovePanel(Guild guild) {
        String guildId = guild.getId();
        List<PrivilegeRole> roles = privilegeRepo.query().where("guild_id", guildId).list();

        StringSelectMenu.Builder menuBuilder = StringSelectMenu.create(menuPrefix + "priv_rm")
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

    private MessageEditBuilder buildViewCategoryPanel(Guild guild) {
        String guildId = guild.getId();
        List<CategoryEntry> entries = categoryRepo.query().where("guild_id", guildId).list();

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

        StringSelectMenu.Builder menuBuilder = StringSelectMenu.create(menuPrefix + "view_cat")
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

    private MessageEditBuilder buildUnlinkPanel(Guild guild, Member member) {
        String guildId = guild.getId();
        String userId = member.getId();
        boolean hasAuth = hasPermissionOrPrivilege(member, guildId);

        List<CategoryEntry> entries = categoryRepo.query().where("guild_id", guildId).list();

        // Filter based on permission: admin sees all, manager sees own, others see none
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

        StringSelectMenu.Builder menuBuilder = StringSelectMenu.create(menuPrefix + "unlink_cat")
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

    private MessageEditBuilder buildCategoryDetailPanel(Guild guild, String categoryId) {
        CategoryEntry entry = categoryRepo.query().where("category_id", categoryId).list()
                .stream().findFirst().orElse(null);
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

        // Count channels by type
        int textCount = 0, voiceCount = 0, forumCount = 0;
        for (GuildChannel ch : cat.getChannels()) {
            switch (ch.getType()) {
                case TEXT, NEWS -> textCount++;
                case VOICE, STAGE -> voiceCount++;
                case FORUM, MEDIA -> forumCount++;
                default -> {}
            }
        }

        String managerStr = entry.getManager_id() != null ? "<@" + entry.getManager_id() + ">" : "_Not set_";
        String roleStr = entry.getDefault_role() != null ? "<@&" + entry.getDefault_role() + ">" : "_Not set_";

        String sb = """
                ### 📂 %s
                📝 **Text Channels:** %d
                🔊 **Voice Channels:** %d
                💬 **Forum Channels:** %d
                
                👤 **Managed by:** %s
                🎭 **Default Role:** %s
                
                -*Unsync detection will be available in a future update*
                """.formatted(cat.getName(), textCount, voiceCount, forumCount, managerStr, roleStr);

        return new MessageEditBuilder().useComponentsV2(true).setComponents(
                Container.of(
                        TextDisplay.of(sb),
                        Separator.create(true, Separator.Spacing.SMALL),
                        ActionRow.of(Button.secondary(btnPrefix + "back_view", "⬅️ Back"))
                ).withAccentColor(ACCENT_MAIN)
        );
    }

    // ==================== PERMISSION MANAGEMENT ====================

    /**
     * Apply permission overrides to a Discord category based on settings.
     */
    private void applyPermissions(Category category, Guild guild, String managerId, String roleId, CategorySetting settings) {
        if (managerId != null) {
            Member manager = guild.getMemberById(managerId);
            if (manager != null) {
                EnumSet<Permission> allow = parsePermissions(settings != null ? settings.getManager_allow() : "");
                EnumSet<Permission> deny = parsePermissions(settings != null ? settings.getManager_deny() : "");
                category.upsertPermissionOverride(manager)
                        .setAllowed(allow)
                        .setDenied(deny)
                        .queue(null, err -> context.log("warn", "Failed to set manager override: " + err.getMessage()));
            }
        }

        if (roleId != null) {
            Role role = guild.getRoleById(roleId);
            if (role != null) {
                EnumSet<Permission> allow = parsePermissions(settings != null ? settings.getRole_allow() : "");
                EnumSet<Permission> deny = parsePermissions(settings != null ? settings.getRole_deny() : "");
                // Spec: Default Role always gets VIEW_CHANNEL
                allow.add(Permission.VIEW_CHANNEL);
                category.upsertPermissionOverride(role)
                        .setAllowed(allow)
                        .setDenied(deny)
                        .queue(null, err -> context.log("warn", "Failed to set role override: " + err.getMessage()));

                // Spec: Deny VIEW_CHANNEL for @everyone to make category private
                if(!roleId.equals(guild.getPublicRole().getId())){
                    category.upsertPermissionOverride(guild.getPublicRole())
                            .deny(Permission.VIEW_CHANNEL)
                            .queue(null, err -> context.log("warn", "Failed to set @everyone override: " + err.getMessage()));
                }
            }
        }
    }

    private EnumSet<Permission> parsePermissions(String permString) {
        if (permString == null || permString.isBlank()) return EnumSet.noneOf(Permission.class);
        EnumSet<Permission> set = EnumSet.noneOf(Permission.class);
        for (String name : permString.split(",")) {
            try {
                set.add(Permission.valueOf(name.trim()));
            } catch (IllegalArgumentException _) {}
        }
        return set;
    }

    private String formatPermSummary(String allow, String deny) {
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

    /** Load permission state from DB into temporary session map. */
    private void loadPermStateFromDb(String userId, String guildId, String target) {
        CategorySetting settings = getOrCreateSetting(guildId);
        String allowStr = target.equals("manager") ? settings.getManager_allow() : settings.getRole_allow();
        String denyStr = target.equals("manager") ? settings.getManager_deny() : settings.getRole_deny();

        Set<String> allowSet = (allowStr != null && !allowStr.isBlank())
                ? Set.of(allowStr.split(",")) : Set.of();
        Set<String> denySet = (denyStr != null && !denyStr.isBlank())
                ? Set.of(denyStr.split(",")) : Set.of();

        LinkedHashMap<String, String> state = new LinkedHashMap<>();
        for (PermInfo pi : MANAGEABLE_PERMISSIONS) {
            String name = pi.perm().name();
            if (allowSet.contains(name)) state.put(name, "ALLOW");
            else if (denySet.contains(name)) state.put(name, "DENY");
            else state.put(name, "INHERIT");
        }
        tempPermState.put(userId, state);
    }

    /** Save temporary permission state to DB. */
    private void savePermStateToDb(String userId, String guildId) {
        LinkedHashMap<String, String> state = tempPermState.get(userId);
        if (state == null) return;

        String target = permTarget.getOrDefault(userId, "manager");
        String allow = state.entrySet().stream()
                .filter(e -> e.getValue().equals("ALLOW"))
                .map(Map.Entry::getKey)
                .collect(Collectors.joining(","));
        String deny = state.entrySet().stream()
                .filter(e -> e.getValue().equals("DENY"))
                .map(Map.Entry::getKey)
                .collect(Collectors.joining(","));

        CategorySetting existing = getOrCreateSetting(guildId);

        CategorySetting updated;
        if (target.equals("manager")) {
            updated = new CategorySetting(existing.getId(), guildId, allow, deny, existing.getRole_allow(), existing.getRole_deny());
        } else {
            updated = new CategorySetting(existing.getId(), guildId, existing.getManager_allow(), existing.getManager_deny(), allow, deny);
        }
        settingRepo.save(updated);

        // Clean session
        permCursor.remove(userId);
        permTarget.remove(userId);
        tempPermState.remove(userId);
    }

    /** Cycle the permission state at the current cursor: INHERIT → ALLOW → DENY → INHERIT. */
    private void swapCurrentPermState(String userId) {
        int cursor = permCursor.getOrDefault(userId, 0);
        LinkedHashMap<String, String> state = tempPermState.get(userId);
        if (state == null || cursor >= MANAGEABLE_PERMISSIONS.size()) return;

        String permName = MANAGEABLE_PERMISSIONS.get(cursor).perm().name();
        String current = state.getOrDefault(permName, "INHERIT");
        String next = switch (current) {
            case "INHERIT" -> "ALLOW";
            case "ALLOW" -> "DENY";
            default -> "INHERIT";
        };
        state.put(permName, next);
    }

    /** Get or create a default setting entry for a guild. */
    private CategorySetting getOrCreateSetting(String guildId) {
        List<CategorySetting> existing = settingRepo.query().where("guild_id", guildId).list();
        if (!existing.isEmpty()) return existing.getFirst();

        CategorySetting fresh = new CategorySetting(null, guildId, "", "", "", "");
        settingRepo.save(fresh);
        return settingRepo.query().where("guild_id", guildId).list().getFirst();
    }

    // ==================== UTILITY ====================

    /** Check if member has MANAGE_CHANNEL permission or a Privilege Role. */
    private boolean hasPermissionOrPrivilege(Member member, String guildId) {
        if (member.hasPermission(Permission.MANAGE_CHANNEL)) return true;
        List<PrivilegeRole> privRoles = privilegeRepo.query().where("guild_id", guildId).list();
        Set<String> privRoleIds = privRoles.stream().map(PrivilegeRole::getRole_id).collect(Collectors.toSet());
        return member.getRoles().stream().anyMatch(r -> privRoleIds.contains(r.getId()));
    }

    private void replyNoPermission(ButtonInteractionEvent event) {
        event.reply("❌ You need **Manage Channels** permission or a Privilege Role to do this!")
                .setEphemeral(true).queue(m -> m.deleteOriginal().queueAfter(5, TimeUnit.SECONDS));
    }

    private void replyNoPermissionSelect(StringSelectInteractionEvent event) {
        event.reply("❌ You don't have permission to perform this action!")
                .setEphemeral(true).queue(m -> m.deleteOriginal().queueAfter(5, TimeUnit.SECONDS));
    }

    private void refreshMainPanel(String userId, Guild guild, Member member) {
        Message msg = controlMessages.get(userId);
        if (msg != null) {
            msg.editMessage(editMainPanel(guild, member).build()).queue(null, _ -> {});
        }
    }

    /** Get a TextInput string value from a modal. */
    private String getModalString(ModalInteractionEvent event, String id) {
        var v = event.getValue(id);
        return v != null ? v.getAsString() : "";
    }

    private Boolean getModalBoolean(ModalInteractionEvent event, String id) {
        var v = event.getValue(id);
        return v != null && v.getAsBoolean();
    }

    /** Get the first entity ID from a select menu or checkbox in a modal. */
    private String getModalFirstId(ModalInteractionEvent event, String id) {
        var v = event.getValue(id);
        if (v == null) return null;
        List<String> list = v.getAsStringList();
        return !list.isEmpty() ? list.getFirst() : null;
    }

    /** Get all selected IDs from a select menu or checkbox group in a modal. */
    private List<String> getModalIdList(ModalInteractionEvent event, String id) {
        var v = event.getValue(id);
        if (v == null) return List.of();
        return v.getAsStringList();
    }
}
