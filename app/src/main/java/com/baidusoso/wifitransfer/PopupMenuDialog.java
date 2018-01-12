package com.baidusoso.wifitransfer;

import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.NetworkInfo;
import android.net.wifi.WifiManager;
import android.provider.Settings;
import android.text.TextUtils;
import android.view.Display;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import com.hwangjr.rxbus.RxBus;
import com.hwangjr.rxbus.annotation.Subscribe;
import com.hwangjr.rxbus.annotation.Tag;

import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.OnClick;
import butterknife.Unbinder;
import timber.log.Timber;

/**
 * Created by masel on 2016/10/10.
 */

public class PopupMenuDialog {
    Unbinder mUnbinder;
    @BindView(R.id.popup_menu_title)
    TextView mTxtTitle;
    @BindView(R.id.popup_menu_subtitle)
    TextView mTxtSubTitle;
    @BindView(R.id.shared_wifi_state)
    ImageView mImgLanState;
    @BindView(R.id.shared_wifi_state_hint)
    TextView mTxtStateHint;
    @BindView(R.id.shared_wifi_address)
    TextView mTxtAddress;
    @BindView(R.id.shared_wifi_settings)
    Button mBtnWifiSettings;
    @BindView(R.id.shared_wifi_button_split_line)
    View mButtonSplitLine;
    WifiConnectChangedReceiver mWifiConnectChangedReceiver = new WifiConnectChangedReceiver();
    private Context context;
    private Dialog dialog;
    private Display display;

    public PopupMenuDialog(Context context) {
        this.context = context;
        WindowManager windowManager = (WindowManager) context
                .getSystemService(Context.WINDOW_SERVICE);
        display = windowManager.getDefaultDisplay();
        RxBus.get().register(this);
    }

    public PopupMenuDialog builder() {
        View view = LayoutInflater.from(context).inflate(
                R.layout.layout_popup_menu_dialog, null);

        view.setMinimumWidth(display.getWidth());

        dialog = new Dialog(context, R.style.PopupMenuDialogStyle);
        dialog.setContentView(view);
        mUnbinder = ButterKnife.bind(this, dialog);
        dialog.setOnDismissListener(this::onDialogDismiss);

        Window dialogWindow = dialog.getWindow();
        dialogWindow.setGravity(Gravity.LEFT | Gravity.BOTTOM);
        WindowManager.LayoutParams lp = dialogWindow.getAttributes();
        lp.x = 0;
        lp.y = 0;
        dialogWindow.setAttributes(lp);

        return this;
    }

    public PopupMenuDialog setCancelable(boolean cancel) {
        dialog.setCancelable(cancel);
        return this;
    }

    public PopupMenuDialog setCanceledOnTouchOutside(boolean cancel) {
        dialog.setCanceledOnTouchOutside(cancel);
        return this;
    }

    public void show() {
        checkWifiState(WifiUtils.getWifiConnectState(context));
        dialog.show();
        registerWifiConnectChangedReceiver();
    }

    @OnClick({R.id.shared_wifi_cancel, R.id.shared_wifi_settings})
    public void onClick(View view) {
        switch (view.getId()) {
            case R.id.shared_wifi_cancel:
                dialog.dismiss();
                break;
            case R.id.shared_wifi_settings:
                context.startActivity(new Intent(Settings.ACTION_WIFI_SETTINGS));
                break;
        }
    }

    void registerWifiConnectChangedReceiver() {
        IntentFilter intentFilter = new IntentFilter(WifiManager.NETWORK_STATE_CHANGED_ACTION);
        context.registerReceiver(mWifiConnectChangedReceiver, intentFilter);
    }

    void unregisterWifiConnectChangedReceiver() {
        context.unregisterReceiver(mWifiConnectChangedReceiver);
    }

    @Subscribe(tags = {@Tag(Constants.RxBusEventType.WIFI_CONNECT_CHANGE_EVENT)})
    public void onWifiConnectStateChanged(NetworkInfo.State state) {
        checkWifiState(state);
    }

    void checkWifiState(NetworkInfo.State state) {
        if (state == NetworkInfo.State.CONNECTED || state == NetworkInfo.State.CONNECTING) {
            if (state == NetworkInfo.State.CONNECTED) {
                String ip = WifiUtils.getWifiIp(context);
                if (!TextUtils.isEmpty(ip)) {
                    onWifiConnected(ip);
                    return;
                }
            }
            onWifiConnecting();
            return;
        }
        onWifiDisconnected();
    }

    void onWifiDisconnected() {
        mTxtTitle.setText(R.string.wlan_disabled);
        mTxtTitle.setTextColor(context.getResources().getColor(android.R.color.black));
        mTxtSubTitle.setVisibility(View.VISIBLE);
        mImgLanState.setImageResource(R.drawable.shared_wifi_shut_down);
        mTxtStateHint.setText(R.string.fail_to_start_http_service);
        mTxtAddress.setVisibility(View.GONE);
        mButtonSplitLine.setVisibility(View.VISIBLE);
        mBtnWifiSettings.setVisibility(View.VISIBLE);
    }

    void onWifiConnecting() {
        mTxtTitle.setText(R.string.wlan_enabled);
        mTxtTitle.setTextColor(context.getResources().getColor(R.color.colorWifiConnected));
        mTxtSubTitle.setVisibility(View.GONE);
        mImgLanState.setImageResource(R.drawable.shared_wifi_enable);
        mTxtStateHint.setText(R.string.retrofit_wlan_address);
        mTxtAddress.setVisibility(View.GONE);
        mButtonSplitLine.setVisibility(View.GONE);
        mBtnWifiSettings.setVisibility(View.GONE);
    }

    void onWifiConnected(String ipAddr) {
        mTxtTitle.setText(R.string.wlan_enabled);
        mTxtTitle.setTextColor(context.getResources().getColor(R.color.colorWifiConnected));
        mTxtSubTitle.setVisibility(View.GONE);
        mImgLanState.setImageResource(R.drawable.shared_wifi_enable);
        mTxtStateHint.setText(R.string.pls_input_the_following_address_in_pc_browser);
        mTxtAddress.setVisibility(View.VISIBLE);
        mTxtAddress.setText(String.format(context.getString(R.string.http_address), ipAddr, Constants.HTTP_PORT));
        mButtonSplitLine.setVisibility(View.GONE);
        mBtnWifiSettings.setVisibility(View.GONE);
    }

    void onDialogDismiss(DialogInterface dialog) {
        Timber.d("dialog dismiss!");
        if (mUnbinder != null) {
            mUnbinder.unbind();
            RxBus.get().post(Constants.RxBusEventType.POPUP_MENU_DIALOG_SHOW_DISMISS, Constants.MSG_DIALOG_DISMISS);
            unregisterWifiConnectChangedReceiver();
            RxBus.get().unregister(PopupMenuDialog.this);
        }
    }
}
