package de.secretj12.hopfenjagd.create_game;

import android.app.Service;
import android.content.ComponentName;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentPagerAdapter;
import androidx.viewpager.widget.ViewPager;

import com.google.android.material.tabs.TabLayout;

import java.util.ArrayList;

import de.secretj12.hopfenjagd.Player;
import de.secretj12.hopfenjagd.R;
import de.secretj12.hopfenjagd.Service_Game;

public class activity_create_game extends AppCompatActivity {
    private boolean creating;
    private Service_Game.LocalBinder binder;
    private ServiceConnection serviceConnection;

    private String game_id;
    private fragment_create_game_id fragment_id;
    private fragment_create_game_player fragment_player;
    private ArrayList<Player> players;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_create_game);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        PagerAdapter pagerAdapter = new PagerAdapter(getSupportFragmentManager());
        ViewPager viewPager = findViewById(R.id.view_pager);
        viewPager.setAdapter(pagerAdapter);
        TabLayout tabs = findViewById(R.id.tabs);
        tabs.setupWithViewPager(viewPager);

        final Button next = findViewById(R.id.button_start);
        next.setEnabled(false);

        next.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if(players.size() < 2) {
                    Toast.makeText(activity_create_game.this, getString(R.string.not_enough_players), Toast.LENGTH_SHORT).show();
                    return;
                }
                boolean has_runner = false;
                boolean has_hunter = false;
                for(Player p : players) {
                    if(p.isRunner()) has_runner = true;
                    else has_hunter = true;
                }
                if(!has_hunter) {
                    Toast.makeText(activity_create_game.this, getString(R.string.not_enough_hunter), Toast.LENGTH_SHORT).show();
                    return;
                }
                if(!has_runner) {
                    Toast.makeText(activity_create_game.this, getString(R.string.not_enough_runner), Toast.LENGTH_SHORT).show();
                    return;
                }

                binder.startGame();
                startActivity(new Intent(activity_create_game.this, binder.getCorrectActivity()));
            }
        });

        Intent start_service_game = new Intent(this, Service_Game.class);
        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            startForegroundService(start_service_game);
        else
            startService(start_service_game);

        serviceConnection = new ServiceConnection() {
            @Override
            public void onServiceConnected(ComponentName componentName, IBinder iBinder) {
                binder = (Service_Game.LocalBinder) iBinder;

                binder.setOnStateChangeListener(new Service_Game.onStateChangedListener() {
                    @Override
                    public void onStateChanged() {
                        if(binder.getCorrectActivity() != activity_create_game.class)
                            startActivity(new Intent(activity_create_game.this, binder.getCorrectActivity()));
                        if(binder.getState() == Service_Game.PREPARING) {
                            creating = false;
                            activity_create_game.this.game_id = binder.getGameID();
                            if(fragment_id != null)
                                runOnUiThread(new Runnable() {
                                    @Override
                                    public void run() {
                                        fragment_id.setGameID(game_id);
                                    }
                                });
                        }
                    }
                });

                if(binder.getState() == Service_Game.PREPARING) {
                    creating = false;
                    activity_create_game.this.game_id = binder.getGameID();
                    Log.e(getString(R.string.app_name), "1");
                    if(fragment_id != null)
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                Log.e(getString(R.string.app_name), "2");
                                fragment_id.setGameID(game_id);
                            }
                        });
                }

                binder.setOnPlayersChangeListener(new Service_Game.onPlayersChangeListener() {
                    @Override
                    public void onPlayersChange(ArrayList<Player> players) {
                        activity_create_game.this.players = players;
                        if(fragment_player != null) fragment_player.updateList(players, binder);
                    }
                });

                next.setEnabled(true);
            }

            @Override
            public void onServiceDisconnected(ComponentName componentName) {
                creating = false;
            }
        };

        bindService(start_service_game, serviceConnection, BIND_IMPORTANT);

        creating = true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        int id = item.getItemId();

        if(id == android.R.id.home) {
            onBackPressed();
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    public class PagerAdapter extends FragmentPagerAdapter {

        private PagerAdapter(FragmentManager fm) {
            super(fm);
        }

        @Override
        public Fragment getItem(int i) {
            switch(i){
                case 0:
                    fragment_id = new fragment_create_game_id();
                    Bundle bundle_id = new Bundle();
                    if(game_id != null) bundle_id.putString("game_id", game_id);
                    fragment_id.setArguments(bundle_id);
                    return fragment_id;
                case 1:
                    fragment_player = new fragment_create_game_player();
                    if(players != null) fragment_player.updateList(players, binder);
                    return fragment_player;
            }
            return null;
        }

        @Override
        public int getCount() {
            return 2;
        }

        @Override
        public CharSequence getPageTitle(int position) {
            switch(position){
                case 0:
                    return getString(R.string.game_id);
                default:
                    return getString(R.string.player);
            }
        }
    }

    @Override
    public void onBackPressed() {
        if(creating) Toast.makeText(this, R.string.wait_for_creation, Toast.LENGTH_SHORT).show();
        else {
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setTitle(R.string.stop_game)
                    .setIcon(R.mipmap.hopfendolde)
                    .setMessage(getString(R.string.wanna_end_game))
                    .setCancelable(false)
                    .setPositiveButton(getString(R.string.yes), new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            binder.leaveGame();
                            stopActivity();
                        }
                    })
                    .setNegativeButton(getString(R.string.no), new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            dialog.cancel();
                        }
                    });
            builder.create().show();
        }
    }

    private void stopActivity() {
        if(isActive) startActivity(new Intent(this, binder.getCorrectActivity()));
    }

    private boolean isActive;

    @Override
    protected void onPause() {
        super.onPause();
        isActive = false;
    }

    @Override
    protected void onResume() {
        super.onResume();
        isActive = true;
    }

    @Override
    protected void onStop() {
        try{
            unbindService(serviceConnection);
        }catch (IllegalArgumentException e) {}
        super.onStop();
    }
}