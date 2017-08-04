package com.yiran.client;

import java.io.IOException;

/**
 * Created by yiran on 17-6-22.
 */
public interface RequestListener {
    void listenFunction(short reqType,byte[] reqData) throws IOException;

}
