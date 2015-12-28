package com.gaudima.gaudima.soundcloudplayer.main;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.os.IBinder;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.ImageButton;
import android.widget.SeekBar;
import android.widget.TextView;

import com.android.volley.toolbox.NetworkImageView;
import com.gaudima.gaudima.soundcloudplayer.R;
import com.gaudima.gaudima.soundcloudplayer.api.Api;

import org.json.JSONObject;

public class MusicPlayerActivity extends MusicPlayerServiceBindedActivity {
    private final static String TAG = "MusicPlayerActivity";

    private NetworkImageView albumArt;
    private ImageButton previous;
    private ImageButton playpause;
    private ImageButton next;
    private SeekBar seekBar;
    private TextView songName;
    private TextView artistName;
    private boolean currentlyPlaying = false;
    private boolean seeking = false;

    @Override
    public void onMusicServiceConnected() {
        super.onMusicServiceConnected();
        musicPlayerService.setInfoListener(new MusicPlayerService.InfoListener() {
            @Override
            public void onInfo(JSONObject obj) {
                try {
                    String artworkUrl = obj.getString("artwork_url");
                    if (artworkUrl.equals("null")) {
                        artworkUrl = obj.getJSONObject("user").getString("avatar_url");
                    }
                    artworkUrl = artworkUrl.replace("-large.", "-t500x500.");
                    albumArt.setImageUrl(
                            Api.getInstance(getApplicationContext()).getApiUrlWithParams(
                                    artworkUrl),
                            Api.getInstance(getApplicationContext()).getImageLoader());
                    songName.setText(obj.getString("title"));
                    artistName.setText(obj.getJSONObject("user").getString("username"));
                } catch (Exception e) {
                    Log.d(TAG, e.getMessage());
                }
            }
        });
        musicPlayerService.requestInfo();
        musicPlayerService.setProgressListener(new MusicPlayerService.ProgressListener() {
            @Override
            public void onProgress(int progress, int duration, boolean playing) {
                if (!seeking) {
                    seekBar.setMax(duration);
                    seekBar.setProgress(progress);
                }
                if (currentlyPlaying != playing) {
                    if (playing) {
                        playpause.setImageDrawable(ContextCompat.getDrawable(
                                getApplicationContext(), R.drawable.ic_pause_white_48dp));
                        currentlyPlaying = true;
                    } else {
                        playpause.setImageDrawable(ContextCompat.getDrawable(
                                getApplicationContext(), R.drawable.ic_play_arrow_white_48dp));
                        currentlyPlaying = false;
                    }
                }
            }
        });
        musicPlayerService.setBufferingListener(new MediaPlayer.OnBufferingUpdateListener() {
            @Override
            public void onBufferingUpdate(MediaPlayer mp, int percent) {
                seekBar.setSecondaryProgress(seekBar.getMax() * percent / 100);
            }
        });
        previous.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                musicPlayerService.getPrevious();
            }
        });
        next.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                seekBar.setProgress(0);
                musicPlayerService.getNext();
            }
        });
        playpause.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                musicPlayerService.playPause();
            }
        });
        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            private int seekProgress = 0;

            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser) {
                    seekProgress = progress;
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
                seekProgress = 0;
                musicPlayerService.pause();
                seeking = true;
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                musicPlayerService.seekTo(seekProgress);
                musicPlayerService.play();
                seeking = false;
            }
        });
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_music_player);
        albumArt = (NetworkImageView) findViewById(R.id.musicPlayerAlbumArt);
        previous = (ImageButton) findViewById(R.id.musicPlayerPrevious);
        playpause = (ImageButton) findViewById(R.id.musicPlayerPlayPause);
        next = (ImageButton) findViewById(R.id.musicPlayerNext);
        seekBar = (SeekBar) findViewById(R.id.musicPlayerSeekBar);
        songName = (TextView) findViewById(R.id.musicPlayerSongName);
        artistName = (TextView) findViewById(R.id.musicPlayerArtistName);

        Log.d(TAG, "onCreate");
    }

    @Override
    protected void onDestroy() {
        musicPlayerService.unsetProgressListener();
        super.onDestroy();
    }
}
