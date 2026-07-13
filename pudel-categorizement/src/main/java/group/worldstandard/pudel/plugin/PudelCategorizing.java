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
import group.worldstandard.pudel.api.database.*;
import group.worldstandard.pudel.plugin.builder.PanelBuilder;
import group.worldstandard.pudel.plugin.entity.CategoryEntry;
import group.worldstandard.pudel.plugin.entity.PermissionProfile;
import group.worldstandard.pudel.plugin.entity.PrivilegeRole;
import group.worldstandard.pudel.plugin.handler.*;
import group.worldstandard.pudel.plugin.handler.ButtonHandler;
import group.worldstandard.pudel.plugin.handler.ModalHandler;
import group.worldstandard.pudel.plugin.handler.SelectMenuHandler;
import group.worldstandard.pudel.plugin.service.PermissionService;
import group.worldstandard.pudel.plugin.session.SessionManager;
import net.dv8tion.jda.api.entities.*;
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.EntitySelectInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.StringSelectInteractionEvent;
import net.dv8tion.jda.api.interactions.IntegrationType;
import net.dv8tion.jda.api.interactions.InteractionContextType;
import net.dv8tion.jda.api.utils.messages.MessageCreateBuilder;

import java.util.concurrent.TimeUnit;

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
        version = "2.1.0",
        description = "Manages Discord categories and lets administrators assign category managers."
)
public class PudelCategorizing {

    // ==================== HANDLER IDS ====================
    private static final String BTN_HANDLER = ":button:";
    private static final String MODAL_HANDLER = ":modal:";
    private static final String STRING_MENU_HANDLER = ":string:";
    private static final String ENTITY_MENU_HANDLER = ":entity:";

    // ==================== RUNTIME PREFIXES ====================
    private String btnPrefix;
    private String modalPrefix;
    private String stringMenuPrefix;
    private String entityMenuPrefix;

    // ==================== SERVICES & STATE ====================
    private PluginContext context;
    private PluginRepository<CategoryEntry> categoryRepo;
    private PluginRepository<PermissionProfile> profileRepo;
    private PluginRepository<PrivilegeRole> privilegeRepo;

    private PermissionService permissionService;
    private SessionManager sessionManager;
    private PanelBuilder panelBuilder;
    private ButtonHandler buttonHandler;
    private ModalHandler modalHandler;
    private SelectMenuHandler selectMenuHandler;
    private EntitySelectMenuHandler entitySelectMenuHandler;

    // ==================== LIFECYCLE ====================

    @OnEnable
    public void onEnable(PluginContext ctx) {
        this.context = ctx;
        String prefix = ctx.getDatabaseManager().getSchemaName();
        this.btnPrefix = prefix + BTN_HANDLER;
        this.modalPrefix = prefix + MODAL_HANDLER;
        this.stringMenuPrefix = prefix + STRING_MENU_HANDLER;
        this.entityMenuPrefix = prefix + ENTITY_MENU_HANDLER;

        initializeDatabase(ctx.getDatabaseManager());
        initializeServices();
        initializeHandlers();

        ctx.log("info", "%s (v%s) has initialized on '%s'".formatted(
                ctx.getInfo().getName(), ctx.getInfo().getVersion(), ctx.getPudel().getUserAgent())
        );
    }

    @OnShutdown
    public boolean onShutdown(PluginContext ctx) {
        try {
            if (sessionManager != null) {
                sessionManager.clearAllSessions();
            }
            ctx.log("info", "%s (v%s) graceful shutdown on '%s'".formatted(
                    ctx.getInfo().getName(), ctx.getInfo().getVersion(), ctx.getPudel().getUserAgent())
            );
            context = null;
            return true;
        } catch (Exception e) {
            ctx.log("error", "Unable to shutdown gracefully: '%s'".formatted(e.getMessage()), e);
            return false;
        }
    }

    // ==================== INITIALIZATION ====================

    private void initializeDatabase(PluginDatabaseManager db) {
        migrateDatabase(db);
        createRepositories(db);
    }

    private void migrateDatabase(PluginDatabaseManager db) {
        db.migrate(1, _ -> {
            TableSchema categorySchema = TableSchema.builder("category")
                    .column("guild_id", ColumnType.TEXT, false)
                    .column("category_id", ColumnType.TEXT, false)
                    .column("manager_id", ColumnType.TEXT, true)
                    .column("manager_role_profile", ColumnType.TEXT, true)
                    .column("default_role", ColumnType.TEXT, true)
                    .column("default_role_profile", ColumnType.TEXT, true)
                    .uniqueIndex("category_id")
                    .build();
            context.log("info", "Creating table '%s': %s".formatted(categorySchema.getTableName(), db.createTable(categorySchema)));

            TableSchema profileSchema = TableSchema.builder("permission_profile")
                    .column("guild_id", ColumnType.TEXT, false)
                    .column("name", ColumnType.TEXT, false)
                    .column("allow", ColumnType.TEXT, false, "''")
                    .column("deny", ColumnType.TEXT, false, "''")
                    .uniqueIndex("guild_id", "name")
                    .build();
            context.log("info", "Creating table '%s': %s".formatted(profileSchema.getTableName(), db.createTable(profileSchema)));

            TableSchema privilegeSchema = TableSchema.builder("privilege_role")
                    .column("guild_id", ColumnType.TEXT, false)
                    .column("role_id", ColumnType.TEXT, false)
                    .index("guild_id")
                    .build();
            context.log("info", "Creating table '%s': %s".formatted(privilegeSchema.getTableName(), db.createTable(privilegeSchema)));
        });

        db.migrate(2, m -> {
            m.dropIndex("privilege_role", "guild_id");
            m.createIndex("privilege_role", true, "guild_id", "role_id");
        });

        db.migrate(3, m -> {
            m.dropIndex("category", "category_id");
            m.createIndex("category", true, "guild_id", "category_id");
        });
    }

    private void createRepositories(PluginDatabaseManager db) {
        this.categoryRepo = db.getRepository("category", CategoryEntry.class);
        this.profileRepo = db.getRepository("permission_profile", PermissionProfile.class);
        this.privilegeRepo = db.getRepository("privilege_role", PrivilegeRole.class);
    }

    private void initializeServices() {
        this.sessionManager = new SessionManager();
        this.permissionService = new PermissionService(profileRepo, privilegeRepo, categoryRepo, this::logWarn);
    }

    private void initializeHandlers() {
        this.panelBuilder = new PanelBuilder(btnPrefix, stringMenuPrefix, entityMenuPrefix, permissionService, sessionManager);
        this.buttonHandler = new ButtonHandler(btnPrefix, permissionService, sessionManager, panelBuilder);
        this.modalHandler = new ModalHandler(modalPrefix, permissionService, sessionManager, panelBuilder, logModal());
        this.selectMenuHandler = new SelectMenuHandler(stringMenuPrefix, permissionService, sessionManager, panelBuilder);
        this.entitySelectMenuHandler = new EntitySelectMenuHandler(entityMenuPrefix, sessionManager, panelBuilder);
    }

    // ==================== SLASH COMMAND ====================

    @SlashCommand(
            name = "categorizement",
            description = "Open Control Panel for management category channel",
            nsfw = false,
            global = false,
            integrationTo = {IntegrationType.GUILD_INSTALL},
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
        Message old = sessionManager.removeControlMessage(userId);
        if (old != null) {
            try { old.delete().queue(null, _ -> {}); } catch (Exception ignored) {}
        }

        // Clean session state
        sessionManager.clearUserSession(userId);

        event.reply(
                new MessageCreateBuilder()
                        .useComponentsV2(true)
                        .setComponents(panelBuilder.buildMainPanel(guild, member))
                        .build()
        ).setEphemeral(true).queue(hook -> hook.retrieveOriginal().queue(msg -> sessionManager.putControlMessage(userId, msg)));
    }

    // ==================== BUTTON HANDLER ====================

    @group.worldstandard.pudel.api.annotation.ButtonHandler(BTN_HANDLER)
    public void handleButton(ButtonInteractionEvent event) {
        buttonHandler.handle(event);
    }

    // ==================== MODAL HANDLER ====================

    @group.worldstandard.pudel.api.annotation.ModalHandler(MODAL_HANDLER)
    public void handleModal(ModalInteractionEvent event) {
        modalHandler.handle(event);
    }

    // ==================== SELECT MENU HANDLERS ====================

    @group.worldstandard.pudel.api.annotation.SelectMenuHandler(STRING_MENU_HANDLER)
    public void handleSelectMenu(StringSelectInteractionEvent event) {
        selectMenuHandler.handle(event);
    }

    @group.worldstandard.pudel.api.annotation.SelectMenuHandler(ENTITY_MENU_HANDLER)
    public void handleEntityMenu(EntitySelectInteractionEvent event) {
        entitySelectMenuHandler.handle(event);
    }

    // ==================== LOGGING HELPERS ====================

    private void logWarn(String message) {
        context.log("warn", message);
    }

    // Adapter for ModalHandler logging
    private ModalHandler.ModalLogger logModal() {
        return new ModalHandler.ModalLogger() {
            @Override
            public void warn(String message) {
                context.log("warn", message);
            }

            @Override
            public void error(String message, Throwable t) {
                context.log("error", message, t);
            }
        };
    }
}