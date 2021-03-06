package com.transloadit.androidsdkexample;

import android.content.Intent;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.support.v4.content.FileProvider;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.widget.Button;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import droidninja.filepicker.FilePickerBuilder;
import droidninja.filepicker.FilePickerConst;

public class CaptureActivity extends AppCompatActivity implements View.OnClickListener {

    static final int REQUEST_IMAGE_CAPTURE = 1;
    static final int REQUEST_SELECT_FILE = 2;

    private String mCurrentPhotoPath;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_capture);

        Button buttonPhoto = (Button) findViewById(R.id.button_take_photo);
        Button buttonFile = (Button) findViewById(R.id.button_select_file);
        Typeface font = Typeface.createFromAsset(getAssets(), "OpenSans-Semibold.ttf");
        buttonPhoto.setTypeface(font);
        buttonFile.setTypeface(font);

        buttonFile.setOnClickListener(this);
        buttonPhoto.setOnClickListener(this);
    }

    private void dispatchTakePictureIntent() {
        Intent takePictureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        // Ensure that there's a camera activity to handle the intent
        if (takePictureIntent.resolveActivity(getPackageManager()) != null) {
            // Create the File where the photo should go
            File photoFile = null;
            try {
                photoFile = createImageFile();
            } catch (IOException ex) {
                // Error occurred while creating the File
                ex.printStackTrace();
                return;
            }
            // Continue only if the File was successfully created
            if (photoFile != null) {
                // We can only define one FileProvider in all the manifests and since droidninja's
                // filepicker already contains one, we cannot define our own and must therefore
                // use its fileprovider.
                // See https://github.com/DroidNinja/Android-FilePicker/blob/master/filepicker/src/main/AndroidManifest.xml#L19
                Uri photoURI = FileProvider.getUriForFile(this,
                        "com.transloadit.androidsdkexample.droidninja.filepicker.provider",
                        photoFile);
                takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, photoURI);
                startActivityForResult(takePictureIntent, REQUEST_IMAGE_CAPTURE);
            }
        }

    }

    private void dispatchSelectFileIntent() {
        FilePickerBuilder.getInstance().setMaxCount(1)
                .setSelectedFiles(new ArrayList<String>())
                .setActivityTheme(R.style.AppTheme)
                .pickPhoto(this);
    }

    private File createImageFile() throws IOException {
        // Create an image file name
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
        String imageFileName = "JPEG_" + timeStamp + "_";
        File storageDir = getExternalFilesDir(Environment.DIRECTORY_PICTURES);
        File image = File.createTempFile(
                imageFileName,  /* prefix */
                ".jpg",         /* suffix */
                storageDir      /* directory */
        );

        // Save a file: path for use with ACTION_VIEW intents
        mCurrentPhotoPath = image.getAbsolutePath();
        return image;
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        String currentPhotoPath = "";
        if(requestCode == REQUEST_IMAGE_CAPTURE && resultCode == RESULT_OK) {
            currentPhotoPath = mCurrentPhotoPath;
        } else if(requestCode == FilePickerConst.REQUEST_CODE_PHOTO && resultCode == RESULT_OK) {
            List<String> paths = data.getStringArrayListExtra(FilePickerConst.KEY_SELECTED_MEDIA);
            currentPhotoPath = paths.get(0);
        } else {
            return;
        }

        Intent intent = new Intent(this, UploadActivity.class);
        intent.putExtra(UploadActivity.CURRENT_PHOTO_PATH, currentPhotoPath);
        this.startActivity(intent);
    }

    @Override
    public void onClick(View v) {
        switch(v.getId()) {
            case R.id.button_take_photo:
                dispatchTakePictureIntent();
                break;
            case R.id.button_select_file:
                dispatchSelectFileIntent();
                break;
        }
    }
}
