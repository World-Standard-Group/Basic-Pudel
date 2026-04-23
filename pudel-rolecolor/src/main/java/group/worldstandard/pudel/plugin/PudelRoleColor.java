/*
 * Basic Pudel - Role Color Plugin
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
import group.worldstandard.pudel.plugin.entity.ManagedRole;
import group.worldstandard.pudel.plugin.entity.UserAssignment;
import group.worldstandard.pudel.plugin.helper.RoleColorService;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.IntegrationType;
import net.dv8tion.jda.api.interactions.InteractionContextType;
import net.dv8tion.jda.api.interactions.commands.OptionType;

import java.awt.Color;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

/**
 * RoleColorPlugin — Allows users to set custom color roles.
 *
 * <p>Delegates business logic to {@link RoleColorService}.
 * Entity classes live in {@link group.worldstandard.pudel.plugin.entity}.
 *
 * @author Zazalng
 * @since 1.0.0
 */
@Plugin(
        name = "Pudel's Role Color",
        version = "1.0.2",
        author = "Zazalng",
        description = "Custom color roles for users"
)
public class PudelRoleColor {

    private static final Pattern HEX_PATTERN = Pattern.compile("^#?([A-Fa-f0-9]{6})$");

    private PluginContext context;
    private RoleColorService service;

    // ==================== LIFECYCLE ====================

    @OnEnable
    public void onEnable(PluginContext ctx) {
        this.context = ctx;
        initializeDatabase(ctx.getDatabaseManager());

        ctx.log("info", "RoleColor plugin loaded & database initialized.");
    }

    @OnShutdown
    public boolean onShutdown(PluginContext ctx) {
        return true;
    }

    // ==================== Database =====================
    private void initializeDatabase(PluginDatabaseManager db){
        migrationDatabase(db);
        createRepository(db);
    }

    private void migrationDatabase(PluginDatabaseManager db){
        db.migrate(1, _ -> {
            TableSchema roleSchema = TableSchema.builder("managed_roles")
                    .column("role_id", ColumnType.BIGINT, false)
                    .column("guild_id", ColumnType.BIGINT, false)
                    .column("hex_code", ColumnType.STRING, 7, false)
                    .index("role_id")
                    .uniqueIndex("guild_id", "hex_code")
                    .build();

            db.createTable(roleSchema);

            TableSchema assignmentSchema = TableSchema.builder("user_assignments")
                    .column("user_id", ColumnType.BIGINT, false)
                    .column("guild_id", ColumnType.BIGINT, false)
                    .column("role_id", ColumnType.BIGINT, false)
                    .index("role_id")
                    .uniqueIndex("user_id", "guild_id")
                    .build();

            db.createTable(assignmentSchema);
        });
    }

    private void createRepository(PluginDatabaseManager db){
        PluginRepository<ManagedRole> roleRepo = db.getRepository("managed_roles", ManagedRole.class);
        PluginRepository<UserAssignment> assignmentRepo = db.getRepository("user_assignments", UserAssignment.class);

        this.service = new RoleColorService(context, roleRepo, assignmentRepo);
    }

    // ==================== SLASH COMMAND ====================

    @SlashCommand(
            name = "rolecolor",
            description = "Set your name color or reset it.",
            nsfw = false,
            options = {
                    @CommandOption(
                            name = "input",
                            description = "Hex color code (e.g. FF0000) or 'reset'",
                            type = OptionType.STRING,
                            required = true
                    )
            },
            global = false,
            integrationTo = {IntegrationType.GUILD_INSTALL},
            integrationContext = {InteractionContextType.GUILD}
    )
    public void onRoleColor(SlashCommandInteractionEvent event) {
        // Defer reply since Discord API calls might take time
        event.deferReply().setEphemeral(true).queue();

        Guild guild = event.getGuild();
        Member member = event.getMember();
        String input = event.getOption("input").getAsString().trim();

        if (guild == null || member == null) {
            event.getHook().editOriginal("❌ This command can only be used in a server.").queue(m -> m.delete().queueAfter(5, TimeUnit.SECONDS));
            return;
        }

        // Check bot permissions
        if (!guild.getSelfMember().hasPermission(Permission.MANAGE_ROLES)) {
            event.getHook().editOriginal("❌ I need the **Manage Roles** permission to function.").queue();
            return;
        }

        try {
            if (input.equalsIgnoreCase("reset")) {
                service.handleReset(guild, member, event);
            } else {
                String cleanHex = input.replace("#", "").toUpperCase();
                if (!HEX_PATTERN.matcher(cleanHex).matches()) {
                    event.getHook().editOriginal("❌ Invalid format. Please use a hex code like `FF0000`.").queue(m -> m.delete().queueAfter(5, TimeUnit.SECONDS));
                    return;
                }

                Color color;
                try {
                    color = new Color(Integer.parseInt(cleanHex, 16));
                } catch (NumberFormatException e) {
                    event.getHook().editOriginal("❌ Invalid color code.").queue(m -> m.delete().queueAfter(5, TimeUnit.SECONDS));
                    return;
                }

                service.handleSetColor(guild, member, cleanHex, color, event);
            }
        } catch (Exception e) {
            context.log("error", "Error in rolecolor: " + e.getMessage());
            event.getHook().editOriginal("❌ An internal error occurred.").queue(m -> m.delete().queueAfter(5, TimeUnit.SECONDS));
        }
    }
}