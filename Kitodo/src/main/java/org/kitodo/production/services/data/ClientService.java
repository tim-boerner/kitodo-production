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

package org.kitodo.production.services.data;

import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.kitodo.data.database.beans.Client;
import org.kitodo.data.database.exceptions.DAOException;
import org.kitodo.data.database.persistence.ClientDAO;
import org.kitodo.production.services.ServiceManager;
import org.kitodo.production.services.data.base.SearchDatabaseService;
import org.primefaces.model.SortOrder;

public class ClientService extends SearchDatabaseService<Client, ClientDAO> {

    private static ClientService instance = null;

    /**
     * Return singleton variable of type ClientService.
     *
     * @return unique instance of ClientService
     */
    public static ClientService getInstance() {
        if (Objects.equals(instance, null)) {
            synchronized (ClientService.class) {
                if (Objects.equals(instance, null)) {
                    instance = new ClientService();
                }
            }
        }
        return instance;
    }

    /**
     * Constructor.
     */
    private ClientService() {
        super(new ClientDAO());
    }

    @Override
    public Long countDatabaseRows() throws DAOException {
        return countDatabaseRows("SELECT COUNT(*) FROM Client");
    }

    @Override
    public Long countResults(Map filters) throws DAOException {
        return countDatabaseRows();
    }

    @Override
    public List<Client> getAllForSelectedClient() {
        throw new UnsupportedOperationException();
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<Client> loadData(int first, int pageSize, String sortField, SortOrder sortOrder, Map filters) {
        return dao.getByQuery("FROM Client" + getSort(sortField, sortOrder), filters, first, pageSize);
    }

    /**
     * Refresh project object after update.
     *
     * @param client
     *            object
     */
    @Override
    public void refresh(Client client) {
        dao.refresh(client);
    }

    /**
     * Add standard list columns to client.
     * @param client Client to add standard list columns to.
     * @return updated client
     */
    public Client addStandardListColumns(Client client) {
        client.setListColumns(ServiceManager.getListColumnService().getAllStandardListColumns());
        return client;
    }
}
