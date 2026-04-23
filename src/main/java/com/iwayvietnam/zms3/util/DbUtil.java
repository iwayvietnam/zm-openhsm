package com.iwayvietnam.zms3.util;

import com.iwayvietnam.zms3.mover.MovedItem;
import com.zimbra.common.service.ServiceException;
import com.zimbra.cs.db.DbMailItem;
import com.zimbra.cs.mailbox.Mailbox;

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
