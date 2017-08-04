package com.yiran.client;


import javax.crypto.*;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.*;
import java.net.Socket;
import java.nio.ByteOrder;
import java.security.*;
import java.util.*;
import java.util.concurrent.Semaphore;

import android.util.Base64;

/**
 * Created by yiran on 17-6-7.
 * 客户端类，通过此类可以链接到服务器
 */

public class JZClient {
    private static JZClient client;
    private static final byte SYC_BYTE = '\r';  //同步字符
    private static final byte CTL_BYTE = 0x10;  //控制字符
    private static final byte[] CTRLF = new byte[]{'\n'};
    private static final int MAX_TRANSMISSION_DATA_SIZE = 32768; //最大传输数据的大小
    private static final int SYC_TIMES = 3;  //同步次数
    private static final int DEFAULT_HEART_BEAT_PERIOD = 5; //默认心跳时间
    private static final byte[] AES_IV = new byte[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0};
    private static final int AES_KEY_LENGTH = 128;
    private static final int LOGIN_SUCCESS = 1;
    private static final int ERR_USRNOTEXIST = 29;
    private boolean loginStatus;
    private int heartBeatPeriod;
    private String serverIp,userName,userPassword;  //服务器IP,用户名,用户密码
    private Socket socket;
    private DataOutputStream socketOS;
    private BufferedInputStream socketBIS;
    private PublicKey remotePublicKey,localPublicKey; //保存远程和本地RSA公钥
    private PrivateKey localPrivateKey; //保存本地秘钥
    private SecretKey aesKey;  //AES秘钥
    private Cipher aesEncIns,aesDecIns;  //AES加解密实例
    private HeartBeatThread heartBeatThread;  //心跳线程实例
    private RcvThread rcvThread; //接受报文线程实例
    private Map<Integer,RequestListener> reqListenerMap; //请求监听器哈希表
    private Map<Integer,ResponseListener> resListenerMap; //响应监听器哈希表
    private int id; //保存客户端id
    private Base64 base64;
    private Semaphore sendSemaphore;  // 发送信号量，互斥发送

    public static void main(String[] args) throws IOException, InterruptedException, NoSuchPaddingException, NoSuchAlgorithmException, InvalidKeyException, InvalidAlgorithmParameterException, BadPaddingException, IllegalBlockSizeException {
        int i;
        JZClient client = new JZClient("192.168.10.183","yiran","snivwkbsk123");
        RequestListener listenerTest = new RequestListener() {
            @Override
            public void listenFunction(short reqType, byte[] reqData) {
                System.out.println(new String(reqData));
            }
        };
        client.addRequestListener((short)1,listenerTest);
        client.login();
        i = 0;
        while(true){
            client.request((short) 1,1,("我来测试很长的数据会不会出现乱码,6666哈哈哈哈哈" + i++).getBytes("UTF-8"));
            Thread.sleep(1);
        }
    }


    public static void init(){
        if(client != null && client.loginStatus == true){
            return;
        }
        client = new JZClient();
    }


    public static void init(String serverIp,String userName,String userPassword){
        if(client != null && client.loginStatus == true){
            return;
        }
        client = new JZClient(serverIp,userName,userPassword);
    }


    public static JZClient getClient(){
        return client;
    }

    public JZClient(){
        this.loginStatus = false;
        this.heartBeatPeriod = DEFAULT_HEART_BEAT_PERIOD;
        this.reqListenerMap = new Hashtable<Integer,RequestListener>(); //建立请求监听器哈希表
        this.resListenerMap = new Hashtable<Integer,ResponseListener>(); //建立请求响应监听器哈希表
        this.sendSemaphore = new Semaphore(1);
        createHeartBeatThread();
        createRcvThread();
    }
    public JZClient(String serverIp,String userName,String userPassword){
        this.serverIp = serverIp;
        this.userName = userName;
        this.userPassword = userPassword;
        this.loginStatus = false;
        this.sendSemaphore = new Semaphore(1);
        this.heartBeatPeriod = DEFAULT_HEART_BEAT_PERIOD;
        this.reqListenerMap = new Hashtable<Integer,RequestListener>();//建立请求监听器哈希表
        this.resListenerMap = new Hashtable<Integer,ResponseListener>(); //建立请求响应监听器哈希表
        createHeartBeatThread();
        createRcvThread();
    }


    public void login() throws IOException, NoSuchPaddingException, NoSuchAlgorithmException, InvalidKeyException, InvalidAlgorithmParameterException, BadPaddingException, IllegalBlockSizeException, InterruptedException {
        int i;
        byte[] rcvData;
        byte[] cipher;
        byte[] cipherBase64;
        byte[] verifyMsg;
        byte[] aesPasswordCipher;
        byte[] aesPassword;
        String verify;
        String remotePublicKeyStr;
        String localPublicKeyStr;
        /*如果已经登录了，就没必要再次登录了*/
        if(loginStatus == true){
            return;
        }
        /*生成本地秘钥对*/
        Map<String,byte[]> keyMap = MyRSA.generateKeyBytes();
        localPublicKey = MyRSA.restorePublicKey(keyMap.get(MyRSA.PUBLIC_KEY));
        localPrivateKey = MyRSA.restorePrivateKey(keyMap.get(MyRSA.PRIVATE_KEY));
        /*TCP连接，建立socket*/
        socket = new Socket(serverIp,1080);
        socketOS = new DataOutputStream(socket.getOutputStream());
        socketBIS = new BufferedInputStream(socket.getInputStream());
        sendData(userName.getBytes("UTF8"));
        /*接受远程公钥*/
        rcvData = _receiveData();  //接收信息
        if(byte2int(rcvData) == ERR_USRNOTEXIST){
            return;
        }
        rcvData = Arrays.copyOf(rcvData,rcvData.length - 1);//因为服务端发来的key字符串最后一个字节是0
        remotePublicKeyStr = new String(rcvData);
        System.out.println(remotePublicKeyStr);
        remotePublicKeyStr = MyRSA.removePem(remotePublicKeyStr);
        System.out.print("去除pem格式的:\n" + remotePublicKeyStr + "\n");
        remotePublicKey = MyRSA.restorePublicKey(base64Decode(remotePublicKeyStr.getBytes("UTF-8")));

        /*使用Base64编码本地公钥,并且封装成pem格式的*/
        localPublicKeyStr = new String(base64Encode(keyMap.get(MyRSA.PUBLIC_KEY),1));
        localPublicKeyStr = "-----BEGIN PUBLIC KEY-----\n" + localPublicKeyStr + "\n-----END PUBLIC KEY-----\n";
        System.out.println(localPublicKeyStr);
        /*组装认证内容,并且使用本地rsa公钥加密*/
        verify = userPassword + "+" + localPublicKeyStr;
        cipher = MyRSA.RSAEncode(remotePublicKey,verify.getBytes("UTF-8"));
        cipherBase64 = base64Encode(cipher,0);
        sendData(cipherBase64);
        verifyMsg = _receiveData(); //接收信息
        if(byte2int(verifyMsg) != LOGIN_SUCCESS){
            System.out.println("认证失败");
            return;
        }
        verifyMsg = Arrays.copyOfRange(verifyMsg,4,8);
        this.id = byte2int(verifyMsg);  //复制客户端id
        /*接收AES密码*/
        aesPasswordCipher = _receiveData();
        aesPasswordCipher = Arrays.copyOf(aesPasswordCipher,aesPasswordCipher.length - 1); //多出的字符是0
        aesPasswordCipher = base64Decode(aesPasswordCipher);
        aesPassword = MyRSA.RSADecode(localPrivateKey,aesPasswordCipher);
        aesPassword = Arrays.copyOf(aesPassword,AES_KEY_LENGTH/8);
        /*生成AES加解密实例*/
        aesKey = new SecretKeySpec(aesPassword,"AES"); //根据AES秘钥数组生成AES秘钥实例
        aesEncIns = Cipher.getInstance("AES/CBC/NoPadding");
        aesEncIns.init(Cipher.ENCRYPT_MODE,aesKey,new IvParameterSpec(AES_IV));
        aesDecIns = Cipher.getInstance("AES/CBC/NoPadding");
        aesDecIns.init(Cipher.DECRYPT_MODE,aesKey,new IvParameterSpec(AES_IV));
        loginStatus = true; //成功登录

        /*下面是调试用的代码*/
        System.out.println("AES密码是:");
        for(i = 0;i < AES_KEY_LENGTH/8;i++){
            System.out.printf("%x ",aesPassword[i]);
        }
        /*上面是调试用的代码*/

    }

    private void createHeartBeatThread(){
        this.heartBeatThread = new HeartBeatThread(this);
        heartBeatThread.start();
    }

    private void createRcvThread(){
        this.rcvThread = new RcvThread(this);
        rcvThread.start();
    }

    public byte[] aesDecrypt(byte[] cipher) throws BadPaddingException, IllegalBlockSizeException {
        return aesDecIns.doFinal(cipher);
    }


    /**
     * @param input,
     * @return
     */
    public static byte[] base64Encode(byte[] input,int flag){
        byte[] one_line;
        byte[] base64_fomat;
        int len,line_cnt,i,j;


        if(flag == 1){
            one_line = Base64.encode(input,Base64.NO_WRAP);
            len = one_line.length;
            line_cnt = len/64;
            if(len%64 != 0)line_cnt++;
            base64_fomat = new byte[len + line_cnt - 1];
            for(i = 0;i < len;i++){
                j = i/64;
                base64_fomat[i + j] = one_line[i];
                if(i%64 == 0 && i != 0){
                    base64_fomat[i + j - 1] = '\n';
                }
            }
            return base64_fomat;
        }else{
            return Base64.encode(input,Base64.NO_WRAP);
        }
//        return Base64.encode(input,Base64.NO_WRAP);
    }

    public static byte[] base64Decode(byte[] input){
        return Base64.decode(input,Base64.NO_WRAP);
    }


    /*
    * 字节数组转整形
    *
    * */
    private int byte2int(byte[] byteData){
        if(ByteOrder.nativeOrder() == ByteOrder.BIG_ENDIAN){
            /*大端存储*/
            return (byteData[3] | (byteData[2] << 8) | (byteData[1] << 16) | (byteData[0] << 24));
        }else{
            /*小端存储*/
            return (byteData[0] | (byteData[1] << 8) | (byteData[2] << 16) | (byteData[3] << 24));
        }
    }


    /*
    * 测试方法
    * */
    private void sendAESDataTest(byte[] data) throws IOException, BadPaddingException, IllegalBlockSizeException {
        byte[] AESCipher;
        byte[] dataTemp = new byte[64];
        System.arraycopy(data,0,dataTemp,0,data.length);
        CommunicationMsg comMsg = new CommunicationMsg(1,(short)2,(short)2,0,dataTemp);
        AESCipher = aesEncIns.doFinal(comMsg.getBytes());
        sendData(AESCipher);
    }

    public void sendAESData(byte[] data) throws BadPaddingException, IllegalBlockSizeException, IOException, InterruptedException {
        if(this.loginStatus){
            sendSemaphore.acquire();
            _sendAESData(data);
            sendSemaphore.release();
        }
    }

    /*
    * 发送AES加密数据
    *
    * */
    private void _sendAESData(byte[] data) throws BadPaddingException, IllegalBlockSizeException, IOException {
        int alignLen;  //对齐长度
        byte[] dataAlign;  //128Bit对齐数据
        byte[] AESCipher;

        alignLen = (int)Math.ceil((double)data.length/(double)(AES_KEY_LENGTH/8))*(AES_KEY_LENGTH/8); //确定对齐后的长度
        dataAlign = new byte[alignLen];
        System.arraycopy(data,0,dataAlign,0,data.length);
        AESCipher = aesEncIns.doFinal(dataAlign);
        sendData(AESCipher);
    }

    private void sendData(byte[] data) throws IOException {
        int i;
        short sendSize = (short)data.length;
        byte[] sendHead = new byte[2];
        byte[] sendByte = new byte[1];  //逐字节发送

        if(data.length >= MAX_TRANSMISSION_DATA_SIZE){ //如果发送的数据超过一次传输的最大大小
            //throw 一个异常
        }
        sendByte[0] = SYC_BYTE;
        for(i = 0;i < 5;i++){  //发送若干个同步字符
            socketOS.write(sendByte);
        }
        sendHead[0] = (byte)((short)(sendSize << 1) >> 0);
        sendHead[1] = (byte)((short)(sendSize << 1) >> 8);
        for(i = 0;i < 2;i++){  //透明传输处理
            if(sendHead[i] == SYC_BYTE || sendHead[i] == CTL_BYTE){
                sendByte[0] = CTL_BYTE;
                socketOS.write(sendByte);
                sendByte[0] = sendHead[i];
                socketOS.write(sendByte);
            }else{
                sendByte[0] = sendHead[i];
                socketOS.write(sendByte);
            }
        }
        socketOS.write(data,0,data.length);
    }


    public byte[] rcvData() throws IOException {
        if(loginStatus){
            return _receiveData();
        }else{
            return null;
        }
    }

    private byte[] _receiveData() throws IOException {
        int rcvStatus = 0;
        int rcvSize = 0;
        int monitorCount = 0;
        int sycCount = 0;  //同步计数
        int headIndex = 0;
        byte[] ret;
        byte[] headBytes = new byte[2];
        byte[] receiveByte = new byte[1];  //一次只读取一个字节

        while(true){
            if(rcvStatus == 0){  //处于监听状态
                if(monitorCount++ == 50){  //如果接受字符超过50个还没有收到报文的头(Head)
                    return null;
                }
                socketBIS.read(receiveByte,0,1); //貌似这个不知道会不会发生阻塞
                if(receiveByte[0] == SYC_BYTE){ //如果接受到了同步字符
                    if(++sycCount == SYC_TIMES){ //同步完成,则开始接受head
                        while(true){
                            socketBIS.read(receiveByte,0,1);
                            if(receiveByte[0] == SYC_BYTE)continue; //同步字符可能超过SYC_TIMES个
                            if(receiveByte[0] == CTL_BYTE){  //透明传输字符,下一个才是真正需要接受的字符
                                socketBIS.read(receiveByte,0,1);
                            }
                            headBytes[headIndex++] = receiveByte[0];
                            if(headIndex == 2){
                                rcvStatus = 1;  //进入报文接受状态
                                rcvSize = ((headBytes[0] & 0xff) | ((headBytes[1] << 8) & 0xff00)) >> 1; //高7位才是大小;
                                break;
                            }
                        }
                    }
                }else{
                    sycCount = 0;
                }
            }else if(rcvStatus == 1){
                ret = new byte[rcvSize];
                socketBIS.read(ret,0,rcvSize);
                break;
            }
        }
        return ret;
    }

    public boolean loginStatus(){
        return loginStatus;
    }

    /**
     * 向服务器发送请求
     * @param reqType 请求类型
     * @param reqData 请求的参数或者数据
     */
    public void request(short reqType,int destID,byte[] reqData) throws BadPaddingException, IllegalBlockSizeException, IOException {
        CommunicationMsg comMsg = new CommunicationMsg(id,(short)2,reqType,destID,reqData);
        _sendAESData(comMsg.getBytes());
    }

    /**
     * 添加请求监听器
     * @param reqType 请求类型
     * @param reqListener 请求监听器
     */
    public void addRequestListener(short reqType,RequestListener reqListener){
        reqListenerMap.put((int)reqType,reqListener);
    }

    /**
     * 添加请求响应监听器
     * @param reqType 请求类型
     * @param resListener 响应监听器
     */
    public void addResponseListener(short reqType,ResponseListener resListener){
        resListenerMap.put((int)reqType,resListener);
    }


    public void setServerIp(String serverIp) {
        this.serverIp = serverIp;
    }

    public void setUserPassword(String userPassword) {
        this.userPassword = userPassword;
    }

    public void setUserName(String userName) {
        this.userName = userName;
    }

    public ResponseListener getResListener(short reqType){
        return resListenerMap.get((int)reqType);
    }

    public RequestListener getReqListener(short reqType){
        return reqListenerMap.get((int)reqType);
    }
    public int getId(){
        return this.id;
    }

    public int getHeartBeatPeriod() {
        return heartBeatPeriod;
    }


}


class RcvThread implements Runnable{
    private JZClient client;
    private Thread t;
    private RequestListener requestListener;
    private ResponseListener responseListener;

    public RcvThread(JZClient client){
        this.client = client;
    }

    @Override
    public void run() {
        int msgType;
        byte[] rcvData;
        byte[] plainData;
        CommunicationMsg comMsg;
        while(true){
            if(client.loginStatus()){
                try {
                    rcvData = client.rcvData();
                    if(rcvData == null){
                        continue;
                    }
                    plainData = client.aesDecrypt(rcvData);
                    comMsg = new CommunicationMsg(plainData);
                    msgType = comMsg.getType();
                    switch (msgType){
                        case 1:
                            break;
                        case 2: //请求报文
                            requestListener = null;
                            requestListener = client.getReqListener(comMsg.getReqType());
                            if(requestListener != null){
                                requestListener.listenFunction(comMsg.getReqType(),comMsg.getData());
                            }
                            break;
                        case 3:  //请求响应报文
                            responseListener = null;
                            responseListener = client.getResListener(comMsg.getReqType());
                            if(responseListener != null){
                                responseListener.listenFunction(comMsg.getReqType(),comMsg.getData());
                            }
                            break;
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                } catch (BadPaddingException e) {
                    e.printStackTrace();
                } catch (IllegalBlockSizeException e) {
                    e.printStackTrace();
                }
            }else{
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }

    }

    public void start(){
        t = new Thread(this,"RecvThread");
        t.start();
    }
}

class HeartBeatThread implements Runnable{
    private JZClient client;
    private Thread t;
    public HeartBeatThread(JZClient client){
        this.client = client;
    }

    @Override
    public void run() {
        CommunicationMsg comMsg = new CommunicationMsg(client.getId());
        try {
            comMsg.setAsHeartBeat();  //设置为心跳报文
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        }
        while(true){
            /*如果已经登录，则发送心跳报文*/
            if(client.loginStatus()){
                try {
                    comMsg.setClientId(client.getId());
                    comMsg.setDestID(0);
                    client.sendAESData(comMsg.getBytes());
                } catch (BadPaddingException e) {
                    e.printStackTrace();
                } catch (IllegalBlockSizeException e) {
                    e.printStackTrace();
                } catch (IOException e) {
                    e.printStackTrace();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
            try {
                Thread.sleep(1000*client.getHeartBeatPeriod());
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

    }

    public void start(){
        t = new Thread(this,"heart beat");
        t.start();
    }
}
