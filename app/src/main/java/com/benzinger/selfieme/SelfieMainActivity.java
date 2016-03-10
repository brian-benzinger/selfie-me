package com.benzinger.selfieme;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.view.ContextMenu;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.GridView;
import android.widget.Toast;

import com.benzinger.selfieme.adapters.ImageAdapter;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * Main activity setting up the gridview which displays all selfies taken
 * and has option to take new selfie and delete old selfie
 */
public class SelfieMainActivity extends AppCompatActivity {

    public static final String FILENAME_EXTRA = "filename";
    public static final String ACTION = "com.benzinger.selfieme.receivers.NOTIFICATION_ALARM";
    private static final int REQUEST_IMAGE_CAPTURE = 1;
    private static final int TWO_MINS = 60*1000*2;
    private List<Uri> picturePaths;
    private Uri currentFileUri;
    private ImageAdapter imageAdapter;
    private int selectedPosition;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_selfie_main);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        loadPics();
        GridView gridview = (GridView) findViewById(R.id.gridview);
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
        switch(id){
            case R.id.delete_pic:
                Uri uri = (Uri)imageAdapter.getItem(selectedPosition);
                deleteImageFile(uri);
                picturePaths.remove(selectedPosition);
                imageAdapter.notifyDataSetChanged();
                return true;
            default:
                return super.onContextItemSelected(item);
        }
    }

    /**
     * Initially load all pics in the private storage directory into the list
     */
    private void loadPics(){
        if(picturePaths == null){
            picturePaths = new ArrayList<>();
        }
        File storageDir = getExternalFilesDir(null);
        if(storageDir != null){
            for (File child : storageDir.listFiles()){
                Uri uri =Uri.fromFile(child);
                if(!picturePaths.contains(uri)){
                    picturePaths.add(uri);
                }
            }
        }else{
            Toast.makeText(SelfieMainActivity.this, "Why is ExternalFileDir null?", Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * Create preemptive save file for the picture
     * Send out the camera intent
     */
    private void openCamera() {
        Intent takePictureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        // Ensure that there's a camera activity to handle the intent
        if (takePictureIntent.resolveActivity(getPackageManager()) != null) {
            File currentFile = null;
            try {
                currentFile = createImageFile();
            } catch (IOException ex) {
                Toast.makeText(SelfieMainActivity.this, "Problem creating file in external storage: "+ex.getMessage(),
                        Toast.LENGTH_SHORT).show();
            }
            // Continue only if the file was successfully created
            if (currentFile != null) {
                currentFileUri = Uri.fromFile(currentFile);
                takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, Uri.fromFile(currentFile));
                startActivityForResult(takePictureIntent, REQUEST_IMAGE_CAPTURE);
            }
        }
    }

    /**
     * Create the image file which is needed for camera to write into
     * @return the File object
     */
    private File createImageFile() throws IOException {
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
        String imageFileName = "selfie_" + timeStamp + "_";
        File storageDir = getExternalFilesDir(null);
        return File.createTempFile(imageFileName, ".jpg", storageDir);
    }

    /**
     *  Callback function once camera has been closed
     *  (either successful shot, or cancelled)
     */
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQUEST_IMAGE_CAPTURE && resultCode == RESULT_OK) {
            Toast.makeText(SelfieMainActivity.this, "You've been Selfied!", Toast.LENGTH_SHORT).show();
            if(currentFileUri != null && !picturePaths.contains(currentFileUri)){
                picturePaths.add(currentFileUri);
                imageAdapter.notifyDataSetChanged();
            }
        }else{
            Toast.makeText(SelfieMainActivity.this, "Cancelled the shot", Toast.LENGTH_SHORT).show();
            deleteImageFile(currentFileUri);
        }
    }

    /**
     * Utility function to delete a file at URI
     * @param uri the location of file
     * @return true if deleted, false if not
     */
    private boolean deleteImageFile(Uri uri){
        String scheme = uri.getScheme(); //delete the preemptive file created
        String fileName;
        if (scheme.equals("file")) {
            fileName = uri.getLastPathSegment();
            File file = new File(getExternalFilesDir(null), fileName);
            if (file.exists()) {
                return file.delete();
            }
        }
        return false;
    }

    /**
     * Create the alarm
     * scheduled every two min
     */
    private void createAlarm (){
        AlarmManager alarmManager = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
        Intent intent = new Intent(ACTION);
        PendingIntent alarmIntent = PendingIntent.getBroadcast(this, 0, intent, 0);
        alarmManager.setRepeating(AlarmManager.ELAPSED_REALTIME_WAKEUP, System.currentTimeMillis()+TWO_MINS, TWO_MINS, alarmIntent);
    }
}
