package de.secretj12.hopfenjagd;

import com.google.firebase.firestore.GeoPoint;

public class Player {
    private String name;
    private String ID;
    private boolean isCatched;
    private boolean isRunner;
    private GeoPoint location;
    private String photo_url;

    public Player(String name, String ID, boolean isCatched, boolean isRunner, GeoPoint location, String photo_url) {
        this.name = name;
        this.ID = ID;
        this.isCatched = isCatched;
        this.isRunner = isRunner;
        this.location = location;
        this.photo_url = photo_url;
    }

    public String getName() {
        return name;
    }

    public String getID() {
        return ID;
    }

    public boolean isCatched() {
        return isCatched;
    }

    public boolean isRunner() {
        return isRunner;
    }

    public GeoPoint getLocation() {
        return location;
    }

    public String getPhotoURL() {
        return photo_url;
    }
}
