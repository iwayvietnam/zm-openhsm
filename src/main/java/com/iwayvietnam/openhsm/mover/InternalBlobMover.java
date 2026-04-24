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

import com.iwayvietnam.openhsm.util.Log;
import com.zimbra.common.account.Key;
import com.zimbra.common.service.ServiceException;
import com.zimbra.common.util.StringUtil;
import com.zimbra.common.util.ZimbraLog;
import com.zimbra.cs.account.Account;
import com.zimbra.cs.account.Provisioning;
import com.zimbra.cs.mailbox.MailItem;
import com.zimbra.cs.mailbox.Mailbox;
import com.zimbra.cs.mailbox.MailboxManager;
import com.zimbra.cs.store.MailboxBlob;
import com.zimbra.cs.store.StoreManager;
import com.zimbra.cs.store.file.FileBlobStore;
import com.zimbra.cs.volume.Volume;
import com.zimbra.cs.volume.VolumeManager;

import java.io.File;
import java.io.IOException;
import java.util.*;

/**
 * Internal blob mover
 * @author Nguyen Van Nguyen <nguyennv1981@gmail.com>
 */
public class InternalBlobMover implements BlobMover {
    private final MoverState state = new MoverState();
    private final Map<String, MailboxBlob> allLinkedNewBlobs = new HashMap<>();

    public MoverState moveBlobs(String query, Set<MailItem.Type> types, List<Short> sourceVolumeIds, short destVolumeId, Long maxBytes, int mboxId, String accountId) throws ServiceException {
        this.validateVolume(destVolumeId);
        for(short volumeId : sourceVolumeIds) {
            this.validateVolume(volumeId);
        }
        Log.openhsm.info("Moving blobs matching query '%s' for type(s) %s from volume(s) %s to volume %d.", query, new Object[]{MailItem.Type.toString(types), StringUtil.join(", ", sourceVolumeIds), state.getDestVolumeId()});

        state.setQuery(query);
        state.setDestVolumeId(destVolumeId);
        if (maxBytes != null) {
            state.setMaxBytes(maxBytes);
        }
        ZimbraLog.removeAccountFromContext();
        ZimbraLog.addMboxToContext(mboxId);

        if (!isLocalMailbox(accountId)) {
            Log.openhsm.info("Skipping mailbox %d because it has been moved to another server.", new Object[]{mboxId});
            return this.state;
        }
        Mailbox mbox = MailboxManager.getInstance().getMailboxById(mboxId);
        Account account = mbox.getAccount();
        return state;
    }

    private void safeMoveBlobs(Mailbox mbox, Collection<MovedItem> items) throws ServiceException {
    }

    private void moveBlobsInternal(Mailbox mbox, Collection<MovedItem> items) throws ServiceException {
        var destVolumeId = state.getDestVolumeId();
        Log.openhsm.info("Moving blobs for %d items in mailbox %d to volume %d.", new Object[]{items.size(), mbox.getId(), destVolumeId});

        var numMoved = 0;
        Map<String, MailboxBlob> linkedNewBlobs = new HashMap<>();
        Set<MailboxBlob> unprocessedNewBlobs = new HashSet<>();
        Set<MailboxBlob> blobsToDelete = new HashSet<>();
        var success = false;

        for(var item : items) {
            if (state.shouldAbort()) {
                state.setWasAborted(true);
                Log.openhsm.warn("Aborting BlobMover session");
                break;
            }

            var storeManager = (FileBlobStore) StoreManager.getReaderSMInstance(item.getVolumeId());
            var volumeId = Short.toString(item.getVolumeId());
            var oldBlob = storeManager.getMailboxBlob(mbox, item.getId(), item.getModifyContent(), volumeId);
            if (oldBlob != null) {
                MailboxBlob newBlob = null;

                try {
                    MailboxBlob copiedBlob = (MailboxBlob)allLinkedNewBlobs.get(item.getBlobDigest());
                    if (copiedBlob == null) {
                        copiedBlob = (MailboxBlob)linkedNewBlobs.get(item.getBlobDigest());
                    }

                    if (copiedBlob != null) {
                        File file = copiedBlob.getLocalBlob().getFile();
                        if (!file.exists()) {
                            Log.openhsm.info("Unable to link to %s because the file was deleted.  %s", new Object[]{file.getPath(), info});
                            allLinkedNewBlobs.remove(item.getBlobDigest());
                            linkedNewBlobs.remove(item.getBlobDigest());
                            copiedBlob = null;
                        }
                    }

                    if (copiedBlob != null) {
                        newBlob = storeManager.link(copiedBlob.getLocalBlob(), mbox, item.getId(), item.getModifyContent(), destVolumeId);
                    } else {
                        newBlob = storeManager.copy(oldBlob.getLocalBlob(), mbox, item.getId(), item.getModifyContent(), destVolumeId);
                        long newBlobSize = ((MailboxBlob)newBlob).getLocalBlob().getFile().length();
                        if (state.getNumBytesMoved() + newBlobSize > state.getMaxBytes()) {
                            Log.openhsm.info("Exceeded limit of %d bytes.  Aborting BlobMover.", new Object[]{state.getMaxBytes()});
                            storeManager.delete(newBlob);
                            state.setShouldAbort(true);
                            continue;
                        }

                        state.incrementNumBytesMoved(newBlobSize);
                    }
                } catch (IOException e) {
                    throw ServiceException.FAILURE("Unable to copy " + oldBlob + " to volume " + destVolumeId, e);
                }

                unprocessedNewBlobs.add(newBlob);
                item.setOldBlob(oldBlob);
                item.setNewBlob(newBlob);
                int linkCount = 1;

                try {
                    linkCount = IO.linkCount(oldBlob.getLocalBlob().getPath());
                } catch (IOException e) {
                    Log.openhsm.warn("Unable to determine link count of " + oldBlob + ": " + e);
                }

                if (linkCount > 1) {
                    linkedNewBlobs.put(item.getBlobDigest(), newBlob);
                }
            } else {
                Log.openhsm.warn("Could not find blob for item %d, revision %d on volume %d.", new Object[]{item.getId(), item.getModifyContent(), item.getVolumeId()});
            }
        }
    }

    private boolean isLocalMailbox(String accountId) throws ServiceException {
        Account account = Provisioning.getInstance().get(Key.AccountBy.id, accountId);
        if (account == null) {
            Log.openhsm.warn("Unable to look up account %s.", new Object[]{accountId});
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
