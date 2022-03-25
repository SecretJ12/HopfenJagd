package de.secretj12.hopfenjagd.map;

import android.Manifest;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.fragment.app.Fragment;

import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.MapView;
import com.google.android.gms.maps.MapsInitializer;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.model.BitmapDescriptor;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.MarkerOptions;

import java.util.ArrayList;
import java.util.HashMap;

import de.secretj12.hopfenjagd.Player;
import de.secretj12.hopfenjagd.R;

public class fragment_maps extends Fragment implements OnMapReadyCallback {
    private MapView mMapView;
    private GoogleMap googleMap;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_maps, container, false);

        mMapView = view.findViewById(R.id.fragment_maps);
        mMapView.onCreate(savedInstanceState);
        mMapView.onResume();

        MapsInitializer.initialize(getActivity().getApplicationContext());

        mMapView.getMapAsync(this);

        return view;
    }

    @Override
    public void onMapReady(final GoogleMap googleMap) {
        Log.v("scottish", "ready");

        this.googleMap = googleMap;
        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && ActivityCompat.checkSelfPermission(getContext(), Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED) {
            Log.v("scottish", "show");
            googleMap.setMyLocationEnabled(true);
        } else {
            Log.v("scottish", "request");
            requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                    5533);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if(requestCode == 5533) {
            if(grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                try {
                    googleMap.setMyLocationEnabled(true);
                } catch (SecurityException e) {}
            }
        }
    }

    public void updatePlayers(ArrayList<Player> players, HashMap<String, Bitmap> pictures) {
        googleMap.clear();
        for(Player p : players) {
            if(p.getLocation() != null)
                googleMap.addMarker(
                    new MarkerOptions()
                            .position(new LatLng(p.getLocation().getLatitude(), p.getLocation().getLongitude()))
                            .title(p.getName())
                            .snippet(p.isRunner()?getString(R.string.runner):getString(R.string.hunter))
                            .anchor(0.5f, pictures.containsKey(p.getID())?0.5f:1f)
                            .icon(pictures.containsKey(p.getID())?
                                            BitmapDescriptorFactory.fromBitmap(pictures.get(p.getID()))
                                            :
                                            getMarkerIcon(
                                                    p.isRunner()?
                                                            R.color.runner
                                                            :
                                                            R.color.hunter
                                            )
                            )
                );
        }
    }

    public BitmapDescriptor getMarkerIcon(int color) {
        float[] hsv = new float[3];
        Color.colorToHSV(getResources().getColor(color), hsv);
        return BitmapDescriptorFactory.defaultMarker(hsv[0]);

    }

    public void centerCam(Player p) {
        googleMap.animateCamera(CameraUpdateFactory.newLatLngZoom(new LatLng(p.getLocation().getLatitude(), p.getLocation().getLongitude()), 16.8f));
    }

    @Override
    public void onResume() {
        super.onResume();
        mMapView.onResume();
    }

    @Override
    public void onStop() {
        super.onStop();
        mMapView.onStop();
    }

    @Override
    public void onPause() {
        super.onPause();
        mMapView.onPause();
    }

    @Override
    public void onStart() {
        super.onStart();
        mMapView.onStart();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        mMapView.onDestroy();
    }
}
