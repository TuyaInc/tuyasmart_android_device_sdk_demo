package com.tuya.ai.ipcsdkdemo.video;

import android.content.Context;

import com.tuya.smart.aiipc.ipc_sdk.api.IMediaTransManager;
import com.tuya.smart.aiipc.ipc_sdk.service.IPCServiceManager;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;

public class FileAudioCapture {

    private final static int AUDIO_FRAME_SIZE = 640;
    private final static int AUDIO_FPS = 25;


    private InputStream fis;

    private byte[] pcmBuffer;

    IMediaTransManager transManager;

    Context context;

    private int mChannel;

    public FileAudioCapture(Context context, int channel) {

        this.mChannel = channel;
        this.context = context;
        try {
            fis = context.getAssets().open("rawfiles/jupiter_8k_16bit_mono.raw");
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
        pcmBuffer = new byte[AUDIO_FRAME_SIZE];

        transManager = IPCServiceManager.getInstance().getService(IPCServiceManager.IPCService.MEDIA_TRANS_SERVICE);
    }

    public void startFileCapture() {

        new Thread(new Runnable() {
            @Override
            public void run() {

                while (true) {
                    try {
                        int size = fis.read(pcmBuffer);
                        if (size < AUDIO_FRAME_SIZE) {
                            fis.reset();
                        }
                    } catch (IOException e) {
                        e.printStackTrace();
                    }finally {

                    }
                    transManager.pushMediaStream(mChannel, 0, pcmBuffer);

                    int frameRate = AUDIO_FPS;
                    int sleepTick = 1000 / frameRate;

                    try {
                        Thread.sleep(sleepTick);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
            }
        }).start();
    }

    public void stopFileCapture() {
        try {
            fis.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
