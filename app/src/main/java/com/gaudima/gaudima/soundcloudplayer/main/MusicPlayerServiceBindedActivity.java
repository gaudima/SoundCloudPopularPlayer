package com.gaudima.gaudima.soundcloudplayer.main;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.support.v7.app.AppCompatActivity;


public class MusicPlayerServiceBindedActivity extends AppCompatActivity {

    protected Intent musicPlayerServiceIntent;
    protected MusicPlayerService musicPlayerService;
    protected boolean boundToMusicPlayerService = false;

    private ServiceConnection musicPlayerServiceConnection = new ServiceConnection(){

        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            MusicPlayerService.MBinder binder = (MusicPlayerService.MBinder) service;
            musicPlayerService = binder.getService();
            boundToMusicPlayerService = true;
            onMusicServiceConnected();
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            boundToMusicPlayerService = false;
            musicPlayerService = null;
            onMusicServiceDisconnected();
        }
    };

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if(musicPlayerServiceIntent == null) {
            musicPlayerServiceIntent = new Intent(this, MusicPlayerService.class);
            bindService(musicPlayerServiceIntent, musicPlayerServiceConnection, Context.BIND_AUTO_CREATE);
            startService(musicPlayerServiceIntent);
        }
    }

    @Override
    protected void onDestroy() {
        unbindService(musicPlayerServiceConnection);
        super.onDestroy();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if(boundToMusicPlayerService) {
            musicPlayerService.addVisibleActivity();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if(boundToMusicPlayerService) {
            musicPlayerService.removeVisibleActivity();
        }
    }

    protected void onMusicServiceConnected() {
        if(boundToMusicPlayerService) {
            musicPlayerService.addVisibleActivity();
        }
    }

    protected void onMusicServiceDisconnected() {

    }
}
