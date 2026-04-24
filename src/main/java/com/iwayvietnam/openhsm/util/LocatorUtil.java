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
package com.iwayvietnam.openhsm.util;

import com.iwayvietnam.openhsm.config.Configuration;
import com.iwayvietnam.openhsm.config.PropertiesConfiguration;
import com.iwayvietnam.openhsm.config.SettingsConstants;
import com.iwayvietnam.openhsm.s3.S3Locator;
import com.zimbra.cs.mailbox.Mailbox;

import java.util.UUID;

/**
 * Locator Util
 * @author Nguyen Van Nguyen <nguyennv1981@gmail.com>
 */
public class LocatorUtil {
    public static S3Locator generateLocator(Mailbox mbox) {
        return new S3Locator(
                getBucketName(mbox),
                UUID.randomUUID().toString()
        );
    }

    public static String toStringLocator(S3Locator locator) {
        return String.format("%s/%s", locator.getBucketName(), locator.getKey());
    }

    public static S3Locator fromStringLocator(String locator) {
        String[] locatorParts = locator.split("/", 2);
        if (locatorParts.length >= 2)
            return new S3Locator(locatorParts[0], locatorParts[1]);
        else
            throw new IllegalArgumentException("Invalid locator String");
    }

    public static String getBucketName(Mailbox mbox) {
        return String.format("%s.%s", getBucketNameBase(), mbox.getAccountId());
    }

    public static String getBucketNameBase() {
        return String.format("%s.%s", SettingsConstants.ZIMBRA, getConfiguration().getStoreName());
    }

    private static Configuration getConfiguration(){
        return PropertiesConfiguration.getInstance();
    }
}

