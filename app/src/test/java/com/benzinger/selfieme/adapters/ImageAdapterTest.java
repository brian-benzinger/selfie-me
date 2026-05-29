package com.benzinger.selfieme.adapters;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.net.Uri;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;

import androidx.test.core.app.ApplicationProvider;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.util.ArrayList;
import java.util.List;

/** Unit tests for {@link ImageAdapter}, including its shared-list-by-reference contract. */
@RunWith(RobolectricTestRunner.class)
public class ImageAdapterTest {

    private Context context;
    private List<Uri> pics;
    private ImageAdapter adapter;
    private FrameLayout parent;

    @Before
    public void setUp() {
        context = ApplicationProvider.getApplicationContext();
        pics = new ArrayList<>();
        pics.add(Uri.parse("file:///selfies/a.jpg"));
        adapter = new ImageAdapter(context, pics);
        parent = new FrameLayout(context);
    }

    @Test
    public void getCount_matchesListSize() {
        assertEquals(1, adapter.getCount());
    }

    @Test
    public void getItem_returnsBackingUri() {
        assertEquals(pics.get(0), adapter.getItem(0));
    }

    @Test
    public void getItemId_isZero() {
        assertEquals(0L, adapter.getItemId(0));
    }

    @Test
    public void getView_buildsCenterCroppedImageView() {
        View view = adapter.getView(0, null, parent);

        assertTrue(view instanceof ImageView);
        ImageView imageView = (ImageView) view;
        assertEquals(ImageView.ScaleType.CENTER_CROP, imageView.getScaleType());
        assertEquals(100, imageView.getLayoutParams().width);
        assertEquals(100, imageView.getLayoutParams().height);
    }

    @Test
    public void getView_recyclesConvertView() {
        ImageView recycled = new ImageView(context);
        View view = adapter.getView(0, recycled, parent);
        assertSame(recycled, view);
    }

    @Test
    public void adapter_reflectsMutationsToSharedList() {
        // The activity mutates the same list instance the adapter holds by reference.
        pics.add(Uri.parse("file:///selfies/b.jpg"));
        assertEquals(2, adapter.getCount());
        assertEquals(pics.get(1), adapter.getItem(1));
    }
}
