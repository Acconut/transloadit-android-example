package com.transloadit.androidsdkexample;

import android.app.ProgressDialog;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.widget.ImageView;

public class UploadActivity extends AppCompatActivity implements EncodeFragment.EncodeListener {

    static final String CURRENT_PHOTO_PATH = "current_photo_path";

    private ImageView mImageView;
    private String mCurrentPhotoPath;
    private EncodeFragment mEncodeFragment;
    private ProgressDialog mProgressDialog;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_upload);

        mImageView = (ImageView) findViewById(R.id.imageView);

        Intent intent = getIntent();
        mCurrentPhotoPath = intent.getStringExtra(CURRENT_PHOTO_PATH);

        mEncodeFragment = EncodeFragment.getInstance(getSupportFragmentManager(), mCurrentPhotoPath, null);
    }

    @Override
    protected void onStart() {
        super.onStart();
        showImage();
        uploadImage();
        mProgressDialog = ProgressDialog.show(this, "Encoding...", "Please wait");
    }

    private void showImage() {
        Bitmap imageBitmap = BitmapFactory.decodeFile(mCurrentPhotoPath);
        mImageView.setImageBitmap(imageBitmap);
    }

    private void uploadImage() {
        mEncodeFragment.startEncoding();
    }

    @Override
    public void onEncodingComplete(String resultPath) {
        System.out.println(resultPath);
        mCurrentPhotoPath = resultPath;
        mProgressDialog.dismiss();
        showImage();
    }

    @Override
    public void onEncodingFailed(Exception ex) {
        mProgressDialog.dismiss();
        ex.printStackTrace();
    }
}
