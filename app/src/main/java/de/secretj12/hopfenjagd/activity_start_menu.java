package de.secretj12.hopfenjagd;


import android.Manifest;
import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.location.LocationManager;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;
import android.view.View;
import android.widget.Button;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.firebase.ui.auth.AuthUI;
import com.firebase.ui.auth.IdpResponse;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.remoteconfig.FirebaseRemoteConfig;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import de.secretj12.hopfenjagd.create_game.activity_create_game;

public class activity_start_menu extends AppCompatActivity {
    private FirebaseAuth mAuth;
    private FirebaseUser currentUser;
    private boolean loggedin;
    private FirebaseFirestore db;
    private FirebaseRemoteConfig remoteConfig;
    private Button login;
    private Button start_game;
    private Button join_game;
    private int RC_GOOGLE_SIGN_IN = 3451;
    private ServiceConnection serviceConnection;

    private boolean activityActive;
    private int permission_code = 3423;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        activityActive = true;
        serviceConnection = new ServiceConnection() {
            @Override
            public void onServiceConnected(ComponentName componentName, IBinder iBinder) {
                Service_Game.LocalBinder binder = (Service_Game.LocalBinder) iBinder;
                binder.cancelNotifications();
            }

            @Override
            public void onServiceDisconnected(ComponentName componentName) {
            }
        };

        bindService(new Intent(this, Service_Game_old.class), serviceConnection, BIND_IMPORTANT);

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_start_menu);
        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();
        remoteConfig = FirebaseRemoteConfig.getInstance();
        remoteConfig.setDefaultsAsync(R.xml.firebase_default);
        remoteConfig.fetchAndActivate();

        login = findViewById(R.id.button_login);
        start_game = findViewById(R.id.button_start_game);
        join_game = findViewById(R.id.button_join_game);
        updateLoginState();

        login.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if(loggedin) {
                    Log.i(getString(R.string.app_name), "Logged out");
                    mAuth.signOut();
                    updateLoginState();
                } else {
                    startActivityForResult(AuthUI.getInstance()
                                    .createSignInIntentBuilder()
                                    .setAvailableProviders(Arrays.asList(
                                            new AuthUI.IdpConfig.GoogleBuilder().build()
                                    ))
                                    .build(),
                            RC_GOOGLE_SIGN_IN);
                }
            }
        });

        start_game.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if(checkGNSSenabled()) {
                    Intent intent = new Intent(activity_start_menu.this, activity_create_game.class);
                    activity_start_menu.this.startActivity(intent);
                }
            }
        });

        join_game.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if(checkGNSSenabled()) {
                    Intent intent = new Intent(activity_start_menu.this, activity_join_game.class);
                    activity_start_menu.this.startActivity(intent);
                }
            }
        });

        checkGNSSenabled();
    }

    private boolean checkGNSSenabled() {
        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
                && checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_DENIED) {
            requestPermissions(new String[] {Manifest.permission.ACCESS_FINE_LOCATION}, permission_code);
            return false;
        } else {
            LocationManager locationManager = (LocationManager) getSystemService(LOCATION_SERVICE);

            boolean gps_ok;
            if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                gps_ok = locationManager.isLocationEnabled();
            } else {
                gps_ok = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER);
            }

            if(!gps_ok) {
                AlertDialog.Builder alert = new AlertDialog.Builder(this);
                alert.setTitle(R.string.gps_diabled);
                alert.setMessage(R.string.gps_diabled_message);
                alert.create().show();
            }
            return gps_ok;
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == RC_GOOGLE_SIGN_IN) {
            IdpResponse response = IdpResponse.fromResultIntent(data);

            if (resultCode == RESULT_OK) {
                Log.i(getString(R.string.app_name), "Logged in");

                updateLoginState();
                Login();
            } else {
                Log.e("HopfenJagd", "Login Fehler");
                for(String s: data.getExtras().keySet()){
                    Log.e("HopfenJagd", data.getExtras().get(s).toString());
                }
            }
        } else if (requestCode == permission_code) {
            if(resultCode == RESULT_OK) {
                checkGNSSenabled();
            }
        }
    }

    private void updateLoginState() {
        currentUser = mAuth.getCurrentUser();
        loggedin = currentUser != null;
        start_game.setEnabled(loggedin);
        join_game.setEnabled(loggedin);

        login.setText(loggedin?R.string.logout:R.string.login);
    }

    private void Login() {
        Map<String, Object> userData = new HashMap<>();
        userData.put("isAdmin", false);
        userData.put("name", currentUser.getDisplayName());
        userData.put("actual_game", null);

        db.collection("users")
                .document(currentUser.getUid())
                .set(userData)
                .addOnFailureListener(new OnFailureListener() {
                    @Override
                    public void onFailure(Exception e) {
                        mAuth.signOut();
                        updateLoginState();
                    }
                });
    }

    @Override
    public void onBackPressed() {
        finishAffinity();
    }

    @Override
    protected void onStop() {
        super.onStop();
        try{
            unbindService(serviceConnection);
        }catch (IllegalArgumentException e) {}
    }

    @Override
    protected void onPause() {
        super.onPause();
        activityActive = false;
    }

    @Override
    protected void onResume() {
        super.onResume();
        activityActive = true;
    }
}
