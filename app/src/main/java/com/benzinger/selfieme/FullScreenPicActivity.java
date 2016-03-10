package com.benzinger.selfieme;

import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.widget.ImageView;

/**
 * Fullscreen activity used to display a full-size selfie
 */
public class FullScreenPicActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_full_screen_pic);
        ImageView imageView = (ImageView)findViewById(R.id.fullscreenimageview);
        Drawable picture = Drawable.createFromPath(getIntent().getStringExtra(SelfieMainActivity.FILENAME_EXTRA));
        //imageView.getLayoutParams().height = picture.getIntrinsicHeight(); //set imageview height as height of image
        //imageView.getLayoutParams().width = picture.getIntrinsicWidth();   //set imageview width as width of image
        imageView.setBackground(picture);
    }
}
