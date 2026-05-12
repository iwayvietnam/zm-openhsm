package com.iwayvietnam.openhsm.util;

import com.iwayvietnam.openhsm.mover.MovedItem;
import com.zimbra.common.account.Key;
import com.zimbra.common.service.ServiceException;
import com.zimbra.cs.account.Provisioning;
import com.zimbra.cs.index.SortBy;
import com.zimbra.cs.index.ZimbraQueryResults;
import com.zimbra.cs.mailbox.MailItem;
import com.zimbra.cs.mailbox.Mailbox;
import com.zimbra.cs.mailbox.MessageCache;
import com.zimbra.cs.mailbox.OperationContext;
import com.zimbra.cs.store.MailboxBlob;
import com.zimbra.cs.store.StoreManager;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;

public class MailboxHelper {
    public static boolean isLocalMailbox(String accountId) throws ServiceException {
        var account = Provisioning.getInstance().get(Key.AccountBy.id, accountId);
        if (account == null) {
            Log.openhsm.warn("Unable to look up account %s.", accountId);
            return false;
        } else {
            return Provisioning.onLocalServer(account);
        }
    }

    public static void deleteBlobs(Collection<MailboxBlob> mblobs) {
        if (mblobs != null) {
            for(var mblob : mblobs) {
                StoreManager.getReaderSMInstance(mblob.getLocator()).quietDelete(mblob);
            }
        }
    }

    public static boolean deleteOldBlobs(StoreManager srcStoreManager, Mailbox mbox, MovedItem info, MailboxBlob oldMessageBlob, String locator) throws IOException {
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

    public static List<MovedItem> findItemsForMovement(String query, Set<MailItem.Type> types, List<Short> srcVolumeIds, Mailbox mbox) {
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
}
