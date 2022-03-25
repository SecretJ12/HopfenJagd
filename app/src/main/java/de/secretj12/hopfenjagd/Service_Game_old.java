package de.secretj12.hopfenjagd;


import android.Manifest;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.BitmapFactory;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Binder;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;

import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.EventListener;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FirebaseFirestoreException;
import com.google.firebase.firestore.GeoPoint;
import com.google.firebase.firestore.QuerySnapshot;
import com.google.firebase.remoteconfig.FirebaseRemoteConfig;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import javax.annotation.Nullable;

public class Service_Game_old extends Service {
    //Notification
    private int notification_id;
    private String channel_id = "foreground";
    private String channel_name = "Game Notification";
    private String channel_description = "Notifications while Game";
    private NotificationCompat.Builder builder;
    private NotificationManager nManager;

    //Firebase
    private FirebaseAuth mAuth;
    private FirebaseUser currentUser;
    private FirebaseFirestore db;
    private FirebaseRemoteConfig remoteConfig;

    //ID
    private String game_id;

    //Erstellung
    private boolean createStarted;
    private boolean isCreator;
    private boolean created;
    private boolean creationfailed;

    //eigentliches Spiel
    private boolean isStarted;
    private boolean isStopped;
    private boolean is_finished;
    private long start_time;
    private int time_hunter;
    private int time_runner;
    private boolean isrunner;
    private boolean iscatched;
    private ArrayList<Player> players;

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if(createStarted && !isStopped) return Service.START_NOT_STICKY;
        Log.i("HopfenJagd", "Spiel wird gestartet...");
        createStarted = true;

        createNotificationChannel();
        notification_id = (int) System.currentTimeMillis();

        PendingIntent start_menu_intent = PendingIntent.getActivity(this, 7456, new Intent(this, activity_start_menu.class), PendingIntent.FLAG_CANCEL_CURRENT);

        builder = new NotificationCompat.Builder(this, channel_id)
                .setContentTitle(getString(R.string.app_name))
                .setContentText(getString(R.string.game_starting))
                .setSmallIcon(R.drawable.hopfendolde_icon)
                .setLargeIcon(BitmapFactory.decodeResource(getResources(), R.mipmap.ic_launcher))
                .setContentIntent(start_menu_intent)
                .setUsesChronometer(false);
        nManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);

        startForeground(notification_id, builder.build());
        publishNotificationChanges();

        mAuth = FirebaseAuth.getInstance();
        currentUser = mAuth.getCurrentUser();
        db = FirebaseFirestore.getInstance();
        remoteConfig = FirebaseRemoteConfig.getInstance();

        created = false;
        creationfailed = false;
        game_id = intent.getStringExtra("game_id");

        isStarted = false;
        isStopped = false;
        is_finished = false;
        start_time = -1;
        time_hunter = (int) remoteConfig.getLong("time_hunter");
        time_runner = (int) remoteConfig.getLong("time_runner");
        Log.e("HopfenJagd", "runner: " + remoteConfig.getLong("time_runner"));
        Log.e("HopfenJagd", "hunter: " + remoteConfig.getLong("time_hunter")); //todo make working
        for(String key : remoteConfig.getAll().keySet()) {
            Log.e("HopfenJagd", key + " - " + remoteConfig.getAll().get(key).asLong());
        }
        iscatched = false;
        players = new ArrayList<>();

        isCreator = game_id == "" || game_id == null;
        prepareGame();

        return Service.START_NOT_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return new LocalBinder();
    }

    public boolean isRunning() {
        return isStarted && !isStopped;
    }

    public boolean isStarted() {
        return createStarted;
    }

    public boolean isCreator() {
        return isCreator;
    }

    public class LocalBinder extends Binder {
        public Service_Game_old getService() {
            return Service_Game_old.this;
        }
    }

    private void publishNotificationChanges() {
        nManager.notify(notification_id, builder.build());
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            int importance = NotificationManager.IMPORTANCE_DEFAULT;
            NotificationChannel channel = new NotificationChannel(channel_id, channel_name, importance);
            channel.setDescription(channel_description);

            NotificationManager notificationManager = getSystemService(NotificationManager.class);
            notificationManager.createNotificationChannel(channel);
        }
    }

    public interface OnCreationListener {
        void OnCreation(boolean successful, String game_id);
    }

    private OnCreationListener onCreationListener;

    public void setOnCreationListener(OnCreationListener onCreationListener) {
        if(created) onCreationListener.OnCreation(true, game_id);
        else if (creationfailed) onCreationListener.OnCreation(false, "");
        this.onCreationListener = onCreationListener;
    }

    public void prepareGame() {
        if(isCreator) {
            createNewGame();
        } else {
            db.collection("games")
                    .document(game_id)
                    .get()
                    .addOnCompleteListener(new OnCompleteListener<DocumentSnapshot>() {
                        @Override
                        public void onComplete(@NonNull Task<DocumentSnapshot> task) {
                            if(task.isSuccessful() && task.getResult().exists() && !task.getResult().getBoolean("isStarted")) joinGame();
                            else {
                                if(onCreationListener != null) onCreationListener.OnCreation(false, "");
                                creationfailed = true;
                                stopGame();
                            }
                        }
                    });
        }
    }

    private void joinGame() {
        db.collection("users")
                .document(currentUser.getUid())
                .update("actual_game", game_id)
                .addOnSuccessListener(new OnSuccessListener<Void>() {
                    @Override
                    public void onSuccess(Void aVoid) {
                        registerUserinGame();
                    }
                })
                .addOnFailureListener(new OnFailureListener() {
                    @Override
                    public void onFailure(@NonNull Exception e) {
                        if(onCreationListener != null) onCreationListener.OnCreation(false, "");
                        creationfailed = true;
                        stopSelf();
                    }
                });
    }

    private void createNewGame() {
        game_id = ((int) (Math.random() * 899999 + 100000)) + "";
        game_id = game_id.substring(0, 3) + "-" + game_id.substring(3);

        db.collection("users")
                .document(currentUser.getUid())
                .update("actual_game", game_id)
                .addOnSuccessListener(new OnSuccessListener<Void>() {
                    @Override
                    public void onSuccess(Void aVoid) {

                        Map<String, Object> game_data = new HashMap<>();
                        game_data.put("creator", currentUser.getUid());
                        game_data.put("time_hunter", time_hunter);
                        game_data.put("time_runner", time_runner);
                        game_data.put("isStarted", false);
                        game_data.put("isStopped", false);
                        game_data.put("start_time", -1);

                        db.collection("games")
                                .document(game_id)
                                .set(game_data)
                                .addOnFailureListener(new OnFailureListener() {
                                    @Override
                                    public void onFailure(@NonNull Exception e) {
                                        if(onCreationListener != null) onCreationListener.OnCreation(false, "");
                                        creationfailed = true;
                                        stopGame();
                                    }
                                })
                                .addOnSuccessListener(new OnSuccessListener<Void>() {
                                    @Override
                                    public void onSuccess(Void aVoid) {
                                        registerUserinGame();
                                    }
                                });
                    }
                })
                .addOnFailureListener(new OnFailureListener() {
                    @Override
                    public void onFailure(@NonNull Exception e) {
                        if(onCreationListener != null) onCreationListener.OnCreation(false, "");
                        creationfailed = true;
                        stopSelf();
                    }
                });
    }

    private void registerUserinGame() {
        Map<String, Object> user_data = new HashMap<>();
        user_data.put("name", currentUser.getDisplayName());
        user_data.put("location", null); //todo
        user_data.put("isRunner", false);
        user_data.put("isCatched", false);
        user_data.put("left", false);
        if(currentUser.getPhotoUrl() != null) user_data.put("photo_url", currentUser.getPhotoUrl().toString());

        db.collection("games")
                .document(game_id)
                .collection("users")
                .document(currentUser.getUid())
                .set(user_data)
                .addOnSuccessListener(new OnSuccessListener<Void>() {
                    @Override
                    public void onSuccess(Void aVoid) {
                        created = true;
                        if(onCreationListener != null) onCreationListener.OnCreation(true, game_id);
                        gamePrepared();
                    }
                })
                .addOnFailureListener(new OnFailureListener() {
                    @Override
                    public void onFailure(@NonNull Exception e) {
                        if(onCreationListener != null) onCreationListener.OnCreation(false, "");
                        creationfailed = true;
                        stopGame();
                    }
                });
    }

    private void gamePrepared() {
        DocumentReference game = db.collection("games")
                .document(game_id);

        game.get()
                .addOnSuccessListener(new OnSuccessListener<DocumentSnapshot>() {
                    @Override
                    public void onSuccess(DocumentSnapshot documentSnapshot) {
                        updateSettings(documentSnapshot);
                    }
                });
        game.addSnapshotListener(new EventListener<DocumentSnapshot>() {
            @Override
            public void onEvent(@Nullable DocumentSnapshot documentSnapshot, @Nullable FirebaseFirestoreException e) {
                updateSettings(documentSnapshot);
            }
        });

        game.collection("users")
                .whereEqualTo("left", false)
                .get()
                .addOnSuccessListener(new OnSuccessListener<QuerySnapshot>() {
                    @Override
                    public void onSuccess(QuerySnapshot queryDocumentSnapshots) {
                        updatePlayers(queryDocumentSnapshots);
                    }
                });
        game.collection("users")
                .addSnapshotListener(new EventListener<QuerySnapshot>() {
                    @Override
                    public void onEvent(@Nullable QuerySnapshot queryDocumentSnapshots, @Nullable FirebaseFirestoreException e) {
                        if(queryDocumentSnapshots != null) updatePlayers(queryDocumentSnapshots);
                        else Log.e("HopfenJagd", e.getMessage());
                    }
                });
    }

    private void updateSettings(DocumentSnapshot documentSnapshot) {
        if(documentSnapshot.getBoolean("isStarted") && !isStarted) startGame(documentSnapshot.getLong("start_time"));
        if(documentSnapshot.getBoolean("isStopped") && !isStarted && onStartedListener != null) onStartedListener.OnStarted(false);

        isStarted = documentSnapshot.getBoolean("isStarted");
        isStopped = documentSnapshot.getBoolean("isStopped");
        start_time = documentSnapshot.getLong("start_time");
        time_hunter = documentSnapshot.getLong("time_hunter").intValue();
        time_runner = documentSnapshot.getLong("time_runner").intValue();
    }

    public interface OnPlayersChangeListener {
        void OnPlayersChange(ArrayList<Player> players);
    }

    private OnPlayersChangeListener onPlayersChangeListener;

    public void setOnPlayersChangeListener(OnPlayersChangeListener onPlayersChangeListener) {
        this.onPlayersChangeListener = onPlayersChangeListener;
        onPlayersChangeListener.OnPlayersChange(players);
    }

    public interface OnCatchedListener {
        void OnCatched();
    }

    private OnCatchedListener onCatchedListener;

    public void setOnCatchedListener(OnCatchedListener onCatchedListener) {
        this.onCatchedListener = onCatchedListener;
        if(iscatched)
            onCatchedListener.OnCatched();
    }

    private void updatePlayers(QuerySnapshot queryDocumentSnapshots) {
        players.clear();
        boolean has_runner = false;
        boolean has_hunter = false;
        boolean got_catched = false;
        if(!queryDocumentSnapshots.isEmpty()) {
            for(DocumentSnapshot player : queryDocumentSnapshots.getDocuments()) {
                if(player.getId().equals(currentUser.getUid())) isrunner = player.getBoolean("isRunner");
                Player p = new Player(player.getString("name"), player.getId(), player.getBoolean("isCatched"), player.getBoolean("isRunner"), player.getGeoPoint("location"), player.getString("photo_url"));
                if(player.getBoolean("left") == false || p.isCatched()) players.add(p);

                if(!p.isCatched() && player.getBoolean("left") == false) {
                    if(p.isRunner()) has_runner = true;
                    else has_hunter = true;
                }

                if(p.getID().equals(currentUser.getUid()) && !iscatched && p.isCatched()) {
                    got_catched = true;
                }
            }
        }

        if(isStarted && !isStopped && !is_finished && !(has_runner && has_hunter)) gameEnds(has_runner);
        if(!is_finished && onPlayersChangeListener != null) onPlayersChangeListener.OnPlayersChange(players);

        if(has_hunter && has_runner && got_catched) {
            iscatched = true;
            if(onCatchedListener != null)
                onCatchedListener.OnCatched();
        }
    }

    public void continueAsHunter(boolean change) {
        if(iscatched) {
            if(change) updatePlayer(currentUser.getUid(), false);
            else stopGame();
        }
    }

    public void updatePlayer(String ID, boolean isRunner) {
        if(ID == currentUser.getUid()) isrunner = isRunner;
        db.collection("games")
                .document(game_id)
                .collection("users")
                .document(ID)
                .update("isRunner", isRunner);
    }

    public interface OnStartedListener {
        void OnStarted(boolean successful);
    }

    private OnStartedListener onStartedListener;

    public void setStartGame(final OnStartedListener listener) {
        this.onStartedListener = listener;
        db.collection("games")
                .document(game_id)
                .update("isStarted", true, "start_time", System.currentTimeMillis())
                .addOnFailureListener(new OnFailureListener() {
                    @Override
                    public void onFailure(@NonNull Exception e) {
                        listener.OnStarted(false);
                    }
                });
    }

    public void setOnStartedListener(OnStartedListener listener) {
        this.onStartedListener = listener;
    }

    private void startGame(long when) {
        if(onStartedListener != null) onStartedListener.OnStarted(true);

        registerLocationUpdater();
        builder.setUsesChronometer(true)
                .setWhen(when)
                .setContentText(getString(R.string.game_running));
        publishNotificationChanges();
    }

    public boolean isRunner() {
        return isrunner;
    }

    public long getStartTime() {
        return start_time;
    }

    public int getTimeHunter() {
        return time_hunter;
    }

    public int getTimeRunner() {
        return time_runner;
    }

    public void registerLocationUpdater() {
        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
                && checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_DENIED) {
            //todo denk dir was aus um des zu überprüfen
            //requestPermissions(new String[] {Manifest.permission.ACCESS_FINE_LOCATION}, permission_code);
        } else {
            LocationManager locationManager = (LocationManager) getSystemService(LOCATION_SERVICE);
            locationUpdater = new LocationUpdater();
            locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 10000, 0, locationUpdater);
        }
    }
    public void removeLocationUpdater() {
        LocationManager locationManager = (LocationManager) getSystemService(LOCATION_SERVICE);
        try{
            locationManager.removeUpdates(locationUpdater);
        } catch (IllegalArgumentException e) {}
    }

    private static LocationUpdater locationUpdater;

    private long last_location_update;

    private class LocationUpdater implements LocationListener {

        @Override
        public void onLocationChanged(Location location) {
            if((60 * 1000 * (isrunner?time_runner:time_hunter)) - ((last_location_update - start_time) % (60 * 1000 * (isrunner?time_runner:time_hunter))) < System.currentTimeMillis() - last_location_update){
                db.collection("games")
                        .document(game_id)
                        .collection("users")
                        .document(currentUser.getUid())
                        .update("location", new GeoPoint(location.getLatitude(), location.getLongitude()));
                last_location_update = System.currentTimeMillis();
            }
        }

        @Override
        public void onStatusChanged(String provider, int status, Bundle extras) {}

        @Override
        public void onProviderEnabled(String provider) {}

        @Override
        public void onProviderDisabled(String provider) {}
    }

    public void catched(String id) {
        db.collection("games")
                .document(game_id)
                .collection("users")
                .document(id)
                .update("isCatched", true);
    }

    private OnGameStoppedListener onGameStoppedListener;

    public interface OnGameStoppedListener {
        boolean OnGameStopped(boolean game_finished, boolean won, boolean isrunner);
    }

    public void setOnGameStoppedListener(OnGameStoppedListener listener) {
        this.onGameStoppedListener = listener;
    }

    private void gameEnds(boolean runner_won) {
        is_finished = true;

        stopGame(true, isrunner == runner_won);
    }

    public void leaveGame() {
        stopGame(false, false, false);
    }

    public void stopGame() {
        stopGame(false, false, true);
    }

    public void stopGame(boolean game_finished, boolean won) {
        stopGame(game_finished, won, true);
    }

    private void stopGame(boolean game_finished, boolean won, boolean stop) {
        if(!isStopped && onGameStoppedListener != null)
            if(!onGameStoppedListener.OnGameStopped(game_finished, won, isrunner) && game_finished) {
                NotificationCompat.Builder end_builder = new NotificationCompat.Builder(this, channel_id)
                        .setContentText(getString(R.string.game_ended))
                        .setSmallIcon(R.mipmap.hopfendolde)
                        .setContentText(getString(won?R.string.end_won:R.string.end_lost)
                                + "\n" + getString((won == isrunner)?R.string.end_no_more_hunter:R.string.end_no_more_runner));
                nManager.notify((int) (Math.random()*10000), end_builder.build());
            }

        isStopped = true;
        createStarted = false;
        onPlayersChangeListener = null;
        onCreationListener = null;
        onStartedListener = null;
        onGameStoppedListener = null;

        if(game_id != "" && game_id != null) {
            if(isCreator && !created && stop)
                db.collection("games")
                        .document(game_id)
                        .update("isStopped", true);
            db.collection("games")
                    .document(game_id)
                    .collection("users")
                    .document(currentUser.getUid())
                    .update("left", true);
            db.collection("users")
                    .document(currentUser.getUid())
                    .update("actual_game", "");
            game_id = null;
        }
        removeLocationUpdater();
        stopSelf();
    }
}
