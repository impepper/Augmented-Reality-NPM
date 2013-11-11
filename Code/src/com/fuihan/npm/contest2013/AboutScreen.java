/*==============================================================================
            Copyright (c) 2010-2013 QUALCOMM Austria Research Center GmbH.
            All Rights Reserved.
            Qualcomm Confidential and Proprietary

@file
    AboutScreen.java

@brief
    About Screen Activity for the ImageTargets sample application

==============================================================================*/

package com.fuihan.npm.contest2013;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.text.Html;
import android.text.method.LinkMovementMethod;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.TextView;
import com.fuihan.npm.contest2013.R;

public class AboutScreen extends Activity implements OnClickListener
{
    private TextView mAboutText;
    private Button mStartButton;


    public void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.about_screen);

        mAboutText = (TextView) findViewById(R.id.info_details_description);
        mAboutText.setText(Html.fromHtml(getString(R.string.about_text)));
        mAboutText.setMovementMethod(LinkMovementMethod.getInstance());

        // Setups the link color
        mAboutText.setLinkTextColor(getResources().getColor(
                R.color.holo_light_blue));

        mStartButton = (Button) findViewById(R.id.btn_ar_start);
        mStartButton.setOnClickListener(this);
    }


    /** Starts the ImageTargets main activity */
    private void startARActivity()
    {
        Intent i = new Intent(this, ImageTargets.class);
        startActivity(i);
    }


    public void onClick(View v)
    {
        switch (v.getId())
        {
        case R.id.btn_ar_start:
            startARActivity();
            break;
        }
    }
}
