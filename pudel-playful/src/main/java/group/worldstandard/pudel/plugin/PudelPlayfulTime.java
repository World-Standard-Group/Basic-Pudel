/*
 * Basic Pudel - Playful Time Plugin
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
import group.worldstandard.pudel.plugin.entity.PrankCollection;
import group.worldstandard.pudel.plugin.entity.PrankContainer;
import group.worldstandard.pudel.plugin.helper.PrankExportImportHelper;
import group.worldstandard.pudel.plugin.helper.PrankFireHelper;
import group.worldstandard.pudel.plugin.helper.PrankPanelBuilder;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.attachmentupload.AttachmentUpload;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.components.container.Container;
import net.dv8tion.jda.api.components.label.Label;
import net.dv8tion.jda.api.components.separator.Separator;
import net.dv8tion.jda.api.components.textdisplay.TextDisplay;
import net.dv8tion.jda.api.components.textinput.TextInput;
import net.dv8tion.jda.api.components.textinput.TextInputStyle;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.StringSelectInteractionEvent;
import net.dv8tion.jda.api.interactions.IntegrationType;
import net.dv8tion.jda.api.interactions.InteractionContextType;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.modals.Modal;
import net.dv8tion.jda.api.utils.messages.MessageEditBuilder;
import tools.jackson.databind.ObjectMapper;

import java.awt.*;
import java.net.URI;
import java.util.*;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * Playful Prank Plugin for Pudel Discord Bot.
 *
 * <p>Delegates fire logic to {@link PrankFireHelper},
 * panel rendering to {@link PrankPanelBuilder}, and
 * export/import to {@link PrankExportImportHelper}.
 *
 * @author Zazalng
 * @since 1.0.0
 */
@Plugin(
        name = "Pudel's Playful Time",
        version = "1.1.2",
        author = "Zazalng",
        description = "Harmless prank image/gif collections with custom messages"
)
public class PudelPlayfulTime {

    // ==================== CONSTANTS ====================
    private static final String BTN = "prank:";
    private static final String MODAL = "prank:modal:";
    private static final String MENU = "prank:menu:";

    private static final Color ACCENT_MAIN = new Color(0xFF6B6B);
    private static final Color ACCENT_VIEW = new Color(0x4ECDC4);
    private static final Color ACCENT_PRANK = new Color(0xFFE66D);
    private static final Color ACCENT_IO = new Color(0xA78BFA);

    // ==================== STATE ====================
    private PluginContext ctx;
    private PluginRepository<PrankContainer> containerRepo;
    private PluginRepository<PrankCollection> collectionRepo;

    private final Map<String, String> viewingContainer = new ConcurrentHashMap<>();
    private final Map<String, Message> controlMessages = new ConcurrentHashMap<>();
    private final Random random = new Random();

    private PrankPanelBuilder panelBuilder;
    private PrankFireHelper fireHelper;
    private PrankExportImportHelper exportImportHelper;

    // ==================== LIFECYCLE ====================

    @OnEnable
    public void onEnable(PluginContext ctx) {
        this.ctx = ctx;
        PluginDatabaseManager db = ctx.getDatabaseManager();
        initializeDatabase(db);

        this.panelBuilder = new PrankPanelBuilder(containerRepo, collectionRepo, BTN, MENU, ACCENT_MAIN, ACCENT_VIEW);
        this.fireHelper = new PrankFireHelper(containerRepo, collectionRepo, controlMessages, viewingContainer, random, MODAL, ACCENT_PRANK, panelBuilder);
        this.exportImportHelper = new PrankExportImportHelper(ctx, new ObjectMapper(), containerRepo, collectionRepo, controlMessages, MODAL, ACCENT_IO, panelBuilder);

        ctx.log("info", "Pudel's Playful Time has initialized — let the pranks begin!");
    }

    @OnShutdown
    public boolean onShutdown(PluginContext ctx) {
        for (Message msg : controlMessages.values()) {
            try { msg.delete().queue(null, _ -> {}); } catch (Exception _) {}
        }
        controlMessages.clear();
        viewingContainer.clear();
        return true;
    }

    private void initializeDatabase(PluginDatabaseManager db) {
        TableSchema containerSchema = TableSchema.builder("prank_container")
                .column("user_id", ColumnType.STRING, 20, false)
                .column("container_id", ColumnType.STRING, false)
                .column("name", ColumnType.TEXT, false)
                .column("usage", ColumnType.INTEGER, false, "0")
                .index("user_id")
                .uniqueIndex("container_id")
                .build();
        db.createTable(containerSchema);

        TableSchema collectionSchema = TableSchema.builder("prank_collection")
                .column("prank_id", ColumnType.STRING, false)
                .column("container_id", ColumnType.STRING, false)
                .column("url", ColumnType.STRING, 255, false)
                .column("placeholder", ColumnType.TEXT, false)
                .index("container_id")
                .uniqueIndex("prank_id")
                .build();
        db.createTable(collectionSchema);

        this.containerRepo = db.getRepository("prank_container", PrankContainer.class);
        this.collectionRepo = db.getRepository("prank_collection", PrankCollection.class);
    }

    // ==================== SLASH COMMAND ====================

    @SlashCommand(
            name = "prank",
            description = "Open prank control panel or fire a prank",
            nsfw = false,
            options = {
                    @CommandOption(name = "name", description = "Container name to fire a random prank from", type = OptionType.STRING),
                    @CommandOption(name = "target", description = "User to prank", type = OptionType.USER)
            },
            global = true,
            integrationTo = {IntegrationType.USER_INSTALL, IntegrationType.GUILD_INSTALL},
            integrationContext = {InteractionContextType.PRIVATE_CHANNEL, InteractionContextType.GUILD, InteractionContextType.BOT_DM}
    )
    public void handlePrankCommand(SlashCommandInteractionEvent event) {
        var nameOpt = event.getOption("name");
        var targetOpt = event.getOption("target");

        if (nameOpt != null) {
            fireHelper.firePrank(event, nameOpt.getAsString(), targetOpt != null ? targetOpt.getAsUser() : null);
        } else {
            fireHelper.showFireSelectModal(event);
        }
    }

    // ==================== BUTTON HANDLER ====================

    @ButtonHandler(BTN)
    public void handleButton(ButtonInteractionEvent event) {
        String userId = event.getUser().getId();
        String buttonId = event.getComponentId().substring(BTN.length());

        switch (buttonId) {
            // Main panel
            case "add-container" -> showAddContainerModal(event);
            case "view-container" -> event.editMessage(panelBuilder.editContainerSelectPanel(userId, "view").build()).queue();
            case "delete-container" -> event.editMessage(panelBuilder.editContainerSelectPanel(userId, "delete").build()).queue();
            case "export-container" -> exportImportHelper.handleExportAll(event, userId);
            case "import-container" -> exportImportHelper.showImportModal(event);

            // View container
            case "add-prank" -> showAddPrankOptions(event, userId);
            case "add-prank-url" -> showAddPrankUrlModal(event, userId);
            case "add-prank-upload" -> showAddPrankUploadModal(event, userId);
            case "edit-prank" -> {
                String containerId = viewingContainer.get(userId);
                if (containerId != null) event.editMessage(panelBuilder.editPrankSelectPanel(containerId, "edit").build()).queue();
            }
            case "remove-prank" -> {
                String containerId = viewingContainer.get(userId);
                if (containerId != null) event.editMessage(panelBuilder.editPrankSelectPanel(containerId, "remove").build()).queue();
            }
            case "back-main" -> { viewingContainer.remove(userId); event.editMessage(panelBuilder.editMainPanel(userId).build()).queue(); }
            case "back-view" -> {
                String containerId = viewingContainer.get(userId);
                if (containerId != null) event.editMessage(panelBuilder.editViewPanel(userId, containerId).build()).queue();
                else event.editMessage(panelBuilder.editMainPanel(userId).build()).queue();
            }
        }
    }

    // ==================== MODAL HANDLER ====================

    @ModalHandler(MODAL)
    public void handleModal(ModalInteractionEvent event) {
        String userId = event.getUser().getId();
        String modalId = event.getModalId().substring(MODAL.length());

        try {
            switch (modalId) {
                case "fire-select" -> fireHelper.handleFireSelectModal(event, userId);
                case "add-container" -> handleAddContainerModal(event, userId);
                case "add-prank-upload" -> handleAddPrankUploadModal(event, userId);
                case "add-prank-url" -> handleAddPrankUrlModal(event, userId);
                case "import-json" -> exportImportHelper.handleImportModal(event, userId);
                default -> {
                    if (modalId.startsWith("edit-prank:")) {
                        handleEditPrankModal(event, userId, modalId.substring("edit-prank:".length()));
                    }
                }
            }
        } catch (Exception e) {
            ctx.log("error", "Modal error: " + e.getMessage());
            event.reply("❌ An error occurred: " + e.getMessage())
                    .setEphemeral(true).queue(m -> m.deleteOriginal().queueAfter(5, TimeUnit.SECONDS));
        }
    }

    // ==================== SELECT MENU HANDLER ====================

    @SelectMenuHandler(MENU)
    public void handleSelectMenu(StringSelectInteractionEvent event) {
        String userId = event.getUser().getId();
        String menuId = event.getComponentId().substring(MENU.length());
        String selected = event.getValues().getFirst();

        switch (menuId) {
            case "view-container" -> {
                viewingContainer.put(userId, selected);
                event.editMessage(panelBuilder.editViewPanel(userId, selected).build()).queue();
            }
            case "delete-container" -> handleDeleteContainer(event, userId, selected);
            case "edit-prank" -> showEditPrankModal(event, selected);
            case "remove-prank" -> handleRemovePrank(event, userId, selected);
        }
    }

    // ==================== CONTAINER CRUD ====================

    private void showAddContainerModal(ButtonInteractionEvent event) {
        TextInput nameInput = TextInput.create("name", TextInputStyle.SHORT)
                .setPlaceholder("e.g. hit, bonk, slap").setMinLength(1).setMaxLength(32).setRequired(true).build();

        event.replyModal(Modal.create(MODAL + "add-container", "Create New Container")
                .addComponents(Label.of("Container Name (unique)", nameInput)).build()
        ).queue();
    }

    private void handleAddContainerModal(ModalInteractionEvent event, String userId) {
        String name = getModalValue(event, "name").trim().toLowerCase();
        if (name.isEmpty()) {
            event.reply("❌ Container name cannot be empty!").setEphemeral(true).queue(m -> m.deleteOriginal().queueAfter(5, TimeUnit.SECONDS));
            return;
        }

        if (!containerRepo.query().where("user_id", userId).where("name", name).list().isEmpty()) {
            event.reply("❌ You already have a container named **" + name + "**!").setEphemeral(true).queue(m -> m.deleteOriginal().queueAfter(5, TimeUnit.SECONDS));
            return;
        }

        containerRepo.save(new PrankContainer(userId, UUID.randomUUID().toString(), name));

        Message ctrlMsg = controlMessages.get(userId);
        if (ctrlMsg != null) ctrlMsg.editMessage(panelBuilder.editMainPanel(userId).build()).queue();

        event.reply("✅ Container **" + name + "** created!").setEphemeral(true).queue(m -> m.deleteOriginal().queueAfter(5, TimeUnit.SECONDS));
    }

    private void handleDeleteContainer(StringSelectInteractionEvent event, String userId, String containerId) {
        PrankContainer container = findContainerById(containerId);
        if (container == null || !container.getUserId().equals(userId)) {
            event.reply("❌ Container not found or you don't own it.").setEphemeral(true).queue(m -> m.deleteOriginal().queueAfter(5, TimeUnit.SECONDS));
            return;
        }

        for (PrankCollection p : collectionRepo.query().where("container_id", containerId).list()) {
            collectionRepo.deleteById(p.getId());
        }
        containerRepo.deleteById(container.getId());
        viewingContainer.remove(userId);

        event.editMessage(panelBuilder.editMainPanel(userId).build()).queue();
    }

    // ==================== PRANK CRUD ====================

    private void showAddPrankOptions(ButtonInteractionEvent event, String userId) {
        if (viewingContainer.get(userId) == null) {
            event.reply("❌ No container selected!").setEphemeral(true).queue(m -> m.deleteOriginal().queueAfter(5, TimeUnit.SECONDS));
            return;
        }

        event.editMessage(
                new MessageEditBuilder().useComponentsV2(true).setComponents(Container.of(
                        TextDisplay.of("### ➕ Add Prank"),
                        TextDisplay.of("Choose how to provide the image/GIF:"),
                        Separator.create(true, Separator.Spacing.SMALL),
                        ActionRow.of(Button.success(BTN + "add-prank-upload", "📤 Upload File"), Button.primary(BTN + "add-prank-url", "🔗 Paste URL")),
                        Separator.create(true, Separator.Spacing.SMALL),
                        ActionRow.of(Button.secondary(BTN + "back-view", "⬅️ Back"))
                ).withAccentColor(ACCENT_VIEW)).build()
        ).queue();
    }

    private void showAddPrankUploadModal(ButtonInteractionEvent event, String userId) {
        if (viewingContainer.get(userId) == null) {
            event.reply("❌ No container selected!").setEphemeral(true).queue(m -> m.deleteOriginal().queueAfter(5, TimeUnit.SECONDS));
            return;
        }

        event.replyModal(Modal.create(MODAL + "add-prank-upload", "Add Prank — Upload")
                .addComponents(
                        Label.of("Image/GIF File", AttachmentUpload.create("upload").setRequiredRange(1, 1).setRequired(true).build()),
                        Label.of("Message Template (%m = you, %t = target)", TextInput.create("placeholder", TextInputStyle.PARAGRAPH)
                                .setPlaceholder("%m hit %t so hard!\n(%m = you, %t = target)").setRequired(false).setMaxLength(500).build())
                ).build()
        ).queue();
    }

    private void showAddPrankUrlModal(ButtonInteractionEvent event, String userId) {
        if (viewingContainer.get(userId) == null) {
            event.reply("❌ No container selected!").setEphemeral(true).queue(m -> m.deleteOriginal().queueAfter(5, TimeUnit.SECONDS));
            return;
        }

        event.replyModal(Modal.create(MODAL + "add-prank-url", "Add Prank — URL")
                .addComponents(
                        Label.of("Image/GIF URL", TextInput.create("url", TextInputStyle.SHORT).setPlaceholder("https://example.com/funny.gif").setRequired(true).build()),
                        Label.of("Message Template (%m = you, %t = target)", TextInput.create("placeholder", TextInputStyle.PARAGRAPH)
                                .setPlaceholder("%m hit %t so hard!\n(%m = you, %t = target)").setRequired(false).setMaxLength(500).build())
                ).build()
        ).queue();
    }

    private void handleAddPrankUploadModal(ModalInteractionEvent event, String userId) {
        String containerId = viewingContainer.get(userId);
        if (containerId == null) {
            event.reply("❌ No container selected! Go back and select one.").setEphemeral(true).queue(m -> m.deleteOriginal().queueAfter(5, TimeUnit.SECONDS));
            return;
        }

        var uploadMapping = event.getValue("upload");
        if (uploadMapping == null) {
            event.reply("❌ No file was uploaded!").setEphemeral(true).queue(m -> m.deleteOriginal().queueAfter(5, TimeUnit.SECONDS));
            return;
        }

        List<Message.Attachment> attachments = uploadMapping.getAsAttachmentList();
        if (attachments.isEmpty()) {
            event.reply("❌ No file was uploaded!").setEphemeral(true).queue(m -> m.deleteOriginal().queueAfter(5, TimeUnit.SECONDS));
            return;
        }

        Message.Attachment attachment = attachments.getFirst();
        if (!attachment.isImage() && !attachment.isVideo()) {
            event.reply("❌ Unsupported file type! Please upload a `.png`, `.jpg`, `.gif`, or `.webp` file.")
                    .setEphemeral(true).queue(m -> m.deleteOriginal().queueAfter(5, TimeUnit.SECONDS));
            return;
        }

        String placeholder = getModalValue(event, "placeholder").trim();

        event.deferReply(true).queue(hook -> {
            event.getUser().openPrivateChannel().flatMap(
                    ch -> ch.sendMessage("Please keep this message; it contains a permanent URL for your uploaded file.").addFiles(attachment.getProxy().downloadAsFileUpload(attachment.getFileName()))
            ).queue(m -> {
                collectionRepo.save(new PrankCollection(UUID.randomUUID().toString(), containerId, m.getAttachments().getFirst().getUrl(), placeholder));
                Message ctrlMsg = controlMessages.get(userId);
                if (ctrlMsg != null) ctrlMsg.editMessage(panelBuilder.editViewPanel(userId, containerId).build()).queue();
            });
            hook.editOriginal("✅ Prank added! File uploaded successfully.").queue(m -> m.delete().queueAfter(3, TimeUnit.SECONDS));
        });
    }

    private void handleAddPrankUrlModal(ModalInteractionEvent event, String userId) {
        String containerId = viewingContainer.get(userId);
        if (containerId == null) {
            event.reply("❌ No container selected! Go back and select one.").setEphemeral(true).queue(m -> m.deleteOriginal().queueAfter(5, TimeUnit.SECONDS));
            return;
        }

        String url = getModalValue(event, "url").trim();
        String placeholder = getModalValue(event, "placeholder").trim();

        if (!isValidUrl(url)) {
            event.reply("❌ Invalid URL! Please provide a direct link to an image or GIF.")
                    .setEphemeral(true).queue(m -> m.deleteOriginal().queueAfter(5, TimeUnit.SECONDS));
            return;
        }

        collectionRepo.save(new PrankCollection(UUID.randomUUID().toString(), containerId, url, placeholder));

        Message ctrlMsg = controlMessages.get(userId);
        if (ctrlMsg != null) ctrlMsg.editMessage(panelBuilder.editViewPanel(userId, containerId).build()).queue();

        event.reply("✅ Prank added!").setEphemeral(true).queue(m -> m.deleteOriginal().queueAfter(5, TimeUnit.SECONDS));
    }

    private void showEditPrankModal(StringSelectInteractionEvent event, String prankId) {
        PrankCollection prank = findPrankById(prankId);
        if (prank == null) {
            event.reply("❌ Prank not found!").setEphemeral(true).queue(m -> m.deleteOriginal().queueAfter(5, TimeUnit.SECONDS));
            return;
        }

        event.replyModal(Modal.create(MODAL + "edit-prank:" + prankId, "Edit Prank")
                .addComponents(
                        TextDisplay.of(prank.getUrl()),
                        Label.of("Image/GIF URL", TextInput.create("url", TextInputStyle.SHORT).setPlaceholder("Direct image/gif URL").setValue(prank.getUrl()).setRequired(true).build()),
                        Label.of(
                                "Message Template (%m = you, %t = target)",
                                TextInput.create("placeholder", TextInputStyle.PARAGRAPH)
                                        .setPlaceholder("%m hit %t so hard!")
                                        .setValue(prank.getPlaceholder().isEmpty() ? null:prank.getPlaceholder())
                                        .setRequired(false)
                                        .setMaxLength(500)
                                        .build()
                        )
                ).build()
        ).queue();
    }

    private void handleEditPrankModal(ModalInteractionEvent event, String userId, String prankId) {
        PrankCollection prank = findPrankById(prankId);
        if (prank == null) {
            event.reply("❌ Prank not found!").setEphemeral(true).queue(m -> m.deleteOriginal().queueAfter(5, TimeUnit.SECONDS));
            return;
        }

        String url = getModalValue(event, "url").trim();
        String placeholder = getModalValue(event, "placeholder").trim();

        if (!isValidUrl(url)) {
            event.reply("❌ Invalid URL!").setEphemeral(true).queue(m -> m.deleteOriginal().queueAfter(5, TimeUnit.SECONDS));
            return;
        }

        prank.setUrl(url);
        prank.setPlaceholder(placeholder);
        collectionRepo.save(prank);

        String containerId = viewingContainer.get(userId);
        if (containerId != null) {
            Message ctrlMsg = controlMessages.get(userId);
            if (ctrlMsg != null) ctrlMsg.editMessage(panelBuilder.editViewPanel(userId, containerId).build()).queue();
        }

        event.reply("✅ Prank updated!").setEphemeral(true).queue(m -> m.deleteOriginal().queueAfter(5, TimeUnit.SECONDS));
    }

    private void handleRemovePrank(StringSelectInteractionEvent event, String userId, String prankId) {
        PrankCollection prank = findPrankById(prankId);
        if (prank == null) {
            event.reply("❌ Prank not found!").setEphemeral(true).queue(m -> m.deleteOriginal().queueAfter(5, TimeUnit.SECONDS));
            return;
        }

        collectionRepo.deleteById(prank.getId());

        String containerId = viewingContainer.get(userId);
        if (containerId != null) event.editMessage(panelBuilder.editViewPanel(userId, containerId).build()).queue();
        else event.editMessage(panelBuilder.editMainPanel(userId).build()).queue();
    }


    // ==================== UTILITY METHODS ====================

    private PrankContainer findContainerById(String containerId) {
        List<PrankContainer> list = containerRepo.query().where("container_id", containerId).list();
        return list.isEmpty() ? null : list.getFirst();
    }

    private PrankCollection findPrankById(String prankId) {
        List<PrankCollection> list = collectionRepo.query().where("prank_id", prankId).list();
        return list.isEmpty() ? null : list.getFirst();
    }

    private boolean isValidUrl(String url) {
        try { new URI(url); return url.startsWith("http"); } catch (Exception e) { return false; }
    }

    private String getModalValue(ModalInteractionEvent event, String id) {
        var v = event.getValue(id);
        return v != null ? v.getAsString() : "";
    }
}
