package com.yiran.mmclient;

import android.app.AlertDialog;
import android.content.Intent;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import com.yiran.client.JZClient;
import com.yiran.client.RequestListener;

import java.io.IOException;
import java.io.Serializable;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;

import javax.crypto.BadPaddingException;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;

public class MainActivity extends AppCompatActivity {
    private JZClient client = null;
    private Button btnLogin;
    private EditText editUsrName,editUsrPassword;
    private AlertDialog.Builder alertDialogBuilder;
    private Bundle bundleClient;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        btnLogin = (Button) findViewById(R.id.btn_login);
        editUsrName = (EditText) findViewById(R.id.edit_usr_name);
        editUsrPassword = (EditText) findViewById(R.id.edit_usr_password);
        final Intent intentMenuActivity = new Intent(this,MenuActivity.class);
        bundleClient = new Bundle();
        JZClient.init();
        btnLogin.setOnClickListener(new View.OnClickListener(){
            @Override
            public void onClick(View view) {
                new Thread(){
                    public void run(){
//                        client = new JZClient("192.168.10.183",editUsrName.getText().toString(),editUsrPassword.getText().toString());
                        client = JZClient.getClient();
                        client.setServerIp("45.78.60.111");
                        client.setUserName(editUsrName.getText().toString());
                        client.setUserPassword(editUsrPassword.getText().toString());
                        client.addRequestListener((short) 1, new RequestListener() {
                            @Override
                            public void listenFunction(short i, byte[] bytes) {
                                System.out.println(new String(bytes));
                            }
                        });
                        try {
                            client.login();
                            if(client.loginStatus() == true){
//                                bundleClient.putSerializable("client",(Serializable)client);
//                                intentMenuActivity.putExtras(bundleClient);
                                startActivity(intentMenuActivity);
//                                client.request((short)1,"haha".getBytes("utf-8"));
                            }
                        } catch (IOException e) {
                            e.printStackTrace();
                        } catch (NoSuchPaddingException e) {
                            e.printStackTrace();
                        } catch (NoSuchAlgorithmException e) {
                            e.printStackTrace();
                        } catch (InvalidKeyException e) {
                            e.printStackTrace();
                        } catch (InvalidAlgorithmParameterException e) {
                            e.printStackTrace();
                        } catch (BadPaddingException e) {
                            e.printStackTrace();
                        } catch (IllegalBlockSizeException e) {
                            e.printStackTrace();
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }

                    }
                }.start();
            }
        });
//        while(true){
//            if(client.loginStatus() == true){
//                alertDialog.show();
//            }
//        }
    }
}
