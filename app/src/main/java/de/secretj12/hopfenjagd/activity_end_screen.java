package de.secretj12.hopfenjagd;

import androidx.appcompat.app.AppCompatActivity;

import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Binder;
import android.os.Bundle;
import android.os.IBinder;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import org.w3c.dom.Text;

public class activity_end_screen extends AppCompatActivity {
    Service_Game.LocalBinder binder;

    //todo shows statics and similar

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_end_screen);

        final TextView text = findViewById(R.id.end_text);
        final TextView time = findViewById(R.id.passed_time);

        binder = null;
        Intent intent = new Intent(this, Service_Game.class);
        bindService(intent, new ServiceConnection() {
            @Override
            public void onServiceConnected(ComponentName name, IBinder service) {
                binder = (Service_Game.LocalBinder) service;
                binder.cancelNotifications();

                text.setText(getString(binder.isFinished()?(binder.hasWon()?R.string.end_won:R.string.end_lost):(R.string.game_stopped)));
                if(!binder.isFinished()) time.setText("--:--:--");
                else time.setText(setTimeInLayout(binder.getTime()));
            }

            @Override
            public void onServiceDisconnected(ComponentName name) {
                binder = null;
            }
        }, BIND_AUTO_CREATE);

        Button button = findViewById(R.id.button_end);
        button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                stop();
            }
        });
    }

    private String setTimeInLayout(long time) {
        time /= 1000;
        int h = (int) time/3600;
        int m = (int) time/60%60;
        int s = (int) time%60;
        return (h>9?"":"0") + h + ":" + (m>9?"":"0") + m + ":" + (s>9?"":"0") + s;
    }

    @Override
    public void onBackPressed() {
        stop();
    }

    private void stop() {
        if(binder != null)
            binder.stopService();
        startActivity(new Intent(this, activity_loading_screen.class).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP));
    }
}
