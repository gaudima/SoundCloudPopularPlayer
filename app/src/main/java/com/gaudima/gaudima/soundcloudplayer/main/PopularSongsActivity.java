package com.gaudima.gaudima.soundcloudplayer.main;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.IBinder;
import android.support.v4.widget.SwipeRefreshLayout;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;

import com.gaudima.gaudima.soundcloudplayer.R;

import org.json.JSONArray;
import org.json.JSONObject;

public class PopularSongsActivity extends MusicPlayerServiceBindedActivity {
    private static final String TAG = "PopularSongsActivity";
    private static final String DATA = "data";
    private RecyclerView recyclerView;
    private PopularSongsAdapter adapter;
    private LinearLayoutManager layoutManager;
    private SwipeRefreshLayout swipeRefreshLayout;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.d(TAG, "onCreate");
        setContentView(R.layout.activity_popular_songs);

        recyclerView = (RecyclerView) findViewById(R.id.popularSongsView);
        swipeRefreshLayout = (SwipeRefreshLayout) findViewById(R.id.swipeRefreshLayout);
        recyclerView.setHasFixedSize(true);
        layoutManager = new LinearLayoutManager(this);
        recyclerView.setLayoutManager(layoutManager);
        adapter = new PopularSongsAdapter(this);
        if(savedInstanceState == null) {
            adapter.getSongs();
        } else {
            try {
                adapter.setData(new JSONArray(savedInstanceState.getString(DATA)));
            } catch(Exception e) {
                Log.d(TAG, e.getMessage());
            }
        }

        recyclerView.setAdapter(adapter);

        recyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(RecyclerView recyclerView, int dx, int dy) {
                super.onScrolled(recyclerView, dx, dy);
                if (dy > 0) //check for scroll down
                {
                    int visibleItemCount = layoutManager.getChildCount();
                    int totalItemCount = layoutManager.getItemCount();
                    int pastVisiblesItems = layoutManager.findFirstVisibleItemPosition();

                    if (!adapter.loading) {
                        if ((visibleItemCount + pastVisiblesItems) >= totalItemCount - 5) {
                            Log.v(TAG, "more loading songs!");
                            adapter.getSongs();
                        }
                    }
                }
            }
        });

        adapter.registerAdapterDataObserver(new RecyclerView.AdapterDataObserver() {
            @Override
            public void onChanged() {
                super.onChanged();
                swipeRefreshLayout.setRefreshing(false);
            }
        });

        swipeRefreshLayout.setOnRefreshListener(new SwipeRefreshLayout.OnRefreshListener() {
            @Override
            public void onRefresh() {
                adapter.reset();
                adapter.getSongs();
            }
        });
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putString(DATA, adapter.getData().toString());
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch(item.getItemId()) {
            case R.id.playerPageButton:
                Intent i = new Intent(this, MusicPlayerActivity.class);
                startActivity(i);
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    @Override
    protected void onDestroy() {
        adapter.unbindFromMusicPlyerService();
        Log.d(TAG, "onDestroy");
        super.onDestroy();
    }
}
