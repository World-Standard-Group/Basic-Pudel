/*
 * Basic Pudel - Message Plugin Commands
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
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.components.label.Label;
import net.dv8tion.jda.api.components.selections.StringSelectMenu;
import net.dv8tion.jda.api.components.textinput.TextInput;
import net.dv8tion.jda.api.components.textinput.TextInputStyle;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.channel.unions.GuildChannelUnion;
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.StringSelectInteractionEvent;
import net.dv8tion.jda.api.modals.Modal;

import java.awt.Color;
import java.net.URI;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Interactive Embed Builder Plugin for Pudel Discord Bot
 * <p>
 * Features:
 * - Slash command interactions with buttons and modals
 * - Visual embed builder with live preview
 * - Color picker with presets and custom hex
 * - Field management (add, clear)
 * - Session-based editing per user
 *
 * @author Zazalng
 * @version 2.0.0
 */
@Plugin(
        name = "Pudel Embed Builder",
        version = "2.0.0",
        author = "Zazalng",
        description = "Interactive embed builder with buttons and modals"
)
public class PudelMessagePlugin {

    // ==================== CONSTANTS ====================
    private static final String BUTTON_PREFIX = "embed:";
    private static final String MODAL_PREFIX = "embed:modal:";
    private static final String SELECT_PREFIX = "embed:select:";

    // ==================== STATE MANAGEMENT ====================
    private PluginContext context;
    private final Map<Long, EmbedSession> activeSessions = new ConcurrentHashMap<>();

    // ==================== LIFECYCLE HOOKS ====================

    @OnEnable
    public void onEnable(PluginContext ctx) {
        this.context = ctx;
        ctx.log("info", "PudelMessagePlugin initialized with interactive embed builder");
    }

    @OnShutdown
    public boolean onShutdown() {
        // Clean up active sessions
        for (EmbedSession session : activeSessions.values()) {
            if (session.previewMessage != null) {
                session.previewMessage.delete().queue(null, e -> {});
            }
        }
        activeSessions.clear();
        context.log("info", "PudelMessagePlugin shutdown successfully");
        return true;
    }

    // ==================== SLASH COMMANDS ====================

    @SlashCommand(
            name = "embed",
            description = "Create and build Discord embeds interactively",
            subcommands = {
                    @Subcommand(name = "create", description = "Create a new embed builder session"),
                    @Subcommand(name = "build", description = "Post the current embed to a channel", options = {
                            @CommandOption(name = "channel", description = "Channel to post the embed", type = "CHANNEL", required = true)
                    }),
                    @Subcommand(name = "cancel", description = "Cancel the current embed session"),
                    @Subcommand(name = "preview", description = "Show a preview of the current embed")
            }
    )
    public void handleEmbedCommand(SlashCommandInteractionEvent event) {
        if (!event.isFromGuild()) {
            event.reply("❌ This command can only be used in a server!").setEphemeral(true).queue();
            return;
        }

        String subCommand = event.getSubcommandName();
        if (subCommand == null) return;

        long userId = event.getUser().getIdLong();

        switch (subCommand) {
            case "create" -> createNewSession(event, userId);
            case "build" -> buildEmbed(event, userId);
            case "cancel" -> cancelSession(event, userId);
            case "preview" -> showPreview(event, userId);
        }
    }

    // ==================== BUTTON HANDLERS ====================

    @ButtonHandler(BUTTON_PREFIX)
    public void handleButton(ButtonInteractionEvent event) {
        long userId = event.getUser().getIdLong();
        EmbedSession session = activeSessions.get(userId);

        if (session == null) {
            event.reply("❌ No active session! Use `/embed create` to start.").setEphemeral(true).queue();
            return;
        }

        String buttonId = event.getComponentId().substring(BUTTON_PREFIX.length());

        switch (buttonId) {
            case "title" -> showTitleModal(event);
            case "description" -> showDescriptionModal(event);
            case "color" -> showColorSelectMenu(event);
            case "author" -> showAuthorModal(event);
            case "footer" -> showFooterModal(event);
            case "thumbnail" -> showThumbnailModal(event);
            case "image" -> showImageModal(event);
            case "url" -> showUrlModal(event);
            case "timestamp" -> showTimestampModal(event);
            case "field" -> showFieldModal(event);
            case "clearfields" -> {
                session.fields.clear();
                updateSessionPreview(event, session);
            }
            case "reset" -> {
                session.reset();
                updateSessionPreview(event, session);
            }
        }
    }

    // ==================== MODAL HANDLERS ====================

    @ModalHandler(MODAL_PREFIX)
    public void handleModal(ModalInteractionEvent event) {
        long userId = event.getUser().getIdLong();
        EmbedSession session = activeSessions.get(userId);

        if (session == null) {
            event.reply("❌ Session expired! Use `/embed create` to start a new one.").setEphemeral(true).queue();
            return;
        }

        String modalId = event.getModalId().substring(MODAL_PREFIX.length());

        try {
            switch (modalId) {
                case "title" -> {
                    String title = getModalValue(event, "title");
                    session.title = title.isEmpty() ? null : title;
                }
                case "description" -> {
                    String desc = getModalValue(event, "description");
                    session.description = desc.isEmpty() ? null : desc;
                }
                case "author" -> {
                    String author = getModalValue(event, "author");
                    String authorUrl = getModalValue(event, "authorurl");
                    String authorIcon = getModalValue(event, "authoricon");
                    session.author = author.isEmpty() ? null : author;
                    session.authorUrl = authorUrl.isEmpty() ? null : (isValidUrl(authorUrl) ? authorUrl : null);
                    session.authorIcon = authorIcon.isEmpty() ? null : (isValidUrl(authorIcon) ? authorIcon : null);
                }
                case "footer" -> {
                    String footer = getModalValue(event, "footer");
                    String footerIcon = getModalValue(event, "footericon");
                    session.footer = footer.isEmpty() ? null : footer;
                    session.footerIcon = footerIcon.isEmpty() ? null : (isValidUrl(footerIcon) ? footerIcon : null);
                }
                case "thumbnail" -> {
                    String thumb = getModalValue(event, "thumbnail");
                    if (thumb.isEmpty()) {
                        session.thumbnail = null;
                    } else if (!isValidUrl(thumb)) {
                        event.reply("❌ Invalid URL for thumbnail!").setEphemeral(true).queue();
                        return;
                    } else {
                        session.thumbnail = thumb;
                    }
                }
                case "image" -> {
                    String image = getModalValue(event, "image");
                    if (image.isEmpty()) {
                        session.image = null;
                    } else if (!isValidUrl(image)) {
                        event.reply("❌ Invalid URL for image!").setEphemeral(true).queue();
                        return;
                    } else {
                        session.image = image;
                    }
                }
                case "url" -> {
                    String url = getModalValue(event, "url");
                    if (url.isEmpty()) {
                        session.url = null;
                    } else if (!isValidUrl(url)) {
                        event.reply("❌ Invalid URL!").setEphemeral(true).queue();
                        return;
                    } else {
                        session.url = url;
                    }
                }
                case "timestamp" -> {
                    String timestamp = getModalValue(event, "timestamp");
                    if (timestamp.isEmpty()) {
                        session.timestamp = null;
                    } else {
                        session.timestamp = parseTimestamp(timestamp);
                        if (session.timestamp == null) {
                            event.reply("❌ Invalid timestamp format! Use: DD-MM-YYYY HH:mm:ss+OFFSET").setEphemeral(true).queue();
                            return;
                        }
                    }
                }
                case "field" -> {
                    String name = getModalValue(event, "fieldname");
                    String value = getModalValue(event, "fieldvalue");
                    String inlineStr = getModalValue(event, "fieldinline").toLowerCase();
                    boolean inline = inlineStr.equals("yes") || inlineStr.equals("true") || inlineStr.equals("y");

                    if (session.fields.size() >= 25) {
                        event.reply("❌ Maximum of 25 fields allowed!").setEphemeral(true).queue();
                        return;
                    }

                    session.fields.add(new EmbedField(name, value, inline));
                }
                case "customcolor" -> {
                    String hex = getModalValue(event, "colorhex");
                    Color color = parseColor(hex);
                    if (color == null) {
                        event.reply("❌ Invalid hex color! Use format like: 26cfd5").setEphemeral(true).queue();
                        return;
                    }
                    session.color = color;
                }
            }

            // Update preview
            updateSessionPreviewFromModal(event, session);

        } catch (Exception e) {
            event.reply("❌ Error: " + e.getMessage()).setEphemeral(true).queue();
            context.log("error", "Error processing modal: " + e.getMessage());
        }
    }

    // ==================== SELECT MENU HANDLERS ====================

    @SelectMenuHandler(SELECT_PREFIX)
    public void handleSelectMenu(StringSelectInteractionEvent event) {
        long userId = event.getUser().getIdLong();
        EmbedSession session = activeSessions.get(userId);

        if (session == null) {
            event.reply("❌ Session expired! Use `/embed create` to start a new one.").setEphemeral(true).queue();
            return;
        }

        String menuId = event.getComponentId().substring(SELECT_PREFIX.length());
        String selected = event.getValues().getFirst();

        if (menuId.equals("color")) {
            switch (selected) {
                case "red" -> session.color = Color.RED;
                case "orange" -> session.color = Color.ORANGE;
                case "yellow" -> session.color = Color.YELLOW;
                case "green" -> session.color = Color.GREEN;
                case "blue" -> session.color = Color.BLUE;
                case "purple" -> session.color = new Color(128, 0, 128);
                case "white" -> session.color = Color.WHITE;
                case "black" -> session.color = Color.BLACK;
                case "none" -> session.color = null;
                case "custom" -> {
                    // Show custom color modal
                    TextInput colorInput = TextInput.create("colorhex", TextInputStyle.SHORT)
                            .setPlaceholder("26cfd5 (without #)")
                            .setMinLength(6)
                            .setMaxLength(6)
                            .setRequired(true)
                            .build();

                    Modal modal = Modal.create(MODAL_PREFIX + "customcolor", "Enter Custom Color")
                            .addComponents(Label.of("Hex Color Code", colorInput))
                            .build();

                    event.replyModal(modal).queue();
                    return;
                }
            }

            // Update preview message
            if (session.previewMessage != null) {
                session.previewMessage.editMessageEmbeds(buildPreviewEmbed(session))
                        .setComponents(getBuilderActionRows())
                        .queue();
            }
            event.reply("✅ Color updated!").setEphemeral(true).queue();
        }
    }

    // ==================== SLASH COMMAND HELPER METHODS ====================

    private void createNewSession(SlashCommandInteractionEvent event, long userId) {
        // Delete old session if exists
        EmbedSession oldSession = activeSessions.get(userId);
        if (oldSession != null && oldSession.previewMessage != null) {
            oldSession.previewMessage.delete().queue(null, e -> {});
        }

        // Create new session
        EmbedSession session = new EmbedSession(userId);
        activeSessions.put(userId, session);

        // Send builder interface with buttons
        event.replyEmbeds(buildPreviewEmbed(session))
                .addComponents(getBuilderActionRows())
                .queue(hook -> hook.retrieveOriginal().queue(msg -> session.previewMessage = msg));
    }

    private void buildEmbed(SlashCommandInteractionEvent event, long userId) {
        EmbedSession session = activeSessions.get(userId);
        if (session == null) {
            event.reply("❌ No active embed session! Use `/embed create` first.").setEphemeral(true).queue();
            return;
        }

        GuildChannelUnion channelOption = event.getOption("channel").getAsChannel();
        if (!(channelOption instanceof TextChannel targetChannel)) {
            event.reply("❌ Please select a text channel!").setEphemeral(true).queue();
            return;
        }

        // Check permissions
        if (!event.getGuild().getSelfMember().hasPermission(targetChannel, Permission.MESSAGE_SEND)) {
            event.reply("❌ I don't have permission to send messages in that channel!").setEphemeral(true).queue();
            return;
        }

        if (!event.getMember().hasPermission(targetChannel, Permission.MESSAGE_SEND)) {
            event.reply("❌ You don't have permission to send messages in that channel!").setEphemeral(true).queue();
            return;
        }

        // Build and post the final embed
        MessageEmbed finalEmbed = buildFinalEmbed(session);

        targetChannel.sendMessageEmbeds(finalEmbed).queue(
                msg -> {
                    // Success - clean up session
                    if (session.previewMessage != null) {
                        session.previewMessage.delete().queue(null, e -> {});
                    }
                    activeSessions.remove(userId);
                    event.reply("✅ Embed posted to " + targetChannel.getAsMention() + "!").setEphemeral(true).queue();
                },
                error -> event.reply("❌ Failed to post embed: " + error.getMessage()).setEphemeral(true).queue()
        );
    }

    private void cancelSession(SlashCommandInteractionEvent event, long userId) {
        EmbedSession session = activeSessions.remove(userId);
        if (session == null) {
            event.reply("❌ No active embed session to cancel!").setEphemeral(true).queue();
            return;
        }

        if (session.previewMessage != null) {
            session.previewMessage.delete().queue(null, e -> {});
        }

        event.reply("✅ Embed session cancelled!").setEphemeral(true).queue();
    }

    private void showPreview(SlashCommandInteractionEvent event, long userId) {
        EmbedSession session = activeSessions.get(userId);
        if (session == null) {
            event.reply("❌ No active embed session! Use `/embed create` first.").setEphemeral(true).queue();
            return;
        }

        MessageEmbed finalEmbed = buildFinalEmbed(session);
        event.replyEmbeds(finalEmbed)
                .setContent("**📝 Preview of your embed:**")
                .setEphemeral(true)
                .queue();
    }


    // ==================== MODAL BUILDERS ====================

    private void showTitleModal(ButtonInteractionEvent event) {
        TextInput titleInput = TextInput.create("title", TextInputStyle.SHORT)
                .setPlaceholder("Enter the title (max 256 characters)")
                .setMaxLength(256)
                .setRequired(false)
                .build();

        Modal modal = Modal.create(MODAL_PREFIX + "title", "Set Embed Title")
                .addComponents(Label.of("Embed Title", titleInput))
                .build();

        event.replyModal(modal).queue();
    }

    private void showDescriptionModal(ButtonInteractionEvent event) {
        TextInput descInput = TextInput.create("description", TextInputStyle.PARAGRAPH)
                .setPlaceholder("Enter the description (max 4096 characters)")
                .setMaxLength(4096)
                .setRequired(false)
                .build();

        Modal modal = Modal.create(MODAL_PREFIX + "description", "Set Embed Description")
                .addComponents(Label.of("Embed Description", descInput))
                .build();

        event.replyModal(modal).queue();
    }

    private void showAuthorModal(ButtonInteractionEvent event) {
        TextInput authorInput = TextInput.create("author", TextInputStyle.SHORT)
                .setPlaceholder("Enter author name (max 256 characters)")
                .setMaxLength(256)
                .setRequired(false)
                .build();

        TextInput authorUrlInput = TextInput.create("authorurl", TextInputStyle.SHORT)
                .setPlaceholder("https://example.com")
                .setRequired(false)
                .build();

        TextInput authorIconInput = TextInput.create("authoricon", TextInputStyle.SHORT)
                .setPlaceholder("https://example.com/icon.png")
                .setRequired(false)
                .build();

        Modal modal = Modal.create(MODAL_PREFIX + "author", "Set Embed Author")
                .addComponents(
                        Label.of("Author Name", authorInput),
                        Label.of("Author URL (optional)", authorUrlInput),
                        Label.of("Author Icon URL (optional)", authorIconInput)
                )
                .build();

        event.replyModal(modal).queue();
    }

    private void showFooterModal(ButtonInteractionEvent event) {
        TextInput footerInput = TextInput.create("footer", TextInputStyle.SHORT)
                .setPlaceholder("Enter footer text (max 2048 characters)")
                .setMaxLength(2048)
                .setRequired(false)
                .build();

        TextInput footerIconInput = TextInput.create("footericon", TextInputStyle.SHORT)
                .setPlaceholder("https://example.com/icon.png")
                .setRequired(false)
                .build();

        Modal modal = Modal.create(MODAL_PREFIX + "footer", "Set Embed Footer")
                .addComponents(
                        Label.of("Footer Text", footerInput),
                        Label.of("Footer Icon URL (optional)", footerIconInput)
                )
                .build();

        event.replyModal(modal).queue();
    }

    private void showThumbnailModal(ButtonInteractionEvent event) {
        TextInput thumbInput = TextInput.create("thumbnail", TextInputStyle.SHORT)
                .setPlaceholder("https://example.com/image.png")
                .setRequired(false)
                .build();

        Modal modal = Modal.create(MODAL_PREFIX + "thumbnail", "Set Thumbnail Image")
                .addComponents(Label.of("Thumbnail URL", thumbInput))
                .build();

        event.replyModal(modal).queue();
    }

    private void showImageModal(ButtonInteractionEvent event) {
        TextInput imageInput = TextInput.create("image", TextInputStyle.SHORT)
                .setPlaceholder("https://example.com/image.png")
                .setRequired(false)
                .build();

        Modal modal = Modal.create(MODAL_PREFIX + "image", "Set Embed Image")
                .addComponents(Label.of("Image URL", imageInput))
                .build();

        event.replyModal(modal).queue();
    }

    private void showUrlModal(ButtonInteractionEvent event) {
        TextInput urlInput = TextInput.create("url", TextInputStyle.SHORT)
                .setPlaceholder("https://example.com")
                .setRequired(false)
                .build();

        Modal modal = Modal.create(MODAL_PREFIX + "url", "Set Title URL")
                .addComponents(Label.of("Title URL", urlInput))
                .build();

        event.replyModal(modal).queue();
    }

    private void showTimestampModal(ButtonInteractionEvent event) {
        TextInput timestampInput = TextInput.create("timestamp", TextInputStyle.SHORT)
                .setPlaceholder("DD-MM-YYYY HH:mm:ss+OFFSET (e.g., 25-01-2025 14:30:00+7)")
                .setRequired(false)
                .build();

        Modal modal = Modal.create(MODAL_PREFIX + "timestamp", "Set Timestamp")
                .addComponents(Label.of("Timestamp", timestampInput))
                .build();

        event.replyModal(modal).queue();
    }

    private void showFieldModal(ButtonInteractionEvent event) {
        TextInput nameInput = TextInput.create("fieldname", TextInputStyle.SHORT)
                .setPlaceholder("Field title (max 256 characters)")
                .setMaxLength(256)
                .setRequired(true)
                .build();

        TextInput valueInput = TextInput.create("fieldvalue", TextInputStyle.PARAGRAPH)
                .setPlaceholder("Field content (max 1024 characters)")
                .setMaxLength(1024)
                .setRequired(true)
                .build();

        TextInput inlineInput = TextInput.create("fieldinline", TextInputStyle.SHORT)
                .setPlaceholder("yes or no")
                .setValue("no")
                .setRequired(true)
                .build();

        Modal modal = Modal.create(MODAL_PREFIX + "field", "Add Embed Field")
                .addComponents(
                        Label.of("Field Name", nameInput),
                        Label.of("Field Value", valueInput),
                        Label.of("Inline? (yes/no)", inlineInput)
                )
                .build();

        event.replyModal(modal).queue();
    }

    private void showColorSelectMenu(ButtonInteractionEvent event) {
        StringSelectMenu colorMenu = StringSelectMenu.create(SELECT_PREFIX + "color")
                .setPlaceholder("Choose a color or select 'Custom'")
                .addOption("🔴 Red", "red")
                .addOption("🟠 Orange", "orange")
                .addOption("🟡 Yellow", "yellow")
                .addOption("🟢 Green", "green")
                .addOption("🔵 Blue", "blue")
                .addOption("🟣 Purple", "purple")
                .addOption("⚪ White", "white")
                .addOption("⚫ Black", "black")
                .addOption("🎨 Custom (Hex)", "custom")
                .addOption("❌ Remove Color", "none")
                .build();

        event.reply("**Select a color for your embed:**")
                .addComponents(ActionRow.of(colorMenu))
                .setEphemeral(true)
                .queue();
    }


    // ==================== HELPER METHODS ====================

    private String getModalValue(ModalInteractionEvent event, String id) {
        var mapping = event.getValue(id);
        return mapping != null ? mapping.getAsString() : "";
    }

    private void updateSessionPreview(ButtonInteractionEvent event, EmbedSession session) {
        event.editMessageEmbeds(buildPreviewEmbed(session))
                .setComponents(getBuilderActionRows())
                .queue();
    }

    private void updateSessionPreviewFromModal(ModalInteractionEvent event, EmbedSession session) {
        if (session.previewMessage != null) {
            session.previewMessage.editMessageEmbeds(buildPreviewEmbed(session))
                    .setComponents(getBuilderActionRows())
                    .queue();
        }
        event.reply("✅ Updated!").setEphemeral(true).queue();
    }

    private List<ActionRow> getBuilderActionRows() {
        return List.of(
                ActionRow.of(
                        Button.primary(BUTTON_PREFIX + "title", "📝 Title"),
                        Button.primary(BUTTON_PREFIX + "description", "📄 Description"),
                        Button.primary(BUTTON_PREFIX + "color", "🎨 Color"),
                        Button.primary(BUTTON_PREFIX + "author", "👤 Author"),
                        Button.primary(BUTTON_PREFIX + "footer", "📌 Footer")
                ),
                ActionRow.of(
                        Button.secondary(BUTTON_PREFIX + "thumbnail", "🖼️ Thumbnail"),
                        Button.secondary(BUTTON_PREFIX + "image", "🌄 Image"),
                        Button.secondary(BUTTON_PREFIX + "url", "🔗 URL"),
                        Button.secondary(BUTTON_PREFIX + "timestamp", "⏰ Timestamp")
                ),
                ActionRow.of(
                        Button.success(BUTTON_PREFIX + "field", "➕ Add Field"),
                        Button.danger(BUTTON_PREFIX + "clearfields", "🗑️ Clear Fields"),
                        Button.danger(BUTTON_PREFIX + "reset", "🔄 Reset All")
                )
        );
    }

    private MessageEmbed buildPreviewEmbed(EmbedSession session) {
        EmbedBuilder builder = new EmbedBuilder();
        builder.setTitle("🔨 Embed Builder - Interactive Editor");
        builder.setDescription("""
                Use the buttons below to edit your embed.
                Use `/embed preview` to see the final result.
                Use `/embed build #channel` to post it.
                
                **Current Settings:**""");
        builder.setColor(Color.BLUE);

        String settings = "**Title:** " + (session.title != null ? "✅ `" + truncate(session.title, 30) + "`" : "❌ Not set") + "\n" +
                "**Description:** " + (session.description != null ? "✅ Set (" + session.description.length() + " chars)" : "❌ Not set") + "\n" +
                "**Color:** " + (session.color != null ? "✅ #" + toHex(session.color) : "❌ Not set") + "\n" +
                "**Author:** " + (session.author != null ? "✅ `" + truncate(session.author, 30) + "`" : "❌ Not set") + "\n" +
                "**Footer:** " + (session.footer != null ? "✅ `" + truncate(session.footer, 30) + "`" : "❌ Not set") + "\n" +
                "**Thumbnail:** " + (session.thumbnail != null ? "✅ Set" : "❌ Not set") + "\n" +
                "**Image:** " + (session.image != null ? "✅ Set" : "❌ Not set") + "\n" +
                "**URL:** " + (session.url != null ? "✅ Set" : "❌ Not set") + "\n" +
                "**Timestamp:** " + (session.timestamp != null ? "✅ Set" : "❌ Not set") + "\n" +
                "**Fields:** " + (!session.fields.isEmpty() ? "✅ " + session.fields.size() + " field(s)" : "❌ None") + "\n";

        builder.addField("Configuration", settings, false);

        if (!session.fields.isEmpty()) {
            StringBuilder fieldsInfo = new StringBuilder();
            int i = 1;
            for (EmbedField field : session.fields) {
                fieldsInfo.append(i++).append(". ").append(truncate(field.name, 20))
                        .append(field.inline ? " (inline)" : "").append("\n");
                if (i > 10) {
                    fieldsInfo.append("... and ").append(session.fields.size() - 10).append(" more\n");
                    break;
                }
            }
            builder.addField("Fields Preview", fieldsInfo.toString(), false);
        }

        builder.setFooter("Session for user: " + session.userId);
        builder.setTimestamp(OffsetDateTime.now());

        return builder.build();
    }

    private MessageEmbed buildFinalEmbed(EmbedSession session) {
        EmbedBuilder builder = new EmbedBuilder();

        if (session.title != null) {
            builder.setTitle(session.title, session.url);
        }
        if (session.description != null) {
            builder.setDescription(session.description);
        }
        if (session.color != null) {
            builder.setColor(session.color);
        }
        if (session.author != null) {
            builder.setAuthor(session.author, session.authorUrl, session.authorIcon);
        }
        if (session.footer != null) {
            builder.setFooter(session.footer, session.footerIcon);
        }
        if (session.thumbnail != null) {
            builder.setThumbnail(session.thumbnail);
        }
        if (session.image != null) {
            builder.setImage(session.image);
        }
        if (session.timestamp != null) {
            builder.setTimestamp(session.timestamp);
        }

        for (EmbedField field : session.fields) {
            builder.addField(field.name, field.value, field.inline);
        }

        return builder.build();
    }

    private String truncate(String str, int maxLength) {
        if (str.length() <= maxLength) return str;
        return str.substring(0, maxLength - 3) + "...";
    }

    private boolean isValidUrl(String url) {
        try {
            new URI(url);
            return url.startsWith("http://") || url.startsWith("https://");
        } catch (Exception e) {
            return false;
        }
    }

    private Color parseColor(String hex) {
        try {
            hex = hex.replace("#", "");
            if (hex.length() != 6) return null;
            return Color.decode("#" + hex);
        } catch (Exception e) {
            return null;
        }
    }

    private String toHex(Color color) {
        return String.format("%02x%02x%02x", color.getRed(), color.getGreen(), color.getBlue());
    }

    private OffsetDateTime parseTimestamp(String timestamp) {
        if (timestamp == null || timestamp.isBlank()) {
            return null;
        }

        try {
            String regex = "^(?:(\\d+)[-!@#$%^&*/.]+(\\d+)[-!@#$%^&*/.]+(\\d+)\\s+)?(\\d{2}):(\\d{2}):(\\d{2})([+-]\\d+)$";

            Pattern pattern = Pattern.compile(regex);
            Matcher matcher = pattern.matcher(timestamp.trim());

            if (!matcher.matches()) {
                return null;
            }

            int year, month, day;

            if (matcher.group(1) == null) {
                LocalDate today = LocalDate.now();
                year = today.getYear();
                month = today.getMonthValue();
                day = today.getDayOfMonth();
            } else {
                int p1 = Integer.parseInt(matcher.group(1));
                int p2 = Integer.parseInt(matcher.group(2));
                int p3 = Integer.parseInt(matcher.group(3));

                if (p1 > 31) {
                    year = p1;
                    month = p2;
                    day = p3;
                } else {
                    day = p1;
                    month = p2;
                    year = p3;
                }
            }

            int hour = Integer.parseInt(matcher.group(4));
            int minute = Integer.parseInt(matcher.group(5));
            int second = Integer.parseInt(matcher.group(6));
            int offsetHours = Integer.parseInt(matcher.group(7));

            return OffsetDateTime.of(year, month, day, hour, minute, second, 0,
                    ZoneOffset.ofHours(offsetHours));

        } catch (Exception e) {
            return null;
        }
    }

    // ==================== SESSION CLASSES ====================

    private static class EmbedSession {
        final long userId;
        Message previewMessage;
        String title;
        String description;
        Color color;
        String thumbnail;
        String image;
        String author;
        String authorUrl;
        String authorIcon;
        String footer;
        String footerIcon;
        String url;
        OffsetDateTime timestamp;
        List<EmbedField> fields = new ArrayList<>();

        EmbedSession(long userId) {
            this.userId = userId;
        }

        void reset() {
            title = null;
            description = null;
            color = null;
            thumbnail = null;
            image = null;
            author = null;
            authorUrl = null;
            authorIcon = null;
            footer = null;
            footerIcon = null;
            url = null;
            timestamp = null;
            fields.clear();
        }
    }

    private record EmbedField(String name, String value, boolean inline) {
    }
}

