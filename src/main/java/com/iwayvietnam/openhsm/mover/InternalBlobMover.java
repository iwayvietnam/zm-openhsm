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

import com.iwayvietnam.openhsm.config.PropertiesConfiguration;
import com.iwayvietnam.openhsm.util.Log;
import com.zimbra.common.account.Key;
import com.zimbra.common.service.ServiceException;
import com.zimbra.common.util.StringUtil;
import com.zimbra.common.util.ZimbraLog;
import com.zimbra.cs.account.Provisioning;
import com.zimbra.cs.index.SortBy;
import com.zimbra.cs.mailbox.*;
import com.zimbra.cs.store.MailboxBlob;
import com.zimbra.cs.store.StoreManager;
import com.zimbra.cs.store.file.FileBlobStore;
import com.zimbra.cs.volume.VolumeManager;
import com.zimbra.znative.IO;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Internal blob mover
 * @author Nguyen Van Nguyen <nguyennv1981@gmail.com>
 */
public class InternalBlobMover implements BlobMover {
    private final MoverState state = new MoverState();
    private final Map<String, MailboxBlob> allLinkedNewBlobs = new HashMap<>();
    private final int batchSize = PropertiesConfiguration.getInstance().getHsmBatchSize();
    private static final ConcurrentMap<Integer, Integer> mailboxGuard = new ConcurrentHashMap<>();

    public MoverState moveBlobs(String query, Set<MailItem.Type> types, List<Short> sourceVolumeIds, short destVolumeId, Long maxBytes, int mboxId, String accountId) throws ServiceException {
        this.validateVolume(destVolumeId);
        for(var volumeId : sourceVolumeIds) {
            this.validateVolume(volumeId);
        }
        Log.openhsm.info(
            "Moving blobs matching query '%s' for type(s) %s from volume(s) %s to volume %d.",
            query,
            MailItem.Type.toString(types),
            StringUtil.join(", ", sourceVolumeIds),
            state.getDestVolumeId()
        );

        state.setQuery(query);
        state.setDestVolumeId(destVolumeId);
        if (maxBytes != null) {
            state.setMaxBytes(maxBytes);
        }
        ZimbraLog.removeAccountFromContext();
        ZimbraLog.addMboxToContext(mboxId);

        if (!isLocalMailbox(accountId)) {
            Log.openhsm.info(
            "Skipping mailbox %d because it has been moved to another server.",
                mboxId
            );
            return this.state;
        }
        var mbox = MailboxManager.getInstance().getMailboxById(mboxId);
        var account = mbox.getAccount();
        ZimbraLog.addAccountNameToContext(account.getName());
        var items = new ArrayList<MovedItem>();
        var dumpsterOrNot = new boolean[]{false, true};

        for(var fromDumpster : dumpsterOrNot) {
            var itemIds = new ArrayList<Integer>();
            try (var results = mbox.index.search(new OperationContext(mbox), query, types, SortBy.NONE, 10000, fromDumpster)) {
                while(results.hasNext()) {
                    itemIds.add(results.getNext().getItemId());
                }
            } catch (Exception e) {
                Log.openhsm.warn("Search '%s' failed.  Skipping mailbox.", query, e);
                continue;
            }
            if (!itemIds.isEmpty()) {
                items.addAll(DbHelper.getItems(mbox, itemIds, sourceVolumeIds, fromDumpster));
            }
            if (Provisioning.getInstance().getLocalServer().isHsmMovePreviousRevisions()) {
                items.addAll(DbHelper.getItemsRevisions(mbox, itemIds, sourceVolumeIds, fromDumpster));
            }
            if (!items.isEmpty()) {
                this.safeMoveBlobs(mbox, items);
            }
            if (state.wasAborted()) {
                Log.openhsm.warn("Aborting blob mover session");
                return state;
            }
        }
        return state;
    }

    private void safeMoveBlobs(Mailbox mbox, List<MovedItem> items) throws ServiceException {
        var mboxId = mbox.getId();
        var existing = mailboxGuard.putIfAbsent(mboxId, mboxId);
        if (existing != null) {
            throw ServiceException.FAILURE(
                "Two threads attempted to move blobs in mailbox " + mboxId,
                null
            );
        } else {
            try {
                var fromIndex = 0;
                var totalSize = items.size();
                Log.openhsm.info(
                    "Moving %d items in mailbox %d with batch size %d.",
                    totalSize,
                    mbox.getId(),
                    this.batchSize
                );

                while(fromIndex < totalSize) {
                    var toIndex = fromIndex + this.batchSize;
                    if (toIndex > totalSize) {
                        toIndex = totalSize;
                    }

                    var subList = items.subList(fromIndex, toIndex);
                    var batchItems = Collections.unmodifiableCollection(subList);
                    this.moveBlobsInternal(mbox, batchItems);
                    if (state.wasAborted()) {
                        break;
                    }

                    fromIndex = toIndex;
                }

                Log.openhsm.info("Finished moving blobs.");
            } finally {
                mailboxGuard.remove(mboxId);
            }
        }
    }

    private void moveBlobsInternal(Mailbox mbox, Collection<MovedItem> items) throws ServiceException {
        var destVolumeId = state.getDestVolumeId();
        Log.openhsm.info("Moving blobs for %d items in mailbox %d to volume %d.", new Object[]{items.size(), mbox.getId(), destVolumeId});

        var numMoved = 0;
        var linkedNewBlobs = new HashMap<String, MailboxBlob>();
        var unprocessedNewBlobs = new HashSet<MailboxBlob>();
        var blobsToDelete = new HashSet<MailboxBlob>();

        try {
            for(var item : items) {
                if (state.shouldAbort()) {
                    state.setWasAborted(true);
                    Log.openhsm.warn("Aborting blob mover session");
                    break;
                }

                var storeManager = (FileBlobStore) StoreManager.getReaderSMInstance(item.getVolumeId());
                var volumeId = Short.toString(item.getVolumeId());
                var oldBlob = storeManager.getMailboxBlob(mbox, item.getId(), item.getModifyContent(), volumeId);
                if (oldBlob != null) {
                    MailboxBlob newBlob;

                    try {
                        var copiedBlob = allLinkedNewBlobs.get(item.getBlobDigest());
                        if (copiedBlob == null) {
                            copiedBlob = linkedNewBlobs.get(item.getBlobDigest());
                        }

                        if (copiedBlob != null) {
                            var file = copiedBlob.getLocalBlob().getFile();
                            if (!file.exists()) {
                                Log.openhsm.info(
                                    "Unable to link to %s because the file was deleted. %s",
                                    file.getPath(),
                                    item
                                );
                                allLinkedNewBlobs.remove(item.getBlobDigest());
                                linkedNewBlobs.remove(item.getBlobDigest());
                                copiedBlob = null;
                            }
                        }

                        if (copiedBlob != null) {
                            newBlob = storeManager.link(copiedBlob.getLocalBlob(), mbox, item.getId(), item.getModifyContent(), destVolumeId);
                        } else {
                            newBlob = storeManager.copy(oldBlob.getLocalBlob(), mbox, item.getId(), item.getModifyContent(), destVolumeId);
                            var newBlobSize = newBlob.getLocalBlob().getFile().length();
                            if (state.getNumBytesMoved() + newBlobSize > state.getMaxBytes()) {
                                Log.openhsm.info(
                                    "Exceeded limit of %d bytes.  Aborting BlobMover.",
                                    state.getMaxBytes()
                                );
                                storeManager.delete(newBlob);
                                state.setShouldAbort(true);
                                continue;
                            }

                            state.incrementNumBytesMoved(newBlobSize);
                        }
                    } catch (IOException e) {
                        throw ServiceException.FAILURE(
                            "Unable to copy " + oldBlob + " to volume " + destVolumeId,
                            e
                        );
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
                    Log.openhsm.warn(
                        "Could not find blob for item %d, revision %d on volume %d.",
                        item.getId(),
                        item.getModifyContent(),
                        item.getVolumeId()
                    );
                }
            }

            for(var item : items) {
                if (item.getNewBlob() != null) {
                    ZimbraLog.addItemToContext(item.getId());
                    mbox.lock.lock();
                    if (DbHelper.alterVolume(mbox, item, destVolumeId)) {
                        blobsToDelete.add(item.getOldBlob());
                        MessageCache.purge(item.getBlobDigest());
                        unprocessedNewBlobs.remove(item.getNewBlob());
                        ++numMoved;
                    } else {
                        Log.openhsm.info(
                            "Data was changed while HSM was running. Not moving blob.  %s.",
                            item
                        );
                        blobsToDelete.add(item.getNewBlob());
                        linkedNewBlobs.remove(item.getBlobDigest());
                    }
                } else {
                    Log.openhsm.debug("Skipping blob after abort: %s.", item);
                }
            }

            ZimbraLog.removeItemFromContext(0);
            deleteBlobs(blobsToDelete);
            state.incrementNumMoved(numMoved);
            allLinkedNewBlobs.putAll(linkedNewBlobs);
        } catch (ServiceException e) {
            state.setError(e);
            deleteBlobs(unprocessedNewBlobs);
            deleteBlobs(blobsToDelete);
            throw e;
        } finally {
            if (numMoved > 0) {
                mbox.purge(MailItem.Type.MESSAGE);
            }
            Log.openhsm.info(
                "Finished moving blobs for %d items in mailbox %d to volume %d.",
                new Object[]{items.size(), mbox.getId(), destVolumeId}
            );
        }
    }

    private static void deleteBlobs(Collection<MailboxBlob> mblobs) {
        if (mblobs != null) {
            for(MailboxBlob mblob : mblobs) {
                StoreManager.getReaderSMInstance(mblob.getLocator()).quietDelete(mblob);
            }
        }
    }

    private boolean isLocalMailbox(String accountId) throws ServiceException {
        var account = Provisioning.getInstance().get(Key.AccountBy.id, accountId);
        if (account == null) {
            Log.openhsm.warn("Unable to look up account %s.", accountId);
            return false;
        } else {
            return Provisioning.onLocalServer(account);
        }
    }

    private void validateVolume(short volumeId) throws ServiceException {
        var vol = VolumeManager.getInstance().getVolume(volumeId);
        if (vol.getType() != 1 && vol.getType() != 2) {
            throw ServiceException.FAILURE("Volume is invalid: " + vol, null);
        }
    }
}
