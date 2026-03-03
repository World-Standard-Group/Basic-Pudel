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
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.components.container.Container;
import net.dv8tion.jda.api.components.container.ContainerChildComponent;
import net.dv8tion.jda.api.components.label.Label;
import net.dv8tion.jda.api.components.mediagallery.MediaGallery;
import net.dv8tion.jda.api.components.mediagallery.MediaGalleryItem;
import net.dv8tion.jda.api.components.section.Section;
import net.dv8tion.jda.api.components.selections.EntitySelectMenu;
import net.dv8tion.jda.api.components.selections.StringSelectMenu;
import net.dv8tion.jda.api.components.separator.Separator;
import net.dv8tion.jda.api.components.textdisplay.TextDisplay;
import net.dv8tion.jda.api.components.textinput.TextInput;
import net.dv8tion.jda.api.components.textinput.TextInputStyle;
import net.dv8tion.jda.api.components.thumbnail.Thumbnail;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.channel.ChannelType;
import net.dv8tion.jda.api.entities.channel.middleman.GuildMessageChannel;
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.StringSelectInteractionEvent;
import net.dv8tion.jda.api.modals.Modal;
import net.dv8tion.jda.api.utils.messages.MessageCreateBuilder;
import net.dv8tion.jda.api.utils.messages.MessageEditBuilder;
import net.dv8tion.jda.api.utils.messages.MessageEditData;

import java.awt.Color;
import java.net.URI;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Interactive Embed Builder Plugin for Pudel Discord Bot
 * <p>
 * Built entirely with Discord's Components v2 system.
 * Uses {@link Container}, {@link TextDisplay}, {@link Section},
 * {@link Separator}, {@link MediaGallery}, and {@link Thumbnail}
 * for a rich, modern builder interface with live preview.
 * <p>
 * Features:
 * - Single slash command entry point
 * - Live visual preview rendered as Components v2
 * - Button-based editing and posting
 * - Channel selection via UI
 * - Final embed posted as classic MessageEmbed
 *
 * @author Zazalng
 * @since 3.0.0
 */
@Plugin(
        name = "Pudel's Embed Builder",
        version = "3.0.1",
        author = "Zazalng",
        description = "Interactive embed builder with Components v2 live preview"
)
public class PudelMessagePlugin {

    // ==================== CONSTANTS ====================
    private static final String BUTTON_PREFIX = "embed:";
    private static final String MODAL_PREFIX = "embed:modal:";
    private static final String MENU_PREFIX = "embed:menu:";

    private static final Color DEFAULT_PREVIEW_COLOR = new Color(0x2B2D31);

    // ==================== STATE MANAGEMENT ====================
    private PluginContext context;
    private final Map<Long, EmbedSession> activeSessions = new ConcurrentHashMap<>();

    // ==================== LIFECYCLE HOOKS ====================

    @OnEnable
    public void onEnable(PluginContext ctx) {
        this.context = ctx;
        ctx.log("info", "PudelMessagePlugin initialized (v3.0.0 — Components v2)");
    }

    @OnShutdown
    public boolean onShutdown() {
        for (EmbedSession session : activeSessions.values()) {
            if (session.previewMessage != null) {
                session.previewMessage.delete().queue(null, _ -> {});
            }
        }
        activeSessions.clear();
        return true;
    }

    // ==================== SLASH COMMAND ====================

    @SlashCommand(
            name = "embed",
            description = "Open the interactive embed builder"
    )
    public void handleEmbedCommand(SlashCommandInteractionEvent event) {
        if (!event.isFromGuild()) {
            event.reply("❌ This command can only be used in a server!").setEphemeral(true).queue(m -> m.deleteOriginal().queueAfter(5, TimeUnit.SECONDS));
            return;
        }

        long userId = event.getUser().getIdLong();

        // Delete old session if exists
        EmbedSession oldSession = activeSessions.get(userId);
        if (oldSession != null && oldSession.previewMessage != null) {
            try{
                oldSession.previewMessage.delete().queue(null, _ -> {});
            } catch (IllegalStateException _) {
                context.log("warn", "message_id '".concat(oldSession.previewMessage.getId()).concat("' may long gone. Skip delete."));
            }
        }

        // Create new session
        EmbedSession session = new EmbedSession(userId);
        activeSessions.put(userId, session);

        // Send builder interface using Components v2
        event.reply(buildV2Message(session).build())
                .setEphemeral(true)
                .queue(hook -> hook.retrieveOriginal().queue(msg -> session.previewMessage = msg));
    }

    // ==================== COMPONENT HANDLERS ====================

    @ButtonHandler(BUTTON_PREFIX)
    public void handleButton(ButtonInteractionEvent event) {
        long userId = event.getUser().getIdLong();
        EmbedSession session = activeSessions.get(userId);

        if (session == null) {
            event.reply("❌ Session expired! Use `/embed` to start again.").setEphemeral(true).queue(m -> m.deleteOriginal().queueAfter(5, TimeUnit.SECONDS));
            return;
        }

        String buttonId = event.getComponentId().substring(BUTTON_PREFIX.length());

        switch (buttonId) {
            // Content
            case "title" -> showTitleModal(event);
            case "description" -> showDescriptionModal(event);
            case "color" -> showColorSelectMenu(event);
            case "author" -> showAuthorModal(event);
            case "footer" -> showFooterModal(event);
            case "thumbnail" -> showThumbnailModal(event);
            case "image" -> showImageModal(event);
            case "url" -> showUrlModal(event);
            case "timestamp" -> showTimestampModal(event);

            // Fields
            case "field" -> showFieldModal(event);
            case "clearfields" -> {
                session.fields.clear();
                updateSessionPreview(event, session);
            }

            // Actions
            case "post" -> showChannelSelect(event);
            case "cancel" -> {
                if (session.previewMessage != null) session.previewMessage.delete().queue();
                activeSessions.remove(userId);
                event.reply("❌ Session cancelled.").setEphemeral(true).queue(m -> m.deleteOriginal().queueAfter(5, TimeUnit.SECONDS));
            }
        }
    }


    @ModalHandler(MODAL_PREFIX)
    public void handleModal(ModalInteractionEvent event) {
        long userId = event.getUser().getIdLong();
        EmbedSession session = activeSessions.get(userId);

        if (session == null) {
            event.reply("❌ Session expired!").setEphemeral(true).queue(m -> m.deleteOriginal().queueAfter(5, TimeUnit.SECONDS));
            return;
        }

        String modalId = event.getModalId().substring(MODAL_PREFIX.length());

        try {
            switch (modalId) {
                case "title" -> {
                    String val = getModalValue(event, "title");
                    session.title = val.isEmpty() ? null : val;
                }
                case "description" -> {
                    String val = getModalValue(event, "description");
                    session.description = val.isEmpty() ? null : val;
                }
                case "author" -> {
                    String auth = getModalValue(event, "author");
                    String url = getModalValue(event, "authorurl");
                    String icon = getModalValue(event, "authoricon");
                    session.author = auth.isEmpty() ? null : auth;
                    session.authorUrl = (isValidUrl(url)) ? url : null;
                    session.authorIcon = (isValidUrl(icon)) ? icon : null;
                }
                case "footer" -> {
                    String foot = getModalValue(event, "footer");
                    String icon = getModalValue(event, "footericon");
                    session.footer = foot.isEmpty() ? null : foot;
                    session.footerIcon = (isValidUrl(icon)) ? icon : null;
                }
                case "thumbnail" -> session.thumbnail = validateUrl(event, "thumbnail");
                case "image" -> session.image = validateUrl(event, "image");
                case "url" -> session.url = validateUrl(event, "url");
                case "timestamp" -> {
                    String ts = getModalValue(event, "timestamp");
                    if (!ts.isEmpty()) {
                        OffsetDateTime odt = parseTimestamp(ts);
                        if (odt != null) session.timestamp = odt;
                        else {
                            event.reply("❌ Invalid format! Use: DD-MM-YYYY HH:mm:ss+OFFSET").setEphemeral(true).queue(m -> m.deleteOriginal().queueAfter(5, TimeUnit.SECONDS));
                            return;
                        }
                    } else session.timestamp = null;
                }
                case "field" -> {
                    if (session.fields.size() >= 25) {
                        event.reply("❌ Max 25 fields!").setEphemeral(true).queue(m -> m.deleteOriginal().queueAfter(5, TimeUnit.SECONDS));
                        return;
                    }
                    String name = getModalValue(event, "fieldname");
                    String value = getModalValue(event, "fieldvalue");
                    // StringSelectMenu in modal returns STRING_SELECT type — use getAsStringList()
                    var inlineMapping = event.getValue("fieldinline");
                    boolean inline = inlineMapping != null
                            && !inlineMapping.getAsStringList().isEmpty()
                            && inlineMapping.getAsStringList().getFirst().equals("yes");
                    session.fields.add(new EmbedField(name, value, inline));
                }
                case "customcolor" -> {
                    Color c = parseColor(getModalValue(event, "colorhex"));
                    if (c != null) session.color = c;
                    else {
                        event.reply("❌ Invalid hex!").setEphemeral(true).queue(m -> m.deleteOriginal().queueAfter(5, TimeUnit.SECONDS));
                        return;
                    }
                }
                case "post" -> {
                    var channelMapping = event.getValue("postchannel");
                    if (channelMapping == null || channelMapping.getAsStringList().isEmpty()) {
                        event.reply("❌ Please select a channel!").setEphemeral(true).queue(m -> m.deleteOriginal().queueAfter(5, TimeUnit.SECONDS));
                        return;
                    }
                    String channelId = channelMapping.getAsStringList().getFirst();
                    GuildMessageChannel targetChannel = event.getGuild().getChannelById(GuildMessageChannel.class, channelId);

                    if (targetChannel == null) {
                        event.reply("❌ Channel not found or invalid.").setEphemeral(true).queue(m -> m.deleteOriginal().queueAfter(5, TimeUnit.SECONDS));
                        return;
                    }
                    if (!targetChannel.canTalk()) {
                        event.reply("❌ I cannot send messages to " + targetChannel.getAsMention()).setEphemeral(true).queue(m -> m.deleteOriginal().queueAfter(5, TimeUnit.SECONDS));
                        return;
                    }

                    MessageEmbed finalEmbed = buildFinalEmbed(session);
                    targetChannel.sendMessageEmbeds(finalEmbed).queue(
                            _ -> {
                                if (session.previewMessage != null) session.previewMessage.delete().queue();
                                activeSessions.remove(userId);
                                event.reply("✅ Embed posted in " + targetChannel.getAsMention()).setEphemeral(true).queue(m -> m.deleteOriginal().queueAfter(5, TimeUnit.SECONDS));
                            },
                            error -> event.reply("❌ Failed to post: " + error.getMessage()).setEphemeral(true).queue(m -> m.deleteOriginal().queueAfter(5, TimeUnit.SECONDS))
                    );
                    return; // Don't call updateSessionPreviewFromModal — session is done
                }
            }
            updateSessionPreviewFromModal(event, session);
        } catch (Exception e) {
            event.reply("❌ Error: " + e.getMessage()).setEphemeral(true).queue(m -> m.deleteOriginal().queueAfter(5, TimeUnit.SECONDS));
        }
    }

    @SelectMenuHandler(MENU_PREFIX)
    public void handleSelectMenu(StringSelectInteractionEvent event) {
        long userId = event.getUser().getIdLong();
        EmbedSession session = activeSessions.get(userId);
        if (session == null) return;


        String selected = event.getValues().getFirst();
        if (selected.equals("custom")) {
            TextInput colorInput = TextInput.create("colorhex", TextInputStyle.SHORT)
                    .setPlaceholder("Hex Color (e.g., FF0000)")
                    .setMinLength(6).setMaxLength(6).setRequired(true).build();
            event.replyModal(Modal.create(MODAL_PREFIX + "customcolor", "Custom Color")
                    .addComponents(Label.of("Color Input", colorInput)).build()).queue();
            return;
        }

        // Predefined colors
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
        }

        event.deferEdit().queue();
        if (session.previewMessage != null) {
            session.previewMessage.editMessage(editV2Message(session).build()).queue();
        }
    }

    // ==================== V2 MESSAGE BUILDERS ====================

    /**
     * Builds the full Components v2 create message with builder controls + live preview.
     */
    private MessageCreateBuilder buildV2Message(EmbedSession session) {
        return new MessageCreateBuilder()
                .useComponentsV2(true)
                .setComponents(getBuilderContainer(), buildPreviewContainer(session));
    }

    /**
     * Builds the Components v2 edit message with builder controls + live preview.
     */
    private MessageEditBuilder editV2Message(EmbedSession session) {
        return new MessageEditBuilder()
                .useComponentsV2(true)
                .setComponents(getBuilderContainer(), buildPreviewContainer(session));
    }

    /**
     * Builds the Components v2 edit message (as MessageEditData) for direct use with editMessage().
     */
    private MessageEditData editV2Data(EmbedSession session) {
        return editV2Message(session).build();
    }

    /**
     * Builder controls container — header text, separator, and button action rows.
     */
    private Container getBuilderContainer() {
        return Container.of(
                TextDisplay.of("# 🛠️ Embed Builder\nUse the buttons below to edit. The preview below updates automatically."),
                Separator.create(true, Separator.Spacing.SMALL),
                ActionRow.of(
                        Button.primary(BUTTON_PREFIX + "title", "📝 Title"),
                        Button.primary(BUTTON_PREFIX + "description", "📄 Desc"),
                        Button.primary(BUTTON_PREFIX + "color", "🎨 Color"),
                        Button.primary(BUTTON_PREFIX + "author", "👤 Author"),
                        Button.primary(BUTTON_PREFIX + "footer", "📌 Footer")
                ),
                ActionRow.of(
                        Button.primary(BUTTON_PREFIX + "thumbnail", "🖼️ Thumb"),
                        Button.primary(BUTTON_PREFIX + "image", "🌄 Image"),
                        Button.primary(BUTTON_PREFIX + "url", "🔗 URL"),
                        Button.primary(BUTTON_PREFIX + "timestamp", "⏰ Time")
                ),
                ActionRow.of(
                        Button.success(BUTTON_PREFIX + "field", "➕ Field"),
                        Button.danger(BUTTON_PREFIX + "clearfields", "🗑️ Clear Fields")
                ),
                ActionRow.of(
                        Button.success(BUTTON_PREFIX + "post", "✅ Post Embed"),
                        Button.danger(BUTTON_PREFIX + "cancel", "✖ Cancel")
                )
        );
    }

    /**
     * Live preview container — renders embed content using Components v2.
     * <p>
     * The container's accent color matches the embed color, visually mimicking
     * the colored left-border of a classic embed. Content is rendered using:
     * <ul>
     *   <li>{@link TextDisplay} for author, title, description, fields, footer</li>
     *   <li>{@link Section} + {@link Thumbnail} for author icon and thumbnail image</li>
     *   <li>{@link MediaGallery} for the main image</li>
     *   <li>{@link Separator} for visual dividers between sections</li>
     * </ul>
     * <p>
     * Note: This is a visual approximation. The final posted embed uses classic
     * {@link MessageEmbed} which Discord renders natively.
     */
    private Container buildPreviewContainer(EmbedSession session) {
        boolean hasContent = session.title != null || session.description != null || session.author != null
                || session.footer != null || session.image != null || session.thumbnail != null
                || session.timestamp != null || !session.fields.isEmpty();

        Color accentColor = session.color != null ? session.color : DEFAULT_PREVIEW_COLOR;

        if (!hasContent) {
            return Container.of(
                    TextDisplay.of("### 🆕 New Embed"),
                    TextDisplay.of("_Start adding content using the buttons above._\n_This preview will update automatically._")
            ).withAccentColor(Color.LIGHT_GRAY);
        }

        List<ContainerChildComponent> children = new ArrayList<>();
        boolean thumbnailUsed = false;

        // Author line
        if (session.author != null) {
            String authorText = session.authorUrl != null
                    ? "-# [" + session.author + "](" + session.authorUrl + ")"
                    : "-# " + session.author;
            if (session.authorIcon != null) {
                children.add(Section.of(
                        Thumbnail.fromUrl(session.authorIcon),
                        TextDisplay.of(authorText)
                ));
            } else {
                children.add(TextDisplay.of(authorText));
            }
        }

        // Title (optionally paired with Thumbnail)
        if (session.title != null) {
            String titleText = session.url != null
                    ? "### [" + session.title + "](" + session.url + ")"
                    : "### " + session.title;

            if (session.thumbnail != null) {
                children.add(Section.of(
                        Thumbnail.fromUrl(session.thumbnail),
                        TextDisplay.of(titleText)
                ));
                thumbnailUsed = true;
            } else {
                children.add(TextDisplay.of(titleText));
            }
        } else if (session.thumbnail != null) {
            // No title but thumbnail exists — show with zero-width space
            children.add(Section.of(
                    Thumbnail.fromUrl(session.thumbnail),
                    TextDisplay.of("\u200B")
            ));
            thumbnailUsed = true;
        }

        // Description
        if (session.description != null) {
            children.add(TextDisplay.of(session.description));
        }

        // Fields
        if (!session.fields.isEmpty()) {
            children.add(Separator.create(false, Separator.Spacing.SMALL));

            StringBuilder inlineGroup = new StringBuilder();
            for (EmbedField f : session.fields) {
                if (f.inline) {
                    if (!inlineGroup.isEmpty()) inlineGroup.append("\u2003\u2003\u2003");
                    inlineGroup.append("**").append(f.name).append("**\n").append(f.value);
                } else {
                    if (!inlineGroup.isEmpty()) {
                        children.add(TextDisplay.of(inlineGroup.toString()));
                        inlineGroup.setLength(0);
                    }
                    children.add(TextDisplay.of("**" + f.name + "**\n" + f.value));
                }
            }
            if (!inlineGroup.isEmpty()) {
                children.add(TextDisplay.of(inlineGroup.toString()));
            }
        }

        // Image
        if (session.image != null) {
            children.add(MediaGallery.of(MediaGalleryItem.fromUrl(session.image)));
        }

        // Footer + Timestamp
        if (session.footer != null || session.timestamp != null) {
            children.add(Separator.create(false, Separator.Spacing.SMALL));
            StringBuilder footerText = new StringBuilder("-# ");
            if (session.footer != null) footerText.append(session.footer);
            if (session.footer != null && session.timestamp != null) footerText.append(" • ");
            if (session.timestamp != null) {
                footerText.append(session.timestamp.format(DateTimeFormatter.ofPattern("dd MMM yyyy, HH:mm")));
            }
            children.add(TextDisplay.of(footerText.toString()));
        }

        return Container.of(children).withAccentColor(accentColor);
    }

    // ==================== PREVIEW UPDATE HELPERS ====================

    private void updateSessionPreview(ButtonInteractionEvent event, EmbedSession session) {
        event.editMessage(editV2Message(session).build()).queue();
    }

    private void updateSessionPreviewFromModal(ModalInteractionEvent event, EmbedSession session) {
        if (session.previewMessage != null) {
            session.previewMessage.editMessage(editV2Message(session).build()).queue();
        }
        event.reply("✅ Updated!").setEphemeral(true).queue(m -> m.deleteOriginal().queueAfter(5, TimeUnit.SECONDS));
    }

    // ==================== MODAL BUILDERS ====================

    private void showTitleModal(ButtonInteractionEvent e) {
        e.replyModal(Modal.create(MODAL_PREFIX + "title", "Title")
                .addComponents(
                        Label.of("Embed Title", TextInput.create("title", TextInputStyle.SHORT)
                                .setPlaceholder("Title")
                                .setMaxLength(256)
                                .setRequired(false)
                                .build()
                        )
                ).build()
        ).queue();
    }

    private void showDescriptionModal(ButtonInteractionEvent e) {
        e.replyModal(Modal.create(MODAL_PREFIX + "description", "Description")
                .addComponents(
                        Label.of("Embed Description", TextInput.create("description", TextInputStyle.PARAGRAPH)
                                .setPlaceholder("Description Text")
                                .setMaxLength(4000)
                                .setRequired(false)
                                .build()
                        )
                ).build()
        ).queue();
    }

    private void showAuthorModal(ButtonInteractionEvent e) {
        e.replyModal(Modal.create(MODAL_PREFIX + "author", "Author")
                .addComponents(
                        Label.of("Embed Author Name", TextInput.create("author", TextInputStyle.SHORT)
                                .setPlaceholder("Author Name")
                                .setMaxLength(255)
                                .setRequired(false)
                                .build()
                        ),
                        Label.of("Embed Author URL", TextInput.create("authorurl", TextInputStyle.SHORT)
                                .setPlaceholder("https://example.com")
                                .setRequired(false)
                                .build()
                        ),
                        Label.of("Embed Icon URL", TextInput.create("authoricon", TextInputStyle.SHORT)
                                .setPlaceholder("https://example.com/burh.png")
                                .setRequired(false)
                                .build()
                        )
                ).build()
        ).queue();
    }

    private void showFooterModal(ButtonInteractionEvent e) {
        e.replyModal(Modal.create(MODAL_PREFIX + "footer", "Footer")
                .addComponents(
                        Label.of("Embed Foot Note", TextInput.create("footer", TextInputStyle.SHORT)
                                .setPlaceholder("Foot note")
                                .setMaxLength(2048)
                                .setRequired(false)
                                .build()
                        ),
                        Label.of("Embed Foot Icon", TextInput.create("footericon", TextInputStyle.SHORT)
                                .setPlaceholder("https://example.com/burh.png")
                                .setRequired(false)
                                .build()
                        )
                ).build()
        ).queue();
    }

    private void showThumbnailModal(ButtonInteractionEvent e) {
        e.replyModal(Modal.create(MODAL_PREFIX + "thumbnail", "Thumbnail")
                .addComponents(
                        Label.of("Embed Thumbnail URL", TextInput.create("thumbnail", TextInputStyle.SHORT)
                                .setPlaceholder("https://example.com/burh.png")
                                .setRequired(false)
                                .build()
                        )
                ).build()
        ).queue();
    }

    private void showImageModal(ButtonInteractionEvent e) {
        e.replyModal(Modal.create(MODAL_PREFIX + "image", "Image")
                .addComponents(
                        Label.of("Embed Image URL", TextInput.create("image", TextInputStyle.SHORT)
                                .setPlaceholder("https://example.com/burh.png")
                                .setRequired(false)
                                .build()
                        )
                ).build()
        ).queue();
    }

    private void showUrlModal(ButtonInteractionEvent e) {
        e.replyModal(Modal.create(MODAL_PREFIX + "url", "Title URL")
                .addComponents(
                        Label.of("Embed Title URL", TextInput.create("url", TextInputStyle.SHORT)
                                .setPlaceholder("https://example.com")
                                .setRequired(false)
                                .build()
                        )
                ).build()
        ).queue();
    }

    private void showTimestampModal(ButtonInteractionEvent e) {
        e.replyModal(Modal.create(MODAL_PREFIX + "timestamp", "Timestamp")
                .addComponents(
                        Label.of("Embed Timestamp", TextInput.create("timestamp", TextInputStyle.SHORT)
                                .setPlaceholder(OffsetDateTime.now().format(DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm:ssX")))
                                .setRequired(false)
                                .build()
                        )
                ).build()
        ).queue();
    }

    private void showFieldModal(ButtonInteractionEvent e) {
        StringSelectMenu inlineSelect = StringSelectMenu.create("fieldinline")
                .setPlaceholder("Inline?")
                .addOption("✅ Yes — Inline", "yes")
                .addOption("❌ No — Full Width", "no")
                .build();
        e.replyModal(Modal.create(MODAL_PREFIX + "field", "Add Field")
                .addComponents(
                        Label.of("Field Title", TextInput.create("fieldname", TextInputStyle.SHORT)
                                .setPlaceholder("Header")
                                .setMaxLength(256)
                                .build()
                        ),
                        Label.of("Field Content", TextInput.create("fieldvalue", TextInputStyle.PARAGRAPH)
                                .setPlaceholder("Value")
                                .setMaxLength(1024)
                                .build()
                        ),
                        Label.of("Display Inline?", inlineSelect)
                ).build()
        ).queue();
    }

    private void showColorSelectMenu(ButtonInteractionEvent event) {
        StringSelectMenu menu = StringSelectMenu.create(MENU_PREFIX + "color")
                .addOption("🔴 Red", "red").addOption("🔵 Blue", "blue").addOption("🟢 Green", "green")
                .addOption("🟡 Yellow", "yellow").addOption("🟠 Orange", "orange").addOption("🟣 Purple", "purple")
                .addOption("⚪ White", "white").addOption("⚫ Black", "black").addOption("🎨 Custom", "custom")
                .addOption("❌ Reset", "none")
                .build();
        event.reply(
                new MessageCreateBuilder()
                        .useComponentsV2(true)
                        .setComponents(Container.of(
                                TextDisplay.of("### 🎨 Select Color"),
                                ActionRow.of(menu)
                        ))
                        .build()
        ).setEphemeral(true).queue();
    }

    private void showChannelSelect(ButtonInteractionEvent event) {
        EntitySelectMenu channelMenu = EntitySelectMenu.create("postchannel", EntitySelectMenu.SelectTarget.CHANNEL)
                .setChannelTypes(ChannelType.TEXT, ChannelType.NEWS)
                .setPlaceholder("Select a channel to post this embed")
                .setMaxValues(1)
                .build();
        event.replyModal(Modal.create(MODAL_PREFIX + "post", "Post Embed")
                .addComponents(
                        Label.of("📨 Select Channel", channelMenu)
                ).build()
        ).queue();
    }

    // ==================== FINAL EMBED BUILDER ====================

    /**
     * Builds the final classic {@link MessageEmbed} for posting to the target channel.
     * The target channel receives a real Discord embed, not Components v2.
     */
    private MessageEmbed buildFinalEmbed(EmbedSession session) {
        EmbedBuilder b = new EmbedBuilder();
        if (session.title != null) b.setTitle(session.title, session.url);
        if (session.description != null) b.setDescription(session.description);
        if (session.color != null) b.setColor(session.color);
        if (session.author != null) b.setAuthor(session.author, session.authorUrl, session.authorIcon);
        if (session.footer != null) b.setFooter(session.footer, session.footerIcon);
        if (session.thumbnail != null) b.setThumbnail(session.thumbnail);
        if (session.image != null) b.setImage(session.image);
        if (session.timestamp != null) b.setTimestamp(session.timestamp);
        for (EmbedField f : session.fields) b.addField(f.name, f.value, f.inline);

        if (b.isEmpty()) b.setDescription("_Empty Embed_");
        return b.build();
    }

    // ==================== UTILITY METHODS ====================

    private boolean isValidUrl(String url) {
        try { new URI(url); return url.startsWith("http"); } catch (Exception e) { return false; }
    }

    private String getModalValue(ModalInteractionEvent event, String id) {
        var v = event.getValue(id); return v != null ? v.getAsString() : "";
    }

    private Color parseColor(String hex) {
        try { return Color.decode("#" + hex.replace("#", "")); } catch (Exception e) { return null; }
    }

    private String validateUrl(ModalInteractionEvent event, String fieldId) {
        String url = getModalValue(event, fieldId);
        if (url.isEmpty()) return null;
        if (isValidUrl(url)) return url;
        return null;
    }

    private OffsetDateTime parseTimestamp(String ts) {
        try {
            String regex = "^(?:(\\d+)[-!@#$%^&*/.]+(\\d+)[-!@#$%^&*/.]+(\\d+)\\s+)?(\\d{2}):(\\d{2}):(\\d{2})([+-]\\d+)$";
            Matcher m = Pattern.compile(regex).matcher(ts.trim());
            if (!m.matches()) return null;

            int y, mo, d;
            if(m.group(1) == null) { LocalDate now = LocalDate.now(); y=now.getYear(); mo=now.getMonthValue(); d=now.getDayOfMonth(); }
            else { int p1=Integer.parseInt(m.group(1)); int p2=Integer.parseInt(m.group(2)); int p3=Integer.parseInt(m.group(3));
                if(p1>31) { y=p1; mo=p2; d=p3; } else { d=p1; mo=p2; y=p3; } }

            return OffsetDateTime.of(y, mo, d, Integer.parseInt(m.group(4)), Integer.parseInt(m.group(5)), Integer.parseInt(m.group(6)), 0, ZoneOffset.ofHours(Integer.parseInt(m.group(7))));
        } catch(Exception e) { return null; }
    }

    // ==================== DATA CLASSES ====================

    private static class EmbedSession {
        final long userId;
        Message previewMessage;
        String title, description, thumbnail, image, author, authorUrl, authorIcon, footer, footerIcon, url;
        Color color;
        OffsetDateTime timestamp;
        List<EmbedField> fields = new ArrayList<>();
        EmbedSession(long u) { this.userId = u; }
    }

    private record EmbedField(String name, String value, boolean inline) {}
}