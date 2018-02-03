package com.home.denis.lightcontrol;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.preference.PreferenceManager;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import com.flask.colorpicker.ColorPickerView;
import com.flask.colorpicker.OnColorSelectedListener;
import com.flask.colorpicker.builder.ColorPickerClickListener;
import com.flask.colorpicker.builder.ColorPickerDialogBuilder;

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

public class MainActivity extends AppCompatActivity implements View.OnClickListener,View.OnLongClickListener {

    SharedPreferences sharedPreferences;
    TextView tvStatus;

    // The BroadcastReceiver that tracks network connectivity changes.
    private NetworkReceiver receiver = new NetworkReceiver();

    MqttAndroidClient mqttAndroidClient;
    MqttConnectOptions mqttConnectOptions;
    final String publishTopic = "wemos/toggle";
    final String publishMessage = "Hello World!";
    final String subscriptionTopic = "wemos/test";

    private int colorpicker_btn_saved_id;
    private int colorpicker_btn_saved_color;

    private String GetNameById(int id){
        return getResources().getResourceEntryName(id);
    }

    private void InitColorButton(int id, String default_color_str) {
        Button btn = (Button) findViewById(id);
        String res_name = GetNameById(id);
        String color_str = sharedPreferences.getString(res_name, "");
        if (color_str=="")
        {
            color_str = default_color_str;
            sharedPreferences.edit().putString(res_name, default_color_str).commit();
        }
        btn.getBackground().mutate().setColorFilter(new PorterDuffColorFilter(Color.parseColor(color_str), PorterDuff.Mode.SRC));
        btn.setOnClickListener(this);
        btn.setOnLongClickListener(this);
    }

    private void InitConstColorButton(int id, String default_color_str) {
        Button btn = (Button) findViewById(id);
        btn.getBackground().mutate().setColorFilter(new PorterDuffColorFilter(Color.parseColor(default_color_str), PorterDuff.Mode.SRC));
        btn.setOnClickListener(this);
        btn.setOnLongClickListener(this);
    }


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        tvStatus = (TextView) findViewById(R.id.tvStatus);

        // получаем SharedPreferences, которое работает с файлом настроек
        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        // полная очистка настроек
        //sharedPreferences.edit().clear().commit();

        mqttConnectOptions = new MqttConnectOptions();
        mqttConnectOptions.setAutomaticReconnect(false);
        // if client disconnects, server remembers client (and does not clean up)
        mqttConnectOptions.setCleanSession(false);
        mqttConnectOptions.setConnectionTimeout(300);
        mqttConnectOptions.setKeepAliveInterval(10 * 60);

        InitConstColorButton(R.id.btnBlack, "#000000");
        InitConstColorButton(R.id.btnWhite, "#FFFFFF");
        InitColorButton(R.id.btnColor1, "#FF0000");
        InitColorButton(R.id.btnColor2, "#00FF00");
        InitColorButton(R.id.btnColor3, "#0000FF");
        InitColorButton(R.id.btnColor4, "#FF00FF");

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
        } catch (MqttException ex){
            System.err.println("Exception whilst subscribing");
            ex.printStackTrace();
        }
    }

    public void publishMessage(String topic, String msg){
        if (mqttAndroidClient == null)
            return;
        if (!mqttAndroidClient.isConnected())
            return;
        try {
            MqttMessage message = new MqttMessage();
            message.setPayload(msg.getBytes());
            mqttAndroidClient.publish(topic, message);
            //addToHistory("Message Published");
            //if(!mqttAndroidClient.isConnected()){
            //    addToHistory(mqttAndroidClient.getBufferedMessageCount() + " messages in buffer.");
            //}
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
                publishMessage(publishTopic, publishMessage);
                break;
        }
        return super.onContextItemSelected(item);
    }

    @Override
    protected void onResume() {
        super.onResume();
        Connect2MQTT();
    }

    @Override
    public void onClick(View view) {
        int color;
        int id =  view.getId();
        switch (id) {
            case R.id.btnBlack:
                color = Color.parseColor("#000000");
                UpdateLEDStripeColor(color);
                break;
            case R.id.btnWhite:
                color = Color.parseColor("#FFB496");
                UpdateLEDStripeColor(color);
                break;
            case R.id.btnColor1:
            case R.id.btnColor2:
            case R.id.btnColor3:
            case R.id.btnColor4:
                String res_name = GetNameById(id);
                String color_str = sharedPreferences.getString(res_name, "#000000");
                color = Color.parseColor(color_str);
                UpdateLEDStripeColor(color);
                break;
        }
    }

    private void UpdateLEDStripeColor(int color) {
        int red = Color.red(color);
        int green = Color.green(color);
        int blue = Color.blue(color);
        publishMessage("wemos/rgb_color",  Integer.toString(red) + ";" + Integer.toString(green) + ";" + Integer.toString(blue));
    }

    private void ColorPickerDialogOk(int color){
        Button btn = (Button) findViewById(colorpicker_btn_saved_id);
        btn.getBackground().mutate().setColorFilter(new PorterDuffColorFilter(color, PorterDuff.Mode.SRC));
        String res_name = GetNameById(colorpicker_btn_saved_id);
        String color_str = String.format("#%X", color);
        sharedPreferences.edit().putString(res_name, color_str).commit();
        UpdateLEDStripeColor(color);
    }

    private void ColorPickerDialogCancel(){
        UpdateLEDStripeColor(colorpicker_btn_saved_color);
    }

    private void ColorPickerDialog(int id, int color)
    {
        colorpicker_btn_saved_id = id;
        colorpicker_btn_saved_color = color;

        ColorPickerDialogBuilder
                .with(this)
                .setTitle("Выбор цвета")
                .initialColor(color)
                .lightnessSliderOnly()
                .wheelType(ColorPickerView.WHEEL_TYPE.FLOWER)
                .density(10)
                .setOnColorSelectedListener(new OnColorSelectedListener() {
                    @Override
                    public void onColorSelected(int selectedColor) {
                        UpdateLEDStripeColor(selectedColor);
                    }
                })
                .setPositiveButton("Применить", new ColorPickerClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int selectedColor, Integer[] allColors) {
                        ColorPickerDialogOk(selectedColor);
                    }
                })
                .setNegativeButton("Отмена", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        ColorPickerDialogCancel();
                    }
                })
                .build()
                .show();
    }

    @Override
    public boolean onLongClick(View v) {
        int id =  v.getId();
        switch (id) {
            case R.id.btnColor1:
            case R.id.btnColor2:
            case R.id.btnColor3:
            case R.id.btnColor4:
                String res_name = GetNameById(id);
                String color_str = sharedPreferences.getString(res_name, "#000000");
                int color = Color.parseColor(color_str);
                ColorPickerDialog(id, color);
                return true;
        }
        return false;
    }

    public class NetworkReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            Connect2MQTT();
        }
    }

}
