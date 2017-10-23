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

package org.kitodo.services.validation;

import de.sub.goobi.config.ConfigCore;

import org.kitodo.api.validation.metadata.MetadataValidationInterface;
import org.kitodo.serviceloader.KitodoServiceLoader;

public class MetadataValidationService {

    private MetadataValidationInterface getValidationModule() {
        KitodoServiceLoader<MetadataValidationInterface> loader = new KitodoServiceLoader<>(
                MetadataValidationInterface.class, ConfigCore.getParameter("moduleFolder"));
        return loader.loadModule();
    }
}
