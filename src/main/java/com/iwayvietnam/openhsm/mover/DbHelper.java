/*
 * ***** BEGIN LICENSE BLOCK *****
 * Zm OpenHSM is the the Hierarchical Storage Management extension for Zimbra Collaboration Open Source Edition..
 * Copyright (C) 2026-present iWay Vietnam and/or its affiliates. All rights reserved.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Affero General Public License for more details.
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>
 * ***** END LICENSE BLOCK *****
 *
 * Zimbra OpenHSM
 *
 * Written by Nguyen Van Nguyen <nguyennv1981@gmail.com>
 */
package com.iwayvietnam.openhsm.mover;

import com.zimbra.common.service.ServiceException;
import com.zimbra.cs.db.Db;
import com.zimbra.cs.db.DbMailItem;
import com.zimbra.cs.db.DbUtil;
import com.zimbra.cs.mailbox.Mailbox;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Db Util
 * @author Nguyen Van Nguyen <nguyennv1981@gmail.com>
 */
public class DbHelper {
    public static boolean alterVolume(Mailbox mbox, MovedItem info, int volumeId) throws ServiceException {
        final var table = info.fromRevision() ?
            DbMailItem.getRevisionTableName(mbox, info.fromDumpster()) :
            DbMailItem.getMailItemTableName(mbox, info.fromDumpster());
        final var idColumn = info.fromRevision() ? "item_id" : "id";

        final var sql = String.format(
            "UPDATE %s SET locator = ? WHERE %s = ? AND mod_content = ?",
            table,
            DbMailItem.IN_THIS_MAILBOX_AND + idColumn
        );
        final var numRows = DbUtil.executeUpdate(sql,
            volumeId,
            info.getId(),
            info.getModifyContent()
        );

        return numRows == 1;
    }

    public static Collection<MovedItem> getItems(Mailbox mbox, List<Integer> itemIds, List<Short> volumeIds, boolean fromDumpster) throws ServiceException {
        List<MovedItem> items = new ArrayList<>();
        String table = null;

        for(var i = 0; i < itemIds.size(); i += Db.getINClauseBatchSize()) {
            table = DbMailItem.getMailItemTableName(mbox, fromDumpster);
            int count = Math.min(Db.getINClauseBatchSize(), itemIds.size() - i);
//            String sql = "SELECT id, locator, mod_content, blob_digest  FROM " + table + " WHERE " + DbMailItem.IN_THIS_MAILBOX_AND + "blob_digest IS NOT NULL AND locator IN " + com.zimbra.cs.db.DbUtil.suitableNumberOfVariables(volumeIds) + " AND id IN " + com.zimbra.cs.db.DbUtil.suitableNumberOfVariables(count);
            final var sql = String.format(
                "SELECT id, locator, mod_content, blob_digest FROM %s WHERE %s IS NOT NULL AND locator IN %s AND id IN %s",
                table,
                DbMailItem.IN_THIS_MAILBOX_AND + "blob_digest",
                DbUtil.suitableNumberOfVariables(volumeIds),
                DbUtil.suitableNumberOfVariables(count)
            );
            final var results = DbUtil.executeQuery(sql);
        }
        return items;
    }
}
