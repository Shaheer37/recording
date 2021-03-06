package com.android.shaheer.recording.utils;

import android.content.Context;
import android.media.MediaRecorder;
import android.os.Build;
import android.os.Handler;
import android.util.Log;

import java.io.File;
import java.text.DecimalFormat;

public class Recorder {
    private static final String TAG = Recorder.class.getSimpleName();

    private static final int HOUR_IN_MILLISECONDS = 60000;
    private static final int SECOND_IN_MILLISECONDS = 1000;
    private static final int HOUR_IN_MINUTES = 60;

    private static final int MAX_SUPPORTED_AMPLITUDE = 32767;

    private static final short DELAY_MILLI = 100;

    public enum RecordingStatus{
        initiated, recording, paused, ended
    }

    private Context mContext;

    private MediaRecorder mRecorder;

    private RecordingStatus mRecordingStatus = RecordingStatus.initiated;
    public RecordingStatus getmRecordingStatus() {return mRecordingStatus;}

    private boolean isPrepared = false;

    private long mDuration = 0;

    private File mOutputFile;
    public File getOutputFile() {return mOutputFile;}

    private int channels = Constants.Audio.CHANNELS_DEFAULT;
    private int bitrate = Constants.Audio.BIT_RATE_DEFAULT;

    private FilesUtil filesUtil;

    private static DecimalFormat df = new DecimalFormat("0.00");

    private Handler mHandler = new Handler();
    private Runnable mTickExecutor = new Runnable() {
        @Override
        public void run() {
            tick();
            mHandler.postDelayed(mTickExecutor,DELAY_MILLI);
        }
    };

    private RecorderTickListener mTickListener;


    public Recorder(
        Context context,
        int bitrate,
        int channels,
        RecorderTickListener tickListener
    ) {
        mContext = context;
        this.bitrate = bitrate;
        this.channels = channels;
        mTickListener = tickListener;

        filesUtil = new FilesUtil();
        mOutputFile = filesUtil.getFile(context, null, Constants.Audio.FILE_EXT_M4A);
    }

    public String getFileName() {
        return mOutputFile.getName();
    }

    public void startRecording(File outputFile) throws Exception {
        mRecorder = new MediaRecorder();
        mRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
        mRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
        mRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
        mRecorder.setAudioEncodingBitRate(bitrate);
        mRecorder.setAudioChannels(channels);
        mRecorder.setAudioSamplingRate(Constants.Audio.SAMPLE_RATE_441);
        mRecorder.setOutputFile(outputFile.getAbsolutePath());

        try {
            mRecorder.prepare();
            mRecorder.start();

            isPrepared = true;

            mHandler.postDelayed(mTickExecutor, DELAY_MILLI);
            mRecordingStatus = RecordingStatus.recording;
            Log.d(TAG,"started recording to "+outputFile.getAbsolutePath());
        } catch (Exception e) {
            isPrepared = false;
            Log.e(TAG, "prepare() failed "+e.getMessage());
            throw e;
        }
    }

    public void pauseRecording(){
        Log.e(TAG, "pauseRecording()");
        if(mRecordingStatus == RecordingStatus.recording) {
            mRecordingStatus = RecordingStatus.paused;
            if (Build.VERSION.SDK_INT >= 24) {
                mHandler.removeCallbacks(mTickExecutor);
                mRecorder.pause();
            } else {
                stopRecording();
            }
        }
    }

    public void resumeRecording() throws Exception {
        Log.e(TAG, "resumeRecording()");
        if(mRecordingStatus == RecordingStatus.paused) {
            mRecordingStatus = RecordingStatus.recording;
            if (Build.VERSION.SDK_INT >= 24) {
                mHandler.postDelayed(mTickExecutor, DELAY_MILLI);
                mRecorder.resume();
            } else {
                File file = filesUtil.getFile(mContext, null, Constants.Audio.FILE_EXT_M4A);
                filesUtil.addRecordingPiece(file);
                startRecording(file);
            }
        }
    }

    public void stopRecording() {
        if(mRecorder != null) {
            if(isPrepared) mRecorder.stop();

            mRecorder.release();
            mRecorder = null;

            mHandler.removeCallbacks(mTickExecutor);
            if (Build.VERSION.SDK_INT < 24) filesUtil.mergeFiles(mContext, mOutputFile, Constants.Audio.FILE_EXT_M4A);
        }
        else Log.e(TAG, "recorder is null");
    }

    private void tick() {

        if (mRecorder != null && mTickListener != null) {
            mDuration = mDuration + 100;
            String duration = getFormatedDuration();

            float maxAmp = (float)mRecorder.getMaxAmplitude();
            float maxAmpPercent = maxAmp/MAX_SUPPORTED_AMPLITUDE;
            Log.d(TAG, "Max Amp: "+maxAmp);
            Log.d(TAG, "Max Amp %: "+maxAmpPercent);

            mTickListener.onTick(duration, maxAmpPercent);
        }
    }

    public String getFormatedDuration(){
        int minutes = (int) (mDuration / HOUR_IN_MILLISECONDS);
        int seconds = (int) (mDuration / SECOND_IN_MILLISECONDS) % HOUR_IN_MINUTES;
        String duration = minutes+":"+(seconds<10 ? "0"+seconds : seconds);
//        Log.e(TAG, duration);
        return duration;
    }

    public interface RecorderTickListener{
        void onTick(String duration, float amp);
    }
}
