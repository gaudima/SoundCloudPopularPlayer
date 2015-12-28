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

    public  interface StateListener {
        void onStateChanged(int index, int state);
    }

    public interface InfoListener {
        void onInfo(JSONObject obj);
    }

    public final static int BUFFERING = 0;
    public final static int PLAYING = 1;
    public final static int STOPPED = 2;

    private int currentState;

    private ProgressListener progressListener;
    private InfoListener infoListener;
    private StateListener stateListener;

    private MBinder binder = new MBinder();
    private MediaPlayer player;
    private JSONArray playlist;
    private int currentSong = -1;
    private Handler handler = new Handler();
    private boolean background = false;

    Intent playPause;
    Intent previous;
    Intent next;
    Intent stopService;
    Intent popularSings;
    PendingIntent playPausePending;
    PendingIntent previousPending;
    PendingIntent nextPending;
    PendingIntent stopServicePending;
    PendingIntent popularSongsPending;
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
        stopService = new Intent(this, MusicPlayerService.class);
        popularSings = new Intent(this, PopularSongsActivity.class);
        playPause.setAction(PLAYPAUSE);
        previous.setAction(PREVIOUS);
        next.setAction(NEXT);
        stopService.setAction(STOPSERVICE);
        playPausePending = PendingIntent.getService(this, 0, playPause, 0);
        previousPending = PendingIntent.getService(this, 0, previous, 0);
        nextPending = PendingIntent.getService(this, 0, next, 0);
        stopServicePending = PendingIntent.getService(this, 0, stopService, 0);
        popularSongsPending = PendingIntent.getActivity(this, 0, popularSings, 0);
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
                    stopForeground(true);
                    stopSelf();
            }
        }
        return START_NOT_STICKY;
    }

    public void getPrevious() {
        if(currentState != BUFFERING && player.getCurrentPosition() > 3000) {
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

    public void setPlaybackListener(StateListener state) {
        stateListener = state;
    }

    public void setPlaylist(JSONArray pl) {
        playlist = pl;
    }

    public void setBufferingListener(MediaPlayer.OnBufferingUpdateListener listener) {
        player.setOnBufferingUpdateListener(listener);
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
            if(currentState != BUFFERING) {
                progressListener.onProgress(player.getCurrentPosition(), player.getDuration(), player.isPlaying());
            } else {
                progressListener.onProgress(0, 10, true);
            }
        }
    }

    public void requestStateInfo() {
        if(currentSong != -1) {
            stateListener.onStateChanged(currentSong, currentState);
        }
    }

    public void playSong() {
        try {
            currentState = BUFFERING;
            stateListener.onStateChanged(currentSong, currentState);
            player.reset();
            player.setDataSource(Api.getInstance(getApplicationContext()).getApiUrlWithParams(
                    playlist.getJSONObject(currentSong).getString("stream_url")
            ));
            player.prepareAsync();
            requestInfo();
            upadteNotification();
        } catch(Exception e) {
            Log.d(TAG, e.getMessage());
        }
    }

    public void setSong(int index) {
        if(currentSong != -1) {
            currentState = STOPPED;
            stateListener.onStateChanged(currentSong, currentState);
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
        startForeground();
    }

    void startForeground() {
        if(background && player.isPlaying()) {
            startForeground(100, getNotification());
        } else {
            stopSelf();
        }
    }

    public Notification getNotification() {
        try {
            RemoteViews views = new RemoteViews(getPackageName(), R.layout.notification);
            Notification notification = new Notification.Builder(this)
                    .setSmallIcon(R.drawable.ic_play_arrow_white_48dp)
                    .setContent(views)
                    .setContentIntent(popularSongsPending)
                    .build();

            views.setTextViewText(R.id.notificationSongName, playlist.getJSONObject(currentSong)
                    .getString("title"));
            views.setTextViewText(R.id.notificationArtist, playlist.getJSONObject(currentSong)
                    .getJSONObject("user").getString("username"));
            if (player.isPlaying()) {
                views.setImageViewResource(R.id.notificationPlayPause, R.drawable.ic_pause_black_48dp);
            } else {
                views.setImageViewResource(R.id.notificationPlayPause, R.drawable.ic_play_arrow_black_48dp);
            }

            views.setOnClickPendingIntent(R.id.notificationPrevious, previousPending);
            views.setOnClickPendingIntent(R.id.notificationPlayPause, playPausePending);
            views.setOnClickPendingIntent(R.id.notificationNext, nextPending);
            views.setOnClickPendingIntent(R.id.notificationStopService, stopServicePending);
            return notification;
        } catch(Exception e) {
            Log.d(TAG, e.getMessage());
        }
        return null;
    }

    public void upadteNotification() {
        if(background) {
            notificationManager.notify(100, getNotification());
        }
    }


    @Override
    public void onPrepared(MediaPlayer mp) {
        currentState = PLAYING;
        stateListener.onStateChanged(currentSong, currentState);
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
        stopForeground(true);
        background = false;
    }

    @Override
    public boolean onUnbind(Intent intent) {
        return false;
    }

    @Override
    public void onCompletion(MediaPlayer mp) {
        currentState = STOPPED;
        stateListener.onStateChanged(currentSong, currentState);
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
