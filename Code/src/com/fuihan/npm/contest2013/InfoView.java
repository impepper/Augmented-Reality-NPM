package com.fuihan.npm.contest2013;

import android.content.Context;
import android.view.*;
import android.view.View.OnClickListener;
import android.widget.ImageButton;
import android.widget.TextView;
import android.media.MediaPlayer;

import com.fuihan.npm.contest2013.R;

public class InfoView extends View implements OnClickListener
{

	private ImageButton mCloseButton;
	private ImageButton mAudioButton;
    private TextView mTxtInfoTitle;
    private TextView mTxtInfoDescription;
//    private MediaPlayer myMediaPlayer;
	public InfoView(Context context) {
		super(context);
		// TODO Auto-generated constructor stub
    	mTxtInfoTitle = (TextView) findViewById(R.id.info_details_title);
    	mTxtInfoTitle.setText( "Hello" );
    	mTxtInfoDescription = (TextView) findViewById(R.id.info_details_description);
    	mTxtInfoDescription.setText( "Good" ); 
    	
//    	Context context1 = getApplicationContext();
//    	MediaPlayer mp = MediaPlayer.create(context, R.raw.a_new_president);
//    	mp.start();
    	
//    	myMediaPlayer = MediaPlayer.create(context, R.raw.a_new_president); 
//    	myMediaPlayer.start();
    	
        mCloseButton = (ImageButton) findViewById(R.id.btn_info_close);
        mCloseButton.setOnClickListener(this);
        
        mAudioButton = (ImageButton) findViewById(R.id.btn_info_audio);
        mAudioButton.setOnClickListener(this);
	}
	
    public void onClick(View v)
    {
        switch (v.getId())
        {
        case R.id.btn_info_close:
//        	myMediaPlayer.stop();
//        	myMediaPlayer.release();
            break;
        }
    }
    
    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
//            moveTaskToBack(true);
        	mCloseButton.performClick();
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }    
    
}
