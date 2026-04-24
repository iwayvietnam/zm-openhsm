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

import java.util.Date;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * Mover State
 * @author Nguyen Van Nguyen <nguyennv1981@gmail.com>
 */
public class MoverState {
    private final Date startDate = new Date();
    private volatile Date endDate = null;
    private volatile int totalMailboxes = 0;
    private volatile int numBlobsMoved = 0;
    private final Set<Integer> mailboxMoved = new CopyOnWriteArraySet<>();
    private final Set<Integer> mailboxToMove = new CopyOnWriteArraySet<>();
    private volatile boolean wasAborted = false;
    private volatile boolean shouldAbort = false;
    private volatile Throwable error = null;
    private volatile short destVolumeId = -1;
    private volatile long maxBytes = Long.MAX_VALUE;
    private volatile long numBytesMoved = 0L;
    private volatile String query = null;

    public Date getStartDate() {
        return startDate;
    }

    public Date getEndDate() {
        return endDate;
    }

    public void setEndDate() {
        endDate = new Date();
    }

    public short getDestVolumeId() {
        return destVolumeId;
    }

    public void setDestVolumeId(short volumeId) {
        destVolumeId = volumeId;
    }

    public int getNumMailboxes() {
        return mailboxMoved.size();
    }

    public int getNumBlobsMoved() {
        return numBlobsMoved;
    }

    public int getTotalMailboxes() {
        return totalMailboxes;
    }

    public void setTotalMailboxes(int totalMailboxes) {
        this.totalMailboxes = totalMailboxes;
    }

    public String getQuery() {
        return query;
    }

    public void setQuery(String query) {
        this.query = query;
    }

    public boolean wasAborted() {
        return wasAborted;
    }

    public boolean shouldAbort() {
        return shouldAbort;
    }

    public Throwable getError() {
        return error;
    }

    public void setError(Throwable error) {
        this.error = error;
    }

    public void incrementNumMoved(int numMoved) {
        numBlobsMoved += numMoved;
    }

    public void setShouldAbort(boolean shouldAbort) {
        this.shouldAbort = shouldAbort;
    }

    public void setWasAborted(boolean wasAborted) {
        this.wasAborted = wasAborted;
    }

    public void setMaxBytes(long maxBytes) {
        this.maxBytes = maxBytes;
    }

    public long getMaxBytes() {
        return this.maxBytes;
    }

    public void incrementNumBytesMoved(long numBytes) {
        numBytesMoved += numBytes;
    }

    public long getNumBytesMoved() {
        return numBytesMoved;
    }

    public void addMovedMailboxId(int id) {
        this.mailboxMoved.add(id);
    }

    public Set<Integer> getMailboxMoved() {
        return this.mailboxMoved;
    }

    public Set<Integer> getMailboxToMove() {
        return this.mailboxToMove;
    }

    public void addMailboxToMove(Set<Integer> mailboxToMove) {
        this.mailboxToMove.addAll(mailboxToMove);
    }
}
