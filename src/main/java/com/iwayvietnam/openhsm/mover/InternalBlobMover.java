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

import com.zimbra.common.account.Key;
import com.zimbra.common.service.ServiceException;
import com.zimbra.common.util.StringUtil;
import com.zimbra.common.util.ZimbraLog;
import com.zimbra.cs.account.Account;
import com.zimbra.cs.account.Provisioning;
import com.zimbra.cs.hsm.util.Log;
import com.zimbra.cs.mailbox.MailItem;
import com.zimbra.cs.mailbox.Mailbox;
import com.zimbra.cs.mailbox.MailboxManager;
import com.zimbra.cs.volume.Volume;
import com.zimbra.cs.volume.VolumeManager;

import java.util.List;
import java.util.Set;

/**
 * Internal blob mover
 * @author Nguyen Van Nguyen <nguyennv1981@gmail.com>
 */
public class InternalBlobMover implements BlobMover {
    private final MoverState state = new MoverState();

    public MoverState moveBlobs(String query, Set<MailItem.Type> types, List<Short> sourceVolumeIds, short destVolumeId, Long maxBytes, int mboxId, String accountId) throws ServiceException {
        this.validateVolume(destVolumeId);
        for(short volumeId : sourceVolumeIds) {
            this.validateVolume(volumeId);
        }
        Log.hsm.info("Moving blobs matching query '%s' for type(s) %s from volume(s) %s to volume %d.", query, new Object[]{MailItem.Type.toString(types), StringUtil.join(", ", sourceVolumeIds), state.getDestVolumeId()});

        state.setQuery(query);
        state.setDestVolumeId(destVolumeId);
        if (maxBytes != null) {
            state.setMaxBytes(maxBytes);
        }
        ZimbraLog.removeAccountFromContext();
        ZimbraLog.addMboxToContext(mboxId);

        if (!isLocalMailbox(accountId)) {
            Log.hsm.info("Skipping mailbox %d because it has been moved to another server.", new Object[]{mboxId});
            return this.state;
        }
        Mailbox mbox = MailboxManager.getInstance().getMailboxById(mboxId);
        Account account = mbox.getAccount();
        return state;
    }


    private boolean isLocalMailbox(String accountId) throws ServiceException {
        Account account = Provisioning.getInstance().get(Key.AccountBy.id, accountId);
        if (account == null) {
            Log.hsm.warn("Unable to look up account %s.", new Object[]{accountId});
            return false;
        } else {
            return Provisioning.onLocalServer(account);
        }
    }

    private void validateVolume(short volumeId) throws ServiceException {
        Volume vol = VolumeManager.getInstance().getVolume(volumeId);
        if (vol.getType() != 1 && vol.getType() != 2) {
            throw ServiceException.FAILURE("Volume is invalid: " + vol, (Throwable)null);
        }
    }
}
