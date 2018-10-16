package com.example.leg10n.wifitest;

import android.content.IntentFilter;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;

import static com.example.leg10n.wifitest.WifiConnectionReceiver.getIntentFilterForWifiConnectionReceiver;

public class MainActivity extends AppCompatActivity {
    WifiConnectionReceiver receiver = new WifiConnectionReceiver();
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        registerReceiver(receiver, new IntentFilter(getIntentFilterForWifiConnectionReceiver()));
    }
    protected void onDestroy(){
        unregisterReceiver(receiver);

        super.onDestroy();
    }
}
