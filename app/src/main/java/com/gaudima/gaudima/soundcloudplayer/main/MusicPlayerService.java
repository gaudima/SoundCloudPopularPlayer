package com.gaudima.gaudima.soundcloudplayer.main;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.app.TaskStackBuilder;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.os.PowerManager;
import android.support.annotation.Nullable;

import android.support.v4.app.NotificationCompat;
import android.util.Log;
import android.widget.RemoteViews;
import android.widget.TextView;

import com.gaudima.gaudima.soundcloudplayer.R;
import com.gaudima.gaudima.soundcloudplayer.api.Api;

import org.json.JSONArray;
import org.json.JSONObject;
import org.w3c.dom.Text;

public class MusicPlayerService extends Service implements
        MediaPlayer.OnPreparedListener,
        MediaPlayer.OnErrorListener,
        MediaPlayer.OnCompletionListener {

    public static final String TAG = "MusicPlayerService";

    public static final String PLAYPAUSE = "action.PlayPause";
    public static final String PREVIOUS = "action.Previous";
    public static final String NEXT = "action.Next";
    public static final String STOPSERVICE = "action.StopService";

    public class MBinder extends Binder {
        MusicPlayerService getService() {
            return MusicPlayerService.this;
        }
    }

    public interface ProgressListener {
        void onProgress(int progress, int duration, boolean playing);
    }

    public  interface PlaybackListener {
        void onStarted(int index);
        void onStopped(int index);
    }

    public interface InfoListener {
        void onInfo(JSONObject obj);
    }

    private ProgressListener progressListener;
    private InfoListener infoListener;
    private PlaybackListener playbackListener;

    private MBinder binder = new MBinder();
    private MediaPlayer player;
    private JSONArray playlist;
    private int currentSong = -1;
    private boolean preparing = true;
    private Handler handler = new Handler();
    private boolean background = false;

    Intent playPause;
    Intent previous;
    Intent next;
    PendingIntent playPausePending;
    PendingIntent previousPending;
    PendingIntent nextPending;
    NotificationManager notificationManager;

    private final Runnable r = new Runnable() {
        @Override
        public void run() {
            requestProgress();
            handler.postDelayed(this, 1000);
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        player = new MediaPlayer();
        initPlayer();
        handler.postDelayed(r, 1000);
        playPause = new Intent(this, MusicPlayerService.class);
        previous = new Intent(this, MusicPlayerService.class);
        next = new Intent(this, MusicPlayerService.class);
        playPause.setAction(PLAYPAUSE);
        previous.setAction(PREVIOUS);
        next.setAction(NEXT);
        playPausePending = PendingIntent.getService(this, 0, playPause, 0);
        previousPending = PendingIntent.getService(this, 0, previous, 0);
        nextPending = PendingIntent.getService(this, 0, next, 0);
        notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        Log.d(TAG, "onCreate");
    }

    private void initPlayer() {
        player.setWakeMode(getApplicationContext(), PowerManager.PARTIAL_WAKE_LOCK);
        player.setAudioStreamType(AudioManager.STREAM_MUSIC);
        player.setOnPreparedListener(this);
        player.setOnErrorListener(this);
        player.setOnCompletionListener(this);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if(intent.getAction() != null) {
            Log.d(TAG, "onAction");
            switch (intent.getAction()) {
                case PLAYPAUSE:
                    playPause();
                    break;
                case PREVIOUS:
                    getPrevious();
                    break;
                case NEXT:
                    getNext();
                    break;
                case STOPSERVICE:
                    stopService(new Intent(getApplicationContext(), MusicPlayerService.class));
            }
        }
        return START_NOT_STICKY;
    }

    public void getPrevious() {
        if(!preparing && player.getCurrentPosition() > 3000) {
            seekTo(0);
        } else {
            if(currentSong > 0) {
                setSong(currentSong - 1);
                playSong();
            }
        }
    }

    public void getNext() {
        if(currentSong < playlist.length() - 1) {
            setSong(currentSong + 1);
            playSong();
        }
    }

    public void setProgressListener(ProgressListener progress) {
        progressListener = progress;
    }

    public void setInfoListener(InfoListener info) {
        infoListener = info;
    }

    public void setPlaybackListener(PlaybackListener playback) {
        playbackListener = playback;
    }

    public void setPlaylist(JSONArray pl) {
        playlist = pl;
    }

    public void requestInfo() {
        try {
            if(infoListener != null ) {
                infoListener.onInfo(playlist.getJSONObject(currentSong));
            }
        } catch(Exception e) {
            Log.d(TAG, e.getMessage());
        }
    }

    public void requestProgress() {
        if(progressListener != null) {
            if(!preparing) {
                progressListener.onProgress(player.getCurrentPosition(), player.getDuration(), player.isPlaying());
            } else {
                progressListener.onProgress(0, 10, true);
            }
        }
    }

    public void playSong() {
        try {
            preparing = true;
            player.reset();
            player.setDataSource(Api.getInstance(getApplicationContext()).getApiUrlWithParams(
                    playlist.getJSONObject(currentSong).getString("stream_url")
            ));
            player.prepareAsync();
            requestInfo();
            playbackListener.onStarted(currentSong);
            upadteNotification();
        } catch(Exception e) {
            Log.d(TAG, e.getMessage());
        }
    }

    public void setSong(int index) {
        if(currentSong != -1) {
            playbackListener.onStopped(currentSong);
        }
        currentSong = index;
    }

    public void playPause() {
        if(player.isPlaying()) {
            player.pause();
        } else {
            player.start();
        }
        upadteNotification();
    }

    public void seekTo(int progress) {
        player.seekTo(progress);
    }

    public void pause() {
        if(player.isPlaying()) {
            player.pause();
        }
    }

    public void play() {
        if(!player.isPlaying()) {
            player.start();
        }
    }

    public int getCurrentSong() {
        return currentSong;
    }

    public void unsetProgressListener() {
        progressListener = null;
    }

    public void openNotification() {
        background = true;
        upadteNotification();
    }

    public void upadteNotification() {
        if(background) {
            try {
                RemoteViews views = new RemoteViews(getPackageName(), R.layout.notification);
                Notification notification = new Notification.Builder(this)
                        .setSmallIcon(R.drawable.ic_play_arrow_white_48dp)
                        .setContent(views)
                        .build();

                views.setTextViewText(R.id.notificationSongName, playlist.getJSONObject(currentSong)
                        .getString("title"));
                views.setTextViewText(R.id.notificationArtist, playlist.getJSONObject(currentSong)
                        .getJSONObject("user").getString("username"));
                if(player.isPlaying()) {
                    views.setImageViewResource(R.id.notificationPlayPause, R.drawable.ic_pause_black_48dp);
                } else {
                    views.setImageViewResource(R.id.notificationPlayPause, R.drawable.ic_play_arrow_black_48dp);
                }

                views.setOnClickPendingIntent(R.id.notificationPrevious, previousPending);
                views.setOnClickPendingIntent(R.id.notificationPlayPause, playPausePending);
                views.setOnClickPendingIntent(R.id.notificationNext, nextPending);

                notificationManager.notify(100, notification);
            } catch(Exception e) {
                Log.d(TAG, e.getMessage());
            }
        }
    }


    @Override
    public void onPrepared(MediaPlayer mp) {
        preparing = false;
        mp.start();
        upadteNotification();
    }

    @Override
    public boolean onError(MediaPlayer mp, int what, int extra) {
        return false;
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        Log.d(TAG, "onBind");
        return binder;
    }

    public void closeNotification() {
        notificationManager.cancel(100);
        background = false;
    }

    @Override
    public boolean onUnbind(Intent intent) {
        if(!player.isPlaying()) {
            stopService(new Intent(getApplicationContext(), MusicPlayerService.class));
        } else {
            background = true;
            upadteNotification();
        }
        return false;
    }

    @Override
    public void onCompletion(MediaPlayer mp) {
        playbackListener.onStarted(currentSong);
        getNext();
    }

    @Override
    public void onDestroy() {
        player.stop();
        player.release();
        Log.d(TAG, "onDestroy");
        super.onDestroy();
    }
}
