package com.benzinger.selfieme;

import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.widget.ImageView;

import androidx.appcompat.app.AppCompatActivity;

/**
 * Fullscreen activity used to display a full-size selfie
 */
public class FullScreenPicActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_full_screen_pic);
        ImageView imageView = findViewById(R.id.fullscreenimageview);
        Drawable picture = Drawable.createFromPath(getIntent().getStringExtra(SelfieMainActivity.FILENAME_EXTRA));
        imageView.setBackground(picture);
    }
}
