package player.christine.client;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.MediaMetadataRetriever;

import androidx.annotation.NonNull;
import com.google.android.material.tabs.TabLayout;
import android.support.v4.media.MediaBrowserCompat;
import android.support.v4.media.MediaMetadataCompat;
import android.support.v4.media.session.MediaControllerCompat;
import android.support.v4.media.session.MediaSessionCompat;
import android.support.v4.media.session.PlaybackStateCompat;

import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.media.MediaBrowserServiceCompat;
import androidx.viewpager.widget.ViewPager;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import android.os.Bundle;
import android.text.InputType;
import android.util.Pair;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import com.sothree.slidinguppanel.SlidingUpPanelLayout;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;

import io.socket.client.Socket;
import io.socket.emitter.Emitter;
import player.christine.client.adapters.ViewPagerAdapter;
import player.christine.client.misc.ServerPipeline;
import player.christine.client.misc.ServerSocketApplication;
import player.christine.client.musicplayer.MediaBrowserHelper;
import player.christine.client.musicplayer.MediaSeekBar;
import player.christine.client.musicplayer.MusicService;
import player.christine.client.musicplayer.Player;

public class MainActivity extends AppCompatActivity {

    private androidx.appcompat.widget.Toolbar toolbar;
    private ViewPager viewPager;
    private TabLayout tabLayout;
    private SharedPreferences preferences;
    private SharedPreferences.Editor preferencesEditor;

    private static SlidingUpPanelLayout playerLayout;
    private ConstraintLayout expandedLayout;
    private RelativeLayout minimizedLayout;
    private static ImageView coverView;
    private Button nextButton;
    private Button pauseButton;
    private Button previousButton;
    private TextView timeView;
    private static TextView songNameView;
    private static MediaSeekBar positionBar;
    private static TextView minimizedSongNameView;
    private static MediaSeekBar minimizedTimeBar;
    private static ImageView mininizedCoverView;
    private Button mininizedPauseButton;

    private static Socket mSocket;

    @Override
    public void onStart() {
        super.onStart();
        Player.mMediaBrowserHelper.onStart();
    }

    @Override
    public void onStop() {
        super.onStop();
        Player.mMediaBrowserHelper.onStop();
        positionBar.disconnectController();
    }

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

        setupNextButton();
        setupPreviousButton();

        pauseButton.setOnClickListener(setupPause());
        mininizedPauseButton.setOnClickListener(setupPause());

        boolean isRemote = preferences.getBoolean("isRemoteLibrary", false);
        if (isRemote) updateRemoteLists();
        Player.mMediaBrowserHelper = new MediaBrowserConnection(this);
        Player.mMediaBrowserHelper.registerCallback(new MediaBrowserListener());
        Player.wakePlayer();
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        MenuItem item = findViewById(R.id.play_on_server_checkbox);
        boolean isRemote = preferences.getBoolean("isRemoteLibrary", false);
        if (item != null)
            item.setVisible(isRemote);
        return super.onPrepareOptionsMenu(menu);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.options_menu, menu);
        boolean isRemote = preferences.getBoolean("isRemoteLibrary", false);
        if (isRemote) {
            menu.findItem(R.id.play_on_server_checkbox).setVisible(true);
            if (!mSocket.connected()) {
                initializeSocket();
                mSocket.connect();
            }
        } else
            menu.findItem(R.id.play_on_server_checkbox).setVisible(false);
        menu.findItem(R.id.remote_library_checkbox).setChecked(isRemote);
        return super.onCreateOptionsMenu(menu);
    }

    @SuppressLint("NonConstantResourceId")
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
                } else
                    mSocket.disconnect();
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
            case R.id.check_music_folder:
                if (mSocket.connected())
                    mSocket.emit("check_music_folder");
                return true;
            case R.id.play_on_server_checkbox:
                if (mSocket.connected())
                    if (item.isChecked()) {
                        mSocket.emit("unmute_server");
                        Player.setMuted(false);
                    } else {
                        mSocket.emit("mute_server");
                        Player.setMuted(true);
                    }
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
        mSocket.on("new_ones", onNewSongsAdded);
        mSocket.on("play_on", onPlayOn);
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
        if (!updateRemoteLists())
            mSocket.disconnect();
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

    public static void socketSend(String message) {
        mSocket.emit(message);
    }

    private Emitter.Listener onConnectError = args -> runOnUiThread(() -> {
        Toast.makeText(getApplicationContext(), R.string.error_connecting, Toast.LENGTH_LONG).show();
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

    private Emitter.Listener onStatus = args -> runOnUiThread(() -> {
        JSONObject data = (JSONObject) args[0];
        String status;
        Integer time;
        Boolean isOnClient;
        try {
            time = data.getInt("current_time");
            Player.mMediaBrowserHelper.getTransportControls().seekTo(time);
            status = data.getString("status");
            if (status.equals("true")) {
                status = "playing";
                Player.setIsRemotePlaying(true);
                Player.mMediaBrowserHelper.getTransportControls().play();
            } else if (status.equals("false")) {
                status = "paused";
                Player.setIsRemotePlaying(false);
                Player.mMediaBrowserHelper.getTransportControls().pause();
            }
            isOnClient = data.getBoolean("is_on_client");
            Player.setMuted(isOnClient);
            Toast.makeText(getApplicationContext(), status, Toast.LENGTH_SHORT).show();
        } catch (JSONException e) {
            Toast.makeText(getApplicationContext(), e.getMessage(), Toast.LENGTH_SHORT).show();
            mSocket.disconnect();
        }
    });

    private Emitter.Listener onNewSongsAdded = args -> runOnUiThread(() -> {
        JSONObject data = (JSONObject) args[0];
        Integer newOnes;
        try {
            newOnes = data.getInt("new_ones");
            Toast.makeText(getApplicationContext(), "Added new songs ("  + newOnes + ")", Toast.LENGTH_SHORT).show();
        } catch (JSONException e) {
            Toast.makeText(getApplicationContext(), e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    });

    private Emitter.Listener onPlayOn = args -> runOnUiThread(() -> {
        JSONObject data = (JSONObject) args[0];
        Boolean isOnClient;
        try {
            isOnClient = data.getBoolean("is_on_client");
            Toast.makeText(getApplicationContext(), "Playing on client is "  + isOnClient, Toast.LENGTH_SHORT).show();
        } catch (JSONException e) {
            Toast.makeText(getApplicationContext(), e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    });

    private boolean updateRemoteLists() {
        JSONArray JSONStorage;
        try {
            JSONStorage = new JSONArray(new ServerPipeline.BecomeList("songs").execute().get());
        } catch (JSONException | InterruptedException | ExecutionException | IOException | CancellationException e) {
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
        previousButton.setOnClickListener(v -> Player.mMediaBrowserHelper.getTransportControls().skipToPrevious());
    }

    private void setupNextButton() {
        nextButton.setOnClickListener(v -> Player.mMediaBrowserHelper.getTransportControls().skipToNext());
    }

    private View.OnClickListener setupPause() {
        return v -> {
            if (Player.isPlaying()) {
                Player.mMediaBrowserHelper.getTransportControls().pause();
                pauseButton.setText(R.string.play);
                mininizedPauseButton.setText(R.string.play);
            } else {
                Player.mMediaBrowserHelper.getTransportControls().play();
                pauseButton.setText(R.string.pause);
                mininizedPauseButton.setText(R.string.pause);
            }
        };
    }

    public static void changePlayingSong(String title, String artist, Bitmap bitmap) {
        String tempName = title + " - " + artist;

        songNameView.setText(tempName);
        minimizedSongNameView.setText(tempName);

        coverView.setImageBitmap(bitmap);
        mininizedCoverView.setImageBitmap(bitmap);
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

    /**
     * Customize the connection to our {@link MediaBrowserServiceCompat}
     * and implement our app specific desires.
     */
    private class MediaBrowserConnection extends MediaBrowserHelper {
        private MediaBrowserConnection(Context context) {
            super(context, MusicService.class);
        }

        @Override
        protected void onConnected(@NonNull MediaControllerCompat mediaController) {
            positionBar.setMediaController(mediaController);
            minimizedTimeBar.setMediaController(mediaController);
        }

        @Override
        protected void onChildrenLoaded(@NonNull String parentId,
                                        @NonNull List<MediaBrowserCompat.MediaItem> children) {
            super.onChildrenLoaded(parentId, children);

            final MediaControllerCompat mediaController = getMediaController();

            // Queue up all media items for this simple sample.
            for (final MediaBrowserCompat.MediaItem mediaItem : children) {
                mediaController.addQueueItem(mediaItem.getDescription());
            }

            // Call prepare now so pressing play just works.
            mediaController.getTransportControls().prepare();
        }
    }

    /**
     * Implementation of the {@link MediaControllerCompat.Callback} methods we're interested in.
     * <p>
     * Here would also be where one could override
     * {@code onQueueChanged(List<MediaSessionCompat.QueueItem> queue)} to get informed when items
     * are added or removed from the queue. We don't do this here in order to keep the UI
     * simple.
     */
    private class MediaBrowserListener extends MediaControllerCompat.Callback {
        @Override
        public void onPlaybackStateChanged(PlaybackStateCompat playbackState) {
            if (playbackState == null)
                return;
            else if (playbackState.getState() == PlaybackStateCompat.STATE_PLAYING) {
                Player.mMediaBrowserHelper.getTransportControls().play();
                pauseButton.setText(R.string.pause);
                mininizedPauseButton.setText(R.string.pause);
            } else if (playbackState.getState() == PlaybackStateCompat.STATE_PAUSED) {
                Player.mMediaBrowserHelper.getTransportControls().pause();
                pauseButton.setText(R.string.play);
                mininizedPauseButton.setText(R.string.play);
            }
        }

        @Override
        public void onMetadataChanged(MediaMetadataCompat mediaMetadata) {
            if (mediaMetadata == null)
                return;
            String tempName = mediaMetadata.getString(MediaMetadataCompat.METADATA_KEY_TITLE) +
                    " - " + mediaMetadata.getString(MediaMetadataCompat.METADATA_KEY_ARTIST);
            if (tempName.equals(Player.getCurrentSongName()))
                return;
            songNameView.setText(tempName);
            minimizedSongNameView.setText(tempName);
            Bitmap bitmap = Player.extractAlbumArt(
                    mediaMetadata.getString(MediaMetadataCompat.METADATA_KEY_MEDIA_URI),
                    MainActivity.this);
            coverView.setImageBitmap(bitmap);
            mininizedCoverView.setImageBitmap(bitmap);
        }

        @Override
        public void onSessionDestroyed() {
            super.onSessionDestroyed();
        }

        @Override
        public void onQueueChanged(List<MediaSessionCompat.QueueItem> queue) {
            super.onQueueChanged(queue);
        }
    }
}
