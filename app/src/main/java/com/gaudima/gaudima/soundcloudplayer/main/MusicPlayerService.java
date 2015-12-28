package com.gaudima.gaudima.soundcloudplayer.main;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.os.PowerManager;
import android.support.annotation.Nullable;

import android.util.Log;
import android.widget.RemoteViews;

import com.gaudima.gaudima.soundcloudplayer.R;
import com.gaudima.gaudima.soundcloudplayer.api.Api;

import org.json.JSONArray;
import org.json.JSONObject;

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
    public final static int INITIAL = -1;

    private int currentState = INITIAL;

    private ProgressListener progressListener;
    private InfoListener infoListener;
    private StateListener stateListener;

    private MBinder binder = new MBinder();
    private MediaPlayer player;
    private JSONArray playlist;
    private int currentSong = 0;
    private Handler handler = new Handler();
    private boolean background = false;
    private int activitiesVisible = 0;

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

    public void setStateListener(StateListener state) {
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
            if(currentState == PLAYING) {
                progressListener.onProgress(player.getCurrentPosition(), player.getDuration(), player.isPlaying());
            } else {
                progressListener.onProgress(0, 1000, false);
            }
        }
    }

    public void requestStateInfo() {
        if(stateListener != null) {
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
            updateNotification();
        } catch(Exception e) {
            Log.d(TAG, e.getMessage());
        }
    }

    public void addVisibleActivity() {
        activitiesVisible += 1;
        Log.d(TAG, "addVisibleActivities");
        if(activitiesVisible == 1) {
            closeNotification();
        }
    }

    public void removeVisibleActivity() {
        activitiesVisible -= 1;
        Log.d(TAG, "removeVisibleActivities");
        if(activitiesVisible <= 0) {
            openNotification();
        }
    }

    public void setSong(int index) {
        currentState = STOPPED;
        stateListener.onStateChanged(currentSong, currentState);
        currentSong = index;
    }

    public void playPause() {
        if(currentState == INITIAL) {
            currentState = BUFFERING;
            playSong();
        } else if(currentState == PLAYING) {
            currentState = STOPPED;
            player.pause();
        } else {
            currentState = PLAYING;
            player.start();
        }
        updateNotification();
    }

    public void seekTo(int progress) {
        player.seekTo(progress);
    }

    public void pause() {
        if(currentState == PLAYING) {
            currentState = STOPPED;
            player.pause();
        }
    }

    public void play() {
        if(currentState == STOPPED) {
            currentState = PLAYING;
            player.start();
        }
    }

    public int getCurrentSong() {
        return currentSong;
    }

    public int getState() {
        return currentState;
    }

    public void unsetProgressListener() {
        progressListener = null;
    }

    private void openNotification() {
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

    private Notification getNotification() {
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

    private void updateNotification() {
        if(background) {
            notificationManager.notify(100, getNotification());
        }
    }


    @Override
    public void onPrepared(MediaPlayer mp) {
        currentState = PLAYING;
        stateListener.onStateChanged(currentSong, currentState);
        mp.start();
        updateNotification();
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

    private void closeNotification() {
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
        if(currentState != STOPPED) {
            player.stop();
        }
        player.release();
        Log.d(TAG, "onDestroy");
        super.onDestroy();
    }
}
