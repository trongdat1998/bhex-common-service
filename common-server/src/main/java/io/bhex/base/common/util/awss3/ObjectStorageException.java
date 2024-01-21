/**********************************
 *@项目名称: broker-parent
 *@文件名称: io.bhex.broker.common.objectstorage
 *@Date 2018/7/6
 *@Author peiwei.ren@bhex.io 
 *@Copyright（C）: 2018 BlueHelix Inc.   All rights reserved.
 *注意：本内容仅限于内部传阅，禁止外泄以及用于其他的商业目的。
 ***************************************/
package io.bhex.base.common.util.awss3;

public class ObjectStorageException extends RuntimeException {

    public ObjectStorageException() {
    }

    public ObjectStorageException(String message) {
        super(message);
    }

    public ObjectStorageException(String message, Throwable cause) {
        super(message, cause);
    }

    public ObjectStorageException(Throwable cause) {
        super(cause);
    }

}
