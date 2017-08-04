package com.yiran.client;

import java.io.UnsupportedEncodingException;
import java.util.Arrays;

/**
 * Created by yiran on 17-6-15.
 * 该类保存报文数据
 */
public class CommunicationMsg {
    private static final int MAX_DATA_SIZE = 4096;
    private int clientId;
    private short type;
    private short reqType;
    private int destID;
    private short dataSize;
    private short checkSum;
    private byte[] data;

    public CommunicationMsg(){}

    public CommunicationMsg(int clientId){
        this.clientId = clientId;
    }

    public CommunicationMsg(int clientId, short type, short reqType, int destID, byte[] data){
        this.clientId = clientId;
        this.type = type;
        this.reqType = reqType;
        this.destID = destID;
        this.data = Arrays.copyOf(data,data.length);
        dataSize = (short) data.length;
    }

    public void setAsHeartBeat() throws UnsupportedEncodingException {
        this.type = 4;
        this.dataSize = 0;
    }

    /*
    * 根据字节流建立报文对象,小端存储
    * */
    public CommunicationMsg(byte[] byteData){
        /*转换clientId成员,0-3*/
        clientId = (byteData[0] << 0*8) | (byteData[1] << 1*8) | (byteData[2] << 2*8) | (byteData[3] << 3*8);
        /*转换type成员,4-5*/
        type = (short)((byteData[4] << 0*8) | (byteData[5] << 1*8));
        /*转换reqType成员,6-7*/
        reqType = (short)((byteData[6] << 0*8) | (byteData[7] << 1*8));
        /*转换destID成员,8-11*/
        destID = (byteData[8] << 0*8) | (byteData[9] << 1*8) | (byteData[10] << 2*8) | (byteData[11] << 3*8);
        /*转换dataSize成员,12-13*/
        dataSize = (short)(((byteData[12] & 0x0000000FF) | ((byteData[13] << 1*8)) & 0x00000FF00) & 0x00000FFFF);
        /*转换checkSum成员,14-15*/
        checkSum = (short)((byteData[14] << 0*8) | (byteData[15] << 1*8));
        /*复制data成员*/
        data = new byte[byteData.length - 16];
        System.arraycopy(byteData,16,data,0,dataSize);

    }


    /*
    * 根据字节流更新报文
    * */
    public void updateFromBytes(byte[] byteData){
        /*转换clientId成员,0-3*/
        clientId = (byteData[0] << 0*8) | (byteData[1] << 1*8) | (byteData[2] << 2*8) | (byteData[3] << 3*8);
        /*转换type成员,4-5*/
        type = (short)((byteData[4] << 0*8) | (byteData[5] << 1*8));
        /*转换reqType成员,6-7*/
        reqType = (short)((byteData[6] << 0*8) | (byteData[7] << 1*8));
        /*转换destID成员,8-11*/
        destID = (byteData[8] << 0*8) | (byteData[9] << 1*8) | (byteData[10] << 2*8) | (byteData[11] << 3*8);
        /*转换dataSize成员,12-13*/
        dataSize = (short)((byteData[12] << 0*8) | (byteData[13] << 1*8));
        /*转换checkSum成员,14-15*/
        checkSum = (short)((byteData[14] << 0*8) | (byteData[15] << 1*8));
        /*复制data成员*/
        data = new byte[dataSize];
        System.arraycopy(byteData,16,data,0,dataSize);

    }

    /*
    * 小端方式转换成字节流,除开data成员,其余按照4字节对齐
    * */
    public byte[] getBytes(){
        byte[] byteMsg;
        if(data != null){
            byteMsg = new byte[16 + data.length];
        }else{
            byteMsg = new byte[16];
        }
        /*转换clientId成员,0-3*/
        byteMsg[0] = (byte)((clientId & 0x000000ff) >> 0*8);
        byteMsg[1] = (byte)((clientId & 0x0000ff00) >> 1*8);
        byteMsg[2] = (byte)((clientId & 0x00ff0000) >> 2*8);
        byteMsg[3] = (byte)((clientId & 0xff000000) >> 3*8);
        /*转换type成员,4-5*/
        byteMsg[4] = (byte)((type & 0x00ff) >> 0*8);
        byteMsg[5] = (byte)((type & 0xff00) >> 1*8);
        /*转换reqType成员,6-7*/
        byteMsg[6] = (byte)((reqType & 0x00ff) >> 0*8);
        byteMsg[7] = (byte)((reqType & 0xff00) >> 1*8);
        /*转换destID成员,8-11*/
        byteMsg[8] = (byte)((destID & 0x000000ff) >> 0*8);
        byteMsg[9] = (byte)((destID & 0x0000ff00) >> 1*8);
        byteMsg[10] = (byte)((destID & 0x00ff0000) >> 2*8);
        byteMsg[11] = (byte)((destID & 0xff000000) >> 3*8);
        /*转换dataSize成员,12-13*/
        byteMsg[12] = (byte)((dataSize & 0x00ff) >> 0*8);
        byteMsg[13] = (byte)((dataSize & 0xff00) >> 1*8);
        /*转换checkSum成员,14-15*/
        byteMsg[14] = (byte)((checkSum & 0x00ff) >> 0*8);
        byteMsg[15] = (byte)((checkSum & 0xff00) >> 1*8);
        /*复制data成员*/
        if(data != null){
            System.arraycopy(data,0,byteMsg,16,data.length);
        }

        return byteMsg;
    }

    public void setClientId(int clientId){
        this.clientId = clientId;
    }

    public void setData(byte[] data){
        this.data = data;
    }

    public void setDestID(int destID){
        this.destID = destID;
    }

    public short getType(){
        return this.type;
    }


    public short getReqType(){
        return this.reqType;
    }

    public byte[] getData(){
        return this.data;
    }
}
