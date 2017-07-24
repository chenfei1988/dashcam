package com.dashcam.photovedio;

/**
 * Created by Administrator on 2017/7/12.
 */

public class DriveVideo {
    private int id;
    private String name;
    private int lock;
    private int resolution;//分辨率
    private String path;

    public DriveVideo(int id, String name, int lock, int resolution) {
        this.id = id;
        this.name = name;
        this.lock = lock;
        this.resolution = resolution;
    }

    public DriveVideo(String name, int lock, int resolution, String path) {
        this.name = name;
        this.lock = lock;
        this.resolution = resolution;
        this.path = path;
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public int getLock() {
        return lock;
    }

    public void setLock(int lock) {
        this.lock = lock;
    }

    public int getResolution() {
        return resolution;
    }

    public void setResolution(int resolution) {
        this.resolution = resolution;
    }
}
