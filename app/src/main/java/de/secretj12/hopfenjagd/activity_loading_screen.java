package de.secretj12.hopfenjagd;

import android.annotation.SuppressLint;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;

import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.remoteconfig.FirebaseRemoteConfig;
import com.google.firebase.remoteconfig.FirebaseRemoteConfigSettings;

public class activity_loading_screen extends AppCompatActivity {
    private int DELAY = 500;
    private ServiceConnection serviceConnection;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_loading_screen);
    }

    @Override
    protected void onResume() {
        super.onResume();

        final FirebaseRemoteConfig remoteConfig = FirebaseRemoteConfig.getInstance();
        remoteConfig.fetchAndActivate().addOnCompleteListener(new OnCompleteListener<Boolean>() {
            @Override
            public void onComplete(@NonNull Task<Boolean> task) {
                Log.d(getString(R.string.app_name), "reload successful: " + task.isSuccessful());
                Log.d(getString(R.string.app_name), "time hunter: " + remoteConfig.getLong("time_hunter") + " - time runner: " + remoteConfig.getLong("time_runner"));
            }
        });

        final long start_time = System.currentTimeMillis();
        Intent service_intent = new Intent(this, Service_Game.class);
        serviceConnection = new ServiceConnection() {
            @Override
            public void onServiceConnected(ComponentName name, IBinder service) {
                final Service_Game.LocalBinder binder = (Service_Game.LocalBinder) service;
                binder.cancelNotifications();

                if(System.currentTimeMillis()-start_time > DELAY)
                    startActivity(new Intent(activity_loading_screen.this, binder.getCorrectActivity()));
                else
                    new Handler().postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            startActivity(new Intent(activity_loading_screen.this, binder.getCorrectActivity()));
                        }
                    }, start_time + DELAY - System.currentTimeMillis());
            }

            @Override
            public void onServiceDisconnected(ComponentName name) {
                Log.e(getString(R.string.app_name), "Service not bound");
            }
        };
        bindService(service_intent, serviceConnection, BIND_AUTO_CREATE);
    }

    @Override
    protected void onStop() {
        try{
            unbindService(serviceConnection);
        }catch (IllegalArgumentException e) {}
        super.onStop();
    }
}
