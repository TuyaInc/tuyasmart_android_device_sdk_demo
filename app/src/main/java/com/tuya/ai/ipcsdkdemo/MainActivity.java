package com.tuya.ai.ipcsdkdemo;

import android.Manifest;
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.SurfaceView;

import androidx.appcompat.app.AppCompatActivity;

import com.tuya.ai.ipcsdkdemo.video.AudioCapture;
import com.tuya.ai.ipcsdkdemo.video.FileAudioCapture;
import com.tuya.ai.ipcsdkdemo.video.FileVideoCapture;
import com.tuya.ai.ipcsdkdemo.video.H264FileVideoCapture;
import com.tuya.ai.ipcsdkdemo.video.VideoCapture;
import com.tuya.smart.aiipc.base.permission.PermissionUtil;
import com.tuya.smart.aiipc.cardv.CardvImps;
import com.tuya.smart.aiipc.cardv.CardvJNIApi;
import com.tuya.smart.aiipc.ipc_sdk.IPCSDK;
import com.tuya.smart.aiipc.ipc_sdk.api.Common;
import com.tuya.smart.aiipc.ipc_sdk.api.IControllerManager;
import com.tuya.smart.aiipc.ipc_sdk.api.IMediaTransManager;
import com.tuya.smart.aiipc.ipc_sdk.api.IMqttProcessManager;
import com.tuya.smart.aiipc.ipc_sdk.api.INetConfigManager;
import com.tuya.smart.aiipc.ipc_sdk.api.IParamConfigManager;
import com.tuya.smart.aiipc.ipc_sdk.callback.DPConst;
import com.tuya.smart.aiipc.ipc_sdk.callback.DPEvent;
import com.tuya.smart.aiipc.ipc_sdk.callback.NetConfigCallback;
import com.tuya.smart.aiipc.ipc_sdk.service.IPCServiceManager;
import com.tuya.smart.aiipc.netconfig.ConfigProvider;
import com.tuya.smart.aiipc.netconfig.mqtt.TuyaNetConfig;
import com.tuya.smart.aiipc.trans.IPCLog;
import com.tuya.smart.aiipc.trans.TranJNIApi;
import com.tuya.smart.aiipc.trans.TransJNIInterface;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.Enumeration;
import java.util.TimeZone;

import static com.tuya.smart.aiipc.ipc_sdk.callback.DPEvent.TUYA_DP_AP_MODE;
import static com.tuya.smart.aiipc.ipc_sdk.callback.DPEvent.TUYA_DP_AP_SWITCH;
import static com.tuya.smart.aiipc.ipc_sdk.callback.DPEvent.TUYA_DP_AP_TIME_SYNC;
import static com.tuya.smart.aiipc.ipc_sdk.callback.DPEvent.TUYA_DP_AP_TIME_ZONE;
import static com.tuya.smart.aiipc.ipc_sdk.callback.DPEvent.TUYA_DP_LIGHT;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "IPC_DEMO";

    SurfaceView surfaceView;

    H264FileVideoCapture h264FileMainVideoCapture;

    private Handler mHandler;

    VideoCapture videoCapture;
    AudioCapture audioCapture;

    FileAudioCapture fileAudioCapture;
    FileVideoCapture fileVideoCapture;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        surfaceView = findViewById(R.id.surface);
        mHandler = new Handler();

        findViewById(R.id.reset).setOnClickListener(v -> IPCServiceManager.getInstance().reset());

        findViewById(R.id.start_record).setOnClickListener(v -> TransJNIInterface.getInstance().startLocalStorage());

        findViewById(R.id.stop_record).setOnClickListener(v -> TransJNIInterface.getInstance().stopLocalStorage());

        findViewById(R.id.call).setOnClickListener(v -> {
            IMediaTransManager mediaTransManager = IPCServiceManager.getInstance().getService(IPCServiceManager.IPCService.MEDIA_TRANS_SERVICE);

            try {
                InputStream fileStream = getAssets().open("leijun.jpeg");

                byte[] buffer = new byte[2048];
                int bytesRead;
                ByteArrayOutputStream output = new ByteArrayOutputStream();
                while ((bytesRead = fileStream.read(buffer)) != -1) {
                    output.write(buffer, 0, bytesRead);
                }
                byte[] file = output.toByteArray();
                mediaTransManager.sendDoorBellCallForPress(file, Common.NOTIFICATION_CONTENT_TYPE_E.NOTIFICATION_CONTENT_JPEG);

            } catch (IOException e) {
                e.printStackTrace();
            }
        });

        PermissionUtil.check(this, new String[]{
                Manifest.permission.WRITE_EXTERNAL_STORAGE,
                Manifest.permission.READ_EXTERNAL_STORAGE,
                Manifest.permission.WAKE_LOCK,
                Manifest.permission.RECORD_AUDIO
        }, this::initSDK);

    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        IPCSDK.closeWriteLog();
    }

    int is_ap = 0;
    int started = -1;

    private void initSDK() {

        CardvJNIApi.setImps(new CardvImps() {
            @Override
            public String tuya_hal_wired_get_ip() {
                return getIP();
            }

            @Override
            public boolean tuya_hal_wired_station_conn() {
                return true;
            }

            @Override
            public int tuya_hal_wired_get_mac() {
                return 0;
            }
        });

        IPCSDK.initSDK(this);
        IPCSDK.openWriteLog(MainActivity.this, "/sdcard/tuya_log/ipc/", 3);
        LoadParamConfig();

        INetConfigManager iNetConfigManager = IPCServiceManager.getInstance().getService(IPCServiceManager.IPCService.NET_CONFIG_SERVICE);

        iNetConfigManager.config("QR_OUTPUT", surfaceView.getHolder());
        iNetConfigManager.config(INetConfigManager.QR_PARAMETERS, (INetConfigManager.OnParameterSetting) (p, camera) -> {
            camera.setDisplayOrientation(90);
        });

        String pid = BuildConfig.PID;
        String uuid = BuildConfig.UUID;
        String authkey = BuildConfig.AUTHOR_KEY;

        iNetConfigManager.setPID(pid);
        iNetConfigManager.setUserId(uuid);
        iNetConfigManager.setAuthorKey(authkey);

        TuyaNetConfig.setDebug(true);

        ConfigProvider.enableMQTT(false);

        //注册处理DP的接口
        IControllerManager controllerManager = IPCServiceManager.getInstance().getService(IPCServiceManager.IPCService.CONTROLLER_SERVICE);
        controllerManager.setDpEventSimpleCallback((v, dpid, time_stamp) -> {

            Log.w(TAG, "dpCallback: " + dpid);

            switch (dpid) {
                case TUYA_DP_AP_TIME_SYNC:
                    int i = CardvJNIApi.set_service_time((String) v);
                    Log.w(TAG, "set_service_time: " + v + "/" + i);
                    break;
                case TUYA_DP_AP_TIME_ZONE:
                    int i1 = CardvJNIApi.set_time_zone((String) v);
                    Log.w(TAG, "set_time_zone: " + v + "/" + i1);
                    break;
                case DPEvent.TUYA_DP_SD_RECORD_MODE:
                    if (v instanceof Integer) {
                        if ((int) v == 0) {
                            boolean b = ((IMediaTransManager) IPCServiceManager.getInstance().getService(IPCServiceManager.IPCService.MEDIA_TRANS_SERVICE)).autoLocalStorage(false);
                            Log.w(TAG, "autoLocalStorage: " + b);
                        } else if ((int) v == 1) {
                            boolean b = ((IMediaTransManager) IPCServiceManager.getInstance().getService(IPCServiceManager.IPCService.MEDIA_TRANS_SERVICE)).autoLocalStorage(true);
                            Log.w(TAG, "autoLocalStorage: " + b);
                        }
                        Log.w("handlerDPEvent", "TUYA_DP_SD_RECORD_MODE: " + v);
                    }
                    return new DPConst.DPResult(v, DPConst.Type.PROP_ENUM);
                case TUYA_DP_LIGHT:
                    return new DPConst.DPResult(true, DPConst.Type.PROP_BOOL);
                case TUYA_DP_AP_MODE:
                    String ap_ssid = "test-ap", ap_pw = "12345678";
                    if (isWifiApOpen(MainActivity.this)) {
                        is_ap = 1;
                    } else {
                        is_ap = 0;
                    }
                    return new DPConst.DPResult(String.format("{\\\"is_ap\\\":%d,\\\"ap_ssid\\\":\\\"%s\\\",\\\"password\\\":\\\"%s\\\"}", is_ap, ap_ssid, ap_pw), DPConst.Type.PROP_STR);
                case TUYA_DP_AP_SWITCH:
                    //按照实际AP打开的情况返回
                    is_ap = 1;
                    TranJNIApi.dpReport(dpid, DPConst.Type.PROP_STR, String.format("{\\\"ap_enable\\\":%d,\\\"errcode\\\":0}", is_ap), 1);
                    ap_ssid = "test-ap";
                    ap_pw = "12345678";
                    TranJNIApi.dpReport(TUYA_DP_AP_MODE, DPConst.Type.PROP_STR, String.format("{\\\"is_ap\\\":%d,\\\"ap_ssid\\\":\\\"%s\\\",\\\"password\\\":\\\"%s\\\"}", is_ap, ap_ssid, ap_pw), 1);
                    return null;
            }

            return null;
        });

        IPCServiceManager.getInstance().setResetHandler(isHardward -> {

            if (mHandler != null) {
                mHandler.postDelayed(() -> {
                    //restart
                    Intent mStartActivity = getPackageManager().getLaunchIntentForPackage(getPackageName());
                    if (mStartActivity != null) {
                        int mPendingIntentId = 123456;
                        PendingIntent mPendingIntent = PendingIntent.getActivity(this, mPendingIntentId
                                , mStartActivity, PendingIntent.FLAG_CANCEL_CURRENT);
                        AlarmManager mgr = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
                        mgr.set(AlarmManager.RTC, System.currentTimeMillis() + 100, mPendingIntent);
                        Runtime.getRuntime().exit(0);
                    }

                }, 1500);
            }
        });

        NetConfigCallback netConfigCallback = new NetConfigCallback() {

            @Override
            public void configOver(boolean first, String token) {
                IPCLog.w(TAG, "configOver: token" + token);
                IMediaTransManager transManager = IPCServiceManager.getInstance().getService(IPCServiceManager.IPCService.MEDIA_TRANS_SERVICE);

                IMqttProcessManager mqttProcessManager = IPCServiceManager.getInstance().getService(IPCServiceManager.IPCService.MQTT_SERVICE);

                //如果为AP模式 直接开启推流 不需要等待上线回调
                if (started != 0 && isWifiApOpen(MainActivity.this)) {

                    transManager.initTransSDK(token, "/sdcard/tuya_ipc/", "/sdcard/tuya_ipc/", BuildConfig.PID, BuildConfig.UUID, BuildConfig.AUTHOR_KEY);

                    IPCLog.w(TAG, "AP start multimedia");
                    started = transManager.startMultiMediaTrans(5);
                    IPCLog.w(TAG, "startMultiMediaTrans: " + started);

                    PermissionUtil.check(MainActivity.this, new String[]{
                            Manifest.permission.RECORD_AUDIO,
                    }, () -> {
                        videoCapture = new VideoCapture(Common.ChannelIndex.E_CHANNEL_VIDEO_MAIN);
                        audioCapture = new AudioCapture(Common.ChannelIndex.E_CHANNEL_AUDIO_MAIN);

                        fileVideoCapture = new FileVideoCapture(MainActivity.this, Common.ChannelIndex.E_CHANNEL_VIDEO_MAIN_2);
                        fileAudioCapture = new FileAudioCapture(MainActivity.this, Common.ChannelIndex.E_CHANNEL_AUDIO_SUB);

                        videoCapture.startVideoCapture();
                        audioCapture.startCapture();

                        fileVideoCapture.startVideoCapture();
                        fileAudioCapture.startFileCapture();
                    });
                } else {
                    //非AP模式，需要等待上线回调后 再开启推流
                    mqttProcessManager.setMqttStatusChangedCallback(status -> {
                        IPCLog.w(TAG, "onMqttStatus: " + status);

                        if (status == Common.MqttConnectStatus.GB_STAT_CLOUD_CONN) {

                            started = transManager.startMultiMediaTrans(5);
                            Log.w(TAG, "startMultiMediaTrans: " + started);

                            PermissionUtil.check(MainActivity.this, new String[]{
                                    Manifest.permission.RECORD_AUDIO,
                            }, () -> {
                                videoCapture = new VideoCapture(Common.ChannelIndex.E_CHANNEL_VIDEO_MAIN);
                                audioCapture = new AudioCapture(Common.ChannelIndex.E_CHANNEL_AUDIO_MAIN);

                                fileVideoCapture = new FileVideoCapture(MainActivity.this, Common.ChannelIndex.E_CHANNEL_VIDEO_MAIN_2);
                                fileAudioCapture = new FileAudioCapture(MainActivity.this, Common.ChannelIndex.E_CHANNEL_AUDIO_SUB);

                                videoCapture.startVideoCapture();
                                audioCapture.startCapture();

                                fileVideoCapture.startVideoCapture();
                                fileAudioCapture.startFileCapture();
                            });
                        }
                    });

                    transManager.initTransSDK(token, "/sdcard/tuya_ipc/", "/sdcard/tuya_ipc/", BuildConfig.PID, BuildConfig.UUID, BuildConfig.AUTHOR_KEY);
                }

                transManager.setP2PEventCallback((event, value) -> {
                    switch (event) {
                        case TRAN_VIDEO_CLARITY_SET:
                            int val = Integer.valueOf(value.toString());
                            IPCLog.d(TAG, "TRAN_VIDEO_CLARITY_SET " + val);
                            if (val == IMediaTransManager.TRAN_VIDEO_CLARITY_VALUE.HIGH) {
                            } else if (val == IMediaTransManager.TRAN_VIDEO_CLARITY_VALUE.STANDARD) {

                            }
                            break;
                        case TRANS_LIVE_VIDEO_START:
                            break;
                        case TRANS_LIVE_VIDEO_STOP:
                            break;
                    }
                });

                transManager.addAudioTalkCallback(bytes -> {
                    Log.d(TAG, "audio callback: " + bytes.length);
                });

                syncTimeZone();
            }

            @Override
            public void startConfig() {
                Log.d(TAG, "startConfig: ");
            }

            @Override
            public void recConfigInfo() {
                Log.d(TAG, "recConfigInfo: ");
            }

            @Override
            public void onNetConnectFailed(int i, String s) {

            }

            @Override
            public void onNetPrepareFailed(int i, String s) {

            }
        };

        iNetConfigManager.configNetInfo(netConfigCallback);

    }

    private void LoadParamConfig() {
        IParamConfigManager configManager = IPCServiceManager.getInstance().getService(IPCServiceManager.IPCService.MEDIA_PARAM_SERVICE);

        /**
         * 主码流参数配置
         * */
        configManager.setInt(Common.ChannelIndex.E_CHANNEL_VIDEO_MAIN, Common.ParamKey.KEY_VIDEO_WIDTH, 1280);
        configManager.setInt(Common.ChannelIndex.E_CHANNEL_VIDEO_MAIN, Common.ParamKey.KEY_VIDEO_HEIGHT, 720);
        configManager.setInt(Common.ChannelIndex.E_CHANNEL_VIDEO_MAIN, Common.ParamKey.KEY_VIDEO_FRAME_RATE, 30);
        configManager.setInt(Common.ChannelIndex.E_CHANNEL_VIDEO_MAIN, Common.ParamKey.KEY_VIDEO_I_FRAME_INTERVAL, 2);
        configManager.setInt(Common.ChannelIndex.E_CHANNEL_VIDEO_MAIN, Common.ParamKey.KEY_VIDEO_BIT_RATE, 1024000);


        configManager.setInt(Common.ChannelIndex.E_CHANNEL_VIDEO_SUB, Common.ParamKey.KEY_VIDEO_WIDTH, 1280);
        configManager.setInt(Common.ChannelIndex.E_CHANNEL_VIDEO_SUB, Common.ParamKey.KEY_VIDEO_HEIGHT, 720);
        configManager.setInt(Common.ChannelIndex.E_CHANNEL_VIDEO_SUB, Common.ParamKey.KEY_VIDEO_FRAME_RATE, 15);
        configManager.setInt(Common.ChannelIndex.E_CHANNEL_VIDEO_SUB, Common.ParamKey.KEY_VIDEO_I_FRAME_INTERVAL, 2);
        configManager.setInt(Common.ChannelIndex.E_CHANNEL_VIDEO_SUB, Common.ParamKey.KEY_VIDEO_BIT_RATE, 512000);

        configManager.setInt(Common.ChannelIndex.E_CHANNEL_VIDEO_MAIN_2, Common.ParamKey.KEY_VIDEO_WIDTH, 1280);
        configManager.setInt(Common.ChannelIndex.E_CHANNEL_VIDEO_MAIN_2, Common.ParamKey.KEY_VIDEO_HEIGHT, 720);
        configManager.setInt(Common.ChannelIndex.E_CHANNEL_VIDEO_MAIN_2, Common.ParamKey.KEY_VIDEO_FRAME_RATE, 30);
        configManager.setInt(Common.ChannelIndex.E_CHANNEL_VIDEO_MAIN_2, Common.ParamKey.KEY_VIDEO_I_FRAME_INTERVAL, 2);
        configManager.setInt(Common.ChannelIndex.E_CHANNEL_VIDEO_MAIN_2, Common.ParamKey.KEY_VIDEO_BIT_RATE, 1024000);


        configManager.setInt(Common.ChannelIndex.E_CHANNEL_VIDEO_SUB_2, Common.ParamKey.KEY_VIDEO_WIDTH, 1280);
        configManager.setInt(Common.ChannelIndex.E_CHANNEL_VIDEO_SUB_2, Common.ParamKey.KEY_VIDEO_HEIGHT, 720);
        configManager.setInt(Common.ChannelIndex.E_CHANNEL_VIDEO_SUB_2, Common.ParamKey.KEY_VIDEO_FRAME_RATE, 15);
        configManager.setInt(Common.ChannelIndex.E_CHANNEL_VIDEO_SUB_2, Common.ParamKey.KEY_VIDEO_I_FRAME_INTERVAL, 2);
        configManager.setInt(Common.ChannelIndex.E_CHANNEL_VIDEO_SUB_2, Common.ParamKey.KEY_VIDEO_BIT_RATE, 512000);

        /**
         * 音频流参数
         * */
        configManager.setInt(Common.ChannelIndex.E_CHANNEL_AUDIO_MAIN, Common.ParamKey.KEY_AUDIO_CHANNEL_NUM, 1);
        configManager.setInt(Common.ChannelIndex.E_CHANNEL_AUDIO_MAIN, Common.ParamKey.KEY_AUDIO_SAMPLE_RATE, 8000);
        configManager.setInt(Common.ChannelIndex.E_CHANNEL_AUDIO_MAIN, Common.ParamKey.KEY_AUDIO_SAMPLE_BIT, 16);
        configManager.setInt(Common.ChannelIndex.E_CHANNEL_AUDIO_MAIN, Common.ParamKey.KEY_AUDIO_FRAME_RATE, 25);

        configManager.setInt(Common.ChannelIndex.E_CHANNEL_AUDIO_SUB, Common.ParamKey.KEY_AUDIO_CHANNEL_NUM, 1);
        configManager.setInt(Common.ChannelIndex.E_CHANNEL_AUDIO_SUB, Common.ParamKey.KEY_AUDIO_SAMPLE_RATE, 8000);
        configManager.setInt(Common.ChannelIndex.E_CHANNEL_AUDIO_SUB, Common.ParamKey.KEY_AUDIO_SAMPLE_BIT, 16);
        configManager.setInt(Common.ChannelIndex.E_CHANNEL_AUDIO_SUB, Common.ParamKey.KEY_AUDIO_FRAME_RATE, 25);
    }

    private static void syncTimeZone() {
        int rawOffset = TransJNIInterface.getInstance().getAppTimezoneBySecond();
        String[] availableIDs = TimeZone.getAvailableIDs(rawOffset * 1000);
        if (availableIDs.length > 0) {
            android.util.Log.d(TAG, "syncTimeZone: " + rawOffset + " , " + availableIDs[0] + " ,  ");
        }
    }

    protected String getIP() {
        if (isWifiApOpen(this)) {
            Log.w(TAG, "isWifiApOpen");
            return "192.168.43.1";
        }

        NetworkInfo info = ((ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE)).getActiveNetworkInfo();
        if (info != null && info.isConnected()) {
            if (info.getType() == ConnectivityManager.TYPE_ETHERNET) {
                // 以太网络
                return getLocalIp();
            } else if (info.getType() == ConnectivityManager.TYPE_WIFI) {
                //  无线网络
                WifiManager wifiManager = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
                if (wifiManager != null) {
                    WifiInfo wifiInfo = wifiManager.getConnectionInfo();
                    String ipAddress = intIP2StringIP(wifiInfo.getIpAddress());
                    return ipAddress;
                }
            }
        }

        return "0.0.0.0";
    }

    private static String intIP2StringIP(int ip) {
        return (ip & 0xFF) + "." +
                ((ip >> 8) & 0xFF) + "." +
                ((ip >> 16) & 0xFF) + "." +
                (ip >> 24 & 0xFF);
    }

    private static String getLocalIp() {
        try {
            for (Enumeration<NetworkInterface> en = NetworkInterface
                    .getNetworkInterfaces(); en.hasMoreElements(); ) {
                NetworkInterface intf = en.nextElement();
                for (Enumeration<InetAddress> enumIpAddr = intf
                        .getInetAddresses(); enumIpAddr.hasMoreElements(); ) {
                    InetAddress inetAddress = enumIpAddr.nextElement();
                    if (!inetAddress.isLoopbackAddress()
                            && inetAddress instanceof Inet4Address) {
                        return inetAddress.getHostAddress();
                    }
                }
            }
        } catch (SocketException ex) {
            Log.e(TAG, ex.getMessage());
        }
        return "0.0.0.0";
    }

    public static boolean isWifiApOpen(Context context) {
        try {
            WifiManager manager = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
            //通过放射获取 getWifiApState()方法
            Method method = manager.getClass().getDeclaredMethod("getWifiApState");
            //调用getWifiApState() ，获取返回值
            int state = (int) method.invoke(manager);
            //通过放射获取 WIFI_AP的开启状态属性
            Field field = manager.getClass().getDeclaredField("WIFI_AP_STATE_ENABLED");
            //获取属性值
            int value = (int) field.get(manager);
            //判断是否开启
            if (state == value) {
                return true;
            } else {
                return false;
            }
        } catch (NoSuchMethodException | InvocationTargetException | IllegalAccessException | NoSuchFieldException e) {
            e.printStackTrace();
        }
        return false;
    }
}
