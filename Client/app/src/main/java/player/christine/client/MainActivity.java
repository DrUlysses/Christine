package player.christine.client;

import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.MediaMetadataRetriever;
import android.os.Handler;
import android.support.design.widget.TabLayout;
import android.support.v4.view.ViewPager;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.text.InputType;
import android.util.Pair;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import com.sothree.slidinguppanel.SlidingUpPanelLayout;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.SortedMap;
import java.util.TimeZone;
import java.util.TreeMap;
import java.util.concurrent.ExecutionException;

import io.socket.client.Socket;
import io.socket.emitter.Emitter;

public class MainActivity extends AppCompatActivity {

    private android.support.v7.widget.Toolbar toolbar;
    private ViewPager viewPager;
    private TabLayout tabLayout;
    private SharedPreferences preferences;
    private SharedPreferences.Editor preferencesEditor;

    private static SlidingUpPanelLayout playerLayout;
    private android.support.constraint.ConstraintLayout expandedLayout;
    private RelativeLayout minimizedLayout;
    private static ImageView coverView;
    private Button nextButton;
    private Button pauseButton;
    private Button previousButton;
    private TextView timeView;
    private static TextView songNameView;
    private static SeekBar positionBar;
    private static TextView minimizedSongNameView;
    private static ProgressBar minimizedTimeBar;
    private static ImageView mininizedCoverView;
    private Button mininizedPauseButton;

    private Socket mSocket;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        ServerPipeline.setCurrentContext(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);

        initializeVariables();

        setSupportActionBar(toolbar);

        viewPager.setAdapter(new ViewPagerAdapter(this));

        tabLayout.setupWithViewPager(viewPager);

        playerLayout.setPanelState(SlidingUpPanelLayout.PanelState.HIDDEN);
        setupPanel();

        setupPositionBars();
        setupNextButton();
        setupPreviousButton();

        pauseButton.setOnClickListener(setupPause());
        mininizedPauseButton.setOnClickListener(setupPause());

        Player.wakePlayer();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.options_menu, menu);
        boolean isRemote = preferences.getBoolean("isRemoteLibrary", false);
        if (isRemote && !mSocket.connected()) {
            initializeSocket();
            mSocket.connect();
        }
        menu.findItem(R.id.remote_library_checkbox).setChecked(isRemote);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.add_song_option:
                startActivity(new Intent(MainActivity.this, AddSongActivity.class));
                return true;
            case R.id.start_sorting_option:
                startActivity(new Intent(MainActivity.this, ManageUnsortedActivity.class));
                return true;
            case R.id.remote_library_checkbox:
                boolean isChecked = !item.isChecked();
                item.setChecked(isChecked);
                if (isChecked) {
                    initializeSocket();
                    mSocket.connect();
                } else {
                    mSocket.disconnect();
                }
                return true;
            case R.id.set_server_ip_option:
                AlertDialog.Builder builder = new AlertDialog.Builder(this);
                builder.setTitle(R.string.enter_server_address);

                final EditText input = new EditText(this);
                input.setInputType(InputType.TYPE_CLASS_TEXT);
                String tempIP = preferences.getString("remoteIP", "127.0.0.1:5000");
                input.setHint(tempIP);
                builder.setView(input);

                builder.setPositiveButton("OK", (dialog, which) -> {
                    preferencesEditor = preferences.edit();
                    preferencesEditor.putString("remoteIP", input.getText().toString());
                    preferencesEditor.apply();
                    initializeSocket();
                });
                builder.setNegativeButton("Cancel", (dialog, which) -> dialog.cancel());

                builder.show();

                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    private void initializeSocket() {
        ServerSocketApplication serverSocketApp = (ServerSocketApplication) getApplication();
        mSocket = serverSocketApp.resetSocket();
        mSocket.on(Socket.EVENT_CONNECT, onConnect);
        mSocket.on(Socket.EVENT_DISCONNECT, onDisconnect);
        mSocket.on(Socket.EVENT_CONNECT_ERROR, onConnectError);
        mSocket.on(Socket.EVENT_CONNECT_TIMEOUT, onConnectError);
        mSocket.on("status", onStatus);
    }

    private void initializeVariables() {
        initializeSocket();
        toolbar = findViewById(R.id.main_toolbar);
        viewPager = findViewById(R.id.main_layout_pager);
        preferences = getSharedPreferences("Preferences", MODE_PRIVATE);
        tabLayout = findViewById(R.id.main_tab_layout);

        Player.setCurrentContext(this);

        playerLayout = findViewById(R.id.main_layout);
        coverView = findViewById(R.id.player_song_cover_image);
        nextButton = findViewById(R.id.player_next_button);
        pauseButton = findViewById(R.id.player_pause_button);
        previousButton = findViewById(R.id.player_previous_button);
        timeView = findViewById(R.id.player_seek_bar_time_view);
        songNameView = findViewById(R.id.player_song_name_view);
        positionBar = findViewById(R.id.player_song_seek_bar);
        expandedLayout = findViewById(R.id.player_main_layout);
        minimizedSongNameView = findViewById(R.id.player_minimized_name);
        minimizedTimeBar = findViewById(R.id.player_minimized_time);
        mininizedCoverView = findViewById(R.id.player_minimized_cover);
        mininizedPauseButton = findViewById(R.id.player_minimized_pause_button);
        minimizedLayout = findViewById(R.id.player_minimized_layout);
    }

    private Emitter.Listener onConnect = args -> runOnUiThread(() -> {
        preferencesEditor = preferences.edit();
        preferencesEditor.putBoolean("isRemoteLibrary", true);
        preferencesEditor.apply();
        invalidateOptionsMenu();
        if (!updateRemoteLists()) {
            mSocket.disconnect();
            return;
        }
        Toast.makeText(getApplicationContext(), R.string.connected, Toast.LENGTH_LONG).show();
        viewPager.setAdapter(new ViewPagerAdapter(this));
        tabLayout.setupWithViewPager(viewPager);
        mSocket.emit("get_status");
    });

    private Emitter.Listener onDisconnect = args -> runOnUiThread(() -> {
        preferencesEditor = preferences.edit();
        preferencesEditor.putBoolean("isRemoteLibrary", false);
        preferencesEditor.apply();
        invalidateOptionsMenu();
        Toast.makeText(getApplicationContext(), R.string.disconnected, Toast.LENGTH_LONG).show();
        Player.setSongList(Player.fillSongList());
        Player.setArtistsSongsList(Player.fillArtistsSongsList());
        viewPager.setAdapter(new ViewPagerAdapter(this));
        tabLayout.setupWithViewPager(viewPager);
    });

    private Emitter.Listener onConnectError = args -> runOnUiThread(() -> {
        preferencesEditor = preferences.edit();
        preferencesEditor.putBoolean("isRemoteLibrary", false);
        preferencesEditor.apply();
        invalidateOptionsMenu();
        Toast.makeText(getApplicationContext(), R.string.error_connecting, Toast.LENGTH_LONG).show();
        Player.setSongList(Player.fillSongList());
        Player.setArtistsSongsList(Player.fillArtistsSongsList());
        viewPager.setAdapter(new ViewPagerAdapter(this));
        tabLayout.setupWithViewPager(viewPager);
    });

    private Emitter.Listener onStatus = args -> runOnUiThread(() -> {
        JSONObject data = (JSONObject) args[0];
        String status;
        try {
            status = data.getString("status");
            Toast.makeText(getApplicationContext(), status, Toast.LENGTH_LONG).show();
        } catch (JSONException e) {
            Toast.makeText(getApplicationContext(), e.getMessage(), Toast.LENGTH_LONG).show();
            mSocket.disconnect();
        }
    });

    private boolean updateRemoteLists() {
        JSONArray JSONStorage;
        try {
            JSONStorage = new JSONArray(new ServerPipeline.BecomeList("songs").execute().get());
        } catch (JSONException | InterruptedException | ExecutionException | IOException e) {
            e.printStackTrace();
            return false;
        }
        SortedMap<String, Pair<String, String>> songList = new TreeMap<>();
        SortedMap<String, SortedMap<String, String>> artistsSongsList = new TreeMap<>();
        Pair<String, String> tempPair;
        for (int i = 0; i < JSONStorage.length(); i++) {
            try {
                JSONArray tempObject = JSONStorage.getJSONArray(i);
                String songTitle = (String) tempObject.get(1);
                String songArtist = (String) tempObject.get(2);
                String songLocation = (String) tempObject.get(0);
                tempPair = new Pair<>(songTitle, songArtist);
                songList.put(songLocation, tempPair);

                if (artistsSongsList.containsKey(songArtist)) {
                    artistsSongsList.get(songArtist).put(songTitle, songLocation);
                } else {
                    SortedMap<String, String> tempMap = new TreeMap<>();
                    tempMap.put(songTitle, songLocation);
                    artistsSongsList.put(songArtist, tempMap);
                }
            } catch (JSONException e) {
                e.printStackTrace();
                return false;
            }
        }
        Player.setSongList(songList);
        Player.setArtistsSongsList(artistsSongsList);
        return true;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        mSocket.disconnect();
        mSocket.off(Socket.EVENT_CONNECT, onConnect);
        mSocket.off(Socket.EVENT_DISCONNECT, onDisconnect);
        mSocket.off(Socket.EVENT_CONNECT_ERROR, onConnectError);
        mSocket.off(Socket.EVENT_CONNECT_TIMEOUT, onConnectError);
        mSocket.off("status", onStatus);
    }

    private void setupPreviousButton() {
        previousButton.setOnClickListener(v -> Player.playPrevious());
    }

    private void setupNextButton() {
        nextButton.setOnClickListener(v -> Player.playNext());
    }

    private View.OnClickListener setupPause() {
        return v -> {
            if (Player.player.isPlaying()) {
                Player.pause();
                pauseButton.setText(R.string.play);
                mininizedPauseButton.setText(R.string.play);
            } else {
                Player.resume();
                pauseButton.setText(R.string.pause);
                mininizedPauseButton.setText(R.string.pause);
            }
        };
    }

    private void setupPositionBars() {
        Handler mHandler = new Handler();
        this.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                DateFormat formatter = new SimpleDateFormat("HH:mm:ss", Locale.US);
                formatter.setTimeZone(TimeZone.getTimeZone("UTC"));
                if(Player.player != null){
                    int milliseconds = Player.player.getCurrentPosition();
                    int mCurrentPosition = milliseconds / 1000;
                    positionBar.setProgress(mCurrentPosition);
                    minimizedTimeBar.setProgress(mCurrentPosition);
                    timeView.setText(formatter.format(new Date(milliseconds)));
                }
                mHandler.postDelayed(this, 1000);
            }
        });

        positionBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (Player.player != null && fromUser) {
                    Player.player.seekTo(progress * 1000);
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {}
        });
    }

    public static void changePlayingSong(String title, String artist, String path) {
        String tempName = title + " - " + artist;

        songNameView.setText(tempName);
        minimizedSongNameView.setText(tempName);

        positionBar.setMax(Player.player.getDuration() / 1000);
        minimizedTimeBar.setMax(Player.player.getDuration() / 1000);

        android.media.MediaMetadataRetriever mmr = new MediaMetadataRetriever();
        mmr.setDataSource(path);
        byte[] data = mmr.getEmbeddedPicture();
        if (data != null) {
            Bitmap bitmap = BitmapFactory.decodeByteArray(data, 0, data.length);
            coverView.setImageBitmap(bitmap); //associated cover art in bitmap
            mininizedCoverView.setImageBitmap(bitmap);
        } else {
            coverView.setImageDrawable(null);
            mininizedCoverView.setImageDrawable(null);
        }
        playerLayout.setPanelState(SlidingUpPanelLayout.PanelState.EXPANDED);
    }

    private void setupPanel() {
        playerLayout.addPanelSlideListener(new SlidingUpPanelLayout.PanelSlideListener() {
            @Override
            public void onPanelSlide(View panel, float slideOffset) {

            }

            @Override
            public void onPanelStateChanged(View panel, SlidingUpPanelLayout.PanelState previousState, SlidingUpPanelLayout.PanelState newState) {
                if (newState.name().equalsIgnoreCase("Collapsed")) {
                    minimizedLayout.setVisibility(View.VISIBLE);
                    expandedLayout.setVisibility(View.GONE);
                } else if (newState.name().equalsIgnoreCase("Expanded")) {
                    minimizedLayout.setVisibility(View.GONE);
                    expandedLayout.setVisibility(View.VISIBLE);
                }
            }
        });
    }
}
