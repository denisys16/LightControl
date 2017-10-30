package com.home.denis.lightcontrol;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.preference.PreferenceManager;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.TextView;

import org.eclipse.paho.android.service.MqttAndroidClient;
import org.eclipse.paho.client.mqttv3.DisconnectedBufferOptions;
import org.eclipse.paho.client.mqttv3.IMqttActionListener;
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.IMqttMessageListener;
import org.eclipse.paho.client.mqttv3.IMqttToken;
import org.eclipse.paho.client.mqttv3.MqttCallbackExtended;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;

public class MainActivity extends AppCompatActivity {

    SharedPreferences sharedPreferences;
    TextView tvStatus;

    // The BroadcastReceiver that tracks network connectivity changes.
    private NetworkReceiver receiver = new NetworkReceiver();

    MqttAndroidClient mqttAndroidClient;
    MqttConnectOptions mqttConnectOptions;
    final String publishTopic = "wemos/toggle";
    final String publishMessage = "Hello World!";
    final String subscriptionTopic = "wemos/test";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        tvStatus = (TextView) findViewById(R.id.tvStatus);

        // получаем SharedPreferences, которое работает с файлом настроек
        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        // полная очистка настроек
        // sp.edit().clear().commit();

        mqttConnectOptions = new MqttConnectOptions();
        mqttConnectOptions.setAutomaticReconnect(false);
        // if client disconnects, server remembers client (and does not clean up)
        mqttConnectOptions.setCleanSession(false);
        mqttConnectOptions.setConnectionTimeout(300);
        mqttConnectOptions.setKeepAliveInterval(10 * 60);

        // Registers BroadcastReceiver to track network connection changes.
        IntentFilter filter = new IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION);
        receiver = new NetworkReceiver();
        this.registerReceiver(receiver, filter);
    }

    private String getPrefServerURI()
    {
        return  sharedPreferences.getString(PrefActivity.KEY_PREF_SERVER_URI, "tcp://192.168.1.108:1883");
    }

    private boolean isNetworkConnected()
    {
        ConnectivityManager cm = (ConnectivityManager)this.getSystemService(Context.CONNECTIVITY_SERVICE);

        NetworkInfo activeNetwork = cm.getActiveNetworkInfo();
        boolean isConnected = activeNetwork != null && activeNetwork.isConnectedOrConnecting();

        return isConnected;
    }

    private void InitMQTTClient() {
        mqttAndroidClient = new MqttAndroidClient(getApplicationContext(), getPrefServerURI(), MqttClient.generateClientId());
        mqttAndroidClient.setCallback(new MqttCallbackExtended() {
            @Override
            public void connectComplete(boolean reconnect, String serverURI) {

                if (reconnect) {
                    updateStatus("Reconnected to : " + serverURI);
                    // Because Clean Session is true, we need to re-subscribe
                    subscribeToTopic();
                } else {
                    updateStatus("Connected to: " + serverURI);
                }
            }

            @Override
            public void connectionLost(Throwable cause) {
                updateStatus("The Connection to "+ mqttAndroidClient.getServerURI() +" was lost.");
            }

            @Override
            public void messageArrived(String topic, MqttMessage message) throws Exception {
                addToHistory("Incoming message: " + new String(message.getPayload()));
            }

            @Override
            public void deliveryComplete(IMqttDeliveryToken token) {

            }
        });
    }

    private void Connect2MQTT() {

        if (mqttAndroidClient != null) {
            if (mqttAndroidClient.isConnected())
                mqttAndroidClient.close();
        }
        if (!isNetworkConnected()) {
            updateStatus("No network connection available.");
            return;
        }

        InitMQTTClient();
        updateStatus("Connecting to " + getPrefServerURI());
        try {

            IMqttToken t = mqttAndroidClient.connect(mqttConnectOptions, null, new IMqttActionListener() {
                @Override
                public void onSuccess(IMqttToken asyncActionToken) {
                    DisconnectedBufferOptions disconnectedBufferOptions = new DisconnectedBufferOptions();
                    disconnectedBufferOptions.setBufferEnabled(true);
                    disconnectedBufferOptions.setBufferSize(100);
                    disconnectedBufferOptions.setPersistBuffer(false);
                    disconnectedBufferOptions.setDeleteOldestMessages(false);
                    mqttAndroidClient.setBufferOpts(disconnectedBufferOptions);
                    subscribeToTopic();
                }

                @Override
                public void onFailure(IMqttToken asyncActionToken, Throwable exception) {
                    updateStatus("Failed to connect to: " + mqttAndroidClient.getServerURI());
                }
            });
            //t.waitForCompletion(5000);

        } catch (MqttException ex){
            ex.printStackTrace();
        }


    }

    private void addToHistory(String mainText){
        System.out.println("LOG: " + mainText);
    }

    private void updateStatus(String mainText){
        addToHistory(mainText);
        tvStatus.setText(mainText);
    }

    public void subscribeToTopic(){
        if (mqttAndroidClient == null)
            return;
        if (!mqttAndroidClient.isConnected())
            return;
        try {
            mqttAndroidClient.subscribe(subscriptionTopic, 0, null, new IMqttActionListener() {
                @Override
                public void onSuccess(IMqttToken asyncActionToken) {
                    addToHistory("Subscribed!");
                }

                @Override
                public void onFailure(IMqttToken asyncActionToken, Throwable exception) {
                    addToHistory("Failed to subscribe");
                }
            });
            /*
            // THIS DOES NOT WORK!
            mqttAndroidClient.subscribe(subscriptionTopic, 0, new IMqttMessageListener() {
                @Override
                public void messageArrived(String topic, MqttMessage message) throws Exception {
                    // message Arrived!
                    addToHistory("Message: " + topic + " : " + new String(message.getPayload()));
                }
            });
            */

        } catch (MqttException ex){
            System.err.println("Exception whilst subscribing");
            ex.printStackTrace();
        }
    }

    public void publishMessage(){
        if (mqttAndroidClient == null)
            return;
        if (!mqttAndroidClient.isConnected())
            return;
        try {
            MqttMessage message = new MqttMessage();
            message.setPayload(publishMessage.getBytes());
            mqttAndroidClient.publish(publishTopic, message);
            addToHistory("Message Published");
            if(!mqttAndroidClient.isConnected()){
                addToHistory(mqttAndroidClient.getBufferedMessageCount() + " messages in buffer.");
            }
        } catch (MqttException e) {
            addToHistory("Error Publishing: " + e.getMessage());
            e.printStackTrace();
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main_menu, menu);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.menu_settings:
                Intent intent = new Intent(this, PrefActivity.class);
                startActivity(intent);
                break;
            case R.id.menu_exit:
                finish();
                break;
            case R.id.menu_publish:
                publishMessage();
                break;
        }
        return super.onContextItemSelected(item);
    }

    @Override
    protected void onResume() {
        super.onResume();
        Connect2MQTT();
    }

    public class NetworkReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            Connect2MQTT();
        }
    }

}
