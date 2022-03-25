package de.secretj12.hopfenjagd.map;

import android.content.ComponentName;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.ServiceConnection;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Rect;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Timer;
import java.util.TimerTask;

import de.secretj12.hopfenjagd.Player;
import de.secretj12.hopfenjagd.R;
import de.secretj12.hopfenjagd.Service_Game;
import de.secretj12.hopfenjagd.Service_Game_old;
import de.secretj12.hopfenjagd.activity_start_menu;

public class activity_map extends AppCompatActivity {
    private FirebaseAuth mAuth;
    private FirebaseUser currentUser;

    private ServiceConnection serviceConnection;
    private Service_Game.LocalBinder binder;
    private ArrayList<Player> players;
    private fragment_maps fragment_maps;
    private HashMap<String, Bitmap> pictures;
    private boolean picures_downloaded;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) setContentView(R.layout.activity_map);
        else setContentView(R.layout.activity_map_old_version);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        mAuth = FirebaseAuth.getInstance();
        currentUser = mAuth.getCurrentUser();

        fragment_maps = (fragment_maps) getSupportFragmentManager().findFragmentById(R.id.fragment_maps);
        final TextView time_hunter_view = findViewById(R.id.time_hunter);
        final TextView time_runner_view = findViewById(R.id.time_runner);
        final ListView player_list = findViewById(R.id.player_list_map);
        players = new ArrayList<>();
        pictures = new HashMap<>();
        picures_downloaded = false;

        final BaseAdapter listAdapter = new BaseAdapter() {
            @Override
            public int getCount() {
                return players.size();
            }

            @Override
            public Object getItem(int position) {
                return players.get(position);
            }

            @Override
            public long getItemId(int position) {
                return position;
            }

            @Override
            public View getView(int position, View view, ViewGroup parent) {
                view = LayoutInflater.from(parent.getContext()).inflate(R.layout.layout_list_player_map, null);

                final Player player = (Player) getItem(position);

                final ImageView player_icon = view.findViewById(R.id.image_player_icon);
                TextView textview_name = view.findViewById(R.id.player_name_map);
                ImageView image_catch = view.findViewById(R.id.image_catch);

                if(pictures.containsKey(player.getID()) && pictures.get(player.getID()) != null) player_icon.setImageBitmap(pictures.get(player.getID()));

                player_icon.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        if(player.getLocation() != null)
                            fragment_maps.centerCam(player);
                    }
                });

                textview_name.setText(player.getName());
                if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) textview_name.setTextColor(getColor(player.isRunner()?R.color.runner:R.color.hunter));
                else textview_name.setTextColor(getBaseContext().getResources().getColor(player.isRunner()?R.color.runner:R.color.hunter));
                if(player.isCatched()) textview_name.setPaintFlags(textview_name.getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG);

                if(player.getID().equals(currentUser.getUid())
                        || binder.isRunner()
                        || !player.isRunner()
                        || player.isCatched())
                            image_catch.setVisibility(View.INVISIBLE);
                image_catch.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        askForCatch(player.getName(), player.getID());
                    }
                });

                return view;
            }
        };

        player_list.setAdapter(listAdapter);

        Intent intent_service = new Intent(this, Service_Game.class);
        serviceConnection = new ServiceConnection() {
            @Override
            public void onServiceConnected(ComponentName componentName, IBinder iBinder) {
                binder = (Service_Game.LocalBinder) iBinder;
                binder.cancelUpdateNotifications();

                Timer timer = new Timer();
                timer.scheduleAtFixedRate(new TimerTask() {
                    @Override
                    public void run() {
                        final int time_hunter_left = 60 * binder.getTimeHunter() - (int) (System.currentTimeMillis() - binder.getStartTime()) /1000 % (60 * binder.getTimeHunter());
                        final int time_runner_left = 60 * binder.getTimeRunner() - (int) (System.currentTimeMillis() - binder.getStartTime()) /1000 % (60 * binder.getTimeRunner());

                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                time_hunter_view.setText((time_hunter_left / 60<10?"0":"") + time_hunter_left / 60 + ":" + (time_hunter_left % 60 < 10?"0":"") + time_hunter_left % 60);
                                time_runner_view.setText((time_runner_left / 60<10?"0":"") + time_runner_left / 60 + ":" + (time_runner_left % 60 < 10?"0":"") + time_runner_left % 60);
                            }
                        });
                    }
                }, new Date(binder.getStartTime()), 1000);

                binder.setOnPlayersChangeListener(new Service_Game.onPlayersChangeListener() {
                    @Override
                    public void onPlayersChange(ArrayList<Player> players) {
                        activity_map.this.players.clear();
                        activity_map.this.players.addAll(players);
                        listAdapter.notifyDataSetChanged();

                        if(!picures_downloaded) {
                            picures_downloaded = true;
                            new PictureFetcher().execute(players, pictures, activity_map.this, listAdapter, fragment_maps);
                        }

                        fragment_maps.updatePlayers(players, pictures);
                    }
                });

                binder.setOnStateChangeListener(new Service_Game.onStateChangedListener() {
                    @Override
                    public void onStateChanged() {
                        activity_map.this.players.addAll(players);
                        if(activityActive)
                            startActivity(new Intent(activity_map.this, binder.getCorrectActivity()));
                    }
                });

                binder.setOnCatchedListener(new Service_Game.onCatchedListener() {
                    @Override
                    public void onCatched() {
                        AlertDialog.Builder builder = new AlertDialog.Builder(activity_map.this);
                        builder.setTitle(R.string.catched)
                                .setIcon(R.mipmap.hopfendolde)
                                .setMessage(R.string.continue_as_hunter)
                                .setCancelable(false)
                                .setPositiveButton(R.string.yes, new DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(DialogInterface dialog, int which) {
                                        binder.continueAsHunter(true);
                                    }
                                })
                                .setNegativeButton(R.string.no, new DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(DialogInterface dialog, int which) {
                                        binder.continueAsHunter(false);
                                    }
                                });
                        builder.create().show();
                    }
                });

                binder.setOnUpdateNotificationListener(new Service_Game.onUpdateNotificationListener() {
                    @Override
                    public boolean onUpdateNotificationListener() {
                        if(!activityActive) return false;
                        //todo
                        return true;
                    }
                });
            }

            @Override
            public void onServiceDisconnected(ComponentName componentName) {
                Log.e(getString(R.string.app_name), "Service failed to connect!");
                stopActivity();
            }
        };

        bindService(intent_service, serviceConnection, BIND_AUTO_CREATE);
    }

    private static class PictureFetcher extends AsyncTask {
        @Override
        protected Object doInBackground(final Object[] objects) {
            try {
                for (Player p : (ArrayList<Player>) objects[0]) {
                    if (p.getPhotoURL() == null) continue;
                    try {
                        URL url = new URL(p.getPhotoURL());
                        HttpURLConnection httpURLConnection = (HttpURLConnection) url.openConnection();
                        httpURLConnection.connect();
                        if (httpURLConnection.getResponseCode() != HttpURLConnection.HTTP_OK) break;
                        Bitmap b = BitmapFactory.decodeStream(httpURLConnection.getInputStream());
                        httpURLConnection.disconnect();

                        Bitmap output = Bitmap.createBitmap(b.getWidth(), b.getHeight(), Bitmap.Config.ARGB_8888);
                        Rect rect = new Rect(0, 0, b.getWidth(), b.getHeight());

                        Paint paint = new Paint();
                        paint.setAntiAlias(true);

                        Canvas canvas = new Canvas(output);
                        canvas.drawARGB(0, 0, 0, 0);
                        canvas.drawCircle(b.getWidth() / 2, b.getHeight() / 2, b.getWidth() / 2, paint);
                        paint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.SRC_IN));
                        canvas.drawBitmap(b, rect, rect, paint);

                        ((HashMap<String, Bitmap>) objects[1]).put(p.getID(), output);
                    } catch (IOException e) {
                        Log.e("HopfenJagd", e.getMessage());
                    }
                }
            } catch(Exception e) {}

            ((activity_map) objects[2]).runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    ((BaseAdapter) objects[3]).notifyDataSetChanged();
                    ((fragment_maps) objects[4]).updatePlayers((ArrayList<Player>) objects[0], (HashMap<String, Bitmap>) objects[1]);
                }
            });
            return null;
        }
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
                .setMessage(getString(R.string.wanna_leave_game))
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

    @Override
    protected void onStop() {
        super.onStop();
        try{
            unbindService(serviceConnection);
        }catch (IllegalArgumentException e) {}
    }

    private void stopActivity() {
        Log.e(getString(R.string.app_name), "activity1: " + binder.getCorrectActivity());
        startActivity(new Intent(this, binder.getCorrectActivity()));
    }

    private void askForCatch(String name, final String player_id) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);

        builder.setIcon(R.mipmap.hopfendolde)
                .setTitle(R.string.catched)
                .setMessage(getString(R.string.catch_question).replace("$name", name))
                .setNegativeButton(R.string.no, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.dismiss();
                    }
                })
                .setPositiveButton(R.string.yes, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        binder.catched(player_id);
                    }
                });
        builder.create().show();
    }

    private boolean activityActive;

    @Override
    protected void onPause() {
        super.onPause();
        activityActive = false;
    }

    @Override
    protected void onResume() {
        super.onResume();
        activityActive = true;
        if(binder != null)
            binder.cancelUpdateNotifications();
        if(binder != null && binder.getCorrectActivity() != activity_map.class) startActivity(new Intent(this, binder.getCorrectActivity()));
    }
}
