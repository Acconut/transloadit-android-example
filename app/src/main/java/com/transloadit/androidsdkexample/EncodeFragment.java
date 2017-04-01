package com.transloadit.androidsdkexample;

import android.content.Context;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;

import com.transloadit.sdk.Assembly;
import com.transloadit.sdk.Transloadit;
import com.transloadit.sdk.exceptions.LocalOperationException;
import com.transloadit.sdk.exceptions.RequestException;
import com.transloadit.sdk.response.AssemblyResponse;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.Date;

import javax.net.ssl.HttpsURLConnection;

public class EncodeFragment extends Fragment {
    public static final String TAG = "EncodeFragment";
    private static final String INPUT_PATH = "input_path";
    private static final String OUTPUT_PATH = "output_path";

    private String mInputPath;
    private String mOutputPath;

    private EncodeTask mTask;
    private EncodeListener mListener;

    public EncodeFragment() {
        // Required empty public constructor
    }

    /**
     * Use this factory method to create a new instance of
     * this fragment using the provided parameters.
     *
     * @param inputPath Parameter 1.
     * @return A new instance of fragment EncodeFragment.
     */
    public static EncodeFragment getInstance(FragmentManager fragmentManager, String inputPath, String outputPath) {
        EncodeFragment fragment = (EncodeFragment) fragmentManager.findFragmentByTag(TAG);
        if (fragment == null) {
            fragment = new EncodeFragment();
            Bundle args = new Bundle();
            args.putString(INPUT_PATH, inputPath);
            args.putString(OUTPUT_PATH, outputPath);
            fragment.setArguments(args);
            fragmentManager.beginTransaction().add(fragment, TAG).commit();
        }

        return fragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            mInputPath = getArguments().getString(INPUT_PATH);
            mOutputPath = getArguments().getString(OUTPUT_PATH);
        }

        // Retain this Fragment across configuration changes in the host Activity.
        setRetainInstance(true);
    }

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        if (context instanceof EncodeListener) {
            mListener = (EncodeListener) context;
        } else {
            throw new RuntimeException(context.toString()
                    + " must implement EncodeListener");
        }
    }

    @Override
    public void onDetach() {
        super.onDetach();
        mListener = null;
    }


    @Override
    public void onDestroy() {
        // Cancel task when Fragment is destroyed.
        cancelEncoding();
        super.onDestroy();
    }

    /**
     * Start non-blocking execution of EncodeTask.
     */
    public void startEncoding() {
        if(mTask != null) return;

        try {
            String outputPath = createFile();

            mTask = new EncodeTask(mListener, outputPath);
            mTask.execute(mInputPath);
        } catch(Exception e) {
            mListener.onEncodingFailed(e);
        }
    }

    private String createFile() throws IOException {
        // Create an image file name
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
        String imageFileName = "JPEG_" + timeStamp + "_";
        File storageDir = this.getActivity().getExternalFilesDir(Environment.DIRECTORY_PICTURES);
        File image = File.createTempFile(
                imageFileName,  /* prefix */
                ".jpg",         /* suffix */
                storageDir      /* directory */
        );

        // Save a file: path for use with ACTION_VIEW intents
        return image.getAbsolutePath();
    }

    /**
     * Cancel (and interrupt if necessary) any ongoing EncodeTask execution.
     */
    public void cancelEncoding() {
        if (mTask != null) {
            mTask.cancel(true);
        }
    }

    public interface EncodeListener {
        void onEncodingComplete(String resultPath);
        void onEncodingFailed(Exception ex);
    }

    private class EncodeTask extends AsyncTask<String, Void, EncodeTask.Result> {

        private Transloadit mTransloadit;
        private EncodeListener mCallback;
        private String mOutputPath;

        EncodeTask(EncodeListener callback, String outputPath) {
            mTransloadit = new Transloadit(
                    getString(R.string.transloadit_auth_key),
                    null
            );
            mCallback = callback;
            mOutputPath = outputPath;

            try {
                mTransloadit.setRequestSigning(false);
            } catch(LocalOperationException e) {}
        }

        /**
         * Wrapper class that serves as a union of a result value and an exception. When the download
         * task has completed, either the result value or exception can be a non-null value.
         * This allows you to pass exceptions to the UI thread that were thrown during doInBackground().
         */
        class Result {
            String mResultValue;
            Exception mException;
            Result(String resultValue) {
                mResultValue = resultValue;
            }
            Result(Exception exception) {
                mException = exception;
            }
        }

        /**
         * Defines work to perform on the background thread.
         */
        @Override
        protected EncodeTask.Result doInBackground(String... paths) {
            Result result = null;
            if (!isCancelled() && paths != null && paths.length > 0) {
                String pathString = paths[0];
                try {
                    AssemblyResponse response = startAssembly(pathString);
                    waitForAssembly(response);
                    String resultPath = downloadResult(response);

                    result = new Result(resultPath);
                } catch(Exception e) {
                    result = new Result(e);
                }
            }
            return result;
        }

        private AssemblyResponse startAssembly(String pathString) throws LocalOperationException, RequestException {
            Assembly assembly = mTransloadit.newAssembly();
            assembly.addOption("template_id", getString(R.string.transloadit_template_id));
            assembly.addFile(new File(pathString));

            return assembly.save(false);
        }

        private AssemblyResponse waitForAssembly(AssemblyResponse response) throws RequestException, LocalOperationException {
            while(!response.isFinished()) {
                response = mTransloadit.getAssemblyByUrl(response.getUrl());
            }
            return response;
        }

        private String downloadResult(AssemblyResponse response) throws JSONException, IOException {
            JSONObject encodeResult = response.json().getJSONObject("results").getJSONArray("encode").getJSONObject(0);
            URL resultUrl = new URL(encodeResult.getString("url"));
            HttpURLConnection connection = (HttpURLConnection) resultUrl.openConnection();

            // Timeout for connection.connect() arbitrarily set to 3000ms.
            connection.setConnectTimeout(3000);
            // For this use case, set HTTP method to GET.
            connection.setRequestMethod("GET");
            // Already true by default but setting just in case; needs to be true since this request
            // is carrying an input (response) body.
            connection.setDoInput(true);
            // Open communications link (network traffic occurs here).
            connection.connect();
            int responseCode = connection.getResponseCode();
            if (responseCode != HttpsURLConnection.HTTP_OK) {
                throw new IOException("HTTP error code: " + responseCode);
            }
            // Retrieve the response body as an InputStream.
            InputStream inputStream = connection.getInputStream();
            if (inputStream != null) {
                // Converts Stream to String with max length of 500.
                FileOutputStream outputStream = new FileOutputStream(mOutputPath);
                byte[] buffer = new byte[50 * 1000];
                int bytesRead = 0;

                while(true) {
                    bytesRead = inputStream.read(buffer);
                    if (!(bytesRead > 0)) break;
                    outputStream.write(buffer, 0, bytesRead);
                }

                outputStream.close();
                inputStream.close();
            }

            connection.disconnect();

            return mOutputPath;
        }

        /**
         * Updates the DownloadCallback with the result.
         */
        @Override
        protected void onPostExecute(Result result) {
            if (result != null && mCallback != null) {
                if (result.mException != null) {
                    mCallback.onEncodingFailed(result.mException);
                } else if (result.mResultValue != null) {
                    mCallback.onEncodingComplete(result.mResultValue);
                }
            }
        }
    }
}
