package de.secretj12.hopfenjagd.create_game;

import android.os.Build;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.CompoundButton;
import android.widget.ListView;
import android.widget.Switch;

import androidx.fragment.app.Fragment;

import java.util.ArrayList;

import de.secretj12.hopfenjagd.Player;
import de.secretj12.hopfenjagd.R;
import de.secretj12.hopfenjagd.Service_Game;

public class fragment_create_game_player extends Fragment {
    private ListView players_config_list;
    private ArrayList<Player> players;
    private Service_Game.LocalBinder binder;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_create_game_player, container, false);

        players_config_list = view.findViewById(R.id.till_start_player_list);

        if(binder != null && players != null) updateList(players, binder);
        return view;
    }

    public void updateList(ArrayList<Player> players, final Service_Game.LocalBinder binder) {
        if(players_config_list == null || getContext() == null) {
            this.players = players;
            this.binder = binder;
            return;
        }

        ArrayAdapter config_adapter = new ArrayAdapter<Player>(
                getContext(),
                R.layout.layout_list_player_config,
                R.id.is_runner,
                players) {
            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                View view = super.getView(position, convertView, parent);

                final Player player = getItem(position);

                final Switch is_runner = view.findViewById(R.id.is_runner);
                is_runner.setText(player.getName());
                if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) is_runner.setTextColor(getContext().getColor(player.isRunner()?R.color.runner:R.color.hunter));
                else is_runner.setTextColor(getContext().getResources().getColor(player.isRunner()?R.color.runner:R.color.hunter));
                is_runner.setChecked(player.isRunner());

                is_runner.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                    @Override
                    public void onCheckedChanged(CompoundButton compoundButton, boolean b) {
                        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) is_runner.setTextColor(getContext().getColor(b?R.color.runner:R.color.hunter));
                        else is_runner.setTextColor(getContext().getResources().getColor(b?R.color.runner:R.color.hunter));
                        if(binder != null) binder.updatePlayer(player.getID(), b);
                        compoundButton.setEnabled(false);
                    }
                });

                return view;
            }
        };

        players_config_list.setAdapter(config_adapter);
        config_adapter.notifyDataSetChanged();
    }
}
