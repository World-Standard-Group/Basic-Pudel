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
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.attachmentupload.AttachmentUpload;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.components.container.Container;
import net.dv8tion.jda.api.components.container.ContainerChildComponent;
import net.dv8tion.jda.api.components.label.Label;
import net.dv8tion.jda.api.components.mediagallery.MediaGallery;
import net.dv8tion.jda.api.components.mediagallery.MediaGalleryItem;
import net.dv8tion.jda.api.components.selections.StringSelectMenu;
import net.dv8tion.jda.api.components.separator.Separator;
import net.dv8tion.jda.api.components.textdisplay.TextDisplay;
import net.dv8tion.jda.api.components.textinput.TextInput;
import net.dv8tion.jda.api.components.textinput.TextInputStyle;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.StringSelectInteractionEvent;
import net.dv8tion.jda.api.modals.Modal;
import net.dv8tion.jda.api.utils.messages.MessageCreateBuilder;
import net.dv8tion.jda.api.utils.messages.MessageEditBuilder;

import java.awt.Color;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.*;
import java.util.concurrent.*;

/**
 * Playful Prank Plugin for Pudel Discord Bot
 * <p>
 * Allows users to create prank containers with image/gif collections,
 * each with custom placeholder text. When invoked, a random image/gif
 * is pulled from the container and posted with the formatted message.
 * <p>
 * Commands:
 * <ul>
 *   <li>{@code /prank} — Opens the main control panel (ephemeral)</li>
 *   <li>{@code /prank <name> [target]} — Fires a random prank from the named container</li>
 * </ul>
 *
 * @author Zazalng
 * @since 1.0.0
 */
@Plugin(
        name = "Pudel's Playful Time",
        version = "1.0.0",
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

    private static final String TRANSFER_SH_URL = "https://transfer.sh";

    // ==================== STATE ====================
    private PluginContext ctx;
    private PluginDatabaseManager db;
    private PluginRepository<PrankContainer> containerRepo;
    private PluginRepository<PrankCollection> collectionRepo;

    /** Tracks which container a user is currently viewing in the control panel. */
    private final Map<String, String> viewingContainer = new ConcurrentHashMap<>();
    /** Tracks control panel messages for editing. */
    private final Map<String, Message> controlMessages = new ConcurrentHashMap<>();

    private final HttpClient httpClient = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_2)
            .connectTimeout(java.time.Duration.ofSeconds(15))
            .build();

    private final Random random = new Random();

    // ==================== LIFECYCLE ====================

    @OnEnable
    public void onEnable(PluginContext ctx) {
        this.ctx = ctx;
        this.db = ctx.getDatabaseManager();
        initializeDatabase();
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

    private void initializeDatabase() {
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
            options = {
                    @CommandOption(
                            name = "name",
                            description = "Container name to fire a random prank from",
                            type = "STRING"
                    ),
                    @CommandOption(
                            name = "target",
                            description = "User to prank",
                            type = "USER"
                    )
            },
            global = true
    )
    public void handlePrankCommand(SlashCommandInteractionEvent event) {
        var nameOpt = event.getOption("name");
        var targetOpt = event.getOption("target");

        if (nameOpt != null) {
            // Fire mode: /prank <name> [target]
            firePrank(event, nameOpt.getAsString(), targetOpt != null ? targetOpt.getAsUser() : null);
        } else {
            // Control panel mode: /prank
            openControlPanel(event);
        }
    }

    // ==================== FIRE PRANK ====================

    private void firePrank(SlashCommandInteractionEvent event, String name, User target) {
        // Find container by name (case-insensitive search)
        List<PrankContainer> containers = containerRepo.query()
                .where("user_id", event.getUser().getId())
                .where("name", name)
                .list();

        if (containers.isEmpty()) {
            event.reply("❌ No prank container named **" + name + "** found!")
                    .setEphemeral(true).queue(m -> m.deleteOriginal().queueAfter(5, TimeUnit.SECONDS));
            return;
        }

        PrankContainer container = containers.getFirst();

        // Get all pranks in this container
        List<PrankCollection> pranks = collectionRepo.query()
                .where("container_id", container.getContainerId())
                .list();

        if (pranks.isEmpty()) {
            event.reply("❌ Container **" + name + "** is empty — add some pranks first!")
                    .setEphemeral(true).queue(m -> m.deleteOriginal().queueAfter(5, TimeUnit.SECONDS));
            return;
        }

        // Pick a random prank
        PrankCollection prank = pranks.get(random.nextInt(pranks.size()));

        // Replace placeholders: %m = invoker, %t = target
        String invokerMention = event.getUser().getAsMention();
        String targetMention = target != null ? target.getAsMention() : "";
        String message = prank.getPlaceholder()
                .replace("%m", invokerMention)
                .replace("%t", targetMention);

        // Increment usage counter
        container.setUsage(container.getUsage() + 1);
        containerRepo.save(container);

        // Build the prank message with Components v2
        Container prankCard = Container.of(
                TextDisplay.of(message),
                MediaGallery.of(MediaGalleryItem.fromUrl(prank.getUrl())),
                Separator.create(false, Separator.Spacing.SMALL),
                TextDisplay.of("-# 📦 " + container.getName() + " • Used " + container.getUsage() + " times")
        ).withAccentColor(ACCENT_PRANK);

        event.reply(
                new MessageCreateBuilder()
                        .useComponentsV2(true)
                        .setComponents(prankCard)
                        .build()
        ).queue();
    }

    // ==================== CONTROL PANEL ====================

    private void openControlPanel(SlashCommandInteractionEvent event) {
        String userId = event.getUser().getId();

        // Clean up old session
        Message oldMsg = controlMessages.remove(userId);
        if (oldMsg != null) {
            try { oldMsg.delete().queue(null, _ -> {}); } catch (Exception _) {}
        }
        viewingContainer.remove(userId);

        event.reply(buildMainPanel(userId).build())
                .setEphemeral(true)
                .queue(hook -> hook.retrieveOriginal().queue(msg -> controlMessages.put(userId, msg)));
    }

    private Container buildMainPanelContainer(String userId) {
        List<PrankContainer> myContainers = containerRepo.query()
                .where("user_id", userId)
                .list();

        List<ContainerChildComponent> children = new ArrayList<>();
        children.add(TextDisplay.of("# 🎭 Prank Control Panel"));
        children.add(Separator.create(true, Separator.Spacing.SMALL));

        if (myContainers.isEmpty()) {
            children.add(TextDisplay.of("_You don't have any prank containers yet._\n_Use **Add Container** to create one!_"));
        } else {
            StringBuilder sb = new StringBuilder();
            sb.append("### 📦 Your Containers\n");
            for (PrankContainer c : myContainers) {
                long prankCount = collectionRepo.query()
                        .where("container_id", c.getContainerId())
                        .count();
                sb.append("• **").append(c.getName()).append("** — ")
                        .append(prankCount).append(" pranks, used ")
                        .append(c.getUsage()).append(" times\n");
            }
            children.add(TextDisplay.of(sb.toString()));
        }

        children.add(Separator.create(true, Separator.Spacing.SMALL));
        children.add(ActionRow.of(
                Button.success(BTN + "add-container", "➕ Add Container"),
                Button.primary(BTN + "view-container", "👁️ View Container"),
                Button.danger(BTN + "delete-container", "🗑️ Delete Container")
        ));

        return Container.of(children).withAccentColor(ACCENT_MAIN);
    }

    private MessageCreateBuilder buildMainPanel(String userId) {
        return new MessageCreateBuilder()
                .useComponentsV2(true)
                .setComponents(buildMainPanelContainer(userId));
    }

    private MessageEditBuilder editMainPanel(String userId) {
        return new MessageEditBuilder()
                .useComponentsV2(true)
                .setComponents(buildMainPanelContainer(userId));
    }

    // ==================== VIEW CONTAINER PANEL ====================

    private MessageEditBuilder editViewPanel(String userId, String containerId) {
        PrankContainer container = findContainerById(containerId);
        if (container == null) return editMainPanel(userId);

        List<PrankCollection> pranks = collectionRepo.query()
                .where("container_id", containerId)
                .list();

        List<ContainerChildComponent> children = new ArrayList<>();
        children.add(TextDisplay.of("# 📦 " + container.getName()));
        children.add(Separator.create(true, Separator.Spacing.SMALL));

        if (pranks.isEmpty()) {
            children.add(TextDisplay.of("_This container is empty._\n_Use **Add Prank** to upload an image/gif!_"));
        } else {
            StringBuilder sb = new StringBuilder();
            sb.append("### 🎴 Pranks (").append(pranks.size()).append(")\n");
            int idx = 1;
            for (PrankCollection p : pranks) {
                String shortId = p.getPrankId().substring(0, 8);
                String truncatedPlaceholder = p.getPlaceholder().length() > 40
                        ? p.getPlaceholder().substring(0, 40) + "..."
                        : p.getPlaceholder();
                sb.append(idx++).append(". `").append(shortId).append("` — _")
                        .append(truncatedPlaceholder).append("_\n");
            }
            children.add(TextDisplay.of(sb.toString()));
        }

        children.add(Separator.create(true, Separator.Spacing.SMALL));
        children.add(ActionRow.of(
                Button.success(BTN + "add-prank", "➕ Add Prank"),
                Button.primary(BTN + "edit-prank", "✏️ Edit Prank"),
                Button.danger(BTN + "remove-prank", "🗑️ Remove Prank")
        ));
        children.add(ActionRow.of(
                Button.secondary(BTN + "back-main", "⬅️ Back")
        ));

        return new MessageEditBuilder()
                .useComponentsV2(true)
                .setComponents(Container.of(children).withAccentColor(ACCENT_VIEW));
    }

    // ==================== BUTTON HANDLER ====================

    @ButtonHandler(BTN)
    public void handleButton(ButtonInteractionEvent event) {
        String userId = event.getUser().getId();
        String buttonId = event.getComponentId().substring(BTN.length());

        switch (buttonId) {
            // Main panel buttons
            case "add-container" -> showAddContainerModal(event);
            case "view-container" -> showContainerSelectMenu(event, userId, "view");
            case "delete-container" -> showContainerSelectMenu(event, userId, "delete");

            // View container buttons
            case "add-prank" -> showAddPrankOptions(event, userId);
            case "add-prank-url" -> showAddPrankUrlModal(event, userId);
            case "add-prank-upload" -> showAddPrankUploadModal(event, userId);
            case "edit-prank" -> showPrankSelectMenu(event, userId, "edit");
            case "remove-prank" -> showPrankSelectMenu(event, userId, "remove");
            case "back-main" -> {
                viewingContainer.remove(userId);
                event.editMessage(editMainPanel(userId).build()).queue();
            }
        }
    }

    // ==================== MODAL HANDLER ====================

    @ModalHandler(MODAL)
    public void handleModal(ModalInteractionEvent event) {
        String userId = event.getUser().getId();
        String modalId = event.getModalId().substring(MODAL.length());

        try {
            if (modalId.equals("add-container")) {
                handleAddContainerModal(event, userId);
            } else if (modalId.equals("add-prank-upload")) {
                handleAddPrankUploadModal(event, userId);
            } else if (modalId.equals("add-prank-url")) {
                handleAddPrankUrlModal(event, userId);
            } else if (modalId.startsWith("edit-prank:")) {
                String prankId = modalId.substring("edit-prank:".length());
                handleEditPrankModal(event, userId, prankId);
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
                event.editMessage(editViewPanel(userId, selected).build()).queue();
            }
            case "delete-container" -> handleDeleteContainer(event, userId, selected);
            case "edit-prank" -> showEditPrankModal(event, selected);
            case "remove-prank" -> handleRemovePrank(event, userId, selected);
        }
    }

    // ==================== ADD CONTAINER ====================

    private void showAddContainerModal(ButtonInteractionEvent event) {
        TextInput nameInput = TextInput.create("name", TextInputStyle.SHORT)
                .setPlaceholder("e.g. hit, bonk, slap")
                .setMinLength(1)
                .setMaxLength(32)
                .setRequired(true)
                .build();

        event.replyModal(Modal.create(MODAL + "add-container", "Create New Container")
                .addComponents(Label.of("Container Name (unique)", nameInput))
                .build()
        ).queue();
    }

    private void handleAddContainerModal(ModalInteractionEvent event, String userId) {
        String name = getModalValue(event, "name").trim().toLowerCase();

        if (name.isEmpty()) {
            event.reply("❌ Container name cannot be empty!")
                    .setEphemeral(true).queue(m -> m.deleteOriginal().queueAfter(5, TimeUnit.SECONDS));
            return;
        }

        // Check uniqueness across all users
        List<PrankContainer> existing = containerRepo.query()
                .where("name", name)
                .list();
        if (!existing.isEmpty()) {
            event.reply("❌ A container named **" + name + "** already exists!")
                    .setEphemeral(true).queue(m -> m.deleteOriginal().queueAfter(5, TimeUnit.SECONDS));
            return;
        }

        PrankContainer container = new PrankContainer(
                userId,
                UUID.randomUUID().toString(),
                name
        );
        containerRepo.save(container);

        // Update main panel
        Message ctrlMsg = controlMessages.get(userId);
        if (ctrlMsg != null) {
            ctrlMsg.editMessage(editMainPanel(userId).build()).queue();
        }

        event.reply("✅ Container **" + name + "** created!")
                .setEphemeral(true).queue(m -> m.deleteOriginal().queueAfter(5, TimeUnit.SECONDS));
    }

    // ==================== DELETE CONTAINER ====================

    private void handleDeleteContainer(StringSelectInteractionEvent event, String userId, String containerId) {
        PrankContainer container = findContainerById(containerId);
        if (container == null || !container.getUserId().equals(userId)) {
            event.reply("❌ Container not found or you don't own it.")
                    .setEphemeral(true).queue(m -> m.deleteOriginal().queueAfter(5, TimeUnit.SECONDS));
            return;
        }


        // Delete all pranks in this container
        List<PrankCollection> pranks = collectionRepo.query()
                .where("container_id", containerId)
                .list();
        for (PrankCollection p : pranks) {
            collectionRepo.deleteById(p.getId());
        }

        // Delete the container
        containerRepo.deleteById(container.getId());

        // If user was viewing this container, go back to main
        viewingContainer.remove(userId);

        // Update panel
        event.editMessage(editMainPanel(userId).build()).queue();
    }

    // ==================== VIEW / SELECT CONTAINER ====================

    private void showContainerSelectMenu(ButtonInteractionEvent event, String userId, String action) {
        List<PrankContainer> myContainers = containerRepo.query()
                .where("user_id", userId)
                .list();

        if (myContainers.isEmpty()) {
            event.reply("❌ You don't have any containers yet!")
                    .setEphemeral(true).queue(m -> m.deleteOriginal().queueAfter(5, TimeUnit.SECONDS));
            return;
        }

        StringSelectMenu.Builder menuBuilder = StringSelectMenu.create(MENU + action + "-container")
                .setPlaceholder("Select a container");

        for (PrankContainer c : myContainers) {
            long prankCount = collectionRepo.query()
                    .where("container_id", c.getContainerId())
                    .count();
            menuBuilder.addOption(
                    c.getName() + " (" + prankCount + " pranks)",
                    c.getContainerId()
            );
        }

        event.reply(
                new MessageCreateBuilder()
                        .useComponentsV2(true)
                        .setComponents(Container.of(
                                TextDisplay.of("### 📦 Select Container to " + (action.equals("view") ? "View" : "Delete")),
                                ActionRow.of(menuBuilder.build())
                        ))
                        .build()
        ).setEphemeral(true).queue();
    }

    // ==================== ADD PRANK ====================

    private void showAddPrankOptions(ButtonInteractionEvent event, String userId) {
        String containerId = viewingContainer.get(userId);
        if (containerId == null) {
            event.reply("❌ No container selected!")
                    .setEphemeral(true).queue(m -> m.deleteOriginal().queueAfter(5, TimeUnit.SECONDS));
            return;
        }

        event.reply(
                new MessageCreateBuilder()
                        .useComponentsV2(true)
                        .setComponents(Container.of(
                                TextDisplay.of("### ➕ Add Prank"),
                                TextDisplay.of("Choose how to provide the image/GIF:"),
                                Separator.create(true, Separator.Spacing.SMALL),
                                ActionRow.of(
                                        Button.success(BTN + "add-prank-upload", "📤 Upload File"),
                                        Button.primary(BTN + "add-prank-url", "🔗 Paste URL")
                                )
                        ).withAccentColor(ACCENT_VIEW))
                        .build()
        ).setEphemeral(true).queue();
    }

    /**
     * Opens a modal with {@link AttachmentUpload} for file upload + placeholder text input.
     * The user uploads the file directly inside the modal.
     */
    private void showAddPrankUploadModal(ButtonInteractionEvent event, String userId) {
        String containerId = viewingContainer.get(userId);
        if (containerId == null) {
            event.reply("❌ No container selected!")
                    .setEphemeral(true).queue(m -> m.deleteOriginal().queueAfter(5, TimeUnit.SECONDS));
            return;
        }

        AttachmentUpload fileUpload = AttachmentUpload.create("upload")
                .setRequiredRange(1, 1)
                .setRequired(true)
                .build();

        TextInput placeholderInput = TextInput.create("placeholder", TextInputStyle.PARAGRAPH)
                .setPlaceholder("%m hit %t so hard!\n(%m = you, %t = target)")
                .setRequired(true)
                .setMaxLength(500)
                .build();

        event.replyModal(Modal.create(MODAL + "add-prank-upload", "Add Prank — Upload")
                .addComponents(
                        Label.of("Image/GIF File", fileUpload),
                        Label.of("Message Template (%m = you, %t = target)", placeholderInput)
                )
                .build()
        ).queue();
    }

    /**
     * Opens a modal with a URL text input + placeholder text input.
     * For users who already have a hosted image URL.
     */
    private void showAddPrankUrlModal(ButtonInteractionEvent event, String userId) {
        String containerId = viewingContainer.get(userId);
        if (containerId == null) {
            event.reply("❌ No container selected!")
                    .setEphemeral(true).queue(m -> m.deleteOriginal().queueAfter(5, TimeUnit.SECONDS));
            return;
        }

        TextInput urlInput = TextInput.create("url", TextInputStyle.SHORT)
                .setPlaceholder("https://example.com/funny.gif")
                .setRequired(true)
                .build();

        TextInput placeholderInput = TextInput.create("placeholder", TextInputStyle.PARAGRAPH)
                .setPlaceholder("%m hit %t so hard!\n(%m = you, %t = target)")
                .setRequired(true)
                .setMaxLength(500)
                .build();

        event.replyModal(Modal.create(MODAL + "add-prank-url", "Add Prank — URL")
                .addComponents(
                        Label.of("Image/GIF URL", urlInput),
                        Label.of("Message Template (%m = you, %t = target)", placeholderInput)
                )
                .build()
        ).queue();
    }

    /**
     * Handles the upload modal — extracts the {@link Message.Attachment} from the
     * {@link AttachmentUpload} component, downloads it, uploads to transfer.sh,
     * then saves the prank with the resulting URL.
     */
    private void handleAddPrankUploadModal(ModalInteractionEvent event, String userId) {
        String containerId = viewingContainer.get(userId);
        if (containerId == null) {
            event.reply("❌ No container selected! Go back and select one.")
                    .setEphemeral(true).queue(m -> m.deleteOriginal().queueAfter(5, TimeUnit.SECONDS));
            return;
        }

        // Get the uploaded attachment(s) from the AttachmentUpload component
        var uploadMapping = event.getValue("upload");
        if (uploadMapping == null) {
            event.reply("❌ No file was uploaded!")
                    .setEphemeral(true).queue(m -> m.deleteOriginal().queueAfter(5, TimeUnit.SECONDS));
            return;
        }

        List<Message.Attachment> attachments = uploadMapping.getAsAttachmentList();
        if (attachments.isEmpty()) {
            event.reply("❌ No file was uploaded!")
                    .setEphemeral(true).queue(m -> m.deleteOriginal().queueAfter(5, TimeUnit.SECONDS));
            return;
        }

        Message.Attachment attachment = attachments.getFirst();
        String fileName = attachment.getFileName().toLowerCase();

        // Validate file type
        if (!fileName.endsWith(".png") && !fileName.endsWith(".jpg") && !fileName.endsWith(".jpeg")
                && !fileName.endsWith(".gif") && !fileName.endsWith(".webp")) {
            event.reply("❌ Unsupported file type! Please upload a `.png`, `.jpg`, `.gif`, or `.webp` file.")
                    .setEphemeral(true).queue(m -> m.deleteOriginal().queueAfter(5, TimeUnit.SECONDS));
            return;
        }

        String placeholder = getModalValue(event, "placeholder").trim();
        if (placeholder.isEmpty()) {
            event.reply("❌ Placeholder message cannot be empty!")
                    .setEphemeral(true).queue(m -> m.deleteOriginal().queueAfter(5, TimeUnit.SECONDS));
            return;
        }

        // Defer reply since upload takes time
        event.deferReply(true).queue(hook -> {
            // Download from Discord proxy and upload to transfer.sh
            attachment.getProxy().download().thenCompose(inputStream ->
                    uploadToTransferSh(attachment.getFileName(), inputStream)
            ).thenAccept(url -> {
                // Save prank with the transfer.sh URL
                PrankCollection prank = new PrankCollection(
                        UUID.randomUUID().toString(),
                        containerId,
                        url,
                        placeholder
                );
                collectionRepo.save(prank);

                // Update view panel
                Message ctrlMsg = controlMessages.get(userId);
                if (ctrlMsg != null) {
                    ctrlMsg.editMessage(editViewPanel(userId, containerId).build()).queue();
                }

                hook.editOriginal("✅ Prank added! File uploaded successfully.").queue(m -> m.delete().queueAfter(5, TimeUnit.SECONDS));
            }).exceptionally(err -> {
                ctx.log("error", "Transfer.sh upload failed: " + err.getMessage());
                hook.editOriginal("❌ Upload failed: " + err.getMessage()).queue(m -> m.delete().queueAfter(8, TimeUnit.SECONDS));
                return null;
            });
        });
    }

    /**
     * Handles the URL modal — validates the URL and saves the prank directly.
     */
    private void handleAddPrankUrlModal(ModalInteractionEvent event, String userId) {
        String containerId = viewingContainer.get(userId);
        if (containerId == null) {
            event.reply("❌ No container selected! Go back and select one.")
                    .setEphemeral(true).queue(m -> m.deleteOriginal().queueAfter(5, TimeUnit.SECONDS));
            return;
        }

        String url = getModalValue(event, "url").trim();
        String placeholder = getModalValue(event, "placeholder").trim();

        if (!isValidUrl(url)) {
            event.reply("❌ Invalid URL! Please provide a direct link to an image or GIF.")
                    .setEphemeral(true).queue(m -> m.deleteOriginal().queueAfter(5, TimeUnit.SECONDS));
            return;
        }

        if (placeholder.isEmpty()) {
            event.reply("❌ Placeholder message cannot be empty!")
                    .setEphemeral(true).queue(m -> m.deleteOriginal().queueAfter(5, TimeUnit.SECONDS));
            return;
        }

        savePrankAndRefresh(event, userId, containerId, url, placeholder);
    }


    private void savePrankAndRefresh(ModalInteractionEvent event, String userId, String containerId, String url, String placeholder) {
        PrankCollection prank = new PrankCollection(
                UUID.randomUUID().toString(),
                containerId,
                url,
                placeholder
        );
        collectionRepo.save(prank);

        // Update view panel
        Message ctrlMsg = controlMessages.get(userId);
        if (ctrlMsg != null) {
            ctrlMsg.editMessage(editViewPanel(userId, containerId).build()).queue();
        }

        event.reply("✅ Prank added!")
                .setEphemeral(true).queue(m -> m.deleteOriginal().queueAfter(5, TimeUnit.SECONDS));
    }

    // ==================== EDIT PRANK ====================

    private void showPrankSelectMenu(ButtonInteractionEvent event, String userId, String action) {
        String containerId = viewingContainer.get(userId);
        if (containerId == null) {
            event.reply("❌ No container selected!")
                    .setEphemeral(true).queue(m -> m.deleteOriginal().queueAfter(5, TimeUnit.SECONDS));
            return;
        }

        List<PrankCollection> pranks = collectionRepo.query()
                .where("container_id", containerId)
                .list();

        if (pranks.isEmpty()) {
            event.reply("❌ This container has no pranks!")
                    .setEphemeral(true).queue(m -> m.deleteOriginal().queueAfter(5, TimeUnit.SECONDS));
            return;
        }

        StringSelectMenu.Builder menuBuilder = StringSelectMenu.create(MENU + action + "-prank")
                .setPlaceholder("Select a prank to " + action);

        for (PrankCollection p : pranks) {
            String shortId = p.getPrankId().substring(0, 8);
            String label = p.getPlaceholder().length() > 50
                    ? p.getPlaceholder().substring(0, 50) + "..."
                    : p.getPlaceholder();
            menuBuilder.addOption(shortId + " — " + label, p.getPrankId());
        }

        event.reply(
                new MessageCreateBuilder()
                        .useComponentsV2(true)
                        .setComponents(Container.of(
                                TextDisplay.of("### 🎴 Select Prank to " + (action.equals("edit") ? "Edit" : "Remove")),
                                ActionRow.of(menuBuilder.build())
                        ))
                        .build()
        ).setEphemeral(true).queue();
    }

    private void showEditPrankModal(StringSelectInteractionEvent event, String prankId) {
        PrankCollection prank = findPrankById(prankId);
        if (prank == null) {
            event.reply("❌ Prank not found!")
                    .setEphemeral(true).queue(m -> m.deleteOriginal().queueAfter(5, TimeUnit.SECONDS));
            return;
        }

        TextInput urlInput = TextInput.create("url", TextInputStyle.SHORT)
                .setPlaceholder("Direct image/gif URL")
                .setValue(prank.getUrl())
                .setRequired(true)
                .build();

        TextInput placeholderInput = TextInput.create("placeholder", TextInputStyle.PARAGRAPH)
                .setPlaceholder("%m hit %t so hard!")
                .setValue(prank.getPlaceholder())
                .setRequired(true)
                .setMaxLength(500)
                .build();

        event.replyModal(Modal.create(MODAL + "edit-prank:" + prankId, "Edit Prank")
                .addComponents(
                        Label.of("Image/GIF URL", urlInput),
                        Label.of("Message Template (%m = you, %t = target)", placeholderInput)
                )
                .build()
        ).queue();
    }

    private void handleEditPrankModal(ModalInteractionEvent event, String userId, String prankId) {
        PrankCollection prank = findPrankById(prankId);
        if (prank == null) {
            event.reply("❌ Prank not found!")
                    .setEphemeral(true).queue(m -> m.deleteOriginal().queueAfter(5, TimeUnit.SECONDS));
            return;
        }

        String url = getModalValue(event, "url").trim();
        String placeholder = getModalValue(event, "placeholder").trim();

        if (!isValidUrl(url)) {
            event.reply("❌ Invalid URL!")
                    .setEphemeral(true).queue(m -> m.deleteOriginal().queueAfter(5, TimeUnit.SECONDS));
            return;
        }

        prank.setUrl(url);
        prank.setPlaceholder(placeholder);
        collectionRepo.save(prank);

        // Update view panel
        String containerId = viewingContainer.get(userId);
        if (containerId != null) {
            Message ctrlMsg = controlMessages.get(userId);
            if (ctrlMsg != null) {
                ctrlMsg.editMessage(editViewPanel(userId, containerId).build()).queue();
            }
        }

        event.reply("✅ Prank updated!")
                .setEphemeral(true).queue(m -> m.deleteOriginal().queueAfter(5, TimeUnit.SECONDS));
    }

    // ==================== REMOVE PRANK ====================

    private void handleRemovePrank(StringSelectInteractionEvent event, String userId, String prankId) {
        PrankCollection prank = findPrankById(prankId);
        if (prank == null) {
            event.reply("❌ Prank not found!")
                    .setEphemeral(true).queue(m -> m.deleteOriginal().queueAfter(5, TimeUnit.SECONDS));
            return;
        }

        collectionRepo.deleteById(prank.getId());

        // Update view panel
        String containerId = viewingContainer.get(userId);
        if (containerId != null) {
            Message ctrlMsg = controlMessages.get(userId);
            if (ctrlMsg != null) {
                ctrlMsg.editMessage(editViewPanel(userId, containerId).build()).queue();
            }
        }

        event.reply("✅ Prank removed!")
                .setEphemeral(true).queue(m -> m.deleteOriginal().queueAfter(5, TimeUnit.SECONDS));
    }

    // ==================== FILE UPLOAD TO TRANSFER.SH ====================

    /**
     * Uploads a Discord attachment to transfer.sh and returns the download URL.
     * Used when users provide files via Discord's file attachment.
     */
    private CompletableFuture<String> uploadToTransferSh(String filename, InputStream data) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                byte[] bytes = data.readAllBytes();
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(TRANSFER_SH_URL + "/" + filename))
                        .header("Content-Type", "application/octet-stream")
                        .PUT(HttpRequest.BodyPublishers.ofByteArray(bytes))
                        .build();

                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

                if (response.statusCode() == 200) {
                    return response.body().trim();
                } else {
                    throw new RuntimeException("Upload failed with status: " + response.statusCode());
                }
            } catch (Exception e) {
                throw new CompletionException(e);
            }
        });
    }

    // ==================== UTILITY METHODS ====================

    private PrankContainer findContainerById(String containerId) {
        List<PrankContainer> list = containerRepo.query()
                .where("container_id", containerId)
                .list();
        return list.isEmpty() ? null : list.getFirst();
    }

    private PrankCollection findPrankById(String prankId) {
        List<PrankCollection> list = collectionRepo.query()
                .where("prank_id", prankId)
                .list();
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
