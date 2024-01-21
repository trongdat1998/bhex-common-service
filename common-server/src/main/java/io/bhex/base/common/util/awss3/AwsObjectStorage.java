/**********************************
 *@项目名称: broker-parent
 *@文件名称: io.bhex.broker.common.objectstorage
 *@Date 2018/7/18
 *@Author peiwei.ren@bhex.io 
 *@Copyright（C）: 2018 BlueHelix Inc.   All rights reserved.
 *注意：本内容仅限于内部传阅，禁止外泄以及用于其他的商业目的。
 ***************************************/
package io.bhex.base.common.util.awss3;

import com.amazonaws.AmazonClientException;
import com.amazonaws.AmazonServiceException;
import com.amazonaws.ClientConfiguration;
import com.amazonaws.Protocol;
import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import com.amazonaws.services.s3.model.*;
import com.google.common.base.Preconditions;
import com.google.common.base.Throwables;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.io.ByteStreams;
import com.google.common.net.MediaType;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.util.Iterator;
import java.util.List;

public class AwsObjectStorage{

    private final ImmutableList<String> urlPrefixList;
    private final String bucket;
    private final AmazonS3 amazonS3;

    public AwsObjectStorage(
            List<String> urlPrefixList,
            String bucket,
            String accessKey,
            String secretKey,
            String regionName) {
        // Preconditions.checkArgument(urlPrefixList != null && !urlPrefixList.isEmpty(), "urlPrefixList is empty");
        Preconditions.checkArgument(bucket != null && !bucket.isEmpty(), "bucket is empty");
        Preconditions.checkArgument(accessKey != null && !accessKey.isEmpty(), "accessKey is empty");
        Preconditions.checkArgument(secretKey != null && !secretKey.isEmpty(), "secretKey is empty");
        Preconditions.checkArgument(regionName != null && !regionName.isEmpty(), "regionName is empty");
        this.urlPrefixList = ImmutableList.copyOf(urlPrefixList);
        this.bucket = bucket;
        AWSCredentials credentials = new BasicAWSCredentials(accessKey, secretKey);
        ClientConfiguration clientConfig = new ClientConfiguration();
        clientConfig.setProtocol(Protocol.HTTP);
        this.amazonS3 = AmazonS3ClientBuilder.standard()
                .withClientConfiguration(clientConfig)
                .withCredentials(new AWSStaticCredentialsProvider(credentials))
                .withRegion(regionName)
                .build();
    }


    public ImmutableList<String> urlPrefixList() {
        return this.urlPrefixList;
    }


    public void uploadObject(String key, MediaType contentType, File file) throws ObjectStorageException {
        Preconditions.checkArgument(key != null && !key.isEmpty(), "limitKey is empty");
        Preconditions.checkNotNull(contentType, "contentType is empty");
        Preconditions.checkNotNull(file, "file is empty");
        try {
            com.amazonaws.services.s3.model.ObjectMetadata objectMetadata = new com.amazonaws.services.s3.model.ObjectMetadata();
            objectMetadata.setContentType(contentType.toString().replace(" ", ""));
            objectMetadata.setContentLength(file.length());
            this.amazonS3.putObject(new PutObjectRequest(this.bucket, key, file).withMetadata(objectMetadata));
        } catch (AmazonClientException e) {
            throw new ObjectStorageException(e);
        } catch (Throwable th) {
            Throwables.throwIfUnchecked(th);
            throw new ObjectStorageException(th);
        }
    }


    public void uploadObject(String key, MediaType contentType, byte[] data) throws ObjectStorageException {
        Preconditions.checkArgument(key != null && !key.isEmpty(), "limitKey is empty");
        Preconditions.checkNotNull(contentType, "contentType is empty");
        Preconditions.checkNotNull(data, "data is empty");
        try {
            com.amazonaws.services.s3.model.ObjectMetadata objectMetadata = new com.amazonaws.services.s3.model.ObjectMetadata();
            objectMetadata.setContentType(contentType.toString().replace(" ", ""));
            objectMetadata.setContentLength(data.length);
            this.amazonS3.putObject(this.bucket, key, new ByteArrayInputStream(data), objectMetadata);
        } catch (AmazonClientException e) {
            throw new ObjectStorageException(e);
        } catch (Throwable th) {
            Throwables.throwIfUnchecked(th);
            throw new ObjectStorageException(th);
        }
    }


    public void downloadObject(String key, File file) throws ObjectStorageException {
        Preconditions.checkArgument(key != null && !key.isEmpty(), "limitKey is empty");
        Preconditions.checkNotNull(file, "file is empty");
        try {
            S3Object cosObject = this.amazonS3.getObject(this.bucket, key);
            try (OutputStream outputStream = new FileOutputStream(file)) {
                ByteStreams.copy(cosObject.getObjectContent(), outputStream);
            }
        } catch (AmazonServiceException e) {
            if (e.getStatusCode() == 404) {
                throw new ObjectStorageNotFoundException("limitKey not found : " + key, e);
            } else {
                throw new ObjectStorageException(e);
            }
        } catch (AmazonClientException e) {
            throw new ObjectStorageException(e);
        } catch (Throwable th) {
            Throwables.throwIfUnchecked(th);
            throw new ObjectStorageException(th);
        }
    }


    public byte[] downloadObject(String key) throws ObjectStorageException {
        Preconditions.checkArgument(key != null && !key.isEmpty(), "limitKey is empty");
        try {
            S3Object s3Object = this.amazonS3.getObject(this.bucket, key);
            return ByteStreams.toByteArray(s3Object.getObjectContent());
        } catch (AmazonServiceException e) {
            if (e.getStatusCode() == 404) {
                throw new ObjectStorageNotFoundException("limitKey not found : " + key, e);
            } else {
                throw new ObjectStorageException(e);
            }
        } catch (AmazonClientException e) {
            throw new ObjectStorageException(e);
        } catch (Throwable th) {
            Throwables.throwIfUnchecked(th);
            throw new ObjectStorageException(th);
        }
    }


    public Iterator<ObjectMetadata> listObjectMetadata(String keyPrefix) throws ObjectStorageException {
        Preconditions.checkNotNull(keyPrefix, "keyPrefix");
        return new Iterator<ObjectMetadata>() {

            private ObjectListing objectListing = null;
            private int index = 0;

            @Override
            public boolean hasNext() {
                if (this.objectListing == null) {
                    this.doGetObjectListing();
                }
                return this.index < this.objectListing.getObjectSummaries().size();
            }

            @Override
            public ObjectMetadata next() {
                if (this.objectListing == null) {
                    this.doGetObjectListing();
                }
                if (this.index >= this.objectListing.getObjectSummaries().size()) {
                    throw new IndexOutOfBoundsException();
                }
                ObjectMetadata next = toObjectMetadata(this.objectListing.getObjectSummaries().get(this.index));
                this.index++;
                if (this.index >= this.objectListing.getObjectSummaries().size() && this.objectListing.isTruncated()) {
                    this.doGetObjectListing();
                }
                return next;
            }

            private void doGetObjectListing() {
                try {
                    String marker = this.objectListing == null ? "" : this.objectListing.getNextMarker();
                    this.objectListing = amazonS3.listObjects(new ListObjectsRequest(bucket, keyPrefix, marker, "", 1000));
                    this.index = 0;
                } catch (AmazonClientException e) {
                    throw new ObjectStorageException(e);
                } catch (Throwable th) {
                    Throwables.throwIfUnchecked(th);
                    throw new ObjectStorageException(th);
                }
            }
        };
    }


    public ObjectMetadata getObjectMetadata(String key) throws ObjectStorageException {
        Preconditions.checkArgument(key != null && !key.isEmpty(), "limitKey is empty");
        try {
            return toObjectMetadata(key, this.amazonS3.getObjectMetadata(this.bucket, key));
        } catch (AmazonServiceException e) {
            if (e.getStatusCode() == 404) {
                throw new ObjectStorageNotFoundException("limitKey not found : " + key, e);
            } else {
                throw new ObjectStorageException(e);
            }
        } catch (AmazonClientException e) {
            throw new ObjectStorageException(e);
        } catch (Throwable th) {
            Throwables.throwIfUnchecked(th);
            throw new ObjectStorageException(th);
        }
    }


    public void shutdown() {
        this.amazonS3.shutdown();
    }

    private static ObjectMetadata toObjectMetadata(S3ObjectSummary summary) {
        return new ObjectMetadata(summary.getKey(), summary.getSize(), summary.getLastModified().getTime(), null);
    }

    private static ObjectMetadata toObjectMetadata(String key, com.amazonaws.services.s3.model.ObjectMetadata metadata) {
        return new ObjectMetadata(key, metadata.getContentLength(), metadata.getLastModified().getTime(), MediaType.parse(metadata.getContentType()));
    }

    public static AwsObjectStorageBuilder newBuilder() {
        return new AwsObjectStorageBuilder();
    }



}
