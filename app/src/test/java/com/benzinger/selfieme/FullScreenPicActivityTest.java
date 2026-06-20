package com.benzinger.selfieme;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import android.content.Intent;
import android.widget.ImageView;

import androidx.test.core.app.ApplicationProvider;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;

/** Tests that {@link FullScreenPicActivity} reads the filename extra and inflates without crashing. */
@RunWith(RobolectricTestRunner.class)
public class FullScreenPicActivityTest {

    @Test
    public void onCreate_missingFile_setsNullBackground() {
        Intent intent = new Intent(ApplicationProvider.getApplicationContext(), FullScreenPicActivity.class);
        intent.putExtra(SelfieMainActivity.FILENAME_EXTRA, "/data/local/tmp/missing_selfie.jpg");

        FullScreenPicActivity activity =
                Robolectric.buildActivity(FullScreenPicActivity.class, intent).setup().get();

        assertNotNull(activity);
        ImageView imageView = activity.findViewById(R.id.fullscreenimageview);
        assertNotNull(imageView);
        // createFromPath returns null for a missing file, so setBackground(null) must be called.
        assertNull("background must be null when the image file does not exist", imageView.getBackground());
    }

    @Test
    public void onCreate_withNoFilenameExtra_setsNullBackground() {
        // No FILENAME_EXTRA → getStringExtra returns null → createFromPath(null) returns null →
        // setBackground(null) clears the background. The activity must not crash.
        Intent intent = new Intent(ApplicationProvider.getApplicationContext(), FullScreenPicActivity.class);

        FullScreenPicActivity activity =
                Robolectric.buildActivity(FullScreenPicActivity.class, intent).setup().get();

        assertNotNull(activity);
        ImageView imageView = activity.findViewById(R.id.fullscreenimageview);
        assertNotNull(imageView);
        assertNull("background must be null when no filename extra is supplied", imageView.getBackground());
    }
}
