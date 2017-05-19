package com.antoniotari.audiosister;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.media.MediaPlayer;
import android.os.IBinder;
import android.util.Log;
import android.view.View;
import android.widget.SeekBar;
import android.widget.TextView;

import java.lang.ref.WeakReference;

import nl.changer.audiowife.AudioWife;
import nl.changer.audiowife.WifeService;

/**
 * Created by Antonio Tari on 29/12/14.
 */
public class AudioSister {
    private static AudioSister _instance=null;
    private WifeService wifeService;
    private WeakReference<View> weakPlay;
    private WeakReference<View> weakStop;
    private WeakReference<TextView> weakTotalTime;
    private WeakReference<TextView> weakElapsedTime;
    private WeakReference<SeekBar> weakSeekBar;
    private String playUrl;
    private String currentNotificationText="";
    private int currentDurationSeconds = -1;
    private Class currentActivityClass;
    private int currentForegroundDrawable;
    private MediaPlayer.OnCompletionListener completionListener;
    private AudioSisterListener audioSisterListener;

    private AudioSister(){
    }

    private ServiceConnection mConnection = new ServiceConnection() {

        public void onServiceConnected(ComponentName className,IBinder binder) {
            WifeService.WifeBinder b = (WifeService.WifeBinder) binder;
            wifeService = b.getService();
            initializeWifeService();
            if(audioSisterListener!=null) {
                audioSisterListener.onInitComplete(playUrl,weakPlay.get());
            }
        }

        public void onServiceDisconnected(ComponentName className) {
            wifeService = null;
        }
    };

    public static AudioSister getInstance(){
        if(_instance==null){
            _instance=new AudioSister();
        }
        return _instance;
    }

//    private void initializeWifeServicePlayStop(){
//        initializeWifeService();
//        wifeService.play(currentActivityClass, currentNotificationText, currentForegroundDrawable);
//        wifeService.pause();
//    }

    private void initializeWifeService() {
        wifeService.init(playUrl,weakPlay.get(),weakStop.get(),weakSeekBar.get(),weakElapsedTime.get(),weakTotalTime.get(),completionListener);
    }

    public void init(Context context,String currentUrl,View playBtn,View stopBtn,SeekBar seekBar,TextView elapsed,TextView totalTime, Class activityToLoadOnNotification,int notificationDrawable,MediaPlayer.OnCompletionListener completionListener,AudioSisterListener audioSisterListener){
        playUrl=currentUrl;
        this.audioSisterListener=audioSisterListener;
        currentForegroundDrawable=notificationDrawable;
        currentActivityClass=activityToLoadOnNotification;
        weakPlay=new WeakReference<View>(playBtn);
        weakStop=new WeakReference<View>(stopBtn);
        weakSeekBar=new WeakReference<SeekBar>(seekBar);
        weakElapsedTime=new WeakReference<TextView>(elapsed);
        weakTotalTime=new WeakReference<TextView>(totalTime);
        this.completionListener=completionListener;
        if(wifeService==null) {
            Intent intent = new Intent(context, WifeService.class);
            context.bindService(intent, mConnection, Context.BIND_AUTO_CREATE);
        }
        else{
            initializeWifeService();
            if(audioSisterListener!=null) {
                audioSisterListener.onInitComplete(playUrl,weakPlay.get());
            }
        }
    }

    public void kill() {
        if(wifeService!=null){
            wifeService.stopSelf();
            wifeService = null;
            AudioWife.getInstance().release();
        }
    }

    public void playNew(String streamUrl, String notificationText, int durationSeconds){
        if(wifeService!=null) {
            currentNotificationText = notificationText;
            currentDurationSeconds = durationSeconds;
            playUrl = streamUrl;
            initializeWifeService();
            wifeService.play(currentActivityClass, currentNotificationText, currentDurationSeconds, currentForegroundDrawable);
        }
    }

    public void playCurrent(){
        if(wifeService!=null) {
            wifeService.play(currentActivityClass, currentNotificationText, currentDurationSeconds, currentForegroundDrawable);
        }
    }

    public void pause(){
        if(wifeService!=null)
            wifeService.pause();
    }

    public void destroy(Context context){
        if(wifeService!=null) {
            wifeService.release();
            //wifeService.stopSelf();
            wifeService=null;
        }
        if (mConnection != null) {
            try {
                Log.d("wifeService", "Focus onDestroy() attempted to unbind service");
                context.unbindService(mConnection);
                //mConnection = null;
            }catch (Exception e){}
        }
    }

    public String getPlayUrl(){
        return playUrl;
    }

    public String getCurrentNotificationText() {
        return currentNotificationText;
    }

    public void setCurrentForegroundDrawable(int currentForegroundDrawable) {
        this.currentForegroundDrawable = currentForegroundDrawable;
    }
}
