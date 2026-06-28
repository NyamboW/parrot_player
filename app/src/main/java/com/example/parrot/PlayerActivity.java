package com.example.parrot;

import static com.example.parrot.ApplicationClass.ACTION_NEXT;
import static com.example.parrot.ApplicationClass.ACTION_PLAY;
import static com.example.parrot.ApplicationClass.ACTION_PREV;
import static com.example.parrot.ApplicationClass.CHANNEL_ID_2;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.view.menu.ActionMenuItem;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.media.session.MediaButtonReceiver;

import android.Manifest;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.app.AlertDialog;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.media.MediaPlayer;
import android.media.session.MediaSession;
import android.media.session.PlaybackState;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.provider.Settings;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.support.v4.media.session.MediaSessionCompat;
import android.support.v4.media.session.PlaybackStateCompat;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.Animation;
import android.view.animation.AnimationSet;
import android.view.animation.TranslateAnimation;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import com.bullhead.equalizer.DialogEqualizerFragment;
import com.bullhead.equalizer.EqualizerFragment;


import java.io.File;
import java.util.ArrayList;
import java.util.Locale;

public class PlayerActivity extends AppCompatActivity implements ActionPlaying{//}, ServiceConnection {

    Button btnPlay, btnNext, btnPrevious, btnRepeat, btnShuffle, btnMic, btnfav, btnback, btnEq;
    TextView txtSongName, txtSongstart, txtSongEnd;
    SeekBar seekMusicBar;
    ImageView imageView;
    String songName;
    //for music service notification
    MusicService musicService;
    MediaSessionCompat mediaSession;


    //Ai code

    private SpeechRecognizer speechRecognizer;
    private Intent speechRecognizerIntent;
    private String keeper = "";
    private String mode = "ON";


    public static final String EXTRA_NAME = "song_name";
    static MediaPlayer mediaPlayer;
    int position;
    ArrayList<File> mySongs;


//back on action bar
   /* @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            onBackPressed();
        }
        return super.onOptionsItemSelected(item);
          } */

    Thread updateSeekBar;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_player);

        //set name on action bar
        /*
       getSupportActionBar().setTitle("AI PARROT");
       getSupportActionBar().setDisplayHomeAsUpEnabled(true);
      getSupportActionBar().setDisplayShowHomeEnabled(true);
      */

        //  eqFrame = findViewById(R.id.eqFrame);
        btnEq = findViewById(R.id.btnEq);
        btnback = findViewById(R.id.btnback);
        btnfav = findViewById(R.id.btnfav);
        btnPrevious = findViewById(R.id.btnPrevious);
        btnNext = findViewById(R.id.btnNext);
        btnPlay = findViewById(R.id.btnPlay);
        btnRepeat = findViewById(R.id.btnRepeat);
        btnShuffle = findViewById(R.id.btnShuffle);
        txtSongName = findViewById(R.id.txtSong);
        txtSongstart = findViewById(R.id.txtSongStart);
        txtSongEnd = findViewById(R.id.txtSongEnd);

        seekMusicBar = findViewById(R.id.seekBar);

        imageView = findViewById(R.id.imgView);


        //musicservice code
//      mediaSession = new MediaSessionCompat(this, "playerAudio");
//        Intent intent1 = new Intent(this, MusicService.class);
//        bindService(intent1, this, BIND_AUTO_CREATE);

        //ai code
        checkvoicecommandPermission();

        btnMic = findViewById(R.id.btnMic);
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(PlayerActivity.this);
        speechRecognizerIntent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        speechRecognizerIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        speechRecognizerIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault());

        speechRecognizer.setRecognitionListener(new RecognitionListener() {
            @Override
            public void onReadyForSpeech(Bundle params) {

            }

            @Override
            public void onBeginningOfSpeech() {

            }

            @Override
            public void onRmsChanged(float rmsdB) {

            }

            @Override
            public void onBufferReceived(byte[] buffer) {

            }

            @Override
            public void onEndOfSpeech() {

            }

            @Override
            public void onError(int error) {

            }

            @Override
            public void onResults(Bundle results) {

                ArrayList<String> matchesFound = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);

                if (matchesFound != null) {
                    //if (mode.equals("ON")) {
                    keeper = matchesFound.get(0);

                    if (keeper.equals("pause the song")) {
                        Toast.makeText(PlayerActivity.this, "Command = " + keeper, Toast.LENGTH_LONG).show();
                    } else if (keeper.equals("play the song")) {
                        playClicked();
                        Toast.makeText(PlayerActivity.this, "Command = " + keeper, Toast.LENGTH_LONG).show();
                    } else if (keeper.equals("play next song")) {
                        nextClicked();
                        Toast.makeText(PlayerActivity.this, "Command = " + keeper, Toast.LENGTH_LONG).show();
                    } else if (keeper.equals("play previous song")) {
                        prevClicked();
                        Toast.makeText(PlayerActivity.this, "Command = " + keeper, Toast.LENGTH_LONG).show();
                        //}

                    } else {
                        Toast.makeText(PlayerActivity.this, "Cannot understand you", Toast.LENGTH_LONG).show();
                    }

                }
            }

            @Override
            public void onPartialResults(Bundle partialResults) {

            }

            @Override
            public void onEvent(int eventType, Bundle params) {

            }
        });

        btnMic.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        speechRecognizer.startListening(speechRecognizerIntent);
                        keeper = "";
                        btnMic.setBackgroundResource(R.drawable.ic_mic1);
                        break;

                    case MotionEvent.ACTION_UP:
                        speechRecognizer.stopListening();
                        btnMic.setBackgroundResource(R.drawable.ic_mic);
                        break;
                }
                return false;
            }
        });


        if (mediaPlayer != null) {
            mediaPlayer.start();
            mediaPlayer.release();
        }

        Intent intent = getIntent();
        Bundle bundle = intent.getExtras();

        mySongs = (ArrayList) bundle.getParcelableArrayList("songs");
        String sName = intent.getStringExtra("songname");
        position = bundle.getInt("pos", 0);
        txtSongName.setSelected(true);

        Uri uri = Uri.parse(mySongs.get(position).toString());
        songName = mySongs.get(position).getName();

        txtSongName.setText(songName);

        mediaPlayer = MediaPlayer.create(getApplicationContext(), uri);
        mediaPlayer.start();
        //showNotification(R.drawable.ic_pause);


        updateSeekBar = new Thread() {
            @Override
            public void run() {
                int totalDuration = mediaPlayer.getDuration();
                int currentPosition = 0;

                while (currentPosition < totalDuration) {
                    try {
                        sleep(500);
                        currentPosition = mediaPlayer.getCurrentPosition();
                        seekMusicBar.setProgress(currentPosition);

                    } catch (InterruptedException | IllegalStateException e) {
                        e.printStackTrace();
                    }
                }
            }
        };

        seekMusicBar.setMax(mediaPlayer.getDuration());
        updateSeekBar.start();
        seekMusicBar.getProgressDrawable().setColorFilter(getResources().getColor(R.color.white), PorterDuff.Mode.MULTIPLY);
        seekMusicBar.getThumb().setColorFilter(getResources().getColor(R.color.white), PorterDuff.Mode.SRC_IN);

        seekMusicBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser) {
                    mediaPlayer.seekTo(progress);
                    txtSongEnd.setText(createTime(progress));

                    // mediaPlayer.getDuration();
                }
            }


            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {

            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                mediaPlayer.seekTo(seekBar.getProgress());
            }
        });

        String endtime = createTime(mediaPlayer.getDuration());
        txtSongEnd.setText(endtime);

        final Handler handler = new Handler();
        final int delay = 1000;

        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                String currentTime = createTime(mediaPlayer.getCurrentPosition());
                txtSongstart.setText(currentTime);
                handler.postDelayed(this, delay);
            }
        }, delay);
/*
        voiceEnabledBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if(mode.equals("ON"))
                {
                    mode= "OFF";
                    voiceEnabledBtn.setText("Voice Enabled Mode - OFF");
                    lowerRelativeLayout.setVisibility(View.VISIBLE);
                }
                else
                {
                    mode= "ON";
                    voiceEnabledBtn.setText("Voice Enabled Mode - ON");
                    lowerRelativeLayout.setVisibility(View.GONE);
                }
            }
        }); */


        btnPlay.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                playClicked();

                TranslateAnimation moveAnim = new TranslateAnimation(-25, 25, -25, 25);
                moveAnim.setInterpolator(new AccelerateInterpolator());
                moveAnim.setDuration(600);
                moveAnim.setFillEnabled(true);
                moveAnim.setFillAfter(true);
                moveAnim.setRepeatMode(Animation.REVERSE);
                moveAnim.setRepeatCount(1);
                imageView.startAnimation(moveAnim);

            }
        });
        mediaPlayer.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
            @Override
            public void onCompletion(MediaPlayer mp) {
                btnNext.performClick();

                //  playNextSong();
            }
        });

        btnNext.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                nextClicked();
                // txtSongEnd.setText(endtime);
                //   txtSongEnd.setText(createTime());
                //   seekMusicBar.

            }
        });
        btnback.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                //   startActivity(new Intent(getApplicationContext(),Music_Fragment.class)

                onBackPressed();
            }
        });

        btnPrevious.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                prevClicked();

            }
        });

        btnfav.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {

            }

        });


        btnRepeat.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

            }
        });

        btnEq.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {

                int sessionId = mediaPlayer.getAudioSessionId();

                DialogEqualizerFragment fragment = DialogEqualizerFragment.newBuilder()

                        .setAudioSessionId(sessionId)
                        .themeColor(ContextCompat.getColor(getApplicationContext(), R.color.eq_back))
                        .textColor(ContextCompat.getColor(getApplicationContext(), R.color.faint_))
                        .accentAlpha(ContextCompat.getColor(getApplicationContext(), R.color.light_blue_600))
                        .darkColor(ContextCompat.getColor(getApplicationContext(), R.color.light_blue_A400))
                        .setAccentColor(ContextCompat.getColor(getApplicationContext(), R.color.real_white))
                        .build();
                fragment.show(getSupportFragmentManager(), "eq");

            }
        });

        btnShuffle.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

            }
        });
    }

    public void StartAnimation(View view, Float degree) {
        ObjectAnimator objectAnimator = ObjectAnimator.ofFloat(imageView, "rotation", 0f, degree);
        objectAnimator.setDuration(1000);
        AnimatorSet animatorSet = new AnimatorSet();
        animatorSet.playTogether(objectAnimator);
        animatorSet.start();
    }


    // milliseconds was duration
    public String createTime(long milliSeconds) {
        String finalTimerString = "";
        String secondsString;

        //Converting total duration into time
        int hours = (int) (milliSeconds / 3600000);
        int minutes = (int) (milliSeconds % 3600000) / 60000;
        int seconds = (int) ((milliSeconds % 3600000) % 60000 / 1000);

        // Adding hours if any
        if (hours > 0)
            finalTimerString = hours + ":";

        // Prepending 0 to seconds if it is one digit
        if (seconds < 10)
            secondsString = "0" + seconds;
        else
            secondsString = "" + seconds;

        finalTimerString = finalTimerString + minutes + ":" + secondsString;

        // Return timer String;
        return finalTimerString;

        /*String time = "";
        int min = duration / 1000 / 60;
        int sec = duration / 1000 % 60;

        time = time + min + ":";
        if (sec < 10) {
            time += "0";
        }
        time += sec;
        return time; */
    }

    private void checkvoicecommandPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (!(ContextCompat.checkSelfPermission(PlayerActivity.this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED)) {
                Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + getPackageName()));
                startActivity(intent);
                finish();
            }
        }
    }


    @Override
    protected void onPause() {
        super.onPause();
       // unbindService(this);

    }

    @Override
    protected void onResume() {
        super.onResume();
        Intent intent1 = new Intent(this, MusicService.class);
      //  bindService(intent1, this, BIND_AUTO_CREATE);
    }

    @Override
    public void nextClicked() {
        mediaPlayer.pause();
        mediaPlayer.stop();
        mediaPlayer.release();

        position = ((position + 1) % mySongs.size());

        Uri uri = Uri.parse(mySongs.get(position).toString());

        mediaPlayer = MediaPlayer.create(PlayerActivity.this, uri);
        songName = mySongs.get(position).getName();
        txtSongName.setText(songName);
        mediaPlayer.start();

        if (mediaPlayer.isPlaying()) {

            //  showNotification(R.drawable.ic_pause);
        } else {

            // showNotification(R.drawable.ic_play);

        }

    }

    @Override
    public void prevClicked() {
        mediaPlayer.stop();
        mediaPlayer.release();
        position = ((position - 1) < 0) ? (mySongs.size() - 1) : position - 1;
        Uri uri = Uri.parse(mySongs.get(position).toString());
        mediaPlayer = MediaPlayer.create(getApplicationContext(), uri);
        songName = mySongs.get(position).getName();
        txtSongName.setText(songName);
        mediaPlayer.start();


        if (mediaPlayer.isPlaying()) {
            btnPlay.setBackgroundResource(R.drawable.ic_pause);
            //  showNotification(R.drawable.ic_pause);
        } else {
            btnPlay.setBackgroundResource(R.drawable.ic_play);

            //  showNotification(R.drawable.ic_play);

        }
    }

    @Override
    public void playClicked() {
        if (mediaPlayer.isPlaying()) {
            btnPlay.setBackgroundResource(R.drawable.ic_play);
            // showNotification(R.drawable.ic_play);
            mediaPlayer.pause();

        } else {
            btnPlay.setBackgroundResource(R.drawable.ic_pause);
            // showNotification(R.drawable.ic_pause);
            mediaPlayer.start();
        }

    }
}

//    @Override
//    public void onServiceConnected(ComponentName componentName, IBinder iBinder) {
//        MusicService.myBinder binder = (MusicService.myBinder) iBinder;
//        musicService = binder.getService();
//        musicService.setCallBack(PlayerActivity.this);
//    }
//
//    @Override
//    public void onServiceDisconnected(ComponentName componentName) {
//        musicService = null;
//    }
//
//
//    public void showNotification(int playPauseBtn) {
//        Intent intent = new Intent(this, PlayerActivity.class);
//        PendingIntent contentIntent = PendingIntent.getActivity(this,
//                0, intent, 0);
//
//        Intent prevIntent = new Intent(this, NotificationReceiver.class)
//                .setAction(ACTION_PREV);
//        PendingIntent prevPendingIntent = PendingIntent.getBroadcast(this, 0, prevIntent,
//                PendingIntent.FLAG_UPDATE_CURRENT);
//
//        Intent playIntent = new Intent(this, NotificationReceiver.class)
//                .setAction(ACTION_PLAY);
//        PendingIntent playPendingIntent = PendingIntent.getBroadcast(this, 0, playIntent,
//                PendingIntent.FLAG_UPDATE_CURRENT);
//
//        Intent nextIntent = new Intent(this, NotificationReceiver.class)
//                .setAction(ACTION_NEXT);
//        PendingIntent nextPendingIntent = PendingIntent.getBroadcast(this, 0, nextIntent,
//                PendingIntent.FLAG_UPDATE_CURRENT);
//
//        //  Bitmap picture = BitmapFactory.decodeResource(getResources(),mySongs.get(position).getThumbnail());
//        Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID_2)
//                //   .setSmallIcon(mySongs.get(position).getThumbnail())
//                .setSmallIcon(R.drawable.ic_play)
//                .setLargeIcon(null)
//                //   .setLargeIcon(picture)
//                .setContentTitle(mySongs.get(position).getName())
//                //  .setContentText(mySongs.get(position).getSinger())
//                .addAction(R.drawable.ic_previous, "Previous", prevPendingIntent)
//                .addAction(playPauseBtn, "Play", playPendingIntent)
//                .addAction(R.drawable.ic_next, "Next", nextPendingIntent)
//                .setStyle(new androidx.media.app.NotificationCompat.MediaStyle()
//                        .setMediaSession(mediaSession.getSessionToken()))
//                .setPriority(NotificationCompat.PRIORITY_LOW)
//                .setContentIntent(contentIntent)
//
//
//
//                .setOnlyAlertOnce(true)
//                .build();
//        NotificationManager notificationManager = (NotificationManager)
//                getSystemService(NOTIFICATION_SERVICE);
//
//        notificationManager.notify(0, notification);
 // }




//}