package de.secretj12.hopfenjagd;

import android.content.ComponentName;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Build;
import android.os.IBinder;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.CompoundButton;
import android.widget.ListView;
import android.widget.Switch;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import java.util.ArrayList;

public class activity_till_start extends AppCompatActivity {
    private Service_Game.LocalBinder binder;
    private ServiceConnection serviceConnection;

    private ListView players_list;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_till_start);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        players_list = findViewById(R.id.till_start_player_list);

        Intent start_service_game = new Intent(this, Service_Game.class);
        serviceConnection = new ServiceConnection() {
            @Override
            public void onServiceConnected(ComponentName componentName, IBinder iBinder) {
                binder = (Service_Game.LocalBinder) iBinder;

                binder.setOnStateChangeListener(new Service_Game.onStateChangedListener() {
                    @Override
                    public void onStateChanged() {
                        startActivity(new Intent(activity_till_start.this, binder.getCorrectActivity()));
                    }
                });

                binder.setOnPlayersChangeListener(new Service_Game.onPlayersChangeListener() {
                    @Override
                    public void onPlayersChange(ArrayList<Player> players) {
                        updateList(players);
                    }
                });
            }

            @Override
            public void onServiceDisconnected(ComponentName componentName) {
                activity_till_start.super.onBackPressed();
            }
        };

        bindService(start_service_game, serviceConnection, BIND_IMPORTANT);
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if(item.getItemId() == android.R.id.home) {
            onBackPressed();
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onBackPressed() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(R.string.stop_game)
                .setIcon(R.mipmap.hopfendolde)
                .setMessage(getString(R.string.wanna_end_game))
                .setCancelable(false)
                .setPositiveButton(getString(R.string.yes), new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        binder.leaveGame();
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

    public void updateList(ArrayList<Player> players) {
        ArrayAdapter config_adapter = new ArrayAdapter<Player>(
                this,
                R.layout.layout_list_player_till_start,
                R.id.till_start_name,
                players) {
            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                View view = super.getView(position, convertView, parent);

                final Player player = getItem(position);

                final TextView name = view.findViewById(R.id.till_start_name);
                name.setText(player.getName());
                if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) name.setTextColor(getContext().getColor(player.isRunner()?R.color.runner:R.color.hunter));
                else name.setTextColor(getContext().getResources().getColor(player.isRunner()?R.color.runner:R.color.hunter));

                return view;
            }
        };

        players_list.setAdapter(config_adapter);
        players_list.setEnabled(false);
        config_adapter.notifyDataSetChanged();
    }

    @Override
    protected void onResume() {
        super.onResume();

        if(binder != null && binder.getCorrectActivity() != activity_till_start.class)
            startActivity(new Intent(activity_till_start.this, binder.getCorrectActivity()));
    }

    @Override
    protected void onStop() {
        try{
            unbindService(serviceConnection);
        }catch (IllegalArgumentException e) {}
        super.onStop();
    }
}
