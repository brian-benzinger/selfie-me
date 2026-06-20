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

import java.io.File;
import java.io.IOException;

/** Tests that {@link FullScreenPicActivity} reads the filename extra and inflates without crashing. */
@RunWith(RobolectricTestRunner.class)
public class FullScreenPicActivityTest {

    // A real 1px PNG so Drawable.createFromPath can return a non-null BitmapDrawable via Robolectric's
    // BitmapFactory shadow, which decodes existing files but returns null for missing ones.
    private static final byte[] ONE_PX_PNG = java.util.Base64.getDecoder().decode(
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNkYPhfDwAChwGA60e6kgAAAABJRU5ErkJggg==");

    @Test
    public void onCreate_existingImageFile_setsNonNullBackground() throws IOException {
        File dir = ApplicationProvider.getApplicationContext().getExternalFilesDir(null);
        File imageFile = new File(dir, "selfie_fullscreen_test.jpg");
        java.nio.file.Files.write(imageFile.toPath(), ONE_PX_PNG);

        Intent intent = new Intent(ApplicationProvider.getApplicationContext(), FullScreenPicActivity.class);
        intent.putExtra(SelfieMainActivity.FILENAME_EXTRA, imageFile.getAbsolutePath());

        FullScreenPicActivity activity =
                Robolectric.buildActivity(FullScreenPicActivity.class, intent).setup().get();

        ImageView imageView = activity.findViewById(R.id.fullscreenimageview);
        assertNotNull("background must be non-null when a real image file is provided",
                imageView.getBackground());
    }

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
