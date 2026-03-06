package group.worldstandard.pudel.plugin.dto;

/**
 * DTO object for export/import json ({@link ContainerDto}'s child)
 * <p>
 * When {@code id} is present and matches an existing prank, the prank
 * is updated (url + placeholder). When {@code id} is null or unrecognized,
 * a new prank is created.
 *
 * @param id          prank ID (nullable — omit to create new)
 * @param url         image/gif URL
 * @param placeholder message template
 */
public record PrankDto(
        String id,
        String url,
        String placeholder
) {
}
