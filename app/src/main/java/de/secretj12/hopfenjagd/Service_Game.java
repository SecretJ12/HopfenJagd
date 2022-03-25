package de.secretj12.hopfenjagd;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationChannelGroup;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.media.AudioAttributes;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Binder;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.provider.MediaStore;
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

import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import javax.annotation.Nullable;

import de.secretj12.hopfenjagd.create_game.activity_create_game;
import de.secretj12.hopfenjagd.map.activity_map;

public class Service_Game extends Service {
    //aktueller Status der App
    private byte state;
    public static final byte OFF = 0;
    public static final byte CREATING = 1;
    public static final byte PREPARING = 2;
    public static final byte RUNNING = 3;
    public static final byte END = 4;

    //Spieleinstellungen
        //ID
    private String game_id;
    private boolean isCreator;
        //eigentliches Spiel
    private long start_time;
    private int time_hunter;
    private int time_runner;
    private boolean isRunner;
    private boolean isCatched;
    private boolean isFinished;
    private long end_time;
    private boolean won;
    private ArrayList<Player> players;


    //Notification
    private int main_notification_id;
    private int update_notification_id;
    private String main_channel_id = "game";
    private String update_channel_id = "update_notification";
    private NotificationCompat.Builder builder;
    private NotificationManager nManager;

    //Firebase
    private FirebaseAuth mAuth;
    private FirebaseUser currentUser;
    private FirebaseFirestore db;
    private FirebaseRemoteConfig remoteConfig;

    //Listener
    private onPlayersChangeListener onPlayersChangeListener;
    private onCatchedListener onCatchedListener;
    private onStateChangedListener onStateChangedListener;
    private onUpdateNotificationListener onUpdateNotificationListener;


    @Override
    public void onCreate() {
        super.onCreate();
        state = OFF;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if(state != OFF) return Service.START_NOT_STICKY;
        Log.i("HopfenJagd", "Spiel wird gestartet...");
        state = CREATING;
        
        prepareNotification();
        startForeground(main_notification_id, builder.build());
        publishNotificationChanges();

        mAuth = FirebaseAuth.getInstance();
        currentUser = mAuth.getCurrentUser();
        db = FirebaseFirestore.getInstance();
        remoteConfig = FirebaseRemoteConfig.getInstance();

        this.game_id = intent.getStringExtra("game_id");
        isCreator =  game_id == null || game_id.equals("");
        start_time = -1;
        won = false;
        isFinished = false;
        end_time = -1;
        time_hunter = (int) remoteConfig.getLong("time_hunter");
        time_runner = (int) remoteConfig.getLong("time_runner");
        Log.d(getString(R.string.app_name), "time hunter: " + time_hunter + " - time runnter: " + time_runner);
        isCatched = false;
        players = new ArrayList<>();

        prepareGame();

        return Service.START_NOT_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return new LocalBinder();
    }

    public class LocalBinder extends Binder {
        public byte getState() {
            return Service_Game.this.state;
        }
        public Class getCorrectActivity() {
            switch(getState()) {
                case CREATING:
                case PREPARING: return isCreator? activity_create_game.class:activity_till_start.class;
                case RUNNING: return activity_map.class;
                case END: return activity_end_screen.class;
                case OFF:
                default: return activity_start_menu.class;
            }
        }
        public String getGameID() {
            return game_id;
        }
        public void setOnPlayersChangeListener(onPlayersChangeListener onPlayersChangeListener) {
            Service_Game.this.onPlayersChangeListener = onPlayersChangeListener;
            onPlayersChangeListener.onPlayersChange(players);
        }
        public void setOnCatchedListener(onCatchedListener onCatchedListener) {
            Service_Game.this.onCatchedListener = onCatchedListener;
        }
        public void setOnStateChangeListener(onStateChangedListener onStateChangedListener) {
            Service_Game.this.onStateChangedListener = onStateChangedListener;
        }
        public void setOnUpdateNotificationListener(onUpdateNotificationListener onUpdateNotificationListener) {
            Service_Game.this.onUpdateNotificationListener = onUpdateNotificationListener;
        }

        public void updatePlayer(String ID, boolean isRunner) {
            Service_Game.this.updatePlayer(ID, isRunner);
        }
        public void startGame() {
            startWholeGame();
        }
        public boolean isRunner() {
            return isRunner;
        }
        public int getTimeHunter() {
            return time_hunter;
        }
        public int getTimeRunner() {
            return time_runner;
        }
        public long getStartTime() {
            return start_time;
        }
        public void catched(String id) {
            Service_Game.this.catched(id);
        }
        public void continueAsHunter(boolean change) {
            Service_Game.this.continueAsHunter(change);
        }
        public void leaveGame() {
            stopGame();
        }
        public boolean hasWon() {
            return won;
        }
        public boolean isFinished() { return isFinished;}
        public long getTime() { return isFinished?end_time-start_time:-1;}
        public void stopService() {
            state = OFF;
            stopSelf();
        }
        public void cancelNotifications() {
            if(nManager != null)
                nManager.cancelAll();
            else {
                nManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
                nManager.cancelAll();
            }
        }
        public void cancelUpdateNotifications() {
            if(nManager != null)
                nManager.cancel(update_notification_id);
        }
    }

    //Listener
    public interface onPlayersChangeListener {
        void onPlayersChange(ArrayList<Player> players);
    }
    public interface onStateChangedListener {
        void onStateChanged();
    }
    public interface onCatchedListener {
        void onCatched();
    }
    public interface onUpdateNotificationListener {
        boolean onUpdateNotificationListener();
    }

    private void prepareNotification() {
        nManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannelGroup notification_group = new NotificationChannelGroup("notification_group", getString(R.string.game));

            int importance = NotificationManager.IMPORTANCE_DEFAULT;
            NotificationChannel main_channel = new NotificationChannel(main_channel_id, this.getString(R.string.notification_channel_name), importance);
            main_channel.setDescription(getString(R.string.notification_channel_description));
            main_channel.setGroup("notification_group");

            NotificationChannel update_channel = new NotificationChannel(update_channel_id, this.getString(R.string.update_channel_name), importance);
            Uri notification_sound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
            AudioAttributes att = new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_GAME)
                    .setContentType(AudioAttributes.CONTENT_TYPE_UNKNOWN)
                    .build();
            update_channel.setSound(notification_sound, att);
            update_channel.enableVibration(true);
            update_channel.setLightColor(Color.YELLOW);
            update_channel.setDescription(getString(R.string.update_channel_description));
            update_channel.setGroup("notification_group");

            nManager = getSystemService(NotificationManager.class);
            nManager.createNotificationChannelGroup(notification_group);
            nManager.createNotificationChannel(main_channel);
            nManager.createNotificationChannel(update_channel);
        }

        main_notification_id = (int) System.currentTimeMillis();
        update_notification_id = main_notification_id + 1;

        PendingIntent loading_screen_intent = PendingIntent.getActivity(this, 7456, new Intent(this, activity_loading_screen.class), 0);

        builder = new NotificationCompat.Builder(this, main_channel_id)
                .setContentTitle(getString(R.string.app_name))
                .setContentText(getString(R.string.game_starting))
                .setSmallIcon(R.drawable.hopfendolde_icon)
                .setLargeIcon(BitmapFactory.decodeResource(getResources(), R.mipmap.ic_launcher))
                .setContentIntent(loading_screen_intent)
                .setUsesChronometer(false);
    }
    private void publishNotificationChanges() {
        nManager.notify(main_notification_id, builder.build());
    }

    private void prepareGame() {
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
                                if(onStateChangedListener != null) onStateChangedListener.onStateChanged();
                                state = OFF;
                                stopGame();
                            }
                        }
                    });
        }
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
                        game_data.put("isFinished", false);
                        game_data.put("start_time", -1);

                        db.collection("games")
                                .document(game_id)
                                .set(game_data)
                                .addOnFailureListener(new OnFailureListener() {
                                    @Override
                                    public void onFailure(@NonNull Exception e) {
                                        state = OFF;
                                        if(onStateChangedListener != null) onStateChangedListener.onStateChanged();
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
                        state = OFF;
                        if(onStateChangedListener != null) onStateChangedListener.onStateChanged();
                        stopSelf();
                    }
                });
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
                        state = OFF;
                        if(onStateChangedListener != null) onStateChangedListener.onStateChanged();
                        stopSelf();
                    }
                });
    }
    private void registerUserinGame() {
        Map<String, Object> user_data = new HashMap<>();
        user_data.put("name", currentUser.getDisplayName());
        user_data.put("location", null);
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
                        state = PREPARING;
                        if(onStateChangedListener != null) onStateChangedListener.onStateChanged();
                        gamePrepared();
                    }
                })
                .addOnFailureListener(new OnFailureListener() {
                    @Override
                    public void onFailure(@NonNull Exception e) {
                        state = OFF;
                        if(onStateChangedListener != null) onStateChangedListener.onStateChanged();
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
                        else Log.e("HopfenJagd", "1: " + e.getMessage());
                    }
                });
    }
    private void updatePlayer(String ID, boolean isRunner) {
        if(ID == currentUser.getUid()) this.isRunner = isRunner;
        db.collection("games")
                .document(game_id)
                .collection("users")
                .document(ID)
                .update("isRunner", isRunner);
    }

    private void updateSettings(DocumentSnapshot documentSnapshot) {
        start_time = documentSnapshot.getLong("start_time");
        time_hunter = documentSnapshot.getLong("time_hunter").intValue();
        time_runner = documentSnapshot.getLong("time_runner").intValue();

        if(documentSnapshot.getBoolean("isStarted") && state == PREPARING) {
            state = RUNNING;
            startGame(documentSnapshot.getLong("start_time"));
        }
        if(documentSnapshot.getBoolean("isFinished")) {
            isFinished = true;
            stopGame();
        }
        if(documentSnapshot.getBoolean("isStopped") && (state == PREPARING || state == RUNNING))  {
            stopGame();
        }
    }

    private void updatePlayers(QuerySnapshot queryDocumentSnapshots) {
        players.clear();
        boolean has_runner = false;
        boolean has_hunter = false;
        boolean got_catched = false;
        if(!queryDocumentSnapshots.isEmpty()) {
            for(DocumentSnapshot player : queryDocumentSnapshots.getDocuments()) {
                if(player.getId().equals(currentUser.getUid())) isRunner = player.getBoolean("isRunner");
                Player p = new Player(player.getString("name"), player.getId(), player.getBoolean("isCatched"), player.getBoolean("isRunner"), player.getGeoPoint("location"), player.getString("photo_url"));
                if(player.getBoolean("left") == false || p.isCatched()) players.add(p);

                if(!p.isCatched() && player.getBoolean("left") == false) {
                    if(p.isRunner()) has_runner = true;
                    else has_hunter = true;
                }

                if(p.getID().equals(currentUser.getUid()) && !isCatched && p.isCatched()) {
                    got_catched = true;
                }
            }
        }

        if(state == RUNNING && !(has_runner && has_hunter)) {
            if(isRunner && has_runner) won = true;
            else if(!isRunner && has_hunter) won = true;
            if(isCreator) {
                db.collection("games")
                        .document(game_id)
                        .update("isFinished", true);
            }
            isFinished = true;
            stopGame();
        }
        if((state == PREPARING || state == RUNNING) && onPlayersChangeListener != null) onPlayersChangeListener.onPlayersChange(players);
        if(state == RUNNING) {
            if(onUpdateNotificationListener == null || !onUpdateNotificationListener.onUpdateNotificationListener()) {
                PendingIntent loading_screen_intent = PendingIntent.getActivity(this, 7456, new Intent(this, activity_loading_screen.class), 0);

                NotificationCompat.Builder end_builder = new NotificationCompat.Builder(this, update_channel_id)
                        .setContentTitle(getString(R.string.notification_update_position_name))
                        .setSmallIcon(R.mipmap.hopfendolde)
                        .setAutoCancel(true)
                        .setContentIntent(loading_screen_intent)
                        .setContentText(getString(R.string.notification_update_position_description));
                nManager.notify(update_notification_id, end_builder.build());
            }
        }

        if(has_hunter && has_runner && got_catched) {
            isCatched = true;
            if(onCatchedListener != null)
                onCatchedListener.onCatched();
        }
    }

    public void registerLocationUpdater() {
        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
                && checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_DENIED) {
            //todo denk dir was aus um des zu überprüfen
            //requestPermissions(new String[] {Manifest.permission.ACCESS_FINE_LOCATION}, permission_code);
        } else {
            LocationManager locationManager = (LocationManager) getSystemService(LOCATION_SERVICE);
            locationUpdater = new Service_Game.LocationUpdater();
            locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 10000, 0, locationUpdater);
        }
        last_location_update = 0;
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
            if((60 * 1000 * (isRunner?time_runner:time_hunter)) - ((last_location_update - start_time) % (60 * 1000 * (isRunner?time_runner:time_hunter))) < System.currentTimeMillis() - last_location_update){
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

    public void startWholeGame() {
        db.collection("games")
                .document(game_id)
                .update("isStarted", true, "start_time", System.currentTimeMillis())
                .addOnFailureListener(new OnFailureListener() {
                    @Override
                    public void onFailure(@NonNull Exception e) {
                        if(onStateChangedListener != null) onStateChangedListener.onStateChanged();
                    }
                });
    }
    public void catched(String id) {
        db.collection("games")
                .document(game_id)
                .collection("users")
                .document(id)
                .update("isCatched", true);
    }

    private void startGame(long when) {
        if(onStateChangedListener != null) onStateChangedListener.onStateChanged();

        registerLocationUpdater();
        builder.setUsesChronometer(true)
                .setWhen(when)
                .setContentText(getString(R.string.game_running));
        publishNotificationChanges();
    }

    private void continueAsHunter(boolean change) {
        if(isCatched) {
            if(change) updatePlayer(currentUser.getUid(), false);
            else {
                isFinished = true;
                stopGame();
            }
        }
    }

    private void stopGame() {
        if(state == END) return;
        
        state = END;
        if(isFinished) end_time = System.currentTimeMillis();
        if(onStateChangedListener != null) onStateChangedListener.onStateChanged();

        nManager.cancelAll();

        PendingIntent loading_screen_intent = PendingIntent.getActivity(this, 7456, new Intent(this, activity_end_screen.class), 0);

        if(isFinished) {
            NotificationCompat.Builder end_builder = new NotificationCompat.Builder(this, main_channel_id)
                    .setContentTitle(getString(R.string.game_ended))
                    .setSmallIcon(R.mipmap.hopfendolde)
                    .setAutoCancel(true)
                    .setContentIntent(loading_screen_intent)
                    .setContentText(getString(won?R.string.end_won:R.string.end_lost)
                            + "\n" + getString((won?isRunner:!isRunner)?R.string.end_no_more_hunter:R.string.end_no_more_runner));
            nManager.notify((int) (Math.random()*10000), end_builder.build());
        }

        onPlayersChangeListener = null;
        onCatchedListener = null;
        onStateChangedListener = null;

        if(game_id != "" && game_id != null) {
            if(isCreator){
                db.collection("games")
                        .document(game_id)
                        .update("isStopped", true);
            }
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
        stopForeground(true);
        stopSelf();
    }
}
