package com.benzinger.selfieme;

import android.Manifest;
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore;
import android.view.ContextMenu;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.GridView;
import android.widget.Toast;

import androidx.activity.result.ActivityResult;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.content.FileProvider;

import com.benzinger.selfieme.adapters.ImageAdapter;
import com.benzinger.selfieme.receivers.AlarmReceiver;
import com.benzinger.selfieme.storage.SelfieStorage;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * Main activity setting up the gridview which displays all selfies taken
 * and has option to take new selfie and delete old selfie
 */
public class SelfieMainActivity extends AppCompatActivity {

    public static final String FILENAME_EXTRA = "filename";
    private static final int REQUEST_IMAGE_CAPTURE = 1;
    private static final int REQUEST_POST_NOTIFICATIONS = 2;
    private static final int TWO_MINS = 60 * 1000 * 2;
    final List<Uri> picturePaths = new ArrayList<>(); // package-private for tests
    File currentFile; // package-private for tests
    private ImageAdapter imageAdapter;
    private int selectedPosition;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_selfie_main);
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        loadPics();
        GridView gridview = findViewById(R.id.gridview);
        imageAdapter = new ImageAdapter(this, picturePaths);
        gridview.setAdapter(imageAdapter);

        gridview.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View v, int position, long id) {
                Intent intent = new Intent(getApplicationContext(), FullScreenPicActivity.class);
                intent.putExtra(FILENAME_EXTRA, picturePaths.get(position).getPath());
                SelfieMainActivity.this.startActivity(intent);
            }
        });

        registerForContextMenu(gridview); //add delete context menu
        requestNotificationPermission(); // API 33+ requires runtime permission to post notifications
        createAlarm(); // create a 2 min alarm
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_selfie_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.action_camera) {
            openCamera();
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onCreateContextMenu(ContextMenu menu, View v, ContextMenu.ContextMenuInfo menuInfo) {
        super.onCreateContextMenu(menu, v, menuInfo);
        getMenuInflater().inflate(R.menu.context_menu, menu);
        selectedPosition = ((AdapterView.AdapterContextMenuInfo) menuInfo).position;
    }

    @Override
    public boolean onContextItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.delete_pic) {
            Uri uri = (Uri) imageAdapter.getItem(selectedPosition);
            deleteImageFile(uri);
            picturePaths.remove(selectedPosition);
            imageAdapter.notifyDataSetChanged();
            return true;
        }
        return super.onContextItemSelected(item);
    }

    /**
     * Initially load all pics in the private storage directory into the list
     */
    private void loadPics() {
        // SelfieStorage tolerates a null directory (returns the list unchanged).
        SelfieStorage.loadSelfies(getExternalFilesDir(null), picturePaths);
    }

    /**
     * Create preemptive save file for the picture
     * Send out the camera intent
     */
    private void openCamera() {
        Intent takePictureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        // Ensure that there's a camera activity to handle the intent
        if (takePictureIntent.resolveActivity(getPackageManager()) != null) {
            currentFile = createImageFile(getExternalFilesDir(null));
            // Continue only if the file was successfully created
            if (currentFile != null) {
                // Modern Android forbids handing a raw file:// Uri to another app; share via FileProvider.
                Uri contentUri = FileProvider.getUriForFile(this, getPackageName() + ".fileprovider", currentFile);
                takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, contentUri);
                takePictureIntent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
                cameraLauncher.launch(takePictureIntent);
            }
        }
    }

    /**
     * Create the pre-emptive image file the camera writes into, or {@code null} if it could not be
     * created. Package-private and {@code storageDir}-parameterised so the failure path is unit-testable.
     */
    File createImageFile(File storageDir) {
        try {
            String imageFileName = SelfieStorage.buildImageFileName(new Date());
            return File.createTempFile(imageFileName, SelfieStorage.FILE_SUFFIX, storageDir);
        } catch (IOException ex) {
            Toast.makeText(this, "Problem creating file in external storage: " + ex.getMessage(),
                    Toast.LENGTH_SHORT).show();
            return null;
        }
    }

    /**
     *  Callback function once camera has been closed
     *  (either successful shot, or cancelled)
     */
    /**
     * Result callback for the camera capture launcher (replaces onActivityResult).
     * Package-private so unit tests can invoke it directly.
     */
    void onCaptureResult(ActivityResult result) {
        if (result.getResultCode() == RESULT_OK) {
            Toast.makeText(this, "You've been Selfied!", Toast.LENGTH_SHORT).show();
            if (currentFile != null) {
                Uri uri = Uri.fromFile(currentFile);
                if (!picturePaths.contains(uri)) {
                    picturePaths.add(uri);
                    imageAdapter.notifyDataSetChanged();
                }
            }
        } else {
            Toast.makeText(this, "Cancelled the shot", Toast.LENGTH_SHORT).show();
            if (currentFile != null && currentFile.exists()) {
                currentFile.delete();
            }
        }
    }

    /**
     * Utility function to delete a file at URI
     * @param uri the location of file
     * @return true if deleted, false if not
     */
    private boolean deleteImageFile(Uri uri) {
        return SelfieStorage.deleteSelfie(getExternalFilesDir(null), uri);
    }

    /**
     * Request the POST_NOTIFICATIONS runtime permission, required on Android 13 (API 33) and above
     * for the selfie reminder notification to be shown.
     */
    private void requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, REQUEST_POST_NOTIFICATIONS);
        }
    }

    /**
     * Create the alarm
     * scheduled every two min
     */
    private void createAlarm() {
        AlarmManager alarmManager = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
        // Explicit intent to our own receiver — implicit broadcasts to manifest receivers are
        // restricted on modern Android, and FLAG_IMMUTABLE is required from API 31+.
        Intent intent = new Intent(this, AlarmReceiver.class);
        PendingIntent alarmIntent = PendingIntent.getBroadcast(this, 0, intent, PendingIntent.FLAG_IMMUTABLE);
        alarmManager.setRepeating(AlarmManager.ELAPSED_REALTIME_WAKEUP,
                System.currentTimeMillis() + TWO_MINS, TWO_MINS, alarmIntent);
    }
}
