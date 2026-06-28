package com.example.parrot;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.NotificationCompat;
import androidx.core.view.GravityCompat;
import androidx.core.view.MenuItemCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.media.session.MediaButtonReceiver;
import androidx.navigation.NavController;
import androidx.navigation.NavDestination;
import androidx.navigation.Navigation;
import androidx.navigation.ui.NavigationUI;

import android.Manifest;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.SearchManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.support.v4.media.session.MediaSessionCompat;
import android.support.v4.media.session.PlaybackStateCompat;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ListView;
import android.widget.SearchView;
import android.widget.TextView;
import android.widget.Toast;

import com.example.parrot.databinding.ActivityMainBinding;
import com.google.android.material.navigation.NavigationView;
import com.google.android.material.snackbar.Snackbar;
import com.karumi.dexter.Dexter;
import com.karumi.dexter.MultiplePermissionsReport;
import com.karumi.dexter.PermissionToken;
import com.karumi.dexter.listener.PermissionDeniedResponse;
import com.karumi.dexter.listener.PermissionGrantedResponse;
import com.karumi.dexter.listener.PermissionRequest;
import com.karumi.dexter.listener.multi.MultiplePermissionsListener;
import com.karumi.dexter.listener.single.PermissionListener;

import java.io.File;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

import static com.example.parrot.PlayerActivity.mediaPlayer;

public class MainActivity extends AppCompatActivity {


    private Menu menu;



  //  ListView listView;
   // String[] items;


    //notification code

    private NotificationCompat.Builder notificationBuilder;
    private static MediaSessionCompat mediaSessionCompat;
    private PlaybackStateCompat.Builder stateBuilder;
    private NotificationManager notificationManager;
    public static final String CHANNEL_ID ="channelId";

    //Test floating icon


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);



        // listView = (ListView) findViewById(R.id.listView);
        //navigation bar code
        final DrawerLayout drawerLayout = findViewById(R.id.drawerLayout);
        findViewById(R.id.imageMenu).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                drawerLayout.openDrawer(GravityCompat.START);
            }
        });

        //navigation code
        NavigationView navigationView = findViewById(R.id.navigationView);
        navigationView.setItemIconTintList(null);

        NavController navController = Navigation.findNavController(this, R.id.navHostFragment);
        NavigationUI.setupWithNavController(navigationView, navController);
        TextView textTitle = findViewById(R.id.textTitle);

        navController.addOnDestinationChangedListener(new NavController.OnDestinationChangedListener() {
            @Override
            public void onDestinationChanged(@NonNull NavController controller, @NonNull NavDestination destination, @Nullable Bundle arguments) {
                //textTitle.setText(destination.getLabel());

            }
        });



    }
    //try search
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater menuInflater = getMenuInflater();
        menuInflater.inflate(R.menu.menu, menu);

        MenuItem searchItem = findViewById(R.id.item_search);
        SearchView searchView = (SearchView) MenuItemCompat.getActionView(searchItem);



        return super.onCreateOptionsMenu(menu);
    }


}

    //fav vode

        //removed code
       // runTimePermission();


        //notification code
       // createNotification();
      //  createMediaSession();



    //swipe between two activities
    /*
    public boolean onTouchEvent(MotionEvent touchEvent){
         switch (touchEvent.getAction())  {
             case MotionEvent.ACTION_DOWN:
                 x1 = touchEvent.getX();
                 y1 = touchEvent.getY();
                 break;
             case MotionEvent.ACTION_UP:
                 x2 = touchEvent.getX();
                 y2 = touchEvent.getY();
                 if (x1>x2){
                     Intent i = new Intent(MainActivity.this,PlayerActivity.class);
                     startActivity(i);
                 }else if(x1>x2){
                     Intent i = new Intent(MainActivity.this, PlayerActivity.class);
                   startActivity(i);
                 }
                 break;
         }
         return false;
    } */
/*

    public void runTimePermission() {
        Dexter.withContext(this).withPermissions(Manifest.permission.READ_EXTERNAL_STORAGE,Manifest.permission.RECORD_AUDIO)
                .withListener(new MultiplePermissionsListener() {
                    @Override
                    public void onPermissionsChecked(MultiplePermissionsReport multiplePermissionsReport) {
                        displaySong();
                    }

                    @Override
                    public void onPermissionRationaleShouldBeShown(List<PermissionRequest> list, PermissionToken permissionToken) {
                        permissionToken.continuePermissionRequest();
                    }
                }).check();

    }

    public ArrayList<File> findSong(File file)
    {
        ArrayList<File> arrayList = new ArrayList<>();
        File[] files = file.listFiles();

        for (File singleFile : files != null ? files : new File[0])
        {
            if (singleFile.isDirectory() && !singleFile.isHidden())
            {
                arrayList.addAll(findSong(singleFile));
            } else {
                if (singleFile.getName().endsWith(".mp3") || singleFile.getName().endsWith(".wav")) {
                    arrayList.add(singleFile);
                }
            }
        }

        return arrayList;
    }


    public void displaySong() {
        final ArrayList<File> mySongs = findSong(Environment.getExternalStorageDirectory());
        items = new String[mySongs.size()];
        for (int i = 0; i < mySongs.size(); i++) {
            items[i] = mySongs.get(i).getName().toString().replace(".mp3", "").replace(".wav", "");

        }
        customAdapter customAdapter = new customAdapter();
        listView.setAdapter(customAdapter);

        listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                String songName =(String) listView.getItemAtPosition(position);

                startActivity(new Intent(getApplicationContext(),PlayerActivity.class)
                .putExtra("songs",mySongs)
                        .putExtra("songname",songName)
                        .putExtra("pos",position)
                );

            }
        });

    }
    //notification code


/*
    @Override
    protected void onStop() {
        super.onStop();
        showNotification();
    } */
    /*

    class customAdapter extends BaseAdapter
    {

        @Override
        public int getCount() {

            return items.length;
        }

        @Override
        public Object getItem(int position) {

            return null;
        }

        @Override
        public long getItemId(int position) {

            return 0;
        }

        @Override
        public View getView(int position, View view, ViewGroup viewGroup) {
            View view1 = getLayoutInflater().inflate(R.layout.list_items,null);
            TextView txtSong = view1.findViewById(R.id.txtSong);
            txtSong.setSelected(true);
            txtSong.setText(items[position]);

            return view1;
        }
    }

    //notification code
    /*
    private void createNotification(){
        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.O){
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    getString(R.string.app_name),
                    NotificationManager.IMPORTANCE_LOW
            );
            NotificationManager manager = getSystemService(NotificationManager.class );
            manager.createNotificationChannel(channel);
        }
    }

    private void createMediaSession(){
        mediaSessionCompat = new MediaSessionCompat(this,"Parrot session");
        stateBuilder = new PlaybackStateCompat.Builder()
                .setActions(
                        PlaybackStateCompat.ACTION_PLAY |
                                PlaybackStateCompat.ACTION_PAUSE |
                                PlaybackStateCompat.ACTION_SKIP_TO_NEXT |
                                PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS
                );
        mediaSessionCompat.setMediaButtonReceiver(null);
        mediaSessionCompat.setPlaybackState(stateBuilder.build());
        mediaSessionCompat.setCallback(new MediaSessionCompat.Callback() {
            @Override
            public void onPlay() {
                super.onPlay();
                mediaPlayer.start();
                //playPause();
                showNotification();


            }

            @Override
            public void onPause() {
                super.onPause();

                //playPause();
                mediaPlayer.pause();
                showNotification();
            }

            @Override
            public void onSkipToNext() {

                super.onSkipToNext();

            }

            @Override
            public void onSkipToPrevious() {
                super.onSkipToPrevious();
            }
        });
        mediaSessionCompat.setActive(true);

    }
    private void showNotification(){
        notificationBuilder = new NotificationCompat.Builder(this,CHANNEL_ID);
        int icon;
        String playPause,playNext;
        if(mediaPlayer.isPlaying()){
            icon = R.drawable.ic_pause;
            playPause = "pause";
        } else {
            icon = R.drawable.ic_play;
            playPause = "play";
        }
        //to add another
        if(mediaPlayer.isPlaying()){
            icon = R.drawable.ic_next;
            playNext = "playnext";
        }
        NotificationCompat.Action playPauseAction = new NotificationCompat.Action(
                icon,playPause,
                MediaButtonReceiver.buildMediaButtonPendingIntent(this,
                        PlaybackStateCompat.ACTION_PLAY_PAUSE)
        );
        PendingIntent contentPendingIntent = PendingIntent.getActivity(this,
                0, new Intent(this,PlayerActivity.class),0);
        // to select proper id

        notificationBuilder.setContentTitle("songname")


                .setSmallIcon(R.drawable.tryicon)
                .setContentIntent(contentPendingIntent)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .addAction(playPauseAction)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setStyle(new androidx.media.app.NotificationCompat.MediaStyle()
                        .setMediaSession(mediaSessionCompat.getSessionToken())
                        //select more than 1 notification
                        .setShowActionsInCompactView(0));


        notificationManager = (NotificationManager)
                getSystemService(NOTIFICATION_SERVICE);
        notificationManager.notify(0,notificationBuilder.build());
    }
    public static class MyReceiver extends BroadcastReceiver {

        public MyReceiver(){

        }
        @Override
        public void onReceive(Context context, Intent intent){
            MediaButtonReceiver.handleIntent(mediaSessionCompat, intent);
        }

    } */

//}


