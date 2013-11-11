/*==============================================================================
            Copyright (c) 2010-2013 QUALCOMM Austria Research Center GmbH.
            All Rights Reserved.
            Qualcomm Confidential and Proprietary

@file
    ImageTargets.java

@brief
    Sample for ImageTargets

==============================================================================*/


package com.fuihan.npm.contest2013;

import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.util.Vector;

import android.R.string;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnCancelListener;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.DisplayMetrics;
import android.view.GestureDetector;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.view.ViewGroup.LayoutParams;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import android.app.AlertDialog;

import com.fuihan.npm.contest2013.R;
import com.fuihan.npm.contest2013.InfoView;
import com.qualcomm.QCAR.QCAR;
import android.media.MediaPlayer;

/** The main activity for the ImageTargets sample. */
public class ImageTargets extends Activity
{
    // Focus mode constants:
    private static final int FOCUS_MODE_NORMAL = 0;
    private static final int FOCUS_MODE_CONTINUOUS_AUTO = 1;

    // Application status constants:
    private static final int APPSTATUS_UNINITED         = -1;
    private static final int APPSTATUS_INIT_APP         = 0;
    private static final int APPSTATUS_INIT_QCAR        = 1;
    private static final int APPSTATUS_INIT_TRACKER     = 2;
    private static final int APPSTATUS_INIT_APP_AR      = 3;
    private static final int APPSTATUS_LOAD_TRACKER     = 4;
    private static final int APPSTATUS_INITED           = 5;
    private static final int APPSTATUS_CAMERA_STOPPED   = 6;
    private static final int APPSTATUS_CAMERA_RUNNING   = 7;

    // Name of the native dynamic libraries to load:
    private static final String NATIVE_LIB_SAMPLE = "ImageTargets";
    private static final String NATIVE_LIB_QCAR = "QCAR";

    // Constants for Hiding/Showing Loading dialog
    static final int HIDE_LOADING_DIALOG = 0;
    static final int SHOW_LOADING_DIALOG = 1;

    private View mLoadingDialogContainer;

    // Our OpenGL view:
    private QCARSampleGLView mGlView;

    // Our renderer:
    private ImageTargetsRenderer mRenderer;

    // Display size of the device:
    private int mScreenWidth = 0;
    private int mScreenHeight = 0;

    // Constant representing invalid screen orientation to trigger a query:
    private static final int INVALID_SCREEN_ROTATION = -1;

    // Last detected screen rotation:
    private int mLastScreenRotation = INVALID_SCREEN_ROTATION;

    // The current application status:
    private int mAppStatus = APPSTATUS_UNINITED;

    // The async tasks to initialize the QCAR SDK:
    private InitQCARTask mInitQCARTask;
    private LoadTrackerTask mLoadTrackerTask;

    // An object used for synchronizing QCAR initialization, dataset loading and
    // the Android onDestroy() life cycle event. If the application is destroyed
    // while a data set is still being loaded, then we wait for the loading
    // operation to finish before shutting down QCAR:
    private Object mShutdownLock = new Object();

    // QCAR initialization flags:
    private int mQCARFlags = 0;

    // The textures we will use for rendering:
    private Vector<Texture> mTextures;

    // Detects the double tap gesture for launching the Camera menu
    private GestureDetector mGestureDetector;

    // Contextual Menu Options for Camera Flash - Autofocus
    private boolean mFlash = false;
    private boolean mContAutofocus = false;

    // The menu item for swapping data sets:
    MenuItem mDataSetMenuItem = null;
    boolean mIsStonesAndChipsDataSetActive  = false;

    private RelativeLayout mUILayout;
    
    // New View and Button -- Pepper
    private RelativeLayout mUIInfo;
    private ImageButton mBtnShowInfo;
    private LinearLayout infoView;
    private ImageButton mBtnCloseInfo;
    private ImageButton mBtnInfoAudio;
    private TextView mTxtInfoTitle;
    private TextView mTxtInfoDescription;
    private View vg;
    private Boolean AudioPlaying = false;
    private Boolean AudioInfo = false;
    private MediaPlayer mp;
    
    private InfoView InfoDetails;
    private String TargetTitle = null;
    private String TargetDescription = null;
    
    
    /** Static initializer block to load native libraries on start-up. */
    static
    {
        loadLibrary(NATIVE_LIB_QCAR);
        loadLibrary(NATIVE_LIB_SAMPLE);
    }

    /**
     * Creates a handler to update the status of the Loading Dialog from an UI
     * Thread
     */
    static class LoadingDialogHandler extends Handler
    {
        private final WeakReference<ImageTargets> mImageTargets;


        LoadingDialogHandler(ImageTargets imageTargets)
        {
            mImageTargets = new WeakReference<ImageTargets>(
                    imageTargets);
        }


        public void handleMessage(Message msg)
        {
            ImageTargets imageTargets = mImageTargets.get();
            if (imageTargets == null)
            {
                return;
            }

            if (msg.what == SHOW_LOADING_DIALOG)
            {
                imageTargets.mLoadingDialogContainer
                        .setVisibility(View.VISIBLE);

            }
            else if (msg.what == HIDE_LOADING_DIALOG)
            {
                imageTargets.mLoadingDialogContainer.setVisibility(View.GONE);
            }
        }
    }


    private Handler loadingDialogHandler = new LoadingDialogHandler(this);


    /** An async task to initialize QCAR asynchronously. */
    private class InitQCARTask extends AsyncTask<Void, Integer, Boolean>
    {
        // Initialize with invalid value:
        private int mProgressValue = -1;

        protected Boolean doInBackground(Void... params)
        {
            // Prevent the onDestroy() method to overlap with initialization:
            synchronized (mShutdownLock)
            {
                QCAR.setInitParameters(ImageTargets.this, mQCARFlags);

                do
                {
                    // QCAR.init() blocks until an initialization step is
                    // complete, then it proceeds to the next step and reports
                    // progress in percents (0 ... 100%).
                    // If QCAR.init() returns -1, it indicates an error.
                    // Initialization is done when progress has reached 100%.
                    mProgressValue = QCAR.init();

                    // Publish the progress value:
                    publishProgress(mProgressValue);

                    // We check whether the task has been canceled in the
                    // meantime (by calling AsyncTask.cancel(true)).
                    // and bail out if it has, thus stopping this thread.
                    // This is necessary as the AsyncTask will run to completion
                    // regardless of the status of the component that
                    // started is.
                } while (!isCancelled() && mProgressValue >= 0
                         && mProgressValue < 100);

                return (mProgressValue > 0);
            }
        }


        protected void onProgressUpdate(Integer... values)
        {
            // Do something with the progress value "values[0]", e.g. update
            // splash screen, progress bar, etc.
        }


        protected void onPostExecute(Boolean result)
        {
            // Done initializing QCAR, proceed to next application
            // initialization status:
            if (result)
            {
                DebugLog.LOGD("InitQCARTask::onPostExecute: QCAR " +
                              "initialization successful");

                updateApplicationStatus(APPSTATUS_INIT_TRACKER);
            }
            else
            {
                // Create dialog box for display error:
                AlertDialog dialogError = new AlertDialog.Builder
                (
                    ImageTargets.this
                ).create();

                dialogError.setButton
                (
                    DialogInterface.BUTTON_POSITIVE,
                    "Close",
                    new DialogInterface.OnClickListener()
                    {
                        public void onClick(DialogInterface dialog, int which)
                        {
                            // Exiting application:
                            System.exit(1);
                        }
                    }
                );

                String logMessage;

                // NOTE: Check if initialization failed because the device is
                // not supported. At this point the user should be informed
                // with a message.
                if (mProgressValue == QCAR.INIT_DEVICE_NOT_SUPPORTED)
                {
                    logMessage = "Failed to initialize QCAR because this " +
                        "device is not supported.";
                }
                else
                {
                    logMessage = "Failed to initialize QCAR.";
                }

                // Log error:
                DebugLog.LOGE("InitQCARTask::onPostExecute: " + logMessage +
                                " Exiting.");

                // Show dialog box with error message:
                dialogError.setMessage(logMessage);
                dialogError.show();
            }
        }
    }


    /** An async task to load the tracker data asynchronously. */
    private class LoadTrackerTask extends AsyncTask<Void, Integer, Boolean>
    {
        protected Boolean doInBackground(Void... params)
        {
            // Prevent the onDestroy() method to overlap:
            synchronized (mShutdownLock)
            {
                // Load the tracker data set:
                return (loadTrackerData() > 0);
            }
        }

        protected void onPostExecute(Boolean result)
        {
            DebugLog.LOGD("LoadTrackerTask::onPostExecute: execution " +
                        (result ? "successful" : "failed"));

            if (result)
            {
                // The stones and chips data set is now active:
                mIsStonesAndChipsDataSetActive = true;

                // Done loading the tracker, update application status:
                updateApplicationStatus(APPSTATUS_INITED);
            }
            else
            {
                // Create dialog box for display error:
                AlertDialog dialogError = new AlertDialog.Builder
                (
                    ImageTargets.this
                ).create();

                dialogError.setButton
                (
                    DialogInterface.BUTTON_POSITIVE,
                    "Close",
                    new DialogInterface.OnClickListener()
                    {
                        public void onClick(DialogInterface dialog, int which)
                        {
                            // Exiting application:
                            System.exit(1);
                        }
                    }
                );

                // Show dialog box with error message:
                dialogError.setMessage("Failed to load tracker data.");
                dialogError.show();
            }
        }
    }


    /** Stores screen dimensions */
    private void storeScreenDimensions()
    {
        // Query display dimensions:
        DisplayMetrics metrics = new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getMetrics(metrics);
        mScreenWidth = metrics.widthPixels;
        mScreenHeight = metrics.heightPixels;
    }


    /** Called when the activity first starts or the user navigates back
     * to an activity. */
    protected void onCreate(Bundle savedInstanceState)
    {
        DebugLog.LOGD("ImageTargets::onCreate");
        super.onCreate(savedInstanceState);

        // Load any sample specific textures:
        mTextures = new Vector<Texture>();
        loadTextures();

        // Query the QCAR initialization flags:
        mQCARFlags = getInitializationFlags();

        // Creates the GestureDetector listener for processing double tap
        mGestureDetector = new GestureDetector(this, new GestureListener());

        // Update the application status to start initializing application:
        updateApplicationStatus(APPSTATUS_INIT_APP);

    }


    /** We want to load specific textures from the APK, which we will later
    use for rendering. */
    private void loadTextures()
    {
        mTextures.add(Texture.loadTextureFromApk("TextureTeapotBrass.png",
                                                 getAssets()));
        mTextures.add(Texture.loadTextureFromApk("TextureTeapotBlue.png",
                                                 getAssets()));
        mTextures.add(Texture.loadTextureFromApk("TextureTeapotRed.png",
                getAssets()));
    }


    /** Configure QCAR with the desired version of OpenGL ES. */
    private int getInitializationFlags()
    {
        int flags = 0;

        // Query the native code:
        if (getOpenGlEsVersionNative() == 1)
        {
            flags = QCAR.GL_11;
        }
        else
        {
            flags = QCAR.GL_20;
        }

        return flags;
    }


    /** Native method for querying the OpenGL ES version.
     * Returns 1 for OpenGl ES 1.1, returns 2 for OpenGl ES 2.0. */
    public native int getOpenGlEsVersionNative();

    /** Native tracker initialization and deinitialization. */
    public native int initTracker();
    public native void deinitTracker();

    /** Native functions to load and destroy tracking data. */
    public native int loadTrackerData();
    public native void destroyTrackerData();

    /** Native sample initialization. */
    public native void onQCARInitializedNative();

    /** Native methods for starting and stopping the camera. */
    private native void startCamera();
    private native void stopCamera();

    /** Native method for setting / updating the projection matrix
     * for AR content rendering */
    private native void setProjectionMatrix();


   /** Called when the activity will start interacting with the user.*/
    protected void onResume()
    {
        DebugLog.LOGD("ImageTargets::onResume");
        super.onResume();
        //mUIInfo.setVisibility(View.INVISIBLE);
        // Create a new handler for the renderer thread to use
        // This is necessary as only the main thread can make changes to the UI
        ImageTargetsRenderer.mainActivityHandler = new Handler() {
            @Override
            public void handleMessage(Message msg) {
	            Context context = getApplicationContext();
	            
	            String text = ((String) msg.obj).substring(0, 4);
	            int duration = Toast.LENGTH_SHORT;
	            Toast toast = Toast.makeText(context, text, duration);
	            if (!text.equalsIgnoreCase("clea")) {
	            	//toast.show();
	            	mUIInfo.setVisibility(View.VISIBLE);
	            } else {
	            	mUIInfo.setVisibility(View.INVISIBLE);
	            }
	            //toast.show();
	            TargetTitle = "";
	            TargetDescription = "";
	            
	            //setInfoDetails(text, text);
	            // The following opens a pre-defined URL based on the name of trackable detected
	            //if (text.equalsIgnoreCase("stones")) {
	            if (text.equalsIgnoreCase("ston")) {
	                Uri uriUrl = Uri.parse("http://ar.qualcomm.at");
	                Intent launchBrowser = new Intent(Intent.ACTION_VIEW, uriUrl);
	                TargetTitle = getString(R.string.title_wang_words_1);
	            	TargetDescription  = getString(R.string.description_wang_words_1);
	                //startActivity(launchBrowser);
	                //mUIInfo.setVisibility(View.VISIBLE);
	            }
	            //if (text.equalsIgnoreCase("chips")) {
	            if (text.equalsIgnoreCase("chip")) {
	                Uri uriUrl = Uri.parse("http://developer.qualcomm.com");
	                Intent launchBrowser = new Intent(Intent.ACTION_VIEW, uriUrl);
	                TargetTitle = getString(R.string.title_wang_words_2);
	            	TargetDescription  = getString(R.string.description_wang_words_2);
	                //startActivity(launchBrowser);
	                //mUIInfo.setVisibility(View.VISIBLE);
	            }
	            if (text.equalsIgnoreCase("PA01")) {
	                TargetTitle = getString(R.string.title_pa01);
	            	TargetDescription  = getString(R.string.description_pa01);
	            	AudioPlaying = false;
	            	mp = MediaPlayer.create(context, R.raw.pa04);
	            }
	            if (text.equalsIgnoreCase("PA02")) {
	                TargetTitle = getString(R.string.title_pa02);
	            	TargetDescription  = getString(R.string.description_pa02);
	            	AudioPlaying = false;
	            	mp = MediaPlayer.create(context, R.raw.pa04);
	            }
	            if (text.equalsIgnoreCase("PA03")) {
	                TargetTitle = getString(R.string.title_pa03);
	            	TargetDescription  = getString(R.string.description_pa03);
	            	AudioPlaying = false;
	            	mp = MediaPlayer.create(context, R.raw.pa04);
	            }
	            if (text.equalsIgnoreCase("PA04")) {
	                TargetTitle = getString(R.string.title_pa04);
	            	TargetDescription  = getString(R.string.description_pa04);
	            	AudioPlaying = true;
	            	mp = MediaPlayer.create(context, R.raw.pa04);
	            }
	            if (text.equalsIgnoreCase("PA05")) {
	                TargetTitle = getString(R.string.title_pa05);
	            	TargetDescription  = getString(R.string.description_pa05);
	            	AudioPlaying = false;
	            	mp = MediaPlayer.create(context, R.raw.pa04);
	            }
	            if (text.equalsIgnoreCase("PA06")) {
	                TargetTitle = getString(R.string.title_pa06);
	            	TargetDescription  = getString(R.string.description_pa06);
	            	AudioPlaying = false;
	            	mp = MediaPlayer.create(context, R.raw.pa04);
	            }
	            if (text.equalsIgnoreCase("PA07")) {
	                TargetTitle = getString(R.string.title_pa07);
	            	TargetDescription  = getString(R.string.description_pa07);
	            	AudioPlaying = false;
	            	mp = MediaPlayer.create(context, R.raw.pa04);
	            }
	            if (text.equalsIgnoreCase("PA08")) {
	                TargetTitle = getString(R.string.title_pa08);
	            	TargetDescription  = getString(R.string.description_pa08);
	            	AudioPlaying = false;
	            	mp = MediaPlayer.create(context, R.raw.pa04);
	            }
	            if (text.equalsIgnoreCase("PA09")) {
	                TargetTitle = getString(R.string.title_pa09);
	            	TargetDescription  = getString(R.string.description_pa09);
	            	AudioPlaying = false;
	            	mp = MediaPlayer.create(context, R.raw.pa04);
	            }
	            if (text.equalsIgnoreCase("PA10")) {
	                TargetTitle = getString(R.string.title_pa10);
	            	TargetDescription  = getString(R.string.description_pa10);
	            	AudioPlaying = true;
	            	mp = MediaPlayer.create(context, R.raw.pa10);
	            }
	            if (text.equalsIgnoreCase("PA11")) {
	                TargetTitle = getString(R.string.title_pa11);
	            	TargetDescription  = getString(R.string.description_pa11);
	            	AudioPlaying = false;
	            	mp = MediaPlayer.create(context, R.raw.pa04);
	            }
	            if (text.equalsIgnoreCase("PA12")) {
	                TargetTitle = getString(R.string.title_pa12);
	            	TargetDescription  = getString(R.string.description_pa12);
	            	AudioPlaying = false;
	            	mp = MediaPlayer.create(context, R.raw.pa04);
	            }
	            if (text.equalsIgnoreCase("PA13")) {
	                TargetTitle = getString(R.string.title_pa13);
	            	TargetDescription  = getString(R.string.description_pa13);
	            	AudioPlaying = true;
	            	mp = MediaPlayer.create(context, R.raw.pa13);
	            }
	            if (text.equalsIgnoreCase("PA14")) {
	                TargetTitle = getString(R.string.title_pa14);
	            	TargetDescription  = getString(R.string.description_pa14);
	            	AudioPlaying = false;
	            	mp = MediaPlayer.create(context, R.raw.pa04);
	            }
	            if (text.equalsIgnoreCase("PA15")) {
	                TargetTitle = getString(R.string.title_pa15);
	            	TargetDescription  = getString(R.string.description_pa15);
	            	AudioPlaying = false;
	            	mp = MediaPlayer.create(context, R.raw.pa04);
	            }
	            if (text.equalsIgnoreCase("PA16")) {
	                TargetTitle = getString(R.string.title_pa16);
	            	TargetDescription  = getString(R.string.description_pa16);
	            	AudioPlaying = false;
	            	mp = MediaPlayer.create(context, R.raw.pa04);
	            }
	            if (text.equalsIgnoreCase("PA17")) {
	                TargetTitle = getString(R.string.title_pa17);
	            	TargetDescription  = getString(R.string.description_pa17);
	            	AudioPlaying = true;
	            	mp = MediaPlayer.create(context, R.raw.pa17);
	            }
	            if (text.equalsIgnoreCase("PA18")) {
	                TargetTitle = getString(R.string.title_pa18);
	            	TargetDescription  = getString(R.string.description_pa18);
	            	AudioPlaying = false;
	            	mp = MediaPlayer.create(context, R.raw.pa04);
	            }
	            if (text.equalsIgnoreCase("PA19")) {
	                TargetTitle = getString(R.string.title_pa19);
	            	TargetDescription  = getString(R.string.description_pa19);
	            	AudioPlaying = true;
	            	mp = MediaPlayer.create(context, R.raw.pa19);
	            }
	            if (text.equalsIgnoreCase("PA20")) {
	                TargetTitle = getString(R.string.title_pa20);
	            	TargetDescription  = getString(R.string.description_pa20);
	            	AudioPlaying = false;
	            	mp = MediaPlayer.create(context, R.raw.pa04);
	            }
	            if (text.equalsIgnoreCase("PA21")) {
	                TargetTitle = getString(R.string.title_pa21);
	            	TargetDescription  = getString(R.string.description_pa21);
	            	AudioPlaying = false;
	            	mp = MediaPlayer.create(context, R.raw.pa04);
	            }
	            if (text.equalsIgnoreCase("PA22")) {
	                TargetTitle = getString(R.string.title_pa22);
	            	TargetDescription  = getString(R.string.description_pa22);
	            	AudioPlaying = false;
	            	mp = MediaPlayer.create(context, R.raw.pa04);
	            }
	            if (text.equalsIgnoreCase("PA23")) {
	                TargetTitle = getString(R.string.title_pa23);
	            	TargetDescription  = getString(R.string.description_pa23);
	            	AudioPlaying = true;
	            	mp = MediaPlayer.create(context, R.raw.pa23);
	            }
	            if (text.equalsIgnoreCase("PA24")) {
	                TargetTitle = getString(R.string.title_pa24);
	            	TargetDescription  = getString(R.string.description_pa24);
	            	AudioPlaying = false;
	            	mp = MediaPlayer.create(context, R.raw.pa04);
	            }
	            if (text.equalsIgnoreCase("PA25")) {
	                TargetTitle = getString(R.string.title_pa25);
	            	TargetDescription  = getString(R.string.description_pa25);
	            	AudioPlaying = false;
	            	mp = MediaPlayer.create(context, R.raw.pa04);
	            }
	            if (text.equalsIgnoreCase("PA26")) {
	                TargetTitle = getString(R.string.title_pa26);
	            	TargetDescription  = getString(R.string.description_pa26);
	            	AudioPlaying = true;
	            	mp = MediaPlayer.create(context, R.raw.pa26);
	            }
	            if (text.equalsIgnoreCase("PA27")) {
	                TargetTitle = getString(R.string.title_pa27);
	            	TargetDescription  = getString(R.string.description_pa27);
	            	AudioPlaying = false;
	            	mp = MediaPlayer.create(context, R.raw.pa04);
	            }
	            if (text.equalsIgnoreCase("PA28")) {
	                TargetTitle = getString(R.string.title_pa28);
	            	TargetDescription  = getString(R.string.description_pa28);
	            	AudioPlaying = false;
	            	mp = MediaPlayer.create(context, R.raw.pa04);
	            }
	            if (text.equalsIgnoreCase("PA29")) {
	                TargetTitle = getString(R.string.title_pa29);
	            	TargetDescription  = getString(R.string.description_pa29);
	            	AudioPlaying = true;
	            	mp = MediaPlayer.create(context, R.raw.pa29);
	            }
	            if (text.equalsIgnoreCase("PA30")) {
	                TargetTitle = getString(R.string.title_pa30);
	            	TargetDescription  = getString(R.string.description_pa30);
	            	AudioPlaying = false;
	            	mp = MediaPlayer.create(context, R.raw.pa04);
	            }
	            if (text.equalsIgnoreCase("PA31")) {
	                TargetTitle = getString(R.string.title_pa31);
	            	TargetDescription  = getString(R.string.description_pa31);
	            	AudioPlaying = false;
	            	mp = MediaPlayer.create(context, R.raw.pa04);
	            }
	            if (text.equalsIgnoreCase("PA32")) {
	                TargetTitle = getString(R.string.title_pa32);
	            	TargetDescription  = getString(R.string.description_pa32);
	            	AudioPlaying = false;
	            	mp = MediaPlayer.create(context, R.raw.pa04);
	            }
	            if (text.equalsIgnoreCase("PA33")) {
	                TargetTitle = getString(R.string.title_pa33);
	            	TargetDescription  = getString(R.string.description_pa33);
	            	AudioPlaying = false;
	            	mp = MediaPlayer.create(context, R.raw.pa04);
	            }
	            if (text.equalsIgnoreCase("PA34")) {
	                TargetTitle = getString(R.string.title_pa34);
	            	TargetDescription  = getString(R.string.description_pa34);
	            	AudioPlaying = false;
	            	mp = MediaPlayer.create(context, R.raw.pa04);
	            }
	            if (text.equalsIgnoreCase("PA35")) {
	                TargetTitle = getString(R.string.title_pa35);
	            	TargetDescription  = getString(R.string.description_pa35);
	            	AudioPlaying = false;
	            	mp = MediaPlayer.create(context, R.raw.pa04);
	            }
	            if (text.equalsIgnoreCase("PA36")) {
	                TargetTitle = getString(R.string.title_pa36);
	            	TargetDescription  = getString(R.string.description_pa36);
	            	AudioPlaying = false;
	            	mp = MediaPlayer.create(context, R.raw.pa04);
	            }
	            if (text.equalsIgnoreCase("PA37")) {
	                TargetTitle = getString(R.string.title_pa37);
	            	TargetDescription  = getString(R.string.description_pa37);
	            	AudioPlaying = false;
	            	mp = MediaPlayer.create(context, R.raw.pa04);
	            }
	            if (text.equalsIgnoreCase("PA38")) {
	                TargetTitle = getString(R.string.title_pa38);
	            	TargetDescription  = getString(R.string.description_pa38);
	            	AudioPlaying = false;
	            	mp = MediaPlayer.create(context, R.raw.pa04);
	            }
	            if (text.equalsIgnoreCase("PA39")) {
	                TargetTitle = getString(R.string.title_pa39);
	            	TargetDescription  = getString(R.string.description_pa39);
	            	AudioPlaying = false;
	            	mp = MediaPlayer.create(context, R.raw.pa04);
	            }	
	            if (text.equalsIgnoreCase("PA40")) {
	                TargetTitle = getString(R.string.title_pa40);
	            	TargetDescription  = getString(R.string.description_pa40);
	            	AudioPlaying = false;
	            	mp = MediaPlayer.create(context, R.raw.pa04);
	            }
	            if (text.equalsIgnoreCase("PA41")) {
	                TargetTitle = getString(R.string.title_pa41);
	            	TargetDescription  = getString(R.string.description_pa41);
	            	AudioPlaying = false;
	            	mp = MediaPlayer.create(context, R.raw.pa04);
	            }
	            
//	        	MediaPlayer mp = MediaPlayer.create(context, R.raw.a_new_president);
//	        	mp.start();
            }
        };
        
        // QCAR-specific resume operation
        QCAR.onResume();

        // We may start the camera only if the QCAR SDK has already been
        // initialized
        if (mAppStatus == APPSTATUS_CAMERA_STOPPED)
        {
            updateApplicationStatus(APPSTATUS_CAMERA_RUNNING);
        }

        // Resume the GL view:
        if (mGlView != null)
        {
            mGlView.setVisibility(View.VISIBLE);
            mGlView.onResume();
        }
    }


    private void updateActivityOrientation()
    {
        Configuration config = getResources().getConfiguration();

        boolean isPortrait = false;

        switch (config.orientation)
        {
        case Configuration.ORIENTATION_PORTRAIT:
            isPortrait = true;
            break;
        case Configuration.ORIENTATION_LANDSCAPE:
            isPortrait = false;
            break;
        case Configuration.ORIENTATION_UNDEFINED:
        default:
            break;
        }

        DebugLog.LOGI("Activity is in "
                + (isPortrait ? "PORTRAIT" : "LANDSCAPE"));
        setActivityPortraitMode(isPortrait);
    }


    /**
     * Updates projection matrix and viewport after a screen rotation
     * change was detected.
     */
    public void updateRenderView()
    {
        int currentScreenRotation = getWindowManager().getDefaultDisplay().getRotation();
        if (currentScreenRotation != mLastScreenRotation)
        {
            // Set projection matrix if there is already a valid one:
            if (QCAR.isInitialized() && (mAppStatus == APPSTATUS_CAMERA_RUNNING))
            {
                DebugLog.LOGD("ImageTargets::updateRenderView");

                // Query display dimensions:
                storeScreenDimensions();

                // Update viewport via renderer:
                mRenderer.updateRendering(mScreenWidth, mScreenHeight);

                // Update projection matrix:
                setProjectionMatrix();

                // Cache last rotation used for setting projection matrix:
                mLastScreenRotation = currentScreenRotation;
            }
        }
    }


    /** Callback for configuration changes the activity handles itself */
    public void onConfigurationChanged(Configuration config)
    {
        DebugLog.LOGD("ImageTargets::onConfigurationChanged");
        super.onConfigurationChanged(config);

        updateActivityOrientation();

        storeScreenDimensions();

        // Invalidate screen rotation to trigger query upon next render call:
        mLastScreenRotation = INVALID_SCREEN_ROTATION;
    }


    /** Called when the system is about to start resuming a previous activity.*/
    protected void onPause()
    {
        DebugLog.LOGD("ImageTargets::onPause");
        super.onPause();

        if (mGlView != null)
        {
            mGlView.setVisibility(View.INVISIBLE);
            mGlView.onPause();
        }

        if (mAppStatus == APPSTATUS_CAMERA_RUNNING)
        {
            updateApplicationStatus(APPSTATUS_CAMERA_STOPPED);
        }

        // Disable flash when paused
        if (mFlash)
        {
            mFlash = false;
            activateFlash(mFlash);
        }

        // QCAR-specific pause operation
        QCAR.onPause();
    }


    /** Native function to deinitialize the application.*/
    private native void deinitApplicationNative();


    /** The final call you receive before your activity is destroyed.*/
    protected void onDestroy()
    {
        DebugLog.LOGD("ImageTargets::onDestroy");
        super.onDestroy();

        // Cancel potentially running tasks
        if (mInitQCARTask != null &&
            mInitQCARTask.getStatus() != InitQCARTask.Status.FINISHED)
        {
            mInitQCARTask.cancel(true);
            mInitQCARTask = null;
        }

        if (mLoadTrackerTask != null &&
            mLoadTrackerTask.getStatus() != LoadTrackerTask.Status.FINISHED)
        {
            mLoadTrackerTask.cancel(true);
            mLoadTrackerTask = null;
        }

        // Ensure that all asynchronous operations to initialize QCAR
        // and loading the tracker datasets do not overlap:
        synchronized (mShutdownLock) {

            // Do application deinitialization in native code:
            deinitApplicationNative();

            // Unload texture:
            mTextures.clear();
            mTextures = null;

            // Destroy the tracking data set:
            destroyTrackerData();

            // Deinit the tracker:
            deinitTracker();

            // Deinitialize QCAR SDK:
            QCAR.deinit();
        }

        System.gc();
    }


    /** NOTE: this method is synchronized because of a potential concurrent
     * access by ImageTargets::onResume() and InitQCARTask::onPostExecute(). */
    private synchronized void updateApplicationStatus(int appStatus)
    {
        // Exit if there is no change in status:
        if (mAppStatus == appStatus)
            return;

        // Store new status value:
        mAppStatus = appStatus;

        // Execute application state-specific actions:
        switch (mAppStatus)
        {
            case APPSTATUS_INIT_APP:
                // Initialize application elements that do not rely on QCAR
                // initialization:
                initApplication();

                // Proceed to next application initialization status:
                updateApplicationStatus(APPSTATUS_INIT_QCAR);
                break;

            case APPSTATUS_INIT_QCAR:
                // Initialize QCAR SDK asynchronously to avoid blocking the
                // main (UI) thread.
                //
                // NOTE: This task instance must be created and invoked on the
                // UI thread and it can be executed only once!
                try
                {
                    mInitQCARTask = new InitQCARTask();
                    mInitQCARTask.execute();
                }
                catch (Exception e)
                {
                    DebugLog.LOGE("Initializing QCAR SDK failed");
                }
                break;

            case APPSTATUS_INIT_TRACKER:
                // Initialize the ImageTracker:
                if (initTracker() > 0)
                {
                    // Proceed to next application initialization status:
                    updateApplicationStatus(APPSTATUS_INIT_APP_AR);
                }
                break;

            case APPSTATUS_INIT_APP_AR:
                // Initialize Augmented Reality-specific application elements
                // that may rely on the fact that the QCAR SDK has been
                // already initialized:
                initApplicationAR();

                // Proceed to next application initialization status:
                updateApplicationStatus(APPSTATUS_LOAD_TRACKER);
                break;

            case APPSTATUS_LOAD_TRACKER:
                // Load the tracking data set:
                //
                // NOTE: This task instance must be created and invoked on the
                // UI thread and it can be executed only once!
                try
                {
                    mLoadTrackerTask = new LoadTrackerTask();
                    mLoadTrackerTask.execute();
                }
                catch (Exception e)
                {
                    DebugLog.LOGE("Loading tracking data set failed");
                }
                break;

            case APPSTATUS_INITED:
                // Hint to the virtual machine that it would be a good time to
                // run the garbage collector:
                //
                // NOTE: This is only a hint. There is no guarantee that the
                // garbage collector will actually be run.
                System.gc();

                // Native post initialization:
                onQCARInitializedNative();

                // Activate the renderer:
                mRenderer.mIsActive = true;

                // Now add the GL surface view. It is important
                // that the OpenGL ES surface view gets added
                // BEFORE the camera is started and video
                // background is configured.
                addContentView(mGlView, new LayoutParams(LayoutParams.MATCH_PARENT,
                        LayoutParams.MATCH_PARENT));
                
                //Pepper
                mUIInfo = (RelativeLayout) findViewById(R.id.rLayout);
                
                LayoutInflater inflater = LayoutInflater.from(this);
                mUIInfo = (RelativeLayout) inflater.inflate(R.layout.info_screen_trigger,
                        null, false);
                
                addContentView(mUIInfo, new LayoutParams(LayoutParams.MATCH_PARENT,
                        LayoutParams.MATCH_PARENT));
                
                mBtnShowInfo = (ImageButton) findViewById(R.id.btn_show_info);
                mBtnShowInfo.setOnClickListener(new OnClickListener(){  
                    public void onClick(View v) {  

                    	
                    	LayoutInflater inflater = (LayoutInflater) getApplicationContext().getSystemService(Context.LAYOUT_INFLATER_SERVICE);

                    	infoView = (LinearLayout) inflater.inflate(R.layout.info_details, (ViewGroup) getCurrentFocus());
                    	mTxtInfoTitle = (TextView) infoView.findViewById(R.id.info_details_title);
                    	mTxtInfoTitle.setText(TargetTitle);
                    	mTxtInfoDescription = (TextView) infoView.findViewById(R.id.info_details_description);
                    	mTxtInfoDescription.setText(TargetDescription);
                    	mBtnInfoAudio = (ImageButton) infoView.findViewById(R.id.btn_info_audio); 
                    	mBtnCloseInfo = (ImageButton) infoView.findViewById(R.id.btn_info_close);
                    	//InfoDetails = new InfoView() ;
//                    	mp.start();
                    	
                    	
                    	
                    	AlertDialog.Builder builder = new AlertDialog.Builder(ImageTargets.this);
 
                    	final Dialog d = builder.setView(infoView).create();
                    	d.getWindow().setLayout(LayoutParams.FILL_PARENT, LayoutParams.FILL_PARENT);
                    	
                    	if (!AudioPlaying){
                    		mBtnInfoAudio.setVisibility(v.GONE);
                    	} else {
                    		mBtnInfoAudio.setVisibility(v.VISIBLE);
                    	}
                    	
                    	mBtnCloseInfo.setOnClickListener(new OnClickListener()
                    	{
                    		@Override
                            public void onClick(View v)
                            {
                                d.dismiss();
                                if (mp.isPlaying()){
                                	mp.stop();
                                }
                                mp.reset();
                            }
                    	  
                    	});
                    	
                    	mBtnInfoAudio.setOnClickListener(new OnClickListener()
                    	{
                    		@Override
                            public void onClick(View v)
                            {
                    			if (AudioPlaying){
                    				if (mp.isPlaying()){
                                    	mp.pause();
                                    	mp.seekTo(0);
                                    }
                    				mBtnInfoAudio.setImageResource(R.drawable.ic_speaker_on);
                    			} else {
                    				if (mp.isPlaying()){
                    					mp.pause();
                    				}
                                    mp.start(); 
                                    mBtnInfoAudio.setImageResource(R.drawable.ic_speaker_off_2);
                    			}
                    			AudioPlaying = !AudioPlaying;
                            }
                    	  
                    	}); 
                    	
                    	d.setOnCancelListener(new OnCancelListener() {

                    	    public void onCancel(DialogInterface dialog) {
                    	    	if (mp.isPlaying()){
//                					mp.pause();
//                					mp.seekTo(0);
                					mp.stop();
                				}
                    	    	mp.reset();
                    	    }
                    	});
                    	
                    	d.show();                    	
                    }
                });
                
                mUIInfo.setVisibility(View.INVISIBLE);

                // Sets the UILayout to be drawn in front of the camera
                mUILayout.bringToFront();

                // Start the camera:
                updateApplicationStatus(APPSTATUS_CAMERA_RUNNING);

                break;

            case APPSTATUS_CAMERA_STOPPED:
                // Call the native function to stop the camera:
                stopCamera();
                break;

            case APPSTATUS_CAMERA_RUNNING:
                // Call the native function to start the camera:
                startCamera();

                // Hides the Loading Dialog
                loadingDialogHandler.sendEmptyMessage(HIDE_LOADING_DIALOG);

                // Sets the layout background to transparent
                mUILayout.setBackgroundColor(Color.TRANSPARENT);

                // Set continuous auto-focus if supported by the device,
                // otherwise default back to regular auto-focus mode.
                // This will be activated by a tap to the screen in this
                // application.
                if (!setFocusMode(FOCUS_MODE_CONTINUOUS_AUTO))
                {
                    mContAutofocus = false;
                    setFocusMode(FOCUS_MODE_NORMAL);
                }
                else
                {
                    mContAutofocus = true;
                }
                break;

            default:
                throw new RuntimeException("Invalid application state");
        }
    }


    /** Tells native code whether we are in portait or landscape mode */
    private native void setActivityPortraitMode(boolean isPortrait);


    /** Initialize application GUI elements that are not related to AR. */
    private void initApplication()
    {
        // Set the screen orientation:
        // NOTE: Use SCREEN_ORIENTATION_LANDSCAPE or SCREEN_ORIENTATION_PORTRAIT
        //       to lock the screen orientation for this activity.
        int screenOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR;

        // This is necessary for enabling AutoRotation in the Augmented View
        if (screenOrientation == ActivityInfo.SCREEN_ORIENTATION_SENSOR)
        {
            // NOTE: We use reflection here to see if the current platform
            // supports the full sensor mode (available only on Gingerbread
            // and above.
            try
            {
                // SCREEN_ORIENTATION_FULL_SENSOR is required to allow all 
                // 4 screen rotations if API level >= 9:
                Field fullSensorField = ActivityInfo.class
                        .getField("SCREEN_ORIENTATION_FULL_SENSOR");
                screenOrientation = fullSensorField.getInt(null);
            }
            catch (NoSuchFieldException e)
            {
                // App is running on API level < 9, do nothing.
            }
            catch (Exception e)
            {
                e.printStackTrace();
            }
        }

        // Apply screen orientation
        setRequestedOrientation(screenOrientation);

        updateActivityOrientation();

        // Query display dimensions:
        storeScreenDimensions();

        // As long as this window is visible to the user, keep the device's
        // screen turned on and bright:
        getWindow().setFlags(
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }


    /** Native function to initialize the application. */
    private native void initApplicationNative(int width, int height);


    /** Initializes AR application components. */
    private void initApplicationAR()
    {
        // Do application initialization in native code (e.g. registering
        // callbacks, etc.):
        initApplicationNative(mScreenWidth, mScreenHeight);

        // Create OpenGL ES view:
        int depthSize = 16;
        int stencilSize = 0;
        boolean translucent = QCAR.requiresAlpha();

        mGlView = new QCARSampleGLView(this);
        mGlView.init(mQCARFlags, translucent, depthSize, stencilSize);

        mRenderer = new ImageTargetsRenderer();
        mRenderer.mActivity = this;
        
        mGlView.setRenderer(mRenderer);

        //mUIInfo = (RelativeLayout) findViewById(R.id.rLayout);
        
        
        LayoutInflater inflater = LayoutInflater.from(this);
        mUILayout = (RelativeLayout) inflater.inflate(R.layout.camera_overlay,
                null, false);

        mUILayout.setVisibility(View.VISIBLE);
        mUILayout.setBackgroundColor(Color.BLACK);
        
        // Gets a reference to the loading dialog
        mLoadingDialogContainer = mUILayout.findViewById(R.id.loading_indicator);

        // Shows the loading indicator at start
        loadingDialogHandler.sendEmptyMessage(SHOW_LOADING_DIALOG);

        // Adds the inflated layout to the view
        addContentView(mUILayout, new LayoutParams(LayoutParams.MATCH_PARENT,
                LayoutParams.MATCH_PARENT));
    }


    /** Tells native code to switch dataset as soon as possible*/
    private native void switchDatasetAsap();

    private native boolean autofocus();
    private native boolean setFocusMode(int mode);

    /** Activates the Flash */
    private native boolean activateFlash(boolean flash);


    /** Returns the number of registered textures. */
    public int getTextureCount()
    {
        return mTextures.size();
    }


    /** Returns the texture object at the specified index. */
    public Texture getTexture(int i)
    {
        return mTextures.elementAt(i);
    }


    /** A helper for loading native libraries stored in "libs/armeabi*". */
    public static boolean loadLibrary(String nLibName)
    {
        try
        {
            System.loadLibrary(nLibName);
            DebugLog.LOGI("Native library lib" + nLibName + ".so loaded");
            return true;
        }
        catch (UnsatisfiedLinkError ulee)
        {
            DebugLog.LOGE("The library lib" + nLibName +
                            ".so could not be loaded");
        }
        catch (SecurityException se)
        {
            DebugLog.LOGE("The library lib" + nLibName +
                            ".so was not allowed to be loaded");
        }

        return false;
    }


    /**
     * Shows the Camera Options Dialog when the Menu Key is pressed
     */
    public boolean onKeyUp(int keyCode, KeyEvent event)
    {
        if (keyCode == KeyEvent.KEYCODE_MENU)
        {
            showCameraOptionsDialog();
            return true;
        }

        return super.onKeyUp(keyCode, event);
    }


    public boolean onTouchEvent(MotionEvent event)
    {
        // Process the Gestures
        return mGestureDetector.onTouchEvent(event);
    }


    /**
     * Process Double Tap event for showing the Camera options menu
     */
    private class GestureListener extends
            GestureDetector.SimpleOnGestureListener
    {
        public boolean onDown(MotionEvent e)
        {
            return true;
        }


        public boolean onSingleTapUp(MotionEvent e)
        {
            // Calls the Autofocus Native Method
            autofocus();

            // Triggering manual auto focus disables continuous
            // autofocus
            mContAutofocus = false;

            return true;
        }


        // Event when double tap occurs
        public boolean onDoubleTap(MotionEvent e)
        {
            // Shows the Camera options
            showCameraOptionsDialog();
            return true;
        }
    }


    /**
     * Shows an AlertDialog with the camera options available
     */
    private void showCameraOptionsDialog()
    {
        // Only show camera options dialog box if app has been already inited
        if (mAppStatus < APPSTATUS_INITED)
        {
            return;
        }

        final int itemCameraIndex = 0;
        final int itemAutofocusIndex = 1;
        final int itemSwitchDatasetIndex = 2;

        AlertDialog cameraOptionsDialog = null;

        CharSequence[] items =
        { getString(R.string.menu_flash_on),
                getString(R.string.menu_contAutofocus_off),
                //getString(R.string.menu_switch_to_tarmac)
                };

        // Updates list titles according to current state of the options
        if (mFlash)
        {
            items[itemCameraIndex] = (getString(R.string.menu_flash_off));
        }
        else
        {
            items[itemCameraIndex] = (getString(R.string.menu_flash_on));
        }

        if (mContAutofocus)
        {
            items[itemAutofocusIndex] = (getString(R.string.menu_contAutofocus_off));
        }
        else
        {
            items[itemAutofocusIndex] = (getString(R.string.menu_contAutofocus_on));
        }

//        if (mIsStonesAndChipsDataSetActive)
//        {
//            items[itemSwitchDatasetIndex] = (getString(R.string.menu_switch_to_tarmac));
//        }
//        else
//        {
//            items[itemSwitchDatasetIndex] = (getString(R.string.menu_switch_to_stone_chips));
//        }

        // Builds the Alert Dialog
        AlertDialog.Builder cameraOptionsDialogBuilder = new AlertDialog.Builder(
                ImageTargets.this);
        cameraOptionsDialogBuilder
                .setTitle(getString(R.string.menu_camera_title));
        cameraOptionsDialogBuilder.setItems(items,
                new DialogInterface.OnClickListener()
                {
                    public void onClick(DialogInterface dialog, int item)
                    {
                        if (item == itemCameraIndex)
                        {
                            // Turns focus mode on/off by calling native
                            // method
                            if (activateFlash(!mFlash))
                            {
                                mFlash = !mFlash;
                            }
                            else
                            {
                                Toast.makeText
                                (
                                    ImageTargets.this,
                                    "Unable to turn " + 
                                    (mFlash ? "off" : "on") + " flash",
                                    Toast.LENGTH_SHORT
                                ).show();
                            }

                            // Dismisses the dialog
                            dialog.dismiss();
                        }
                        else if (item == itemAutofocusIndex)
                        {
                            if (mContAutofocus)
                            {
                                // Sets the Focus Mode by calling the native
                                // method
                                if (setFocusMode(FOCUS_MODE_NORMAL))
                                {
                                    mContAutofocus = false;
                                }
                                else
                                {
                                    Toast.makeText
                                    (
                                        ImageTargets.this,
                                        "Unable to deactivate Continuous Auto-Focus",
                                        Toast.LENGTH_SHORT
                                    ).show();
                                }
                            }
                            else
                            {
                                // Sets the focus mode by calling the native
                                // method
                                if (setFocusMode(FOCUS_MODE_CONTINUOUS_AUTO))
                                {
                                    mContAutofocus = true;
                                }
                                else
                                {
                                    Toast.makeText
                                    (
                                            ImageTargets.this,
                                        "Unable to activate Continuous Auto-Focus",
                                        Toast.LENGTH_SHORT
                                    ).show();
                                }
                            }

                            // Dismisses the dialog
                            dialog.dismiss();
                        }
                        else if (item == itemSwitchDatasetIndex)
                        {

                            switchDatasetAsap();
                            mIsStonesAndChipsDataSetActive = !mIsStonesAndChipsDataSetActive;

                            dialog.dismiss();
                        }

                    }
                });

        // Shows the Dialog
        cameraOptionsDialog = cameraOptionsDialogBuilder.create();
        cameraOptionsDialog.show();
    }
    
    private void setInfoDetails(String title,String description){
    	//mTxtInfoTitle = (TextView) findViewById(R.id.info_details_title);
    	//mTxtInfoDescription = (TextView) findViewById(R.id.info_details_description);
    	//mTxtInfoTitle.setText("@string/title_menu_flash_on");
    	//mTxtInfoDescription.setText("@string/title_menu_flash_off");
    	
    	View header = (View) getLayoutInflater().inflate(R.layout.info_details, null);
    	
    	mTxtInfoTitle = (TextView) header.findViewById(R.id.info_details_title);
    	mTxtInfoTitle.setText( "Hello" );
    	mTxtInfoDescription = (TextView) header.findViewById(R.id.info_details_description);
    	mTxtInfoDescription.setText( ImageTargets.this.getString(R.string.menu_flash_off) );
        
    }
    
         
    
    
}
