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

import org.json.JSONObject;

public class PopularSongsActivity extends AppCompatActivity {
    private static final String TAG = "PopularSongsActivity";
    private RecyclerView recyclerView;
    private PopularSongsAdapter adapter;
    private LinearLayoutManager layoutManager;
    private SwipeRefreshLayout swipeRefreshLayout;


    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.d(TAG, "onCreate");
        setContentView(R.layout.activity_popular_songs);

        recyclerView = (RecyclerView) findViewById(R.id.popularSongsView);
        swipeRefreshLayout = (SwipeRefreshLayout) findViewById(R.id.swipeRefreshLayout);
        recyclerView.setHasFixedSize(true);
        layoutManager = new LinearLayoutManager(this);
        recyclerView.setLayoutManager(layoutManager);

        if(savedInstanceState == null) {
            adapter = new PopularSongsAdapter(this);
            adapter.getSongs();
        } else {
            adapter = (PopularSongsAdapter) getLastCustomNonConfigurationInstance();
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
    public Object onRetainCustomNonConfigurationInstance() {
        return adapter;
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main, menu);
        adapter.setPlayerPage(menu.findItem(R.id.playerPageButton));
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
    protected void onResume() {
        super.onResume();
        adapter.closeNotification();
    }

    @Override
    protected void onPause() {
        adapter.openNotification();
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        Log.d(TAG, "onDestroy");
        adapter.unbindFromMusicPlayerService();
        super.onDestroy();
    }
}
