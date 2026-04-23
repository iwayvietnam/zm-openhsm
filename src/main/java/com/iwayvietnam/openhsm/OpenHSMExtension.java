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
package com.iwayvietnam.openhsm;

import com.zimbra.common.service.ServiceException;
import com.zimbra.common.util.ZimbraLog;
import com.zimbra.cs.extension.ExtensionException;
import com.zimbra.cs.extension.ZimbraExtension;

/**
 * Zimbra S3
 * @author Nguyen Van Nguyen <nguyennv1981@gmail.com>
 */
public class OpenHSMExtension implements ZimbraExtension {
    public static final String EXTENSION_NAME = "s3-store-manager";
    @Override

    public String getName() {
        return EXTENSION_NAME;
    }

    @Override
    public void init() throws ExtensionException, ServiceException {
        ZimbraLog.store.info("S3: initializing S3 Store Manager Extension");
    }

    @Override
    public void destroy() {
        ZimbraLog.store.info("S3: destroying S3 Store Manager Extension");
    }
}
