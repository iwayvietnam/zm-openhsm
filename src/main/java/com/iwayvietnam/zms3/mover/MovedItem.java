package com.iwayvietnam.zms3.mover;

import com.zimbra.cs.store.MailboxBlob;

public class MovedItem {
    private final int id;

    private final short volumeId;

    private final int modifyContent;

    private final String blobDigest;

    private final MailboxBlob oldBlob;

    private final MailboxBlob newBlob;

    private final boolean fromDumpster;

    private final boolean fromRevision;

    private final String locator;

    public MovedItem(
        int id,
        short volumeId,
        int modifyContent,
        String blobDigest,
        MailboxBlob oldBlob,
        MailboxBlob newBlob,
        boolean fromDumpster,
        boolean fromRevision,
        String locator
    ) {
        this.id = id;
        this.volumeId = volumeId;
        this.modifyContent = modifyContent;
        this.blobDigest = blobDigest;
        this.oldBlob = oldBlob;
        this.newBlob = newBlob;
        this.fromDumpster = fromDumpster;
        this.fromRevision = fromRevision;
        this.locator = locator;
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

    public MailboxBlob getNewBlob() {
        return newBlob;
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
}
