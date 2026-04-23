/*
 * ***** BEGIN LICENSE BLOCK *****
 * Zm S3 is the ECS S3 compatible store extension for Zimbra Collaboration Open Source Edition..
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
 * Zimbra S3
 *
 * Written by Nguyen Van Nguyen <nguyennv1981@gmail.com>
 */
package com.iwayvietnam.openhsm.s3;

import com.zimbra.common.util.ZimbraLog;
import com.zimbra.cs.mailbox.Mailbox;
import com.zimbra.cs.store.StoreManager;
import com.zimbra.cs.store.external.ExternalMailboxBlob;

/**
 * Minio Mailbox Blob
 * @author Nguyen Van Nguyen <nguyennv1981@gmail.com>
 */
public class MinioMailboxBlob extends ExternalMailboxBlob {
    protected MinioMailboxBlob(Mailbox mbox, int itemId, int revision, String locator) {
        super(mbox, itemId, revision, locator);
    }

    @Override
    public boolean validateBlob() {
        boolean status = false;

        try {
            status = ((MinioStoreManager) StoreManager.getInstance()).validate(getLocator(), getMailbox());
        } catch (Exception e) {
            ZimbraLog.store.warn(String.format("Failed to validate - %s", getLocator()), e);
        }

        return status;
    }
}
