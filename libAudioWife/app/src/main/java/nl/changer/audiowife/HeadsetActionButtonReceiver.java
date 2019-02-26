package nl.changer.audiowife;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.media.AudioManager;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.KeyEvent;
import android.widget.Toast;

import java.lang.ref.WeakReference;
import java.util.Timer;
import java.util.TimerTask;

/**
 * Broadcast receiver which receives headset action button events.
 * For this to work, you need to add the following in your manifest,
 * by replacing .receivers.HeadsetActionButtonReceiver with the relative
 * class path of where you will put this file:
 * <receiver android:name=".receivers.HeadsetActionButtonReceiver" >
 * <intent-filter android:priority="10000" >
 * <action android:name="android.intent.action.MEDIA_BUTTON" />
 * </intent-filter>
 * </receiver>
 * <p>
 * Then, in the activity in which you are going to use it:
 * - implement HeadsetActionButtonReceiver.Delegate methods
 * - in the onResume add:
 * HeadsetActionButtonReceiver.delegate = this;
 * HeadsetActionButtonReceiver.register(this);
 * - in the onPause add:
 * HeadsetActionButtonReceiver.unregister(this);
 * And that's all.
 *
 * @author gotev Aleksandar Gotev
 */
public class HeadsetActionButtonReceiver extends BroadcastReceiver {

    @SuppressWarnings("FieldCanBeLocal")
    private static int doublePressSpeed = 400; // double keypressed in ms

    public static Delegate delegate;

    private static AudioManager audioManager;
    private static ComponentName remoteControlResponder;

    private static Timer doublePressTimer;
    private static int counter;

    public interface Delegate {
        void onMediaButtonSingleClick();
        void onMediaButtonDoubleClick();
        void onMediaButtonTripleClick();
    }

    @Override
    public void onReceive(Context context, Intent intent) {

        if (intent == null || delegate == null || !Intent.ACTION_MEDIA_BUTTON.equals(intent.getAction())) {
            return;
        }

        final KeyEvent keyEvent = (KeyEvent) intent.getExtras().get(Intent.EXTRA_KEY_EVENT);

        // BLUETOOTH DEBUG CODE
//        if (keyEvent != null) {
//            final WeakReference<Context> weakReference = new WeakReference<>(context);
//            new Handler(Looper.getMainLooper()).post(new Runnable() {
//                @Override
//                public void run() {
//                    Toast.makeText(weakReference.get(),"key event: "+keyEvent.toString(), Toast.LENGTH_LONG).show();
//                }
//            });
//        }

        if (keyEvent == null || keyEvent.getAction() != KeyEvent.ACTION_DOWN) {
            return;
        }
        
        switch (keyEvent.getKeyCode()) {
            case KeyEvent.KEYCODE_MEDIA_PLAY:
                delegate.onMediaButtonSingleClick();
                return;
                break;
            case KeyEvent.KEYCODE_MEDIA_PAUSE:
                delegate.onMediaButtonSingleClick();
                return;
                break;
            case KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE:
                delegate.onMediaButtonSingleClick();
                return;
                break;
            case KeyEvent.KEYCODE_MEDIA_NEXT:
                delegate.onMediaButtonDoubleClick();
                return;
                break;
            case KeyEvent.KEYCODE_MEDIA_PREVIOUS:
                delegate.onMediaButtonTripleClick();
                return;
                break;
        }

        counter++;
        if (doublePressTimer != null) {
            // Cancel the timer if it is running
            doublePressTimer.cancel();
        }
        doublePressTimer = new Timer();
        doublePressTimer.schedule(new TimerTask() {

            @Override
            public void run() {
                if (counter == 1) {
                    delegate.onMediaButtonSingleClick();
                } else if (counter == 2) {
                    delegate.onMediaButtonDoubleClick();
                } else {
                    delegate.onMediaButtonTripleClick();
                }
                counter = 0;
            }
        }, doublePressSpeed);
    }

    public static void register(final Context context) {
        audioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        remoteControlResponder = new ComponentName(context, HeadsetActionButtonReceiver.class);
        audioManager.registerMediaButtonEventReceiver(remoteControlResponder);
    }

    public static void unregister() {
        audioManager.unregisterMediaButtonEventReceiver(remoteControlResponder);
        if (doublePressTimer != null) {
            doublePressTimer.cancel();
            doublePressTimer = null;
        }
    }
}
