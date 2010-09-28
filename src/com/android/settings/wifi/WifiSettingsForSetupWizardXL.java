/*
 * Copyright (C) 2010 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.settings.wifi;

import com.android.settings.R;

import android.app.Activity;
import android.app.StatusBarManager;
import android.content.Context;
import android.net.NetworkInfo.DetailedState;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.Window;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;

import java.util.EnumMap;

/**
 * WifiSetings Activity specific for SetupWizard with X-Large screen size.
 */
public class WifiSettingsForSetupWizardXL extends Activity implements OnClickListener {
    private static final String TAG = WifiSettingsForSetupWizardXL.class.getSimpleName();

    private static final EnumMap<DetailedState, DetailedState> stateMap =
        new EnumMap<DetailedState, DetailedState>(DetailedState.class);

    static {
        stateMap.put(DetailedState.IDLE, DetailedState.DISCONNECTED);
        stateMap.put(DetailedState.SCANNING, DetailedState.SCANNING);
        stateMap.put(DetailedState.CONNECTING, DetailedState.CONNECTING);
        stateMap.put(DetailedState.AUTHENTICATING, DetailedState.CONNECTING);
        stateMap.put(DetailedState.OBTAINING_IPADDR, DetailedState.CONNECTING);
        stateMap.put(DetailedState.CONNECTED, DetailedState.CONNECTED);
        stateMap.put(DetailedState.SUSPENDED, DetailedState.SUSPENDED);  // ?
        stateMap.put(DetailedState.DISCONNECTING, DetailedState.DISCONNECTED);
        stateMap.put(DetailedState.DISCONNECTED, DetailedState.DISCONNECTED);
        stateMap.put(DetailedState.FAILED, DetailedState.DISCONNECTED);
    }

    private TextView mProgressText;
    private ProgressBar mProgressBar;
    private WifiSettings mWifiSettings;
    private TextView mStatusText;

    private StatusBarManager mStatusBarManager;

    // This count reduces every time when there's a notification about WiFi status change.
    // During the term this is >0, The system shows the message "connecting", regardless
    // of the actual WiFi status. After this count's becoming 0, the status message correctly
    // reflects what WiFi Picker told it. This is a tweak for letting users not confused
    // with instable WiFi state during the first scan.
    private int mIgnoringWifiNotificationCount = 5;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        setContentView(R.layout.wifi_settings_for_setup_wizard_xl);
        mWifiSettings =
                (WifiSettings)getFragmentManager().findFragmentById(R.id.wifi_setup_fragment);
        setup();
        // XXX: should we use method?
        getIntent().putExtra(WifiSettings.IN_XL_SETUP_WIZARD, true);

        mStatusBarManager = (StatusBarManager)getSystemService(Context.STATUS_BAR_SERVICE);
        if (mStatusBarManager != null) {
            mStatusBarManager.disable(StatusBarManager.DISABLE_EXPAND
                    | StatusBarManager.DISABLE_NOTIFICATION_ICONS
                    | StatusBarManager.DISABLE_NOTIFICATION_ALERTS
                    | StatusBarManager.DISABLE_SYSTEM_INFO
                    | StatusBarManager.DISABLE_NAVIGATION);
        } else {
            Log.e(TAG, "StatusBarManager isn't available.");
        }
    }

    @Override
    public void onDestroy() {
        if (mStatusBarManager != null) {
            mStatusBarManager.disable(StatusBarManager.DISABLE_NONE);
        }
        super.onDestroy();
    }

    public void setup() {
        mProgressText = (TextView)findViewById(R.id.scanning_progress_text);
        mProgressText.setText(Summary.get(this, DetailedState.SCANNING));
        mProgressBar = (ProgressBar)findViewById(R.id.scanning_progress_bar);
        mProgressBar.setMax(2);
        mProgressBar.setIndeterminate(true);
        mStatusText = (TextView)findViewById(R.id.wifi_setup_status);

        ((Button)findViewById(R.id.wifi_setup_refresh_list)).setOnClickListener(this);
        ((Button)findViewById(R.id.wifi_setup_add_network)).setOnClickListener(this);
        ((Button)findViewById(R.id.wifi_setup_skip_or_next)).setOnClickListener(this);
        ((Button)findViewById(R.id.wifi_setup_connect)).setOnClickListener(this);
        ((Button)findViewById(R.id.wifi_setup_forget)).setOnClickListener(this);
        ((Button)findViewById(R.id.wifi_setup_cancel)).setOnClickListener(this);
    }

    @Override
    public void onClick(View view) {
        final int id = view.getId();
        switch (id) {
        case R.id.wifi_setup_refresh_list:
            mWifiSettings.refreshAccessPoints();
            break;
        case R.id.wifi_setup_add_network:
            mWifiSettings.onAddNetworkPressed();
            break;
        case R.id.wifi_setup_skip_or_next:
            setResult(Activity.RESULT_OK);
            finish();
            break;
        case R.id.wifi_setup_connect:
            mWifiSettings.submit();
            break;
        case R.id.wifi_setup_forget:
            mWifiSettings.forget();
            break;
        case R.id.wifi_setup_cancel:
            mStatusText.setText(R.string.wifi_setup_status_select_network);
            mWifiSettings.detachConfigPreference();
            break;
        }
    }

    // Called from WifiSettings
    public void updateConnectionState(DetailedState originalState) {
        final DetailedState state = stateMap.get(originalState);
        switch (state) {
        case SCANNING: {
            // Let users know the device is working correctly though currently there's
            // no visible network on the list.
            if (mWifiSettings.getAccessPointsCount() == 0) {
                mProgressBar.setIndeterminate(true);
                mProgressText.setText(Summary.get(this, DetailedState.SCANNING));
            } else {
                // Users already already connected to a network, or see available networks.
                mProgressBar.setIndeterminate(false);
            }
            break;
        }
        case CONNECTING: {
            mProgressBar.setIndeterminate(false);
            mProgressBar.setProgress(1);
            mStatusText.setText(R.string.wifi_setup_status_connecting);
            mProgressText.setText(Summary.get(this, state));
            break;
        }
        case CONNECTED: {
            mProgressBar.setIndeterminate(false);
            mProgressBar.setProgress(2);
            mStatusText.setText(R.string.wifi_setup_status_connected);
            mProgressText.setText(Summary.get(this, state));
            setResult(Activity.RESULT_OK);
            finish();
            break;
        }
        default:  // Not connected.
            if (mWifiSettings.getAccessPointsCount() == 0 &&
                    mIgnoringWifiNotificationCount > 0) {
                mIgnoringWifiNotificationCount--;
                mProgressBar.setIndeterminate(true);
                mProgressText.setText(Summary.get(this, DetailedState.SCANNING));
                return;
            } else {
                mProgressBar.setIndeterminate(false);
                mProgressBar.setProgress(0);
                mStatusText.setText(R.string.wifi_setup_status_select_network);
                mProgressText.setText(getString(R.string.wifi_setup_not_connected));
            }
            break;
        }
    }

    public void onWifiConfigPreferenceAttached(boolean isNewNetwork) {
        mStatusText.setText(R.string.wifi_setup_status_edit_network);
    }

    public void onForget() {
        mProgressBar.setIndeterminate(false);
        mProgressBar.setProgress(0);
        mProgressText.setText(getString(R.string.wifi_setup_not_connected));
    }

    public void onRefreshAccessPoints() {
        mProgressBar.setIndeterminate(true);
        mProgressText.setText(Summary.get(this, DetailedState.SCANNING));
    }
}