[English](./README.md) | 简体中文

# tuya-iotos-android-ipc-demo
涂鸦安卓设备端IPC SDK demo（行车记录仪版）

## 介绍

Tuya安卓设备端IPC SDK是在涂鸦IoTOS体系中针对具备音视频能力的安卓设备，如网络摄像机（IPC）、录像机（NVR/DVR）、可视门铃（doorbell）、摄像头灯（Floodloght）等推出的软件开发包。提供标准安卓gradle依赖以及对应的说明文档，覆盖4.4及以上的安卓系统。
行车记录仪版为基于通用版本，增加了对AP模式、双摄像头推流等功能的支持和适配。


## 如何使用
[通用接入文档地址](https://tuyainc.github.io/tuyasmart_android_device_sdk_doc/)

## 行车记录仪版本说明：
### 接入：
* maven仓库为 `maven { url 'https://dl.bintray.com/tuyasmartai/sdk' }`
* SDK依赖版本：参考demo中使用的版本

### 需要接入者实现的部分：
* 实现状态获取接口

```java
CardvJNIApi.setImps(new CardvImps() {
            @Override
            public String tuya_hal_wired_get_ip() {
                // get wired ethernet ip info
                // 注意，getIP()函数为demo提供的示例代码，请根据实际情况自行实现
                return getIP();
            }

            @Override
            public boolean tuya_hal_wired_station_conn() {
                // return whether the hardware is connect to internet
                return true;
            }

            @Override
            public int tuya_hal_wired_get_mac() {
                // get wired ethernet mac info
                return 0;
            }
        });
```

* 实现dp点接收回复。参考demo中的controllerManager.setDpEventSimpleCallback
	* 在`TUYA_DP_AP_MODE`和`TUYA_DP_AP_SWITCH`事件中，返回实际的AP信息

### 新增接口说明：
* 上线回调：和标准版SDK差异在于 通过`Common.MqttConnectStatus.GB_STAT_CLOUD_CONN`事件判断是否设备上线，从而进行推流操作。
* 行车记录仪相关API：CardvJNIApi类

```java

/**
     * start an event
     *
     * @param ipcChan        通道号
     * @param event_duration 持续时间
     * @return success: 0; fail: !0
     */
public static native int trigger_event_by_chan(int ipcChan, int event_duration);

/**
     * stop an event
     *
     * @param ipcChan 通道号
     * @return success: 0; fail: !0
     */
public static native int stop_event_by_chan(int ipcChan);


/**
     * start an event by type
     *
     * @param ipcChan 通道号
     * @param type    类型 CardvJNIApi.E_STORAGE_EVENT_TYPE
     * @return success: 0; fail: !0
     */
public static native int start_event_by_chan_type(int ipcChan, int type);

/**
     * stop an event by type
     * @param ipcChan 通道号
     * @param type 类型 CardvJNIApi.E_STORAGE_EVENT_TYPE
     * @return success: 0; fail: !0
     */
public static native int stop_event_by_chan_type(int ipcChan, int type);

/**
     * get album path
     * @param albumName album name
     * @return filePath: path stores video and pic；thumbnailPath: path stores thumbnail of video and pic
     */
public static native ALBUM_PATH album_get_path(String albumName);


/**
     * write info of newly added file
     * @param albumName album name
     * @param info newly added file info
     * @return success: 0; fail: !0
     */
public static native int album_write_file_info(String albumName, ALBUM_FILE_INFO info);


/**
     * set tuya-sdk inside timezone
     * @param timezone: "+/-hh:mm"
     * @return success: 0; fail: !0
     */
public static native int set_time_zone(String timezone);


/**
     * set time of tuya SDK
     * @param time
     * @return  success: 0; fail: !0
     */
public static native int set_service_time(String time);
```


## 如何获得技术支持
You can get support from Tuya with the following methods:

Tuya Smart Help Center: https://support.tuya.com/en/help  
Technical Support Council: https://iot.tuya.com/council/   

## 使用的开源License
This Tuya Android Device IPC SDK Sample is licensed under the MIT License.

