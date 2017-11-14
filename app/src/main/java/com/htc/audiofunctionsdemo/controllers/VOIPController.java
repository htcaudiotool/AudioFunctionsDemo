package com.htc.audiofunctionsdemo.controllers;

import android.media.AudioManager;
import android.media.AudioTrack;
import android.os.Handler;
import android.os.Message;
import android.util.Log;

import com.htc.audiofunctionsdemo.utils.Constants;
import com.htc.audiofunctionsdemo.utils.RecorderIO;
import com.htc.audiofunctionsdemo.utils.WatchDog;

import java.io.File;
import java.lang.ref.WeakReference;

public class VOIPController implements Controllable {

    public class VOIPControllerThread extends Thread implements WatchDog.Monitor{
        private final static String TAG = "VOIPControllerThread";
        private final int SAMPLE_RATE = Constants.VOIPConfig.RX.SAMPLING_RATE;
        private final int STREAM_TYPE = AudioManager.STREAM_VOICE_CALL;
        private final int CHANNEL = Constants.VOIPConfig.RX.CHANNEL_CONFIG;
        private final int FORMAT = Constants.VOIPConfig.RX.ENCODING_CONFIG;
        private final int MODE = AudioTrack.MODE_STREAM;
        private final int OUTPUT_FREQ = Constants.VOIPConfig.RX.OUTPUT_TONE_FREQ;
        private int mSize = 0;
        private short[] data;
        private Thread playbackThread = null;
        private int sampleNum = 0;
        private boolean isPlaying = false;
        private boolean isMuted = false;

        private AudioTrack mAudioTrack = null;
        private RecorderIO rec = null;

        Handler mRecordIOErrorHandle = new VOIPControllerThreadHandler(this);

        private int mPhoneMode;
        final private VOIPController mParent;
        final private VOIPControllerThread wd_lock;
        private boolean exitPending;
        private int cmd;

        public VOIPControllerThread(AudioManager m, VOIPController parent){
            mSize = AudioTrack.getMinBufferSize(SAMPLE_RATE, CHANNEL, FORMAT);
            mSize /= Constants.VOIPConfig.RX.BYTES_PER_ELEMENT;
            if (SAMPLE_RATE*Constants.VOIPConfig.RX.BUFFER_SIZE_MILLIS/1000*Constants.VOIPConfig.RX.NUM_CHANNELS > mSize)
                mSize = SAMPLE_RATE*Constants.VOIPConfig.RX.BUFFER_SIZE_MILLIS/1000*Constants.VOIPConfig.RX.NUM_CHANNELS;
            Log.d(TAG, "mSize : " + String.valueOf(mSize));

            data = new short[mSize];
            mPhoneMode = AudioManager.MODE_NORMAL;

            mAudioManager = m;
            mParent = parent;
            wd_lock = this;
            exitPending = false;
        }

        private void _start() {
            if (playbackThread != null) {
                Log.d(TAG, "VOIP already start, skip!");
                return;
            }
            Log.d(TAG, "VOIP playback START");
            mPhoneMode = AudioManager.MODE_IN_COMMUNICATION;
            synchronized(this.wd_lock) {
                mAudioManager.setMode(mPhoneMode);
            }

            synchronized(this.wd_lock) {
                rec = new RecorderIO(Constants.VOIPConfig.TX.CIRCULAR_BUFFER_SIZE_MILLIS, Constants.VOIPConfig.TX.BUFFER_SIZE_MILLIS);
                rec.setRecorderIOListener(listenerCache);
                rec.startRecord(path, mRecordIOErrorHandle);
            }

            sampleNum = 0;
            isPlaying = true;
            isMuted = false;

            synchronized(this.wd_lock) {
                if (mAudioTrack != null) {
                    mAudioTrack.stop();
                }
                mAudioTrack = new AudioTrack(STREAM_TYPE, SAMPLE_RATE, CHANNEL, FORMAT, mSize, MODE);
                mAudioTrack.setVolume(1.0f);

                class RxThread implements Runnable {
                    final private VOIPControllerThread lockChecker;
                    public RxThread(VOIPControllerThread lock){
                        lockChecker = lock;
                    }
                    @Override
                    public void run() {
                        //synchronized(lockChecker) {   /* watch dog functionality test */
                        while (isPlaying) {
                            writeAudioData();
                        }
                        synchronized(lockChecker) {
                            mAudioTrack.stop();
                            mAudioTrack.release();
                        }
                        mAudioTrack = null;
                        Log.d(TAG, "playback thread stop");
                    }
                }
                playbackThread = new Thread(new RxThread(this.wd_lock), "VoipPlaybackThread");
                playbackThread.start();
            }

            while (!playbackThread.isAlive()) {
                try {
                    Log.d(TAG, "wait playback thread start +++");
                    Thread.sleep(100);
                    Log.d(TAG, "wait playback thread start ---");
                } catch (InterruptedException e) {
                    // TODO Auto-generated catch block
                    e.printStackTrace();
                }
            }

            synchronized(this.wd_lock) {
                int maxStream = mAudioManager.getStreamMaxVolume(AudioManager.STREAM_VOICE_CALL);
                mAudioManager.setStreamVolume(AudioManager.STREAM_VOICE_CALL, maxStream, 0);
            }
        }

        private void writeAudioData() {
            for (int i = 0; i < mSize; i+=Constants.VOIPConfig.RX.NUM_CHANNELS) {
                short tmpdata = 0;
                double angularFreq = ((double) sampleNum * Math.PI*2) / SAMPLE_RATE * OUTPUT_FREQ;
                if (!isMuted) {
                    tmpdata = (short) (Math.sin(angularFreq) * (Constants.VOIPConfig.RX.NORMALIZATION_FACTOR-1));
                }
                for (int j = 0; j < Constants.VOIPConfig.RX.NUM_CHANNELS; j++)
                    data[i + j] = tmpdata;
                sampleNum++;
                // Log.d(TAG, String.valueOf(data[i]));
            }
            if (sampleNum >= SAMPLE_RATE)
                sampleNum -= SAMPLE_RATE;
            mAudioTrack.write(data, 0, mSize);
            if (mAudioTrack.getPlayState() != AudioTrack.PLAYSTATE_PLAYING)
                mAudioTrack.play();
        }

        private void _muteRx() {
            if (mAudioTrack != null) {
                isMuted = mParent.mute;
                synchronized(this.wd_lock) {
                    mAudioTrack.setVolume(0.0f);
                }
            }
        }

        private void _stop() {
            if (playbackThread == null) {
                Log.d(TAG, "VOIP already stop, skip!");
                return;
            }

            Log.d(TAG, "Voip playback STOP");
            mPhoneMode = AudioManager.MODE_NORMAL;
            synchronized(this.wd_lock) {
                mAudioManager.setMode(mPhoneMode);
            }

            isPlaying = false;
            while (playbackThread.isAlive()) {
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
            playbackThread = null;

            synchronized(this.wd_lock) {
                rec.stopRecord();
            }
            rec = null;
        }

        private void _switchToSpeaker() {
            synchronized (this.wd_lock) {
                mAudioManager.setSpeakerphoneOn(true);
            }
        }

        void _transTxToWav() {
            rec.transTxToWav();
        }

        private void setStop() {
            if (this.isAlive()){
                this.interrupt();
                exitPending = true;
            }
        }

        @Override
        public void monitor() {
            synchronized(this.wd_lock){}
        }

        @Override
        public void run(){
            while(!exitPending){
                synchronized (mParent) { // class VOIP lock
                    cmd = mParent.command;
                    mParent.command = CMD_NONE;
                }

                switch(cmd){
                    case CMD_NONE:
                        try {
                            sleep(500); //ms
                        } catch (InterruptedException e) {}
                        break;
                    case CMD_START:
                        this._start();
                        break;
                    case CMD_MUTE_RX:
                        this._muteRx();
                        break;
                    case CMD_TX_TO_WAV:
                        this._transTxToWav();
                        break;
                    case CMD_STOP:
                        this._stop();
                        break;
                    case CMD_SWITCH_SPKR:
                        this._switchToSpeaker();
                }
                cmd = CMD_NONE;
            }
            Log.d(TAG, "voip thraed exit");
        }
    } // end class voip_thread

    private static class VOIPControllerThreadHandler extends Handler {
        private WeakReference<VOIPControllerThread> mThreadRef;

        VOIPControllerThreadHandler(VOIPControllerThread th) {
            mThreadRef = new WeakReference<>(th);
        }

        @Override
        public void handleMessage(Message msg) {
            VOIPControllerThread th = mThreadRef.get();
            if (th != null) {
                if (msg.what == 1) {
                    // means recorderIO has error
                    if (th.rec.excep != null)
                        th.mParent.e = th.rec.excep;
                    if (th.rec.errorMsg != null)
                        th.mParent.errorMsg = th.rec.errorMsg;
                }
            }
            super.handleMessage(msg);
        }
    }

    private final static String TAG = "VOIPController";
    private AudioManager mAudioManager = null;
    public VOIPControllerThread thread;

    Exception e = null;
    String errorMsg = "";

    private String path;
    final private int CMD_NONE = 0;
    final private int CMD_START = 1;
    final private int CMD_STOP = 2;
    final private int CMD_MUTE_RX = 3;
    final private int CMD_TX_TO_WAV = 4;
    final private int CMD_SWITCH_SPKR = 5;
    private int command;
    private boolean mute;
    private RecorderIO.RecorderIOListener listenerCache;
    private Handler commHandler;

    public VOIPController(AudioManager m, Handler handler){
        mAudioManager = m;
        commHandler = handler;

        /*init thread*/
        command = CMD_NONE;
        mute = false;
        path = "";
        thread = new VOIPControllerThread(m, this);
        thread.start();
    }

    public void start() {
        //start_name("sdcard/Music/record_voip");
        synchronized (this) { // class VOIP lock
            path = Constants.VOIPConfig.TX.PCM_DUMP_FILENAME;
            command = CMD_START;
        }
    }

    public void start_name(String name) {
        synchronized (this) { // class VOIP lock
            path = name;
            command = CMD_START;
        }
    }

    public void stop() {
        synchronized (this) { // class VOIP lock
            command = CMD_STOP;
        }
    }

    public void muteRx(int mute) {
        synchronized (this) { // class VOIP lock
            this.mute = (mute != 0);
            command = CMD_MUTE_RX;
        }
    }

    public void switchToSpeaker() {
        synchronized (this) {
            command = CMD_SWITCH_SPKR;
        }
    }

    public void transTxToWav() {
        synchronized (this) { // class VOIP lock
            command = CMD_TX_TO_WAV;
        }
    }

    public void deleteFile() {
        if (path.equals("")) {
            path = Constants.VOIPConfig.TX.PCM_DUMP_FILENAME;
            File file = new File(path);
            file.delete();
        }
    }

    public int getPhonemode(){
        return mAudioManager.getMode();
    }

    public void clearError() {
        if (e != null) {
            e = null;
            stop();
        }
    }

    public void setRecorderIOListener(RecorderIO.RecorderIOListener listener) {
        listenerCache = listener;
    }

    @Override
    public void destroy() {
        thread.setStop();
    }
}
