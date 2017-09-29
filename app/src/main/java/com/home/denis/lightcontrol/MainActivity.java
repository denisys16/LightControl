package com.home.denis.lightcontrol;

import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.TextView;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main_menu, menu);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        TextView tv = (TextView) findViewById(R.id.textView);
        tv.setText(item.getTitle());
        switch (item.getItemId()) {
            case R.id.menu_settings:
                break;
            case R.id.menu_exit:
                finish();
                break;
        }
        return super.onContextItemSelected(item);
    }
}
