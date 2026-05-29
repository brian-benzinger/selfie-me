package com.benzinger.selfieme;

import static org.junit.Assert.assertNotNull;

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
    public void onCreate_readsFilenameExtraAndInflatesImageView() {
        Intent intent = new Intent(ApplicationProvider.getApplicationContext(), FullScreenPicActivity.class);
        // A path that does not resolve to a real image: createFromPath returns null and
        // setBackground(null) is a no-op, so the activity must still come up cleanly.
        intent.putExtra(SelfieMainActivity.FILENAME_EXTRA, "/data/local/tmp/missing_selfie.jpg");

        FullScreenPicActivity activity =
                Robolectric.buildActivity(FullScreenPicActivity.class, intent).setup().get();

        assertNotNull(activity);
        ImageView imageView = activity.findViewById(R.id.fullscreenimageview);
        assertNotNull(imageView);
    }
}
