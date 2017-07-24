package com.dashcam.base;

/**
 * Created by YangQiang on 2017/2/13.
 */

public class RefreshEvent {


    public RefreshEvent(String photopath, String vediopath) {
        this.photopath = photopath;
        this.vediopath = vediopath;
    }

    public RefreshEvent(int ywlx, String photopath, String vediopath) {
        this.ywlx = ywlx;
        this.photopath = photopath;
        this.vediopath = vediopath;
    }
    public RefreshEvent(int ywlx) {
        this.ywlx = ywlx;
    }
    private int ywlx;
    private  String photopath;
    private  String vediopath;

    public String getPhotopath() {
        return photopath;
    }

    public void setPhotopath(String photopath) {
        this.photopath = photopath;
    }

    public String getVediopath() {
        return vediopath;
    }

    public void setVediopath(String vediopath) {
        this.vediopath = vediopath;
    }

    public int getYwlx() {
        return ywlx;
    }

    public void setYwlx(int ywlx) {
        this.ywlx = ywlx;
    }
}
