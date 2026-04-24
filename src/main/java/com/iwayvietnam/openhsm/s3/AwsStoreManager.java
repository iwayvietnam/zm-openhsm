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
package com.iwayvietnam.openhsm.s3;

import com.iwayvietnam.openhsm.config.PropertiesConfiguration;
import com.iwayvietnam.openhsm.locator.LocatorUtil;
import com.iwayvietnam.openhsm.locator.S3Locator;
import com.iwayvietnam.openhsm.util.Log;
import com.zimbra.common.service.ServiceException;
import com.zimbra.cs.mailbox.Mailbox;
import com.zimbra.cs.store.external.ExternalStoreManager;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Aws Store Manager
 * @author Nguyen Van Nguyen <nguyennv1981@gmail.com>
 */
public class AwsStoreManager extends ExternalStoreManager  {
    private S3Client client;
    private final Set<String> bucketNames = new HashSet<String>();

    @Override
    public void startup() throws IOException, ServiceException {
        Log.openhsm.info("Starting up Aws Store Manager");
        super.startup();
        var config = PropertiesConfiguration.getInstance();
        try {
            var credentials = AwsBasicCredentials.create(config.getAccessKey(), config.getSecretKey());
            client = S3Client.builder()
                    .endpointOverride(new URI(config.getEndpoint()))
                    .credentialsProvider(StaticCredentialsProvider.create(credentials))
                    .serviceConfiguration(S3Configuration.Builder::pathStyleAccessEnabled)
                    .region(Region.AWS_GLOBAL)
                    .build();
        } catch (URISyntaxException e) {
            throw ServiceException.RESOURCE_UNREACHABLE("Invalid endpoint specified", e);
        }
        fillBucketNames();
    }

    @Override
    public void shutdown() {
        Log.openhsm.info("Shutting down Aws Store Manager");
        super.shutdown();
        bucketNames.clear();
        try {
            client.close();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        client = null;
    }

    @Override
    public String writeStreamToStore(InputStream in, long actualSize, Mailbox mailbox) throws IOException, ServiceException {
        Log.openhsm.debug(String.format(
            "writeStreamToStore - start: actualSize - %s, accountId - %s", actualSize, mailbox.getAccountId()
        ));
        var locator = LocatorUtil.generateLocator(mailbox);

        createBucketAsNeeded(locator.getBucketName());

        client.putObject(
            req -> {
                req.bucket(locator.getBucketName()).key(locator.getKey());
            },
            RequestBody.fromInputStream(in, actualSize)
        );

        String stringLocator = LocatorUtil.toStringLocator(locator);
        Log.openhsm.debug(String.format(
            "writeStreamToStore() - end: locator - %s", stringLocator)
        );
        return stringLocator;
    }

    @Override
    public InputStream readStreamFromStore(String locator, Mailbox mailbox) throws IOException {
        Log.openhsm.debug(String.format(
            "readStreamFromStore() - start: locator - %s, accountId - %s", locator, mailbox.getAccountId()
        ));

        var el = LocatorUtil.fromStringLocator(locator);
        Log.openhsm.debug(String.format(
            "readStreamFromStore() - reading: bucket - %s, key - %s", el.getBucketName(), el.getKey())
        );

        return client.getObject(
            req -> {
                req.bucket(el.getBucketName()).key(el.getKey());
            }
        );
    }

    @Override
    public boolean deleteFromStore(String locator, Mailbox mailbox) throws IOException {
        Log.openhsm.debug(String.format(
            "deleteFromStore() - start: locator - %s, accountId - %s", locator, mailbox.getAccountId())
        );
        var el = LocatorUtil.fromStringLocator(locator);
        Log.openhsm.debug(String.format(
            "deleteFromStore() - deleting: bucket - %s, key - %s", el.getBucketName(), el.getKey())
        );

        client.deleteObject(req -> {
            req.bucket(el.getBucketName()).key(el.getKey());
        });
        return true;
    }

    @Override
    public List<String> getAllBlobPaths(Mailbox mbox) throws IOException {
        var bucketName = LocatorUtil.getBucketName(mbox);
        var paths = new ArrayList<String>();
        if (bucketNames.contains(bucketName)) {
            var results = client.listObjects(req -> {
                req.bucket(bucketName);
            });
            for (var result : results.contents()) {
                var locator = new S3Locator(bucketName, result.key());
                paths.add(LocatorUtil.toStringLocator(locator));
            }
        }

        return paths;
    }

    @Override
    public boolean supports(StoreFeature feature) {
        if (feature == StoreFeature.CENTRALIZED) {
            return true;
        } else {
            return super.supports(feature);
        }
    }

    private void createBucketAsNeeded(String bucketName) {
        if (!bucketNames.contains(bucketName)) {
            client.createBucket(req -> {
                req.bucket(bucketName);
            });
            bucketNames.add(bucketName);
        }
    }

    private void fillBucketNames()
    {
        var bucketNameBase = LocatorUtil.getBucketNameBase();
        bucketNames.clear();
        for (var bucket : client.listBuckets().buckets()) {
            if (bucket.name().startsWith(bucketNameBase)) {
                bucketNames.add(bucket.name());
            }
        }
    }
}
