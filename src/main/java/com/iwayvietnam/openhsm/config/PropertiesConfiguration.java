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
package com.iwayvietnam.openhsm.config;

import com.zimbra.common.localconfig.LC;
import com.zimbra.common.util.StringUtil;
import com.zimbra.common.util.ZimbraLog;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

/**
 * Properties Configuration
 * @author Nguyen Van Nguyen <nguyennv1981@gmail.com>
 */
public class PropertiesConfiguration implements Configuration {

    private static final Map<String, String> properties = new HashMap<>();

    private final String endpoint;
    private final String accessKey;
    private final String secretKey;
    private final String storeName;
    private final int deleteThreads;

    private PropertiesConfiguration()
    {
        loadSettingsFromProperties();

        endpoint = loadStringProperty(SettingsConstants.ZM_S3_ENDPOINT);
        accessKey = loadStringProperty(SettingsConstants.ZM_S3_ACCESS_KEY);
        secretKey = loadStringProperty(SettingsConstants.ZM_S3_SECRET_KEY);
        storeName = loadStringProperty(SettingsConstants.ZM_S3_STORE_NAME);
        deleteThreads = loadIntProperty(SettingsConstants.ZM_S3_DELETE_THREADS);
    }

    private static final class InstanceHolder {
        private static final Configuration instance = new PropertiesConfiguration();
    }

    public static Configuration getInstance() {
        return InstanceHolder.instance;
    }

    @Override
    public String getEndpoint() {
        return endpoint;
    }

    @Override
    public String getAccessKey() {
        return accessKey;
    }

    @Override
    public String getSecretKey() {
        return secretKey;
    }

    @Override
    public String getStoreName() {
        return storeName;
    }

    @Override
    public int getDeleteThreads() {
        return deleteThreads;
    }

    private static String loadStringProperty(final String key) {
        return properties.get(key);
    }

    private static int loadIntProperty(final String key) {
        final var value = properties.get(key);
        if (!StringUtil.isNullOrEmpty(value)) {
            return Integer.parseInt((value).trim());
        }
        return 0;
    }

    private static void loadSettingsFromProperties() {
        ZimbraLog.extensions.info("Load config properties");
        try {
            final var confDir = Paths.get(LC.zimbra_home.value(), "conf").toString();
            final var prop = new Properties();
            prop.load(new FileInputStream(confDir + "/" + SettingsConstants.ZM_S3_CONFIG_FILE));
            prop.stringPropertyNames().forEach(key -> properties.put(key, prop.getProperty(key)));
        } catch (IOException e) {
            ZimbraLog.extensions.error(e);
        }
    }
}
