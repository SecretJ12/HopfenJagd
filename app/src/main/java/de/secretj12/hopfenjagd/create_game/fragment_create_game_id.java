package de.secretj12.hopfenjagd.create_game;


import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.fragment.app.Fragment;

import de.secretj12.hopfenjagd.R;

public class fragment_create_game_id extends Fragment {
    private TextView view_game_id;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_create_game_id, container, false);

        view_game_id = view.findViewById(R.id.game_id);


        if(getArguments() != null && getArguments().containsKey("game_id")) view_game_id.setText(getArguments().getString("game_id"));
        return view;
    }

    public void setGameID(String gameID) {
        view_game_id.setText(gameID);
    }
}
