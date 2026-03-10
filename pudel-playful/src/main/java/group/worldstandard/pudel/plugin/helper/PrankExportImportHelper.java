package group.worldstandard.pudel.plugin.helper;

import group.worldstandard.pudel.api.PluginContext;
import group.worldstandard.pudel.api.database.PluginRepository;
import group.worldstandard.pudel.plugin.dto.ContainerDto;
import group.worldstandard.pudel.plugin.dto.PrankDto;
import group.worldstandard.pudel.plugin.entity.PrankCollection;
import group.worldstandard.pudel.plugin.entity.PrankContainer;
import net.dv8tion.jda.api.components.attachmentupload.AttachmentUpload;
import net.dv8tion.jda.api.components.container.Container;
import net.dv8tion.jda.api.components.filedisplay.FileDisplay;
import net.dv8tion.jda.api.components.label.Label;
import net.dv8tion.jda.api.components.separator.Separator;
import net.dv8tion.jda.api.components.textdisplay.TextDisplay;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.modals.Modal;
import net.dv8tion.jda.api.utils.FileUpload;
import net.dv8tion.jda.api.utils.messages.MessageCreateBuilder;
import tools.jackson.databind.ObjectMapper;

import java.awt.Color;
import java.io.InputStream;
import java.net.URI;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * Handles export/import JSON operations for prank containers.
 */
public class PrankExportImportHelper {

    private final PluginContext ctx;
    private final ObjectMapper objectMapper;
    private final PluginRepository<PrankContainer> containerRepo;
    private final PluginRepository<PrankCollection> collectionRepo;
    private final Map<String, Message> controlMessages;
    private final String modalPrefix;
    private final Color accentIo;
    private final PrankPanelBuilder panelBuilder;

    public PrankExportImportHelper(PluginContext ctx,
                                   ObjectMapper objectMapper,
                                   PluginRepository<PrankContainer> containerRepo,
                                   PluginRepository<PrankCollection> collectionRepo,
                                   Map<String, Message> controlMessages,
                                   String modalPrefix,
                                   Color accentIo,
                                   PrankPanelBuilder panelBuilder) {
        this.ctx = ctx;
        this.objectMapper = objectMapper;
        this.containerRepo = containerRepo;
        this.collectionRepo = collectionRepo;
        this.controlMessages = controlMessages;
        this.modalPrefix = modalPrefix;
        this.accentIo = accentIo;
        this.panelBuilder = panelBuilder;
    }

    public void handleExportAll(ButtonInteractionEvent event, String userId) {
        List<PrankContainer> myContainers = containerRepo.query()
                .where("user_id", userId)
                .list();

        if (myContainers.isEmpty()) {
            event.reply("❌ You don't have any containers to export!")
                    .setEphemeral(true).queue(m -> m.deleteOriginal().queueAfter(5, TimeUnit.SECONDS));
            return;
        }

        Map<String, PrankDto[]> map = new LinkedHashMap<>();
        int totalPranks = 0;

        for (PrankContainer c : myContainers) {
            List<PrankCollection> pranks = collectionRepo.query()
                    .where("container_id", c.getContainerId())
                    .list();

            PrankDto[] prankDtos = pranks.stream()
                    .map(p -> new PrankDto(p.getPrankId(), p.getUrl(), p.getPlaceholder()))
                    .toArray(PrankDto[]::new);

            map.put(c.getName(), prankDtos);
            totalPranks += prankDtos.length;
        }

        ContainerDto dto = new ContainerDto(map);

        try {
            byte[] jsonBytes = objectMapper.writerWithDefaultPrettyPrinter()
                    .writeValueAsBytes(dto);

            event.reply(
                    new MessageCreateBuilder()
                            .useComponentsV2(true)
                            .setComponents(Container.of(
                                    TextDisplay.of("### 📤 Exported All Containers"),
                                    Separator.create(false, Separator.Spacing.SMALL),
                                    FileDisplay.fromFile(FileUpload.fromData(jsonBytes, "prank-%s-export.json".formatted(LocalDate.now().format(DateTimeFormatter.ISO_DATE)))),
                                    TextDisplay.of("-# " + myContainers.size() + " container(s), "
                                            + totalPranks + " prank(s) • Save this file to import later")
                            ).withAccentColor(accentIo))
                            .build()
            ).setEphemeral(true).queue();
        } catch (Exception e) {
            ctx.log("error", "Export error: " + e.getMessage());
            event.reply("❌ Failed to export: " + e.getMessage())
                    .setEphemeral(true).queue(m -> m.deleteOriginal().queueAfter(5, TimeUnit.SECONDS));
        }
    }

    public void showImportModal(ButtonInteractionEvent event) {
        AttachmentUpload fileUpload = AttachmentUpload.create("json-file")
                .setRequiredRange(1, 1)
                .setRequired(true)
                .build();

        event.replyModal(Modal.create(modalPrefix + "import-json", "Import Prank Container")
                .addComponents(
                        Label.of("JSON File (exported from /prank)", fileUpload)
                )
                .build()
        ).queue();
    }

    public void handleImportModal(ModalInteractionEvent event, String userId) {
        var uploadMapping = event.getValue("json-file");
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

        if (!attachment.getFileName().toLowerCase().endsWith(".json")) {
            event.reply("❌ Please upload a `.json` file!")
                    .setEphemeral(true).queue(m -> m.deleteOriginal().queueAfter(5, TimeUnit.SECONDS));
            return;
        }

        event.deferReply(true).queue(hook -> attachment.getProxy().download().thenAccept(inputStream -> {
            try (InputStream is = inputStream) {
                ContainerDto dto = objectMapper.readValue(is, ContainerDto.class);

                if (dto.containers() == null || dto.containers().isEmpty()) {
                    hook.editOriginal("❌ JSON file is empty or has invalid format!")
                            .queue(m -> m.delete().queueAfter(5, TimeUnit.SECONDS));
                    return;
                }

                int containersCreated = 0, containersMerged = 0, totalImported = 0, totalUpdated = 0, totalSkipped = 0;

                for (Map.Entry<String, PrankDto[]> entry : dto.containers().entrySet()) {
                    String containerName = entry.getKey().trim().toLowerCase();
                    PrankDto[] prankDtos = entry.getValue();

                    if (containerName.isEmpty() || prankDtos == null) { totalSkipped++; continue; }

                    List<PrankContainer> existing = containerRepo.query()
                            .where("user_id", userId)
                            .where("name", containerName)
                            .list();

                    PrankContainer container;
                    if (!existing.isEmpty()) {
                        container = existing.getFirst();
                        containersMerged++;
                    } else {
                        container = new PrankContainer(userId, UUID.randomUUID().toString(), containerName);
                        containerRepo.save(container);
                        containersCreated++;
                    }

                    Map<String, PrankCollection> existingById = new HashMap<>();
                    for (PrankCollection p : collectionRepo.query()
                            .where("container_id", container.getContainerId()).list()) {
                        existingById.put(p.getPrankId(), p);
                    }

                    for (PrankDto prankDto : prankDtos) {
                        String url = prankDto.url() != null ? prankDto.url().trim() : "";
                        String placeholder = prankDto.placeholder() != null ? prankDto.placeholder().trim() : "";

                        if (url.isEmpty() || placeholder.isEmpty() || !isValidUrl(url)) { totalSkipped++; continue; }

                        if (prankDto.id() != null && existingById.containsKey(prankDto.id())) {
                            PrankCollection existing2 = existingById.get(prankDto.id());
                            boolean changed = false;
                            if (!existing2.getUrl().equals(url)) { existing2.setUrl(url); changed = true; }
                            if (!existing2.getPlaceholder().equals(placeholder)) { existing2.setPlaceholder(placeholder); changed = true; }
                            if (changed) { collectionRepo.save(existing2); totalUpdated++; }
                        } else {
                            collectionRepo.save(new PrankCollection(UUID.randomUUID().toString(), container.getContainerId(), url, placeholder));
                            totalImported++;
                        }
                    }
                }

                Message ctrlMsg = controlMessages.get(userId);
                if (ctrlMsg != null) {
                    ctrlMsg.editMessage(panelBuilder.editMainPanel(userId).build()).queue();
                }

                StringBuilder result = new StringBuilder();
                if (containersCreated > 0) result.append("✅ Created **%d** container(s)".formatted(containersCreated));
                if (containersMerged > 0) {
                    if (!result.isEmpty()) result.append(", ");
                    result.append("🔄 Merged into **%d** existing".formatted(containersMerged));
                }
                if (totalImported > 0 || totalUpdated > 0 || (containersCreated == 0 && containersMerged == 0)) {
                    if (!result.isEmpty()) result.append("\n");
                    result.append("📥 **%d** added, ✏️ **%d** updated".formatted(totalImported, totalUpdated));
                }
                if (totalSkipped > 0) result.append(" (%d skipped)".formatted(totalSkipped));

                hook.editOriginal(result.toString()).queue(m -> m.delete().queueAfter(10, TimeUnit.SECONDS));

            } catch (Exception e) {
                ctx.log("error", "Import error: " + e.getMessage());
                hook.editOriginal("❌ Failed to parse JSON: " + e.getMessage())
                        .queue(m -> m.delete().queueAfter(5, TimeUnit.SECONDS));
            }
        }).exceptionally(e -> {
            ctx.log("error", "Import download error: " + e.getMessage());
            hook.editOriginal("❌ Failed to download file: " + e.getMessage())
                    .queue(m -> m.delete().queueAfter(5, TimeUnit.SECONDS));
            return null;
        }));
    }

    private boolean isValidUrl(String url) {
        try { new URI(url); return url.startsWith("http"); } catch (Exception e) { return false; }
    }
}

