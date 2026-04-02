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
package com.iwayvietnam.zms3.minio;

import com.iwayvietnam.zms3.config.PropertiesConfiguration;
import com.iwayvietnam.zms3.locator.LocatorUtil;
import com.iwayvietnam.zms3.locator.S3Locator;
import com.zimbra.common.service.ServiceException;
import com.zimbra.common.util.ZimbraLog;
import com.zimbra.cs.mailbox.Mailbox;
import com.zimbra.cs.store.MailboxBlob;
import com.zimbra.cs.store.external.ExternalStoreManager;
import io.minio.*;
import io.minio.errors.MinioException;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * Minio Store Manager
 * @author Nguyen Van Nguyen <nguyennv1981@gmail.com>
 */
public class MinioStoreManager extends ExternalStoreManager {
    private MinioClient client;
    private ThreadPoolExecutor executor;
    private final Set<String> bucketNames = new HashSet<String>();

    @Override
    public void startup() throws IOException, ServiceException {
        ZimbraLog.store.info("Starting up Minio Store Manager");
        super.startup();
        var config = PropertiesConfiguration.getInstance();
        client = MinioClient.builder()
                .endpoint(config.getEndpoint())
                .credentials(config.getAccessKey(), config.getSecretKey())
                .build();
        executor = new ThreadPoolExecutor(config.getDeleteThreads(),
            config.getDeleteThreads(),
                0L,
            TimeUnit.MILLISECONDS,
            new ArrayBlockingQueue<>(config.getDeleteThreads() * 10)); // Blocking queue để cho request đợi
        try {
            fillBucketNames();
        } catch (MinioException e) {
            throw ServiceException.RESOURCE_UNREACHABLE(
                "Fill bucket names from Minio failed", e
            );
        }
    }

    @Override
    public void shutdown() {
        ZimbraLog.store.info("Shutting down Minio Store Manager");
        super.shutdown();
        executor.shutdown();
        bucketNames.clear();
        try {
            client.close();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        client = null;
        executor = null;
    }

    @Override
    public String writeStreamToStore(InputStream in, long actualSize, Mailbox mailbox) throws IOException, ServiceException {
        ZimbraLog.store.debug(String.format(
            "writeStreamToStore - start: actualSize - %s, accountId - %s", actualSize, mailbox.getAccountId()
        ));
        var locator = LocatorUtil.generateLocator(mailbox);

        try {
            createBucketAsNeeded(locator.getBucketName());
        } catch (MinioException e) {
            throw ServiceException.RESOURCE_UNREACHABLE(String.format(
                "Create %s bucket failed", locator.getBucketName()), e
            );
        }

        try {
            client.putObject(
                PutObjectArgs.builder()
                    .bucket(locator.getBucketName())
                    .object(locator.getKey())
                    .stream(in, (long) in.available(), -1L)
                    .build()
            );
        } catch (MinioException e) {
            throw ServiceException.RESOURCE_UNREACHABLE(String.format("Put %s to %s failed", locator.getKey(), locator.getBucketName()), e);
        }

        String stringLocator = LocatorUtil.toStringLocator(locator);
        ZimbraLog.store.debug(String.format("writeStreamToStore() - end: locator - %s", stringLocator));
        return stringLocator;
    }

    @Override
    public InputStream readStreamFromStore(String locator, Mailbox mailbox) throws IOException {
        ZimbraLog.store.debug(String.format(
            "readStreamFromStore() - start: locator - %s, accountId - %s", locator, mailbox.getAccountId()
        ));

        var el = LocatorUtil.fromStringLocator(locator);
        ZimbraLog.store.debug(String.format("Minio.readStreamFromStore() - reading: bucket - %s, key - %s", el.getBucketName(), el.getKey()));

        try {
            return client.getObject(
                GetObjectArgs.builder()
                    .bucket(el.getBucketName())
                    .object(el.getKey())
                    .build()
            );
        } catch (MinioException e) {
            throw new IOException(e);
        }
    }

    @Override
    public boolean deleteFromStore(String locator, Mailbox mailbox) throws IOException {
        ZimbraLog.store.debug(String.format("deleteFromStore() - start: locator - %s, accountId - %s", locator, mailbox.getAccountId()));
        var el = LocatorUtil.fromStringLocator(locator);
        ZimbraLog.store.debug(String.format(
            "deleteFromStore() - deleting: bucket - %s, key - %s", el.getBucketName(), el.getKey())
        );

        try {
            executor.submit(new Thread(() -> {
                try {
                    client.removeObject(
                        RemoveObjectArgs.builder()
                            .bucket(el.getBucketName())
                            .object(el.getKey())
                            .build()
                    );
                } catch (MinioException e) {
                    ZimbraLog.store.error(String.format(
                        "Failed to delete from - %s", locator), e
                    );
                }
            }));
        } catch (Exception e) {
            ZimbraLog.store.error(String.format("Failed to delete from - %s", locator), e);
            throw new IOException(e);
        }

        return true;
    }

    @Override
    public List<String> getAllBlobPaths(Mailbox mbox) throws IOException {
        var bucketName = LocatorUtil.getBucketName(mbox);
        var paths = new ArrayList<String>();
        if (bucketNames.contains(bucketName)) {
            var results = client.listObjects(
                ListObjectsArgs.builder().bucket(bucketName).build()
            );
            for (var result : results) {
                try {
                    var item = result.get();
                    var locator = new S3Locator(bucketName, item.objectName());
                    paths.add(LocatorUtil.toStringLocator(locator));
                } catch (MinioException e) {
                    throw new RuntimeException(e);
                }
            }
        }

        return paths;
    }

    @Override
    public MailboxBlob getMailboxBlob(Mailbox mbox, int itemId, int revision, String locator, boolean validate) throws ServiceException {
        var mblob = new MinioMailboxBlob(mbox, itemId, revision, locator);
        return (!validate || mblob.validateBlob()) ? mblob : null;
    }

    @Override
    public boolean supports(StoreFeature feature) {
        if (feature == StoreFeature.CENTRALIZED) {
            return true;
        } else {
            return super.supports(feature);
        }
    }

    public boolean validate(String locator, Mailbox mbox) throws Exception {
        ZimbraLog.store.debug(String.format("validate() - start: locator - %s, accountId - %s", locator, mbox.getAccountId()));
        var el = LocatorUtil.fromStringLocator(locator);
        return (null != client.statObject(
            StatObjectArgs.builder()
                .bucket(el.getBucketName())
                .object(el.getKey())
                .build())
        );
    }

    private void createBucketAsNeeded(String bucketName) throws MinioException {
        if (!bucketNames.contains(bucketName)) {
            client.makeBucket(MakeBucketArgs.builder().bucket(bucketName).build());
            bucketNames.add(bucketName);
        }
    }

    private void fillBucketNames() throws MinioException {
        var bucketNameBase = LocatorUtil.getBucketNameBase();
        bucketNames.clear();
        for (var bucket : client.listBuckets()) {
            if (bucket.name().startsWith(bucketNameBase)) {
                bucketNames.add(bucket.name());
            }
        }
    }
}
