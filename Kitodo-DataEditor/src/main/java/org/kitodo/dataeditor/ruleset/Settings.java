/*
 * (c) Kitodo. Key to digital objects e. V. <contact@kitodo.org>
 *
 * This file is part of the Kitodo project.
 *
 * It is licensed under GNU General Public License version 3 or later.
 *
 * For the full copyright and license information, please read the
 * GPL3-License.txt file that was distributed with this source code.
 */

package org.kitodo.dataeditor.ruleset;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.kitodo.dataeditor.ruleset.xml.Setting;

/**
 * Exposes settings from the ruleset. It is not about the meta-data, but how
 * they are displayed in the editor, so graphical interface is configurable.
 */
public class Settings {
    /**
     * Current settings.
     */
    private Map<String, Setting> currentSettings;

    /**
     * Constructor for a new settings object.
     *
     * @param baseSettings
     *            we start with this setting
     */
    public Settings(Collection<Setting> baseSettings) {
        this.currentSettings = baseSettings.parallelStream()
                .collect(Collectors.toMap(Setting::getKey, Function.identity()));
    }

    /**
     * Returns the settings for a key.
     *
     * @param keyId
     *            key for which the query is
     * @return settings for the key
     */
    Settings getSettingsForKey(String keyId) {
        Setting keySetting = currentSettings.get(keyId);
        return new Settings(keySetting == null ? Collections.emptyList() : keySetting.getSettings());
    }

    /**
     * Whether the key is always showing.
     *
     * @param keyId
     *            key for which the query is
     * @return whether the key is always showing
     */
    boolean isAlwaysShowing(String keyId) {
        if (currentSettings.containsKey(keyId)) {
            return currentSettings.get(keyId).isAlwaysShowing();
        } else {
            return false;
        }
    }

    /**
     * Whether the key is editable.
     *
     * @param keyId
     *            key for which the query is
     * @return whether the key is editable
     */
    boolean isEditable(String keyId) {
        if (currentSettings.containsKey(keyId)) {
            return currentSettings.get(keyId).isEditable();
        } else {
            return true;
        }
    }

    /**
     * Whether the key is excluded.
     *
     * @param keyId
     *            key for which the query is
     * @return whether the key is excluded
     */
    boolean isExcluded(String keyId) {
        if (currentSettings.containsKey(keyId)) {
            return currentSettings.get(keyId).isExcluded();
        } else {
            return false;
        }
    }

    /**
     * Whether the key is multi-line.
     *
     * @param keyId
     *            key for which the query is
     * @return whether the key is multi-line
     */
    boolean isMultiline(String keyId) {
        if (currentSettings.containsKey(keyId)) {
            return currentSettings.get(keyId).isMultiline();
        } else {
            return false;
        }
    }

    /**
     * Connects to others.
     *
     * @param other
     *            to connect others with it
     */
    public void merge(Collection<Setting> other) {
        Collection<Setting> merged = merge(currentSettings.values(), other);
        currentSettings = merged.parallelStream().collect(Collectors.toMap(Setting::getKey, Function.identity()));
    }

    /**
     * Connection of two times settings. Because it may be common and specific,
     * and then specific is better. This is with acquisition stages.
     *
     * @param currentSettings
     *            common
     * @param otherSettings
     *            specific
     * @return connection of two times settings
     */
    private List<Setting> merge(Collection<Setting> currentSettings, Collection<Setting> otherSettings) {
        Map<String, Setting> currentSettingsMap = currentSettings.parallelStream()
                .collect(Collectors.toMap(Setting::getKey, Function.identity()));
        Map<String, Setting> otherSettingsMap = otherSettings.parallelStream()
                .collect(Collectors.toMap(Setting::getKey, Function.identity()));
        Set<String> keyIds = new HashSet<>(currentSettingsMap.keySet());
        keyIds.addAll(otherSettingsMap.keySet());
        List<Setting> result = new ArrayList<>(keyIds.size());
        for (String keyId : keyIds) {
            if (currentSettingsMap.containsKey(keyId)) {
                Setting current = currentSettingsMap.get(keyId);
                if (otherSettingsMap.containsKey(keyId)) {
                    Setting other = otherSettingsMap.get(keyId);
                    Setting merged = new Setting();
                    merged.setKey(current.getKey());
                    merged.setAlwaysShowing(
                        other.getAlwaysShowing() != null ? other.getAlwaysShowing() : current.getAlwaysShowing());
                    merged.setEditable(other.getEditable() != null ? other.getEditable() : current.getEditable());
                    merged.setExcluded(other.getExcluded() != null ? other.getExcluded() : current.getExcluded());
                    merged.setMultiline(other.getMultiline() != null ? other.getMultiline() : current.getMultiline());
                    merged.setSettings(merge(current.getSettings(), other.getSettings()));
                    result.add(merged);
                } else {
                    result.add(current);
                }
            } else {
                result.add(otherSettingsMap.get(keyId));
            }
        }
        return result;
    }

}
