package com.example.finaltestfreq;


import android.app.Activity;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.TextView;


public class MainActivity extends Activity{
  RecordAudio recordTask;
  TextView statusText,frequencies;
  

  boolean isRecording = false,isPlaying = false;

  int frequency = 44100;
  int channelConfiguration = AudioFormat.CHANNEL_CONFIGURATION_MONO;
  int audioEncoding = AudioFormat.ENCODING_PCM_16BIT;

  @Override
  public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_main);
    statusText = (TextView) this.findViewById(R.id.StatusTextView);

    recordTask = new RecordAudio();
    recordTask.execute();
  }

  private class RecordAudio extends AsyncTask<Void, Double, Void> {
    @Override
    protected Void doInBackground(Void... params) {
      isRecording = true;
      try {
       
    	int bufferSize = AudioRecord.getMinBufferSize(frequency,channelConfiguration, audioEncoding);
        AudioRecord audioRecord = new AudioRecord(MediaRecorder.AudioSource.MIC, frequency, channelConfiguration, audioEncoding, bufferSize);
        
        short[] buffer = new short[bufferSize];
        audioRecord.startRecording();
        int blockSize = 256;
        while (isRecording) {
          int bufferReadResult = audioRecord.read(buffer, 0,bufferSize);
          Double[] toTransform = new Double[blockSize];
          for (int i = 0; i < blockSize && i < bufferReadResult; i++) {
        	  toTransform[i] = (double) buffer[i]; 
          }
          publishProgress(toTransform);
        }
        audioRecord.stop();
        
      } catch (Throwable t) {
        Log.e("AudioRecord", "Recording Failed");
      }
      return null;
    }
    protected void onProgressUpdate(Double... progress) {
      statusText.setText(progress[0].toString());
    }
  }
}
