package io.bhex.base.common.util;

import com.google.api.client.repackaged.com.google.common.base.Strings;
import org.apache.commons.lang3.StringUtils;

import javax.crypto.*;
import javax.crypto.spec.SecretKeySpec;
import java.io.UnsupportedEncodingException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Arrays;


public class AESUtil {

    /**
     * 加密
     *
     * @param content 需要加密的内容
     * @param key  加密密钥
     * @return
     */
    public static byte[] encrypt(String content, String key) {
        try {
            KeyGenerator kgen = KeyGenerator.getInstance("AES");
            SecureRandom random = SecureRandom.getInstance("SHA1PRNG");
            random.setSeed(key.getBytes());
            kgen.init(128, random);
            SecretKey secretKey = kgen.generateKey();
            byte[] enCodeFormat = secretKey.getEncoded();
            SecretKeySpec secretKeySpec = new SecretKeySpec(enCodeFormat, "AES");
            Cipher cipher = Cipher.getInstance("AES");
            byte[] byteContent = content.getBytes("utf-8");
            cipher.init(Cipher.ENCRYPT_MODE, secretKeySpec);
            byte[] result = cipher.doFinal(byteContent);
            return result; // 加密
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        } catch (NoSuchPaddingException e) {
            e.printStackTrace();
        } catch (InvalidKeyException e) {
            e.printStackTrace();
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        } catch (IllegalBlockSizeException e) {
            e.printStackTrace();
        } catch (BadPaddingException e) {
            e.printStackTrace();
        }
        return null;
    }



    /**解密
     * @param content  待解密内容
     * @param key 解密密钥
     * @return
     */
    public static byte[] decrypt(byte[] content, String key) {
        try {
            KeyGenerator kgen = KeyGenerator.getInstance("AES");
            SecureRandom random = SecureRandom.getInstance("SHA1PRNG");
            random.setSeed(key.getBytes());
            kgen.init(128, random);
            SecretKey secretKey = kgen.generateKey();
            byte[] enCodeFormat = secretKey.getEncoded();
            SecretKeySpec secretKeySpec = new SecretKeySpec(enCodeFormat, "AES");
            Cipher cipher = Cipher.getInstance("AES");// 创建密码器
            cipher.init(Cipher.DECRYPT_MODE, secretKeySpec);// 初始化
            byte[] result = cipher.doFinal(content);
            return result; // 加密
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        } catch (NoSuchPaddingException e) {
            e.printStackTrace();
        } catch (InvalidKeyException e) {
            e.printStackTrace();
        } catch (IllegalBlockSizeException e) {
            e.printStackTrace();
        } catch (BadPaddingException e) {
            e.printStackTrace();
        }
        return null;
    }



    /**将二进制转换成16进制
     * @param buf
     * @return
     */
    public static String parseByte2HexStr(byte buf[]) {
        StringBuffer sb = new StringBuffer();
        for (int i = 0; i < buf.length; i++) {
            String hex = Integer.toHexString(buf[i] & 0xFF);
            if (hex.length() == 1) {
                hex = '0' + hex;
            }
            sb.append(hex.toUpperCase());
        }
        return sb.toString();
    }

    /**
     * 将16进制转换为二进制
     * @param hexStr
     * @return
     */
    public static byte[] parseHexStr2Byte(String hexStr) {
        if (hexStr.length() < 1)
            return null;
        byte[] result = new byte[hexStr.length()/2];
        for (int i = 0;i< hexStr.length()/2; i++) {
            int high = Integer.parseInt(hexStr.substring(i*2, i*2+1), 16);
            int low = Integer.parseInt(hexStr.substring(i*2+1, i*2+2), 16);
            result[i] = (byte) (high * 16 + low);
        }
        return result;
    }

    public static void main(String[] args) {
            String keys = "MAILGUN\tmailgunuser\t1ac2b-0d7ff0e2";


            Arrays.stream(keys.split("\n")).forEach(k->{
                String[] arr = k.split("\t");
                String content = arr[2].trim();
                String password = arr[0].trim()+arr[1].trim();

                //加密
                System.out.print("加密前：" + content);
                long t0 = System.currentTimeMillis();
                byte[] encryptResult = AESUtil.encrypt(content, password);
                String encryptResultStr = AESUtil.parseByte2HexStr(encryptResult);
                System.out.println("\t加密后：" + encryptResultStr);
                System.out.println(System.currentTimeMillis()-t0);
                //解密
                long t1 = System.currentTimeMillis();
                byte[] decryptFrom = AESUtil.parseHexStr2Byte(encryptResultStr);
                byte[] decryptResult = AESUtil.decrypt(decryptFrom, password);
                System.out.println("解密后：" + new String(decryptResult));
                System.out.println(System.currentTimeMillis()-t1);
            });
    }
}



