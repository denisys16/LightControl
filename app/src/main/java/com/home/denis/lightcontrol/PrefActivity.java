package com.home.denis.lightcontrol;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceManager;

import java.util.prefs.Preferences;

public class PrefActivity extends PreferenceActivity
        implements SharedPreferences.OnSharedPreferenceChangeListener {

    public static final String KEY_PREF_SERVER_URI = "pref_serveruri";
    public static final String KEY_PREF_SERVER_URI_SUMMARY = "\n\nSpecifies the protocol, host name and port to be used to connect to an MQTT server (tcp://192.168.1.1:1883)";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        addPreferencesFromResource(R.xml.pref);

        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        Preference connectionPref = findPreference(KEY_PREF_SERVER_URI);
        // Set summary to be the user-description for the selected value
        connectionPref.setSummary(sharedPreferences.getString(KEY_PREF_SERVER_URI, "") + KEY_PREF_SERVER_URI_SUMMARY);
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        if (key.equals(KEY_PREF_SERVER_URI)) {
            Preference connectionPref = findPreference(key);
            // Set summary to be the user-description for the selected value
            connectionPref.setSummary(sharedPreferences.getString(key, "") + KEY_PREF_SERVER_URI_SUMMARY);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        getPreferenceScreen().getSharedPreferences()
                .registerOnSharedPreferenceChangeListener(this);
    }

    @Override
    protected void onPause() {
        super.onPause();
        getPreferenceScreen().getSharedPreferences()
                .unregisterOnSharedPreferenceChangeListener(this);
    }
}