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
package com.iwayvietnam.openhsm.util;

import com.iwayvietnam.openhsm.mover.MovedItem;
import com.zimbra.common.service.ServiceException;
import com.zimbra.cs.db.DbMailItem;
import com.zimbra.cs.mailbox.Mailbox;

/**
 * Db Util
 * @author Nguyen Van Nguyen <nguyennv1981@gmail.com>
 */
public class DbUtil {
    public static boolean alterVolume(Mailbox mbox, MovedItem info, int volumeId) throws ServiceException {
        String table = null;
        String idColumn = null;

        if (info.fromRevision()) {
            table = DbMailItem.getRevisionTableName(mbox, info.fromDumpster());
            idColumn = "item_id";
        } else {
            table = DbMailItem.getMailItemTableName(mbox, info.fromDumpster());
            idColumn = "id";
        }

        final var sql = String.format("UPDATE %s SET locator = ? WHERE %s = ? AND mod_content = ?", table, DbMailItem.IN_THIS_MAILBOX_AND + idColumn);
        int numRows = com.zimbra.cs.db.DbUtil.executeUpdate(sql,
            volumeId,
            info.getId(),
            info.getModifyContent()
        );

        return numRows == 1;
    }
}
