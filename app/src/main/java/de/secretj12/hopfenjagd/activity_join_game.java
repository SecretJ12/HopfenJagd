package de.secretj12.hopfenjagd;

import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.text.InputFilter;
import android.text.Spanned;
import android.view.KeyEvent;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

public class activity_join_game extends AppCompatActivity {
    private EditText view_game_id;
    private Button next;

    private ServiceConnection serviceConnection;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_join_game);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        view_game_id = findViewById(R.id.game_id);
        next = findViewById(R.id.button_next);

        view_game_id.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                join(view_game_id);
                return false;
            }
        });

        view_game_id.setFilters(new InputFilter[] {
                new InputFilter() {
                    @Override
                    public CharSequence filter(CharSequence charSequence, int start, int end, Spanned dest, int dstart, int dend) {
                        String newString = dest.toString().substring(0, dstart) + charSequence + dest.toString().substring(dend, dest.length());
                        if(filter(newString))
                            return null;
                        else
                            if(charSequence.toString() == "")
                                return dest.subSequence(dstart, dend);
                            else
                                return "";
                    }

                    private boolean filter(String newString) {
                        if(newString.length() > 7
                            || (newString.length() > 3 && newString.charAt(3) != '-')) return false;
                        if(newString.length() > 3) newString = newString.substring(0, 3) + newString.substring(4);
                        try{
                            Integer.parseInt(newString);
                            return true;
                        } catch (NumberFormatException e) {
                            if(newString.length() == 0) return true;
                            return false;
                        }
                    }
                }
        });

        next.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                join(view_game_id);
            }
        });
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {

        if(item.getItemId() == android.R.id.home) {
            onBackPressed();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onBackPressed() {
        startActivity(new Intent(this, activity_start_menu.class).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP));
        super.onBackPressed();
    }

    private void join(EditText view_game_id) {
        setEnabled(false);
        if(view_game_id.getText().length() == 7) {
            Intent intent = new Intent(this, Service_Game.class);
            intent.putExtra("game_id", view_game_id.getText().toString());
            serviceConnection = new ServiceConnection() {
                @Override
                public void onServiceConnected(ComponentName name, IBinder service) {
                    final Service_Game.LocalBinder binder = (Service_Game.LocalBinder) service;
                    if(binder.getState() != Service_Game.OFF && binder.getState() != Service_Game.CREATING) startActivity(new Intent(activity_join_game.this, binder.getCorrectActivity()));
                    else binder.setOnStateChangeListener(new Service_Game.onStateChangedListener() {
                        @Override
                        public void onStateChanged() {
                            if(binder.getState() != Service_Game.CREATING)
                                startActivity(new Intent(activity_join_game.this, binder.getCorrectActivity()));
                        }
                    });
                }

                @Override
                public void onServiceDisconnected(ComponentName name) {
                    Toast.makeText(activity_join_game.this, R.string.join_failed, Toast.LENGTH_LONG).show();
                    setEnabled(true);
                }
            };

            if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                startForegroundService(intent);
            else
                startService(intent);
            bindService(intent, serviceConnection, BIND_IMPORTANT);
            return;
        }
        Toast.makeText(activity_join_game.this, R.string.join_failed, Toast.LENGTH_LONG).show();
        setEnabled(true);
    }

    private void setEnabled(boolean a) {
        view_game_id.setEnabled(a);
        next.setEnabled(a);
    }

    @Override
    protected void onStop() {
        try{
            unbindService(serviceConnection);
        }catch (IllegalArgumentException e) {}
        super.onStop();
    }
}
