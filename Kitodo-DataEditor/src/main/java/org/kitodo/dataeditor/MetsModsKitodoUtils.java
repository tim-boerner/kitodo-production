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

package org.kitodo.dataeditor;

import java.util.List;
import java.util.Optional;

import javax.xml.bind.JAXBElement;

import org.kitodo.dataeditor.exceptions.DataNotFoundException;
import org.kitodo.metsModsKitodo.ExtensionDefinition;
import org.kitodo.metsModsKitodo.KitodoType;
import org.kitodo.metsModsKitodo.ModsDefinition;

class MetsModsKitodoUtils {

    static <T> T getFirstGenericTypeFromObjectList(List<Object> objects, Class<T> type) throws DataNotFoundException {
        for (Object object : objects) {
            if (object instanceof JAXBElement) {
                JAXBElement modsJaxbElement = (JAXBElement) object;
                if (type.isInstance(modsJaxbElement.getValue())) {
                    return (type.cast(modsJaxbElement.getValue()));
                }
            }
            if (type.isInstance(object)) {
                return (type.cast(object));
            }
        }
        throw new DataNotFoundException("No " + type.getName() + " objects found");
    }

    static KitodoType getKitodoTypeFromModsDefinition(ModsDefinition modsDefinition) throws DataNotFoundException {
        Optional<List<Object>> extensionData = Optional.ofNullable(modsDefinition)
            .map(ModsDefinition::getModsGroup);

        if (extensionData.isPresent()) {
            ExtensionDefinition extensionDefinition = getFirstGenericTypeFromObjectList(extensionData.get(),ExtensionDefinition.class);
            return getKitodoTypeFromExtensionDefinition(extensionDefinition);
        }
        throw new DataNotFoundException("ModsDefinition does not have MODS-extension-elements");
    }

    private static KitodoType getKitodoTypeFromExtensionDefinition(ExtensionDefinition extensionDefinition) throws DataNotFoundException {
        Optional<List<Object>> kitodoData = Optional.ofNullable(extensionDefinition)
            .map(ExtensionDefinition::getContent);

        if (kitodoData.isPresent()) {
            return getFirstGenericTypeFromObjectList(kitodoData.get(),KitodoType.class);
        }
        throw new DataNotFoundException("ExtensionDefinition does not have Kitodo-elements");
    }
}
