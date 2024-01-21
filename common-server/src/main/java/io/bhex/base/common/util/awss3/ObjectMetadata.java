/**********************************
 *@项目名称: broker-parent
 *@文件名称: io.bhex.broker.common.objectstorage
 *@Date 2018/7/6
 *@Author peiwei.ren@bhex.io 
 *@Copyright（C）: 2018 BlueHelix Inc.   All rights reserved.
 *注意：本内容仅限于内部传阅，禁止外泄以及用于其他的商业目的。
 ***************************************/
package io.bhex.base.common.util.awss3;

import com.google.common.net.MediaType;

import javax.annotation.CheckForNull;
import javax.annotation.Nullable;

public class ObjectMetadata {

    private final String key;
    private final long size;
    private final long uploadTime;
    private final MediaType contentType;

    public ObjectMetadata(String key, long size, long uploadTime, @Nullable MediaType contentType) {
        this.key = key;
        this.size = size;
        this.uploadTime = uploadTime;
        this.contentType = contentType;
    }

    /**
     * Object Storage limitKey，eg："http://xxx.com/test.jpg", "test.jpg"is limitKey。cannot begin with "/"
     */
    public String key() {
        return key;
    }

    /**
     * object size，unit：Byte
     */
    public long size() {
        return size;
    }

    /**
     * upload timestamp，unit：mills。if object upload many times with same limitKey, updateTime is last upload time
     */
    public long uploadTime() {
        return uploadTime;
    }

    /**
     * upload object content type。reference{@link MediaType}
     * note：if use qcolud, when invoke {@link io.bhex.broker.common.objectstorage.ObjectStorage#listObjectMetadata(String)} to get this limiterValue，this limiterValue is null
     */
    @CheckForNull
    public MediaType contentType() {
        return contentType;
    }

    @Override
    public String toString() {
        return "ObjectMetadata{" +
                "limitKey='" + key + '\'' +
                ", size='" + size + '\'' +
                ", uploadTime=" + uploadTime +
                ", contentType=" + contentType +
                '}';
    }

}
