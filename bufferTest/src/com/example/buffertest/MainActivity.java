package com.example.buffertest;

import android.app.Activity;

import android.app.ActionBar;
import android.app.Fragment;
import android.app.FragmentManager;
import android.content.Context;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.support.v4.widget.DrawerLayout;
import android.widget.ArrayAdapter;
import android.widget.TextView;


public class MainActivity extends Activity implements NavigationDrawerFragment.NavigationDrawerCallbacks {
	int       RECORDER_SAMPLERATE = 44100;
	int       MAX_FREQ = RECORDER_SAMPLERATE/2;
	final int RECORDER_CHANNELS = AudioFormat.CHANNEL_IN_MONO;
	final int RECORDER_AUDIO_ENCODING = AudioFormat.ENCODING_PCM_16BIT;
	final int PEAK_THRESH = 20;

	short[]     buffer           = null;
	int         bufferReadResult = 0;
	AudioRecord audioRecord      = null;
	boolean     aRecStarted      = false;

	int         minBufferSize    = 0;
	float       volume           = 0;
	FFT         fft              = null;
	float[]     fftRealArray     = null;
	int         mainFreq         = 0;

	float       drawScaleH       = (float) 1.5; // TODO: calculate the drawing scales
	float       drawScaleW       = (float) 1.0; // TODO: calculate the drawing scales
	int         drawStepW        = 2;   // display only every Nth freq value
	float       maxFreqToDraw    = 2500; // max frequency to represent graphically
	int         drawBaseLine     = 0;
	int bufferSize;

    /**
     * Fragment managing the behaviors, interactions and presentation of the navigation drawer.
     */
    private NavigationDrawerFragment mNavigationDrawerFragment;

    /**
     * Used to store the last screen title. For use in {@link #restoreActionBar()}.
     */
    private CharSequence mTitle;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mNavigationDrawerFragment = (NavigationDrawerFragment)
                getFragmentManager().findFragmentById(R.id.navigation_drawer);
        mTitle = getTitle();

        // Set up the drawer.
        mNavigationDrawerFragment.setUp(
                R.id.navigation_drawer,
                (DrawerLayout) findViewById(R.id.drawer_layout));
    }

    @Override
    public void onNavigationDrawerItemSelected(int position) {
        // update the main content by replacing fragments
        FragmentManager fragmentManager = getFragmentManager();
        fragmentManager.beginTransaction()
                .replace(R.id.container, PlaceholderFragment.newInstance(position + 1))
                .commit();
    }

    public void onSectionAttached(int number) {
        switch (number) {
            case 1:
                mTitle = getString(R.string.title_section1);
                break;
            case 2:
                mTitle = getString(R.string.title_section2);
                break;
            case 3:
                mTitle = getString(R.string.title_section3);
                break;
        }
    }

    public void restoreActionBar() {
        ActionBar actionBar = getActionBar();
        actionBar.setNavigationMode(ActionBar.NAVIGATION_MODE_STANDARD);
        actionBar.setDisplayShowTitleEnabled(true);
        actionBar.setTitle(mTitle);
    }


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        if (!mNavigationDrawerFragment.isDrawerOpen()) {
            // Only show items in the action bar relevant to this screen
            // if the drawer is not showing. Otherwise, let the drawer
            // decide what to show in the action bar.
            getMenuInflater().inflate(R.menu.main, menu);
            restoreActionBar();
            return true;
        }
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();
        if (id == R.id.action_settings) {
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    /**
     * A placeholder fragment containing a simple view.
     */
    public static class PlaceholderFragment extends Fragment {
        /**
         * The fragment argument representing the section number for this
         * fragment.
         */
        private static final String ARG_SECTION_NUMBER = "section_number";

        /**
         * Returns a new instance of this fragment for the given section
         * number.
         */
        public static PlaceholderFragment newInstance(int sectionNumber) {
            PlaceholderFragment fragment = new PlaceholderFragment();
            Bundle args = new Bundle();
            args.putInt(ARG_SECTION_NUMBER, sectionNumber);
            fragment.setArguments(args);
            return fragment;
        }

        public PlaceholderFragment() {
        }

        @Override
        public View onCreateView(LayoutInflater inflater, ViewGroup container,
                Bundle savedInstanceState) {
            View rootView = inflater.inflate(R.layout.fragment_main, container, false);
            return rootView;
        }

        @Override
        public void onAttach(Activity activity) {
            super.onAttach(activity);
            ((MainActivity) activity).onSectionAttached(
                    getArguments().getInt(ARG_SECTION_NUMBER));
        }
    }
        void setup() {
      
      minBufferSize = AudioRecord.getMinBufferSize(RECORDER_SAMPLERATE,RECORDER_CHANNELS,RECORDER_AUDIO_ENCODING);
      
	// if we are working with the android emulator, getMinBufferSize() does not work
      // and the only samplig rate we can use is 8000Hz
      if (minBufferSize == AudioRecord.ERROR_BAD_VALUE)  {
        RECORDER_SAMPLERATE = 8000; // forced by the android emulator
        MAX_FREQ = RECORDER_SAMPLERATE/2;
        bufferSize =  2 << (int)(log(RECORDER_SAMPLERATE)/log(2)-1);// buffer size must be power of 2!!!
        // the buffer size determines the analysis frequency at: RECORDER_SAMPLERATE/bufferSize
        // this might make trouble if there is not enough computation power to record and analyze
        // a frequency. In the other hand, if the buffer size is too small AudioRecord will not initialize
      } else bufferSize = minBufferSize;
      
      buffer = new short[bufferSize];
      // use the mic with Auto Gain Control turned off!
      audioRecord = new AudioRecord( MediaRecorder.AudioSource.VOICE_RECOGNITION, RECORDER_SAMPLERATE,
                                     RECORDER_CHANNELS,RECORDER_AUDIO_ENCODING, bufferSize);
     
      //audioRecord = new AudioRecord( MediaRecorder.AudioSource.MIC, RECORDER_SAMPLERATE,
       //                              RECORDER_CHANNELS,RECORDER_AUDIO_ENCODING, bufferSize);
      if ((audioRecord != null) && (audioRecord.getState() == AudioRecord.STATE_INITIALIZED)) {
        try {
          // this throws an exception with some combinations
          // of RECORDER_SAMPLERATE and bufferSize 
          audioRecord.startRecording(); 
          aRecStarted = true;
        }
        catch (Exception e) {
          aRecStarted = false;
        }
        
        if (aRecStarted) {
            bufferReadResult = audioRecord.read(buffer, 0, bufferSize);
            // verify that is power of two
            if (bufferReadResult % 2 != 0) bufferReadResult = 2 << (int)(log(bufferReadResult)/log(2)); 
           
            fft = new FFT(bufferReadResult, RECORDER_SAMPLERATE);
            fftRealArray = new float[bufferReadResult]; 
            drawScaleW = drawScaleW*(float)displayWidth/(float)fft.freqToIndex(maxFreqToDraw);
        }
      }
      fill(0);
      noStroke();
    }

    private void fill(int i) {
			// TODO Auto-generated method stub
			
		}

	private int log(int rECORDER_SAMPLERATE2) {
			// TODO Auto-generated method stub
			return 0;
		}

	void draw() {
       background(128); fill(0); noStroke();
       if (aRecStarted) {
           bufferReadResult = audioRecord.read(buffer, 0, bufferSize);  
           
           // After we read the data from the AudioRecord object, we loop through
           // and translate it from short values to double values. We can't do this
           // directly by casting, as the values expected should be between -1.0 and 1.0
           // rather than the full range. Dividing the short by 32768.0 will do that,
           // as that value is the maximum value of short.
           volume = 0;
           for (int i = 0; i < bufferReadResult; i++) {
                fftRealArray[i] = (float) buffer[i] / Short.MAX_VALUE;// 32768.0;
                volume += Math.abs(fftRealArray[i]);
           }
           volume = (float)Math.log10(volume/bufferReadResult);
             
             // apply windowing
            for (int i = 0; i < bufferReadResult/2; ++i) {
              // Calculate & apply window symmetrically around center point
              // Hanning (raised cosine) window
              float winval = (float)(0.5+0.5*Math.cos(Math.PI*(float)i/(float)(bufferReadResult/2)));
              if (i > bufferReadResult/2)  winval = 0;
              fftRealArray[bufferReadResult/2 + i] *= winval;
              fftRealArray[bufferReadResult/2 - i] *= winval;
            }
            // zero out first point (not touched by odd-length window)
            fftRealArray[0] = 0;
            fft.forward(fftRealArray);
             
             //
            fill(255);
            stroke(100);
            pushMatrix();
            rotate(radians(90));
            translate(drawBaseLine-3, 0);
            textAlign(LEFT,CENTER);
            for (float freq = RECORDER_SAMPLERATE/2-1; freq > 0.0; freq -= 150.0) {
              int y = -(int)(fft.freqToIndex(freq)*drawScaleW); // which bin holds this frequency?
              line(-displayHeight,y,0,y); // add tick mark
              text(Math.round(freq)+" Hz", 10, y); // add text label
            }
            popMatrix();
            noStroke();
       
            float lastVal = 0;
            float val = 0;
            float maxVal = 0; // index of the bin with highest value
            int maxValIndex = 0; // index of the bin with highest value
            for(int i = 0; i < fft.specSize(); i++)
            {
              val += fft.getBand(i);
              if (i % drawStepW == 0) {
                   val /= drawStepW; // average volume value
                   int prev_i = i-drawStepW;
                  stroke(255);
                  // draw the line for frequency band i, scaling it up a bit so we can see it
                  line( prev_i*drawScaleW, drawBaseLine, prev_i*drawScaleW, drawBaseLine - lastVal*drawScaleH );
              
                  if (val-lastVal > PEAK_THRESH) {
                      stroke(255,0,0);
                      fill(255,128,128);
                      ellipse(i*drawScaleW, drawBaseLine - val*drawScaleH, 20,20);
                      stroke(255);
                      fill(255);
                      if (val > maxVal) {
                        maxVal = val;
                        maxValIndex = i;
                      }
                  } 
                  line( prev_i*drawScaleW, drawBaseLine - lastVal*drawScaleH, i*drawScaleW, drawBaseLine - val*drawScaleH );
                  lastVal = val;
                  val = 0;  
               }
            }
            if (maxValIndex-drawStepW > 0) {
               fill(255,0,0);
               ellipse(maxValIndex*drawScaleW, drawBaseLine - maxVal*drawScaleH, 20,20);
               fill(0,0,255);
               text( " " + fft.indexToFreq(maxValIndex-drawStepW/2)+"Hz",
                     25+maxValIndex*drawScaleW, drawBaseLine - maxVal*drawScaleH);     
            }
            fill(255); 
            pushMatrix();
            translate(displayWidth/2,drawBaseLine);
            text("buffer readed: " + bufferReadResult, 20, 80);
            text("fft spec size: " + fft.specSize(), 20, 100);
            text("volume: " + volume, 20, 120);  
            popMatrix();
      }
      else {
        fill(255,0,0);
        text("AUDIO RECORD NOT INITIALIZED!!!", 100, height/2);
      }  
      fill(255); 
      pushMatrix();
      translate(0,drawBaseLine);
      text("sample rate: " + RECORDER_SAMPLERATE + " Hz", 20, 80);   
      text("displaying freq: 0 Hz  to  "+maxFreqToDraw+" Hz", 20, 100);   
      text("buffer size: " + bufferSize, 20, 120);   
      popMatrix();
    }

    void stop() {
      audioRecord.stop();
      audioRecord.release();
    }

}
