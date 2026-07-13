package group.worldstandard.pudel.plugin.session;

import net.dv8tion.jda.api.entities.Message;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages user session state for the categorization plugin.
 * Encapsulates all per-user ephemeral state to avoid scattering maps across the main class.
 */
public class SessionManager {

    /** Ephemeral control panel message per user (userId -> Message). */
    private final Map<String, Message> controlMessages = new ConcurrentHashMap<>();
    /** Currently active permission page per user (0-based, pagination of permissions). */
    private final Map<String, Integer> activePermPage = new ConcurrentHashMap<>();
    /** Currently editing profile name per user. */
    private final Map<String, String> editingProfileName = new ConcurrentHashMap<>();
    /** Temporary permission state per user: permEnumName -> "ALLOW"/"INHERIT"/"DENY". */
    private final Map<String, LinkedHashMap<String, String>> tempPermState = new ConcurrentHashMap<>();
    /** Original permission state per user for diff comparison. */
    private final Map<String, LinkedHashMap<String, String>> originalPermState = new ConcurrentHashMap<>();
    /** Create form state per user. */
    private final Map<String, Map<String, String>> createFormState = new ConcurrentHashMap<>();
    /** Import form state per user. */
    private final Map<String, Map<String, String>> importFormState = new ConcurrentHashMap<>();

    // ==================== CONTROL MESSAGES ====================

    public void putControlMessage(String userId, Message message) {
        controlMessages.put(userId, message);
    }

    public Message getControlMessage(String userId) {
        return controlMessages.get(userId);
    }

    public Message removeControlMessage(String userId) {
        return controlMessages.remove(userId);
    }

    // ==================== ACTIVE PERMISSION PAGE ====================

    public int getActivePermPage(String userId) {
        return activePermPage.getOrDefault(userId, 0);
    }

    public void setActivePermPage(String userId, int page) {
        activePermPage.put(userId, Math.max(0, page));
    }

    public void removeActivePermPage(String userId) {
        activePermPage.remove(userId);
    }

    // ==================== ORIGINAL PERMISSION STATE ====================

    public void putOriginalPermState(String userId, LinkedHashMap<String, String> state) {
        originalPermState.put(userId, state);
    }

    public LinkedHashMap<String, String> getOriginalPermState(String userId) {
        return originalPermState.get(userId);
    }

    public void removeOriginalPermState(String userId) {
        originalPermState.remove(userId);
    }

    // ==================== EDITING PROFILE ====================

    public void setEditingProfileName(String userId, String profileName) {
        editingProfileName.put(userId, profileName);
    }

    public String getEditingProfileName(String userId) {
        return editingProfileName.get(userId);
    }

    public void removeEditingProfileName(String userId) {
        editingProfileName.remove(userId);
    }

    // ==================== TEMP PERMISSION STATE ====================

    public void putTempPermState(String userId, LinkedHashMap<String, String> state) {
        tempPermState.put(userId, state);
    }

    public LinkedHashMap<String, String> getTempPermState(String userId) {
        return tempPermState.get(userId);
    }

    public void removeTempPermState(String userId) {
        tempPermState.remove(userId);
    }

    // ==================== CREATE FORM STATE ====================

    public void putCreateFormState(String userId, Map<String, String> state) {
        createFormState.put(userId, state);
    }

    public Map<String, String> getCreateFormState(String userId) {
        return createFormState.get(userId);
    }

    public void removeCreateFormState(String userId) {
        createFormState.remove(userId);
    }

    // ==================== IMPORT FORM STATE ====================

    public void putImportFormState(String userId, Map<String, String> state) {
        importFormState.put(userId, state);
    }

    public Map<String, String> getImportFormState(String userId) {
        return importFormState.get(userId);
    }

    public void removeImportFormState(String userId) {
        importFormState.remove(userId);
    }

    // ==================== BULK CLEANUP ====================

    /**
     * Clears all session state for a user.
     */
    public void clearUserSession(String userId) {
        removeControlMessage(userId);
        removeActivePermPage(userId);
        removeEditingProfileName(userId);
        removeTempPermState(userId);
        removeOriginalPermState(userId);
        removeCreateFormState(userId);
        removeImportFormState(userId);
    }

    /**
     * Clears all sessions (used on shutdown).
     */
    public void clearAllSessions() {
        controlMessages.values().forEach(msg -> {
            try { msg.delete().queue(null, _ -> {}); } catch (Exception ignored) {}
        });
        controlMessages.clear();
        activePermPage.clear();
        editingProfileName.clear();
        tempPermState.clear();
        originalPermState.clear();
        createFormState.clear();
        importFormState.clear();
    }
}