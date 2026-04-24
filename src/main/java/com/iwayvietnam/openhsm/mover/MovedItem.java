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

import com.zimbra.cs.store.MailboxBlob;

/**
 * Moved item
 * @author Nguyen Van Nguyen <nguyennv1981@gmail.com>
 */
public class MovedItem {
    private final int id;

    private final short volumeId;

    private final int modifyContent;

    private final String blobDigest;

    private MailboxBlob oldBlob;

    private MailboxBlob newBlob;

    private final boolean fromDumpster;

    private final boolean fromRevision;

    private String locator;

    public MovedItem(
        int id,
        short volumeId,
        int modifyContent,
        String blobDigest,
        MailboxBlob oldBlob,
        MailboxBlob newBlob,
        boolean fromDumpster,
        boolean fromRevision
    ) {
        this.id = id;
        this.volumeId = volumeId;
        this.modifyContent = modifyContent;
        this.blobDigest = blobDigest;
        this.fromDumpster = fromDumpster;
        this.fromRevision = fromRevision;
    }

    public int getId() {
        return id;
    }

    public short getVolumeId() {
        return volumeId;
    }

    public int getModifyContent() {
        return modifyContent;
    }

    public String getBlobDigest() {
        return blobDigest;
    }

    public MailboxBlob getOldBlob() {
        return oldBlob;
    }
    public void setOldBlob(MailboxBlob oldBlob) {
        this.oldBlob = oldBlob;
    }

    public MailboxBlob getNewBlob() {
        return newBlob;
    }

    public void setNewBlob(MailboxBlob newBlob) {
        this.newBlob = newBlob;
    }

    public boolean fromDumpster() {
        return fromDumpster;
    }

    public boolean fromRevision() {
        return fromRevision;
    }

    public String getLocator() {
        return this.locator;
    }

    public void setLocator(String locator) {
        this.locator = locator;
    }

    public String toString() {
        return String.format("MovedItem: {id=%d, vol=%d, modContent=%d, digest=%s, dumpster=%s, revision=%s, locator=%s}", id, volumeId, modifyContent, blobDigest, Boolean.toString(fromDumpster), Boolean.toString(fromRevision), this.locator);
    }
}
