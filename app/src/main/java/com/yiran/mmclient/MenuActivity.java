package com.yiran.mmclient;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Environment;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import com.yiran.client.JZClient;
import com.yiran.client.RequestListener;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;

import javax.crypto.BadPaddingException;
import javax.crypto.IllegalBlockSizeException;


public class MenuActivity extends AppCompatActivity {
    private Button btnSendMsg;
    private EditText editSendMsg;
    private TextView textShow;
    private JZClient client;
    private AudioRecoderHandler audioRecoderHandler;
    private AudioPlayerHandler audioPlayerHandler;


    public void takeScreenShot() {
        String mSavedPath = Environment.getExternalStorageDirectory() + File.separator + "screenshot.png";
        try {
            Runtime.getRuntime().exec("screencap -p " + mSavedPath);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if(requestCode == 1){
            if(grantResults.length <= 0){
                System.out.println("\n麦克风权限申请失败");
            }
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_menu);
        btnSendMsg = (Button) findViewById(R.id.btn_snd);
        editSendMsg = (EditText) findViewById(R.id.edit_snd);
        textShow = (TextView) findViewById(R.id.text_show);
//        client = (JZClient) getIntent().getSerializableExtra("client");
        client = JZClient.getClient();
        audioRecoderHandler = new AudioRecoderHandler(this);
        audioPlayerHandler = new AudioPlayerHandler();
        audioPlayerHandler.prepare();
        if (ContextCompat.checkSelfPermission(this,
                Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
            System.out.println("\n麦克风权限没有获取");
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.RECORD_AUDIO},
                    1);
        }else{
//
        }
        client.addRequestListener((short)1,new RequestListener(){
            @Override
            public void listenFunction(short reqType, byte[] reqData) throws IOException {
                System.out.println("收到声音报文");
                byte[] comData = Arrays.copyOf(reqData,reqData.length);
                byte[] oriData = AudioPlayerHandler.uncompress(comData);
//                byte[] oriData = Arrays.copyOf(reqData,reqData.length);
                audioPlayerHandler.onPlaying(oriData,0,oriData.length);
            }
        });
        btnSendMsg.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                takeScreenShot();
                audioRecoderHandler.startRecord(new AudioRecoderHandler.AudioRecordingCallback() {
                    @Override
                    public void onRecording(byte[] data, int startIndex, int length) throws BadPaddingException, IllegalBlockSizeException, IOException {
                        byte[] oridData = Arrays.copyOfRange(data,startIndex,startIndex+length);
                        byte[] sendData = AudioRecoderHandler.compress(oridData);
//                        byte[] sendData = Arrays.copyOfRange(data,startIndex,startIndex+length);
                        client.request((short)1,1,sendData);
                    }

                    @Override
                    public void onStopRecord(String savedPath) {

                    }
                });
            }
        });
    }
}
