package com.yiran.client;


import java.security.InvalidKeyException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.HashMap;
import java.util.Map;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;


public class MyRSA {
    public static final String KEY_ALGORITHM = "RSA";
    /** 貌似默认是RSA/NONE/PKCS1Padding，未验证 */
    public static final String CIPHER_ALGORITHM = "RSA/ECB/PKCS1Padding";
    public static final String PUBLIC_KEY = "publicKey";
    public static final String PRIVATE_KEY = "privateKey";

    /** RSA密钥长度必须是64的倍数，在512~65536之间。默认是1024 */
    public static final int KEY_SIZE = 1024;

    public static final String PLAIN_TEXT = "MANUTD is the greatest club in the world";

    public static void main(String[] args) {
        Map<String, byte[]> keyMap = generateKeyBytes();

        // 加密
        PublicKey publicKey = restorePublicKey(keyMap.get(PUBLIC_KEY));


        byte[] encodedText = RSAEncode(publicKey, PLAIN_TEXT.getBytes());

        // 解密
        PrivateKey privateKey = restorePrivateKey(keyMap.get(PRIVATE_KEY));
    }

    /**
     * 生成密钥对。注意这里是生成密钥对KeyPair，再由密钥对获取公私钥
     *
     * @return
     */
    public static Map<String, byte[]> generateKeyBytes() {

        try {
            KeyPairGenerator keyPairGenerator = KeyPairGenerator
                    .getInstance(KEY_ALGORITHM);
            keyPairGenerator.initialize(KEY_SIZE);
            KeyPair keyPair = keyPairGenerator.generateKeyPair();
            RSAPublicKey publicKey = (RSAPublicKey) keyPair.getPublic();
            RSAPrivateKey privateKey = (RSAPrivateKey) keyPair.getPrivate();

            Map<String, byte[]> keyMap = new HashMap<String, byte[]>();
            keyMap.put(PUBLIC_KEY, publicKey.getEncoded());
            keyMap.put(PRIVATE_KEY, privateKey.getEncoded());
            return keyMap;
        } catch (NoSuchAlgorithmException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        return null;
    }

    /**
     * 还原公钥，X509EncodedKeySpec 用于构建公钥的规范
     *
     * @param keyBytes
     * @return
     */
    public static PublicKey restorePublicKey(byte[] keyBytes) {
        X509EncodedKeySpec x509EncodedKeySpec = new X509EncodedKeySpec(keyBytes);

        try {
            KeyFactory factory = KeyFactory.getInstance(KEY_ALGORITHM);
            PublicKey publicKey = factory.generatePublic(x509EncodedKeySpec);
            return publicKey;
        } catch (NoSuchAlgorithmException | InvalidKeySpecException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        return null;
    }

    /**
     * 还原私钥，PKCS8EncodedKeySpec 用于构建私钥的规范
     *
     * @param keyBytes
     * @return
     */
    public static PrivateKey restorePrivateKey(byte[] keyBytes) {
        PKCS8EncodedKeySpec pkcs8EncodedKeySpec = new PKCS8EncodedKeySpec(
                keyBytes);
        try {
            KeyFactory factory = KeyFactory.getInstance(KEY_ALGORITHM);
            PrivateKey privateKey = factory
                    .generatePrivate(pkcs8EncodedKeySpec);
            return privateKey;
        } catch (NoSuchAlgorithmException | InvalidKeySpecException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        return null;
    }

    public static String removePem(String key){
        String ret;
        ret = key.replace("-----BEGIN PUBLIC KEY-----","");
        ret = ret.replace("-----END PUBLIC KEY-----","");
        ret = ret.replace("\n","");
        return ret;
    }

    /**
     * 加密，三步走。
     *
     * @param key
     * @param plainText
     * @return
     */
    public static byte[] RSAEncode(PublicKey key, byte[] plainText) {
        int maxPlainLen = 1024/8 - 11;  //最大加密长度
        int cipherLen = 1024/8;  //密文长度
        int segNum = plainText.length/maxPlainLen;  //明文分段数
        int restLen = plainText.length%maxPlainLen;  //剩余部分的长度
        int i;
        byte[] encPlainText = new byte[maxPlainLen];
        byte[] restEncPlainText;
        byte[] cipherSum = new byte[cipherLen*(segNum + 1)];
        byte[] cipherTem;
        Cipher cipher;

        try {
            cipher = Cipher.getInstance(CIPHER_ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE, key);
            /*分段加密*/
            for(i = 0;i < segNum;i++){
                System.arraycopy(plainText,i*maxPlainLen,encPlainText,0,maxPlainLen);
                cipherTem = cipher.doFinal(encPlainText);
                System.arraycopy(cipherTem,0,cipherSum,i*cipherLen,cipherLen);
            }
            if(restLen != 0){
                restEncPlainText = new byte[restLen];
                System.arraycopy(plainText,i*maxPlainLen,restEncPlainText,0,restLen);
                cipherTem = cipher.doFinal(restEncPlainText);
                System.arraycopy(cipherTem,0,cipherSum,i*cipherLen,cipherLen);
            }
            return cipherSum;
        } catch (NoSuchAlgorithmException | NoSuchPaddingException
                | InvalidKeyException | IllegalBlockSizeException
                | BadPaddingException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        return null;
    }

    /**
     * 解密，三步走。
     *
     * @param key
     * @param encodedText
     * @return
     */
    public static byte[] RSADecode(PrivateKey key, byte[] encodedText) {
        int maxPlainLen = 1024/8 - 11;  //最大加密长度
        int cipherLen = 1024/8;  //密文长度
        int segNum = (int)Math.ceil((double)encodedText.length/(double)cipherLen);
        int i;
        byte[] encodedTextTem = new byte[cipherLen];
        byte[] plainText = new byte[segNum*maxPlainLen];
        byte[] plainTextTem;


        try {
            Cipher cipher = Cipher.getInstance(CIPHER_ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE, key);
            for(i = 0;i < segNum - 1;i++){
                System.arraycopy(encodedText,i*cipherLen,encodedTextTem,0,cipherLen);
                plainTextTem = cipher.doFinal(encodedTextTem);
                System.arraycopy(plainTextTem,0,plainText,i*maxPlainLen,maxPlainLen);
            }
            System.arraycopy(encodedText,i*cipherLen,encodedTextTem,0,cipherLen);
            plainTextTem = cipher.doFinal(encodedTextTem);
            System.arraycopy(plainTextTem,0,plainText,i*maxPlainLen,plainTextTem.length);
            return plainText;
        } catch (NoSuchAlgorithmException | NoSuchPaddingException
                | InvalidKeyException | IllegalBlockSizeException
                | BadPaddingException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        return null;

    }
}