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
package com.iwayvietnam.openhsm.cli;

import org.apache.commons.cli.Options;

/**
 * Move Blobs CLI
 * @author Nguyen Van Nguyen <nguyennv1981@gmail.com>
 */
public class MoveBlobsCLI {
    private static final String OPT_SID = "sid";
    private static final String OPT_DID = "did";
    private static final String OPT_TYPE = "t";
    private static final String OPT_QUERY = "q";
    private static final String OPT_HELP = "h";

    private static Options options = new Options();

    static {
        options.addOption(OPT_SID, "source", true, "Source volume Id");
        options.addOption(OPT_DID, "destination", false, "Destination volume Id");
        options.addOption(OPT_TYPE, "types", false, "Comma-separated list of item types or 'all'");
        options.addOption(OPT_QUERY, "query", false, "Query parameters (default: 'is:anywhere')");
        options.addOption(OPT_HELP, "help", false, "Show help (this output)");
    }

    public static void main(String[] args) {
    }
}
