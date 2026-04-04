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
package com.iwayvietnam.zms3.util;

import org.apache.commons.cli.Options;

/**
 * Migrate CLI
 * @author Nguyen Van Nguyen <nguyennv1981@gmail.com>
 */
public class MigrateCLI {
    private static final String OPT_MAILBOX = "mailbox";
    private static final String OPT_STOP_ON_ERROR = "stopOnError";
    private static final String OPT_HELP = "h";

    private static Options options = new Options();

    static {
        options.addOption(null, OPT_MAILBOX, true, "Mailbox for migration");
        options.addOption(null, OPT_STOP_ON_ERROR, false, "Stop migrate on any error");
        options.addOption(OPT_HELP, "help", false, "Show help (this output)");
    }

    public static void main(String[] args) {
    }
}
