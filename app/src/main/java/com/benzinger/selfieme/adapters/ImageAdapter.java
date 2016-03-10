package com.benzinger.selfieme.adapters;

import android.content.Context;
import android.graphics.Color;
import android.net.Uri;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.GridView;
import android.widget.ImageView;

import java.util.List;


public class ImageAdapter extends BaseAdapter {

    private final Context context;
    private final List<Uri> picList;

    public ImageAdapter(Context c, List<Uri> pictures) {
        context = c;
        picList = pictures;
    }

    @Override
    public int getCount() {
        return picList.size();
    }

    @Override
    public Object getItem(int position) {
        return picList.get(position);
    }

    @Override
    public long getItemId(int position) {
        return 0;
    }

    // create a new ImageView for each item referenced by the Adapter
    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        ImageView imageView;
        if (convertView == null) {
            // if it's not recycled, initialize some attributes
            imageView = new ImageView(context);
            imageView.setLayoutParams(new GridView.LayoutParams(100, 100)); //sets height and with for this individual view
            imageView.setScaleType(ImageView.ScaleType.CENTER_CROP); //if crop required, crop towards center
            imageView.setPadding(2, 2, 2, 2);
            imageView.setBackgroundColor(Color.BLACK);
        } else {
            imageView = (ImageView) convertView;
        }
        imageView.setImageURI(picList.get(position));
        return imageView;
    }
}
