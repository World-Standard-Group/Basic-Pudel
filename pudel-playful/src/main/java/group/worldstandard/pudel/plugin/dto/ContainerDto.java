package group.worldstandard.pudel.plugin.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Map;

/**
 * DTO object for export/import json (root).
 * <p>
 * Serializes as a flat map where each key is a container name
 * and each value is an array of {@link PrankDto} pranks.
 * <pre>
 * {
 *   "bonk": [ { "url": "...", "placeholder": "..." } ],
 *   "slap": [ { "url": "...", "placeholder": "..." } ]
 * }
 * </pre>
 *
 * @param containers map of container name → pranks array
 */
public record ContainerDto(
        @JsonValue Map<String, PrankDto[]> containers
) {
    @JsonCreator
    public ContainerDto(Map<String, PrankDto[]> containers) {
        this.containers = containers;
    }
}
