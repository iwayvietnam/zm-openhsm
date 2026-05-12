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
import com.iwayvietnam.openhsm.util.DbHelper;
import com.iwayvietnam.openhsm.util.Log;
import com.zimbra.common.account.Key;
import com.zimbra.common.service.ServiceException;
import com.zimbra.common.util.ZimbraLog;
import com.zimbra.cs.account.Account;
import com.zimbra.cs.account.Provisioning;
import com.zimbra.cs.index.SortBy;
import com.zimbra.cs.index.ZimbraQueryResults;
import com.zimbra.cs.mailbox.*;
import com.zimbra.cs.store.MailboxBlob;
import com.zimbra.cs.store.StoreManager;
import com.zimbra.cs.store.external.ExternalStoreManager;
import com.zimbra.cs.store.file.FileBlobStore;
import org.apache.commons.io.IOUtils;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * External blob mover
 * @author Nguyen Van Nguyen <nguyennv1981@gmail.com>
 */
public class ExternalBlobMover implements BlobMover {
    private final int parallelismLevel = PropertiesConfiguration.getInstance().getHsmParallelismLevel();
    private final int batchSize = PropertiesConfiguration.getInstance().getHsmBatchSize();
    private final MoverStatus state = new MoverStatus();

    public ExternalBlobMover() {
    }

    @Override
    public MoverStatus moveBlobs(String query, Set<MailItem.Type> types, List<Short> srcVolumeIds, short destVolumeId, Long maxBytes, int mboxId, String accountId) throws ServiceException {
        state.setDestVolumeId(destVolumeId);
        Optional.ofNullable(maxBytes).ifPresent(state::setMaxBytes);

        final var executorService = Executors.newFixedThreadPool(this.parallelismLevel);
        final var dstStoreManager = StoreManager.getReaderSMInstance(destVolumeId);
        Log.openhsm.debug("Destination store manager: %s", dstStoreManager.getClass().getSimpleName());

        try {
            ZimbraLog.removeAccountFromContext();
            ZimbraLog.addMboxToContext(mboxId);
            state.setQuery(query);
            Account account;
            Mailbox mbox;
            try {
                if (!isLocalMailbox(accountId)) {
                    Log.openhsm.debug("Account ID %s is not local", accountId);
                    return state;
                }

                mbox = MailboxManager.getInstance().getMailboxById(mboxId);
                account = mbox.getAccount();
            } catch (ServiceException e) {
                Log.openhsm.error(e);
                return state;
            }

            ZimbraLog.addAccountNameToContext(account.getName());
            final var itemsForMovement = findItemsForMovement(query, types, srcVolumeIds, mbox);
            var fromIndex = 0;

            int toIndex;
            for(int totalSize = itemsForMovement.size(); fromIndex < totalSize; fromIndex = toIndex) {
                toIndex = fromIndex + this.batchSize;
                if (toIndex > totalSize) {
                    toIndex = totalSize;
                }

                var subList = itemsForMovement.subList(fromIndex, toIndex);
                var batchInfos = Collections.unmodifiableCollection(subList);
                var itemInfoIterator = batchInfos.iterator();

                while(itemInfoIterator.hasNext()) {
                    final var futureList = new ArrayList<Future<MovementStatus>>();

                    for(int count = 0; count < this.parallelismLevel && itemInfoIterator.hasNext(); ++count) {
                        final var movedItemInfo = itemInfoIterator.next();
                        final var srcVolumeId = movedItemInfo.getVolumeId();
                        StoreManager srcStoreManager = StoreManager.getReaderSMInstance(srcVolumeId);
                        futureList.add(executorService.submit(() -> this.moveBlob(destVolumeId, dstStoreManager, srcVolumeId, srcStoreManager, mbox, movedItemInfo)));
                    }

                    try {
                        for(var future : futureList) {
                            var movementStatus = future.get();
                            Log.openhsm.debug("ItemId: %s, movement Status: %s, from vol:%s to dest vol:%s", movementStatus.getMovedItem().getId(), movementStatus.getStatus(), movementStatus.getMovedItem().getVolumeId(), destVolumeId);
                        }
                    } catch (final InterruptedException | ExecutionException e) {
                        Log.openhsm.warn("Error while getting thread result", e);
                    }
                }

                if (state.wasAborted()) {
                    Log.openhsm.warn("Aborting blob mover session");
                    break;
                }
            }

            mbox.purge(MailItem.Type.UNKNOWN);
            return state;
        } finally {
            executorService.shutdown();
        }
    }

    private MovementStatus moveBlob(short dstVolumeId, StoreManager dstStoreManager, Short srcVolumeId, StoreManager srcStoreManager, Mailbox mbox, MovedItem item) {
        Log.openhsm.debug("Moving mail item: %s", item.toString());
        final var movementStatus = new MovementStatus(item, Status.FAIL);
        try {
            final var oldMessageBlob = srcStoreManager.getMailboxBlob(mbox, item.getId(), item.getModifyContent(), item.getLocator(), true);
            final var oldBlobInputStream = srcStoreManager.getContent(oldMessageBlob);
            if (oldBlobInputStream == null) {
                return movementStatus;
            }
            final var oldBlobBytes = IOUtils.toByteArray(oldBlobInputStream);
            final var oldBlobActualSize = (long)oldBlobBytes.length;
            IOUtils.closeQuietly(oldBlobInputStream);
            final var inputStream = new ByteArrayInputStream(oldBlobBytes);

            try {
                String locator;
                if (dstStoreManager instanceof FileBlobStore fileBlobStore) {
                    fileBlobStore.storeIncoming(inputStream, true, mbox, item.getId(), item.getModifyContent(), dstVolumeId);
                    locator = Short.toString(dstVolumeId);
                } else if (dstStoreManager instanceof ExternalStoreManager externalStoreManager) {
                    locator = externalStoreManager.writeStreamToStore(inputStream, oldBlobActualSize, mbox, dstVolumeId);
                } else {
                    final var newBlob = dstStoreManager.stage(inputStream, oldBlobActualSize, mbox);
                    locator = newBlob.getLocator();
                }
                Log.openhsm.debug("New blob locator: %s", locator);
                if (state.getNumBytesMoved() + oldBlobActualSize <= state.getMaxBytes()) {
                    state.incrementNumBytesMoved(oldBlobActualSize);
                    if (deleteOldBlobs(srcStoreManager, mbox, item, oldMessageBlob, locator)) {
                        state.incrementNumMoved(1);
                        movementStatus.setStatus(Status.SUCCESS);
                    }
                } else {
                    Log.openhsm.info("Exceeded limit of %s bytes", state.getMaxBytes());
                    state.setShouldAbort(true);
                }
            } catch (final IOException e) {
                Log.openhsm.error("Unable to put blob for item %s to volume %s", item.getId(), dstVolumeId, e);
            }
        } catch (final ServiceException | IOException e) {
            Log.openhsm.error("Unable to get blob for item %s from volume %s", item.getId(), srcVolumeId, e);
        }
        return movementStatus;
    }

    private static List<MovedItem> findItemsForMovement(String query, Set<MailItem.Type> types, List<Short> srcVolumeIds, Mailbox mbox) {
        final var items = new ArrayList<MovedItem>();
        for(boolean fromDumpster : new boolean[]{false, true}) {
            final var itemIds = new ArrayList<Integer>();
            try (ZimbraQueryResults results = mbox.index.search(new OperationContext(mbox), query, types, SortBy.NONE, 10000, fromDumpster)) {
                while(results.hasNext()) {
                    itemIds.add(results.getNext().getItemId());
                }
            } catch (Exception e) {
                Log.openhsm.debug("Failed to get search results");
                continue;
            }
            extractItemsFromDb(srcVolumeIds, mbox, items, fromDumpster, itemIds);
        }
        return items;
    }

    private static void extractItemsFromDb(List<Short> sourceVolumeIds, Mailbox mbox, List<MovedItem> items, boolean fromDumpster, List<Integer> itemIds) {
        try {
            if (!itemIds.isEmpty()) {
                items.addAll(DbHelper.getItems(mbox, itemIds, sourceVolumeIds, fromDumpster));
                items.addAll(DbHelper.getItemsRevisions(mbox, itemIds, sourceVolumeIds, fromDumpster));
            }
        } catch (ServiceException e) {
            Log.openhsm.debug("Failed to get mail items information", e);
        }
    }

    private static boolean isLocalMailbox(String accountId) throws ServiceException {
        var account = Provisioning.getInstance().get(Key.AccountBy.id, accountId);
        if (account == null) {
            Log.openhsm.warn("Unable to look up account %s.", accountId);
            return false;
        } else {
            return Provisioning.onLocalServer(account);
        }
    }

    private static boolean deleteOldBlobs(StoreManager srcStoreManager, Mailbox mbox, MovedItem info, MailboxBlob oldMessageBlob, String locator) throws IOException {
        try {
            mbox.lock.lock();
            DbHelper.alterVolume(mbox, info, locator);
            srcStoreManager.delete(oldMessageBlob);
            MessageCache.purge(info.getBlobDigest());
            return true;
        } catch (ServiceException e) {
            Log.openhsm.error(e);
        } finally {
            mbox.lock.release();
        }

        return false;
    }
}
