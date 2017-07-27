package nl.changer.audiowife;

import android.app.IntentService;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.media.MediaPlayer;
import android.support.annotation.Nullable;
import android.support.v4.app.NotificationCompat;

/**
 * Created by elliotn on 7/23/17.
 */

public class NotificationMediaControls extends IntentService {

    public static final String ACTION_PAUSE = "ACTION_PAUSE";
    public static final String ACTION_PLAY = "ACTION_PLAY";
    public static final String ACTION_EXIT = "ACTION_EXIT";


    public NotificationMediaControls() {
        super("NotificationMediaControls");
    }

    public NotificationMediaControls(String name) {
        super(name);
    }

    @Override
    protected void onHandleIntent(@Nullable Intent intent) {
        String action = intent.getAction();

        if (action == null) {
            return;
        }

        MediaPlayer mediaPlayer = AudioWife.getInstance().getMediaPlayer();
        if (mediaPlayer == null) {
            return;
        }

        switch (action) {
            case ACTION_PAUSE:
                mediaPlayer.pause();
                break;

            case ACTION_PLAY:
             mediaPlayer.start();
                break;

            default:
                // do nothing
                break;
        }

    }


    public static NotificationCompat.Action getPauseAction(Context context) {
        Intent intent = new Intent(context, NotificationMediaControls.class);
        intent.setAction(ACTION_PAUSE);

        PendingIntent pendingIntent = PendingIntent.getService(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT);

        return (new NotificationCompat.Action.Builder(R.drawable.ic_pause_black_24dp,
                context.getString(R.string.pause_button),
                pendingIntent))
                .build();
    }


    public static NotificationCompat.Action getPlayAction(Context context) {
        Intent intent = new Intent(context, NotificationMediaControls.class);
        intent.setAction(ACTION_PLAY);

        PendingIntent pendingIntent = PendingIntent.getService(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT);

        return (new NotificationCompat.Action.Builder(R.drawable.ic_play_arrow_black_24dp,
                context.getString(R.string.play_button),
                pendingIntent))
                .build();
    }


}
