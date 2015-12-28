package com.gaudima.gaudima.soundcloudplayer.main;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.res.Resources;
import android.media.MediaPlayer;
import android.os.IBinder;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v7.widget.CardView;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.TextView;

import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.NetworkImageView;
import com.gaudima.gaudima.soundcloudplayer.R;
import com.gaudima.gaudima.soundcloudplayer.api.Api;

import org.json.JSONArray;
import org.json.JSONObject;

public class PopularSongsAdapter extends RecyclerView.Adapter<PopularSongsAdapter.ViewHolder> {
    private static final String TAG = "PopularSongsAdapter";
    private Api api;
    private JSONArray data = new JSONArray();
    private int offset = 0;
    private Intent musicPlayerServiceIntent;
    private MusicPlayerService musicPlayerService;
    private Context context;
    private MenuItem playerPage;
    private boolean boundToMusicPlayerService = false;

    public boolean loading = false;

    private ServiceConnection musicPlayerServiceConnection = new ServiceConnection(){

        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            MusicPlayerService.MBinder binder = (MusicPlayerService.MBinder)service;
            boundToMusicPlayerService = true;
            musicPlayerService = binder.getService();
            musicPlayerService.setPlaybackListener(new MusicPlayerService.StateListener() {
                @Override
                public void onStateChanged(int index, int state) {
                    try {
                        data.getJSONObject(index).put("playing_status", state);
                        notifyItemChanged(index);
                        if (state != MusicPlayerService.STOPPED) {
                            playerPage.setVisible(true);
                        }
                    } catch (Exception e) {

                    }
                }
            });
            musicPlayerService.closeNotification();
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            boundToMusicPlayerService = false;
            musicPlayerService = null;
        }
    };

    public PopularSongsAdapter(Context ctx) {
        context = ctx;
        api = Api.getInstance(ctx);
        if(musicPlayerServiceIntent == null) {
            musicPlayerServiceIntent = new Intent(ctx.getApplicationContext(), MusicPlayerService.class);
            context.bindService(musicPlayerServiceIntent, musicPlayerServiceConnection, Context.BIND_AUTO_CREATE);
            context.startService(musicPlayerServiceIntent);
        }
        Log.d(TAG, "constructor");
    }

    public void setPlayerPage(MenuItem playerPage) {
        this.playerPage = playerPage;
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        public TextView songName;
        public TextView songArtist;
        public TextView playing;
        public NetworkImageView albumArt;
        public CardView card;

        public ViewHolder(View v) {
            super(v);
            card = (CardView) v.findViewById(R.id.songView);
            songName = (TextView) v.findViewById(R.id.songName);
            songArtist = (TextView) v.findViewById(R.id.songArtist);
            albumArt = (NetworkImageView) v.findViewById(R.id.albumArt);
            playing = (TextView) v.findViewById(R.id.playing);
        }
    }

    @Override
    public PopularSongsAdapter.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.song, parent, false);
        return new ViewHolder(v);
    }

    @Override
    public void onBindViewHolder(ViewHolder holder, final int position) {
        try {
            final JSONObject obj = data.getJSONObject(position);

            String artworkUrl = obj.getString("artwork_url");
            if(artworkUrl.equals("null")) {
                artworkUrl = obj.getJSONObject("user").getString("avatar_url");
            }
            holder.albumArt.setImageUrl(artworkUrl,
                    api.getImageLoader());
            holder.albumArt.getLayoutParams().height = 300;
            holder.albumArt.getLayoutParams().width = 300;
            holder.songName.setText(obj.getString("title"));
            holder.songArtist.setText(obj.getJSONObject("user").getString("username"));
            if(obj.getInt("playing_status") == MusicPlayerService.PLAYING) {
                holder.playing.setText(context.getString(R.string.playing));
            } else if(obj.getInt("playing_status") == MusicPlayerService.BUFFERING) {
                holder.playing.setText(context.getString(R.string.buffering));
            } else {
                holder.playing.setText("");
            }
            holder.card.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    try {
                        if (musicPlayerService != null) {
                            if(obj.getInt("playing_status") != MusicPlayerService.STOPPED) {
                                context.startActivity(new Intent(context, MusicPlayerActivity.class));
                            } else {
                                musicPlayerService.setPlaylist(data);
                                musicPlayerService.setSong(position);
                                musicPlayerService.playSong();
                            }
                        }
                    } catch (Exception e) {
                        Log.d(TAG, e.getMessage());
                    }
                }
            });
        }catch(Exception e) {

        }
    }

    @Override
    public int getItemCount() {
        return data.length();
    }

    public void reset() {
        data = new JSONArray();
        offset = 0;
        notifyDataSetChanged();
    }

    public void getSongs() {
        loading = true;
        api.addToRequestQueue(new JsonObjectRequest(
                api.getApiUrlWithParams(
                        "https://api-v2.soundcloud.com/explore/Popular+Music",
                        "offset",
                        String.valueOf(offset),
                        "limit",
                        "50"),
                new Response.Listener<JSONObject>() {
                    @Override
                    public void onResponse(JSONObject response) {
                        try {
                            JSONArray tracks = response.getJSONArray("tracks");
                            int from = data.length();
                            for (int i = 0; i < tracks.length(); i++) {
                                JSONObject track = tracks.getJSONObject(i);
                                track.put("playing_status",
                                        MusicPlayerService.STOPPED);
                                data.put(track);
                                notifyItemInserted(data.length() - 1);
                            }
                            if (musicPlayerService != null) {
                                musicPlayerService.setPlaylist(data);
                            }
                            Log.d(TAG, data.toString());
                            offset += 50;
                            musicPlayerService.requestStateInfo();
                        } catch (Exception e) {

                        } finally {
                            loading = false;
                        }
                    }
                },
                new Response.ErrorListener() {
                    @Override
                    public void onErrorResponse(VolleyError error) {
                        //Log.d(TAG, error.getMessage());
                    }
                }
        ));
    }

    public void unbindFromMusicPlayerService() {
        musicPlayerService.openNotification();
        if(boundToMusicPlayerService) {
            context.unbindService(musicPlayerServiceConnection);
        }
    }

    public JSONArray getData() {
        return data;
    }
}
