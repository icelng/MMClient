package com.yiran.client;

/**
 * Created by yiran on 17-6-23.
 * 请求响应监听器
 */
public interface ResponseListener {
    void listenFunction(short reqType,byte[] resData);
}
