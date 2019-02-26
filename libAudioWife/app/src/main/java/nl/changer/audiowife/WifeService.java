package nl.changer.audiowife;

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.media.AudioManager;
import android.media.MediaMetadata;
import android.media.MediaPlayer;
import android.media.Rating;
import android.media.session.MediaController;
import android.media.session.MediaSession;
import android.net.Uri;
import android.net.wifi.WifiManager;
import android.os.AsyncTask;
import android.os.Binder;
import android.os.Build;
import android.os.Build.VERSION_CODES;
import android.os.IBinder;
import android.os.PowerManager;
import android.support.v4.app.NotificationCompat;
import android.telephony.PhoneStateListener;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.util.TypedValue;
import android.view.View;
import android.widget.SeekBar;
import android.widget.TextView;

import com.antoniotari.audiosister.GetBitmapAsyncTask;
import com.antoniotari.audiosister.models.Song;

import static nl.changer.audiowife.WifeService.MediaActions.ACTION_FAST_FORWARD;
import static nl.changer.audiowife.WifeService.MediaActions.ACTION_NEXT;
import static nl.changer.audiowife.WifeService.MediaActions.ACTION_PAUSE;
import static nl.changer.audiowife.WifeService.MediaActions.ACTION_PLAY;
import static nl.changer.audiowife.WifeService.MediaActions.ACTION_PREVIOUS;
import static nl.changer.audiowife.WifeService.MediaActions.ACTION_REWIND;
import static nl.changer.audiowife.WifeService.MediaActions.ACTION_STOP;


/**
 * Created by Antonio Tari on 23/12/14.
 */
public class WifeService extends Service implements AudioListener,ForegroundNotificationListener, MediaPlayer.OnPreparedListener,MediaPlayer.OnErrorListener,AudioManager.OnAudioFocusChangeListener {

    //private MediaSessionManager mManager;
    private MediaSession mSession;
    private MediaController mController;
    private Song mCurrentSong = null;
    private PendingIntent mActivityPendingIntent;

    private Notification _currentNotification;
    private WifiManager.WifiLock wifiLock;
    private final IBinder mBinder = new WifeBinder();
    private final WifePhoneStateListener phoneStateListener = new WifePhoneStateListener();
    private NotificationControlsListener notificationControlsListener;
    private AsyncTask<String,Void,Bitmap> bitmapAsyncTask;


    static class MediaActions {
        static final String ACTION_PLAY = "action_play";
        static final String ACTION_PAUSE = "action_pause";
        static final String ACTION_REWIND = "action_rewind";
        static final String ACTION_FAST_FORWARD = "action_fast_foward";
        static final String ACTION_NEXT = "action_next";
        static final String ACTION_PREVIOUS = "action_previous";
        static final String ACTION_STOP = "action_stop";
    }

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

    public void setNotificationControlsListener(NotificationControlsListener notificationControlsListener) {
        this.notificationControlsListener = notificationControlsListener;
    }

    private void generatePlayNotification() {
        buildNotification(generateAction( android.R.drawable.ic_media_pause, "Pause", ACTION_PAUSE ) );
    }
    private void generatePauseNotification() {
        Log.d("MediaPlayerService","generatePauseNotification");
        buildNotification(generateAction(android.R.drawable.ic_media_play, "Play", ACTION_PLAY));
    }

    private MediaPlayer mediaPlayer() {
        return AudioWife.getInstance().getMediaPlayer();
    }

    private void initMediaPlayer() {
        if(mediaPlayer()!=null) {
            mediaPlayer().setWakeMode(getApplicationContext(), PowerManager.PARTIAL_WAKE_LOCK);
            wifiLock = ((WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE))
                    .createWifiLock(WifiManager.WIFI_MODE_FULL, "wifelock");
            wifiLock.acquire();

            //mMediaPlayer.setOnPreparedListener(this);
            //mMediaPlayer.setOnErrorListener(this);
            //mMediaPlayer.prepareAsync(); // prepare async to not block main thread
        }

        if (mController==null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            //mManager = (MediaSessionManager) getSystemService(Context.MEDIA_SESSION_SERVICE);
        }

    }

    @TargetApi(Build.VERSION_CODES.KITKAT_WATCH)
    private Notification.Action generateAction(int icon, String title, String intentAction ) {
        Intent intent = new Intent( getApplicationContext(), WifeService.class );
        intent.setAction(intentAction);
        PendingIntent pendingIntent = PendingIntent.getService(getApplicationContext(), 1, intent, 0);
        return new Notification.Action.Builder(icon, title, pendingIntent).build();
    }

    @SuppressLint ("StaticFieldLeak")
    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    private void buildNotification(final Notification.Action action ) {

        mSession = new MediaSession(getApplicationContext(), "wifeservicesession");
        mController = new MediaController(getApplicationContext(), mSession.getSessionToken());

        if (mCurrentSong == null) {
            //mCurrentSong= new Song("","");
            return;
        }

        if (mCurrentSong.getArt() == null && mCurrentSong.getArtUrl()!=null) {
            if (bitmapAsyncTask != null && !bitmapAsyncTask.isCancelled()) {
                bitmapAsyncTask.cancel(true);
            }

            bitmapAsyncTask = new GetBitmapAsyncTask(){
                @Override
                protected void onPostExecute(Bitmap bitmap) {
                    super.onPostExecute(bitmap);
                    // do not recursively call buildNotification if there's no image
                    if (bitmap != null) {
                        mCurrentSong.setArt(bitmap);
                        buildNotification(action);
                    }
                }
            };

            bitmapAsyncTask.execute(mCurrentSong.getArtUrl());
        }

        mSession.setMetadata(new MediaMetadata.Builder()
                .putBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART, mCurrentSong.getArt())
                .putString(MediaMetadata.METADATA_KEY_ARTIST, mCurrentSong.getArtist())
                .putString(MediaMetadata.METADATA_KEY_ALBUM, mCurrentSong.getAlbum())
                .putString(MediaMetadata.METADATA_KEY_TITLE, mCurrentSong.getTitle())
                .build());
        // Indicate you're ready to receive media commands
        mSession.setActive(true);
        mSession.setCallback(new MediaSessionCallbacks());
        // Indicate you want to receive transport controls via your Callback
        mSession.setFlags(MediaSession.FLAG_HANDLES_TRANSPORT_CONTROLS);

        Intent intent = new Intent(getApplicationContext(), WifeService.class);
        intent.setAction(ACTION_STOP);
        PendingIntent pendingIntent = PendingIntent.getService(getApplicationContext(), 1, intent, 0);
        Notification.Builder builder = new Notification.Builder( this )
                .setStyle(new Notification.MediaStyle()
                        // Attach our MediaSession token
                        .setMediaSession(mSession.getSessionToken())
                        // Show our playback controls in the compat view
                        .setShowActionsInCompactView(0,1,2))
                .setSmallIcon(R.drawable.ic_launcher)
                .setContentTitle(mCurrentSong.getTitle())
                .setContentText(mCurrentSong.getArtist())
                .setVisibility(Notification.VISIBILITY_PUBLIC)
                .setLargeIcon(mCurrentSong.getArt())
                .setDeleteIntent(pendingIntent)
                .setShowWhen(false)
                .setColor(getThemeAccentColor());

        if (mActivityPendingIntent != null) {
            builder.setContentIntent(mActivityPendingIntent);
        }

        builder.addAction(generateAction(android.R.drawable.ic_media_previous, "Previous", ACTION_PREVIOUS));
        //builder.addAction( generateAction( android.R.drawable.ic_media_rew, "Rewind", ACTION_REWIND ) );
        builder.addAction(action);
        //builder.addAction( generateAction( android.R.drawable.ic_media_ff, "Fast Foward", ACTION_FAST_FORWARD ) );
        builder.addAction(generateAction(android.R.drawable.ic_media_next, "Next", ACTION_NEXT));
        //style.setShowActionsInCompactView(0,1,2,3,4);

        NotificationManager notificationManager = (NotificationManager)getSystemService(Context.NOTIFICATION_SERVICE);
        notificationManager.notify(1, builder.build());
    }

    public int getThemeAccentColor () {
        final TypedValue value = new TypedValue();
        getTheme().resolveAttribute (R.attr.colorAccent, value, true);
        return value.data;
    }

    /**
     * If the startService(intent) method is called and the service is not yet running,
     * the service object is created and the onCreate() method of the service is called.
     */
    @Override
    public void onCreate(){
        super.onCreate();

        // REGISTER RECEIVER THAT HANDLES SCREEN ON AND SCREEN OFF LOGIC
        // to show the notification on lock screen
        if (Build.VERSION.SDK_INT < VERSION_CODES.O) {
            IntentFilter filter = new IntentFilter(Intent.ACTION_SCREEN_ON);
            filter.addAction(Intent.ACTION_SCREEN_OFF);
            BroadcastReceiver receiver = new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    if (mediaPlayer() != null && mediaPlayer().isPlaying()) {
                        generatePlayNotification();
                    }
                }
            };
            registerReceiver(receiver, filter);
        }
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

            handleIntent(intent);
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

    private boolean wasPlayingBeforeAudioFocusChange = false;

    @Override
    public void onAudioFocusChange(int focusChange) {
        switch (focusChange) {
            case AudioManager.AUDIOFOCUS_GAIN:
                // resume playback
                if (mediaPlayer() == null) {
                    initMediaPlayer();
                }
                else if (!mediaPlayer().isPlaying()) {
                    //mediaPlayer().start();
                }
                mediaPlayer().setVolume(1.0f, 1.0f);
                break;

            case AudioManager.AUDIOFOCUS_LOSS:
                // Lost focus for an unbounded amount of time: stop playback and release media player
                if (mediaPlayer() != null && mediaPlayer().isPlaying()) {
                    mediaPlayer().stop();
                }
                if (mediaPlayer() != null) {
                    mediaPlayer().release();
                }
                break;

            case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT:
                // Lost focus for a short time, but we have to stop
                // playback. We don't release the media player because playback
                // is likely to resume
                if (mediaPlayer().isPlaying()) {
                    //mediaPlayer().pause();
                    mediaPlayer().setVolume(0.1f, 0.1f);
                }
                break;

            case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK:
                // Lost focus for a short time, but it's ok to keep playing
                // at an attenuated level
                if (mediaPlayer().isPlaying()) {
                    mediaPlayer().setVolume(0.1f, 0.1f);
                }
                break;
        }
    }

    private void clear() {
        try {
            if (bitmapAsyncTask != null && !bitmapAsyncTask.isCancelled()) {
                bitmapAsyncTask.cancel(true);
                bitmapAsyncTask = null;
            }

            if (mediaPlayer() != null) mediaPlayer().release();
            if(wifiLock!=null)wifiLock.release();
            AudioWife.getInstance().release();
            if (mSession!=null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                mSession.release();
            }
            unregisterPhoneCallListener();

            NotificationManager notificationManager = (NotificationManager)getSystemService(Context.NOTIFICATION_SERVICE);
            notificationManager.cancel(1);
        }catch (Exception e) {
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.d("MediaPlayerService","onDestroy");
        clear();
    }

    @Override
    public boolean onUnbind(Intent intent) {
        Log.d("MediaPlayerService","onUnbind");
        clear();
        return super.onUnbind(intent);
    }

    private PendingIntent createActivityPendingIntent(Class theActivity) {
        return PendingIntent.getActivity(getApplicationContext(), 0,
                new Intent(getApplicationContext(), theActivity),
                PendingIntent.FLAG_UPDATE_CURRENT);
    }

    private void showForegroundControls(Class theActivity, String songName, int iconRes){
        // assign the song name to songName
        PendingIntent pi = createActivityPendingIntent(theActivity);
        _currentNotification = new NotificationCompat.Builder(this)
                .setContentIntent(pi)
                .setSmallIcon(iconRes)
                .setTicker(songName)
                .setContentTitle(getApplicationInfo().loadLabel(getPackageManager()).toString())
                .setContentText("Playing: " + songName)
                .addAction(NotificationMediaControls.getPlayAction(this))
                .addAction(NotificationMediaControls.getPauseAction(this))
                .build();
        _currentNotification.flags |= Notification.FLAG_ONGOING_EVENT;
        showNotification();
    }

    private void showNotification(){
//        if(_currentNotification!=null){
//            startForeground(11111, _currentNotification);
//        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            generatePlayNotification();
        } else if(_currentNotification!=null) {
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
    public void play(Class theActivity, Song song, int durationSeconds, int iconRes) {
        Log.d("MediaPlayerService","play");
        mCurrentSong = song;
        mActivityPendingIntent = createActivityPendingIntent(theActivity);
        AudioWife.getInstance().setDuration(durationSeconds * 1000L);
        AudioWife.getInstance().play();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            generatePlayNotification();
        } else {
            showForegroundControls(theActivity, song.getArtist() +" - "+song.getTitle(), iconRes);
        }
        registerPhoneCallListener();
    }

    @Override
    public void pause() {
        Log.d("MediaPlayerService","pause");
        AudioWife.getInstance().pause();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            generatePauseNotification();
        } else {
            stopForeground(true);
        }
        unregisterPhoneCallListener();
    }

    @Override
    public void release() {
        AudioWife.getInstance().release();
        stopForeground(true);
        unregisterPhoneCallListener();
    }

    public MediaPlayer getMediaPlayer() {
        return AudioWife.getInstance().getMediaPlayer();
    }

    @Override
    public void addForeground() {
        //the notification is initialized on play from the service
        //the listener only takes care of adding/removing an existing notification using the AudioWife buttons
        showNotification();
        Log.d("MediaPlayerService","addForeground");
    }

    @Override
    public void removeForeground() {

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            generatePauseNotification();
        } else {
            stopForeground(true);
        }
    }


    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    private void handleIntent(Intent intent ) {
        if( intent == null || intent.getAction() == null || mController == null) {
            return;
        }

        final String action = intent.getAction();

        if(action.equalsIgnoreCase(ACTION_PLAY)) {
            mController.getTransportControls().play();
        } else if(action.equalsIgnoreCase(ACTION_PAUSE)) {
            mController.getTransportControls().pause();
        } else if(action.equalsIgnoreCase(ACTION_FAST_FORWARD)) {
            mController.getTransportControls().fastForward();
        } else if(action.equalsIgnoreCase(ACTION_REWIND)) {
            mController.getTransportControls().rewind();
        } else if(action.equalsIgnoreCase(ACTION_PREVIOUS)) {
            mController.getTransportControls().skipToPrevious();
        } else if(action.equalsIgnoreCase(ACTION_NEXT)) {
            mController.getTransportControls().skipToNext();
        } else if(action.equalsIgnoreCase(ACTION_STOP)) {
            mController.getTransportControls().stop();
        }
    }

    public class WifeBinder extends Binder {
        public WifeService getService() {
            return WifeService.this;
        }
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    class MediaSessionCallbacks extends MediaSession.Callback {
        @Override
        public void onPlay() {
            super.onPlay();
            Log.e( "MediaPlayerService", "onPlay");
            if (notificationControlsListener != null) {
                notificationControlsListener.onPlay();
            }
            AudioWife.getInstance().play();
            registerPhoneCallListener();
            generatePlayNotification();
        }

        @Override
        public void onPause() {
            super.onPause();
            Log.e( "MediaPlayerService", "onPause");
            if (notificationControlsListener != null) {
                notificationControlsListener.onPause();
            }
            pause();
        }

        @Override
        public void onSkipToNext() {
            super.onSkipToNext();
            Log.e( "MediaPlayerService", "onSkipToNext");
            if (notificationControlsListener != null) {
                notificationControlsListener.onNext();
            }
            buildNotification( generateAction(android.R.drawable.ic_media_pause, "Pause", ACTION_PAUSE ) );
        }

        @Override
        public void onSkipToPrevious() {
            super.onSkipToPrevious();
            Log.e( "MediaPlayerService", "onSkipToPrevious");
            if (notificationControlsListener != null) {
                notificationControlsListener.onPrevious();
            }
            buildNotification( generateAction( android.R.drawable.ic_media_pause, "Pause", ACTION_PAUSE ) );
        }

        @Override
        public void onFastForward() {
            super.onFastForward();
            Log.e( "MediaPlayerService", "onFastForward");
            //Manipulate current media here
        }

        @Override
        public void onRewind() {
            super.onRewind();
            Log.e( "MediaPlayerService", "onRewind");
            //Manipulate current media here
        }

        @Override
        public void onStop() {
            super.onStop();
            Log.e( "MediaPlayerService", "onStop");
            if (notificationControlsListener != null) {
                notificationControlsListener.onStop();
            }
            pause();
            //Stop media player here
            NotificationManager notificationManager = (NotificationManager) getApplicationContext().getSystemService(Context.NOTIFICATION_SERVICE);
            notificationManager.cancel(1);
            Intent intent = new Intent( getApplicationContext(), WifeService.class);
            stopService(intent);
        }

        @Override
        public void onSeekTo(long pos) {
            super.onSeekTo(pos);
        }

        @Override
        public void onSetRating(Rating rating) {
            super.onSetRating(rating);
        }
    }
}
