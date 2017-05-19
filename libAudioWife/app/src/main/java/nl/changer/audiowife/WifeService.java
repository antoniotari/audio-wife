package nl.changer.audiowife;

import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.net.Uri;
import android.net.wifi.WifiManager;
import android.os.Binder;
import android.os.IBinder;
import android.os.PowerManager;
import android.support.v7.app.NotificationCompat;
import android.telephony.PhoneStateListener;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.view.View;
import android.widget.SeekBar;
import android.widget.TextView;


/**
 * Created by Antonio Tari on 23/12/14.
 */
public class WifeService extends Service implements AudioListener,ForegroundNotificationListener, MediaPlayer.OnPreparedListener,MediaPlayer.OnErrorListener,AudioManager.OnAudioFocusChangeListener {
    private static final String ACTION_PLAY = "com.example.action.PLAY";
    private MediaPlayer mMediaPlayer=null;
    Notification _currentNotification;
    WifiManager.WifiLock wifiLock;
    private final IBinder mBinder = new WifeBinder();
    private WifePhoneStateListener phoneStateListener = new WifePhoneStateListener();

    private class WifePhoneStateListener extends PhoneStateListener {
        /**
         * Callback invoked when device call state changes.
         * @param state call state
         * @param incomingNumber incoming call phone number. If application does not have
         * {@link android.Manifest.permission#READ_PHONE_STATE READ_PHONE_STATE} permission, an empty
         * string will be passed as an argument.
         *
         * @see TelephonyManager#CALL_STATE_IDLE
         * @see TelephonyManager#CALL_STATE_RINGING
         * @see TelephonyManager#CALL_STATE_OFFHOOK
         */
        public void onCallStateChanged(int state, String incomingNumber) {
            // default implementation empty
            if(state == TelephonyManager.CALL_STATE_RINGING) {
                pause();
            }
        }
    }


    private void initMediaPlayer(){
        mMediaPlayer=AudioWife.getInstance().getMediaPlayer();
        if(mMediaPlayer!=null) {
            mMediaPlayer.setWakeMode(getApplicationContext(), PowerManager.PARTIAL_WAKE_LOCK);
            wifiLock = ((WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE))
                    .createWifiLock(WifiManager.WIFI_MODE_FULL, "mylock");
            wifiLock.acquire();

            //mMediaPlayer.setOnPreparedListener(this);
            //mMediaPlayer.setOnErrorListener(this);
            //mMediaPlayer.prepareAsync(); // prepare async to not block main thread
        }
        registerPhoneCallListener();
    }

    /**
     * If the startService(intent) method is called and the service is not yet running,
     * the service object is created and the onCreate() method of the service is called.
     */
    @Override
    public void onCreate(){
        super.onCreate();
    }

    /**
     * Once the service is started(onCreate), the onStartCommand(intent) method in the service is called.
     * It passes in the Intent object from the startService(intent) call.
     *
     * If startService(intent) is called while the service is running, its onStartCommand() is
     * called. Therefore your service needs to be prepared that onStartCommand() can be called
     * several times.
     * @param intent
     * @param flags
     * @param startId
     * @return
     */
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d("wifeservice","onStartCommand");
        //check if the service was restarted
        if(intent!=null) {
            int flagStart = intent.getFlags();
            switch (flagStart) {
                case START_FLAG_REDELIVERY:
                    //in case the service was started with Service.START_REDELIVER_INTENT
                    break;
                case START_FLAG_RETRY:
                    //in case the service was started with Service.START_STICKY
                    break;
                default:
                    //not restarted
            }
        }


        AudioManager audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        int result = audioManager.requestAudioFocus(this, AudioManager.STREAM_MUSIC,
                AudioManager.AUDIOFOCUS_GAIN);

        if (result != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
            // could not get audio focus.
        }

        //if (intent.getAction().equals(ACTION_PLAY)) {
            //initMediaPlayer();
        //}

        return Service.START_STICKY;

    }

    @Override
    public IBinder onBind(Intent arg0) {
        Log.d("wifeservice","onBind");
        return mBinder;
    }

    /** Called when MediaPlayer is ready */
    public void onPrepared(MediaPlayer player) {
        //player.start();
    }

    @Override
    public boolean onError(MediaPlayer mediaPlayer, int i, int i2) {
        return false;
    }

    private void registerPhoneCallListener() {
        TelephonyManager telephonyManager = (TelephonyManager) getSystemService(TELEPHONY_SERVICE);
        telephonyManager.listen(phoneStateListener, PhoneStateListener.LISTEN_CALL_STATE);
    }

    private void unregisterPhoneCallListener() {
        TelephonyManager telephonyManager = (TelephonyManager) getSystemService(TELEPHONY_SERVICE);
        telephonyManager.listen(phoneStateListener, PhoneStateListener.LISTEN_NONE);
    }

    @Override
    public void onAudioFocusChange(int focusChange) {
        switch (focusChange) {
            case AudioManager.AUDIOFOCUS_GAIN:
                // resume playback
                if (mMediaPlayer == null) initMediaPlayer();
                else if (!mMediaPlayer.isPlaying()) mMediaPlayer.start();
                mMediaPlayer.setVolume(1.0f, 1.0f);
                break;

            case AudioManager.AUDIOFOCUS_LOSS:
                // Lost focus for an unbounded amount of time: stop playback and release media player
                if (mMediaPlayer.isPlaying()) mMediaPlayer.stop();
                mMediaPlayer.release();
                mMediaPlayer = null;
                break;

            case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT:
                // Lost focus for a short time, but we have to stop
                // playback. We don't release the media player because playback
                // is likely to resume
                if (mMediaPlayer.isPlaying()) mMediaPlayer.pause();
                break;

            case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK:
                // Lost focus for a short time, but it's ok to keep playing
                // at an attenuated level
                if (mMediaPlayer.isPlaying()) mMediaPlayer.setVolume(0.1f, 0.1f);
                break;
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.d("wifeservice","onDestroy");

        try {
            if (mMediaPlayer != null) mMediaPlayer.release();
            if(wifiLock!=null)wifiLock.release();
            AudioWife.getInstance().release();
        }catch (IllegalStateException e) {
        }
    }

    private void showForegroundControls(Class theActivity,String songName,int iconRes){
        // assign the song name to songName
        PendingIntent pi = PendingIntent.getActivity(getApplicationContext(), 0,
                new Intent(getApplicationContext(), theActivity),
                PendingIntent.FLAG_UPDATE_CURRENT);
        //_currentNotification = new Notification();
        //_currentNotification.tickerText = songName;
        //_currentNotification.icon = iconRes;
        //_currentNotification.flags |= Notification.FLAG_ONGOING_EVENT;
        //_currentNotification.setLatestEventInfo(getApplicationContext(), getApplicationInfo().loadLabel(getPackageManager()).toString(),
        //        "Playing: " + songName, pi);

        _currentNotification = new NotificationCompat.Builder(this)
                .setContentIntent(pi)
                .setSmallIcon(iconRes)
                .setTicker(songName)
                .setContentTitle(getApplicationInfo().loadLabel(getPackageManager()).toString())
                .setContentText("Playing: " + songName)
                .build();
        _currentNotification.flags |= Notification.FLAG_ONGOING_EVENT;
        showNotification();
    }

    private void showNotification(){
        if(_currentNotification!=null){
            startForeground(11111, _currentNotification);

        }
    }

    @Override
    public void init(String currentUrl,View playBtn,View stopBtn,SeekBar seekBar,TextView elapsed,TextView totalTime,MediaPlayer.OnCompletionListener completionListener) {
        AudioWife.getInstance().init(this, Uri.parse(currentUrl!=null?currentUrl:"") /*new Uri.Builder().path(currentUrl).build()*/)
                //.useDefaultUi(_mainLayout, getLayoutInflater());
                .setPlayView(playBtn)
                .setPauseView(stopBtn)
                .setSeekBar(seekBar)
                .setRuntimeView(elapsed)
                .setTotalTimeView(totalTime)
                .addOnCompletionListener(completionListener);
        initMediaPlayer();
        //set the notification listener on audiowife
        AudioWife.getInstance().setForegroundNotificationListener(this);
    }

    @Override
    public void play(Class theActivity,String songName,int durationSeconds, int iconRes) {
        AudioWife.getInstance().setDuration(durationSeconds * 1000L);
        AudioWife.getInstance().play();
        showForegroundControls(theActivity,songName,iconRes);
    }

    @Override
    public void pause() {
        AudioWife.getInstance().pause();
        stopForeground(true);
    }

    @Override
    public void release() {
        AudioWife.getInstance().release();
        stopForeground(true);
    }

    public MediaPlayer getMediaPlayer(){
        return AudioWife.getInstance().getMediaPlayer();
    }

    @Override
    public void addForeground() {
        //the notification is initialized on play from the service
        //the listener only takes care of adding/removing an existing notification using the AudioWife buttons
        showNotification();
    }

    @Override
    public void removeForeground() {
        stopForeground(true);
    }

    public class WifeBinder extends Binder {
        public WifeService getService() {
            return WifeService.this;
        }
    }
}