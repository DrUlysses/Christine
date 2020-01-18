package player.christine.client;

import android.content.ContentResolver;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.PowerManager;
import android.provider.MediaStore;
import android.util.Pair;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;

final class Player {
    static MediaPlayer player;
    private static int position;
    private static int currentTrackNum;
    private static int currentTracksAmount;
    static String currentChosenArtist;
    private static LinkedHashMap<Integer, String> currentTrackSequence;
    static SortedMap<String, Pair<String, String>> currentSongs;
    static SortedMap<String, SortedMap<String, String>> currentArtistsSongs;
    private static SharedPreferences preferences;
    private static SharedPreferences.Editor preferencesEditor;
    private static Context currentContext;
    private static Uri currentRemoteSongPath;
    private static Uri nextRemoteSongPath;
    private static Uri previousRemoteSongPath;
    private static boolean isRemotePlaying;

    private Player() {
        preferences  = currentContext.getSharedPreferences("Preferences", Context.MODE_PRIVATE);
        currentSongs = new TreeMap<>();
        currentArtistsSongs = new TreeMap<>();
        setSongList(fillSongList());
        setArtistsSongsList(fillArtistsSongsList());
        position = 0;
        currentTrackNum = preferences.getInt("currentTrackNum", 0);
        currentTracksAmount = preferences.getInt("currentTracksAmount", 1);
        currentTrackSequence = new LinkedHashMap<>();
        Set<String> tempTrackSequence = preferences.getStringSet("currentTrackSequence", null);
        // TODO: can be beautified
        if (tempTrackSequence != null) {
            Iterator<String> iter = tempTrackSequence.iterator();
            for (int i = 0; i < tempTrackSequence.size(); i++)
                currentTrackSequence.put(i, iter.next());
        } else
            currentTrackSequence.put(0, "");
        player = new MediaPlayer();
    }

    public static void wakePlayer() {
        if (!currentTrackSequence.get(0).equals("")) {
            playCurrentSong();
            pause();
        }
    }

    private static void updateSavedPlaylist() {
        preferencesEditor = preferences.edit();
        preferencesEditor.putInt("currentTrackNum", currentTrackNum);
        preferencesEditor.putInt("currentTracksAmount", currentTracksAmount);
        Set<String> tempList = new HashSet<>(currentTrackSequence.values());
        preferencesEditor.putStringSet("currentTrackSequence", tempList);
        preferencesEditor.apply();
    }

    static void setCurrentContext(Context context) {
        currentContext = context;
        new Player();
    }

    private static void updateCurrentRemoteSong() {
        // Current next and prev. Just update all current
        if (preferences.getBoolean("isRemoteLibrary", false)) {
            try {
                currentRemoteSongPath = Uri.parse(new ServerPipeline.GetSong("current").execute().get());
                nextRemoteSongPath = Uri.parse(new ServerPipeline.GetSong("next").execute().get());
                previousRemoteSongPath = Uri.parse(new ServerPipeline.GetSong("previous").execute().get());
            } catch (InterruptedException | ExecutionException | IOException | CancellationException e) {
                e.printStackTrace();
                return;
            }
        }
    }

    private static void updateNextRemoteSong() {
        // Like go forward - download new next song
        if (preferences.getBoolean("isRemoteLibrary", false)) {
            try {
                previousRemoteSongPath = currentRemoteSongPath;
                currentRemoteSongPath = nextRemoteSongPath;
                nextRemoteSongPath = Uri.parse(new ServerPipeline.GetSong("next").execute().get());
            } catch (InterruptedException | ExecutionException | IOException | CancellationException e) {
                e.printStackTrace();
                return;
            }
        }
    }

    private static void updatePreviousRemoteSong() {
        // Like go backward - download new previous song
        if (preferences.getBoolean("isRemoteLibrary", false)) {
            try {
                nextRemoteSongPath = currentRemoteSongPath;
                currentRemoteSongPath = previousRemoteSongPath;
                previousRemoteSongPath = Uri.parse(new ServerPipeline.GetSong("previous").execute().get());
            } catch (InterruptedException | ExecutionException | IOException | CancellationException e) {
                e.printStackTrace();
                return;
            }
        }
    }

    private static void play(Uri songPath, Context context) {
        currentContext = context;
        String title = currentSongs.get(currentTrackSequence.get(currentTrackNum)).first;
        String artist = currentSongs.get(currentTrackSequence.get(currentTrackNum)).second;
        if (player != null) player.release();
        position = 0;
        player = MediaPlayer.create(currentContext, songPath);
        if (player != null) {
            player.setWakeMode(context, PowerManager.PARTIAL_WAKE_LOCK);
            setupPlayer();
            MainActivity.changePlayingSong(title, artist, songPath.toString());
            player.start();
           if (preferences.getBoolean("isRemoteLibrary", false))
               player.setVolume(0, 0);
        } else {
            Toast.makeText(context, R.string.player_error, Toast.LENGTH_LONG).show();
        }
    }

    private static void setupPlayer() {
        player.setOnCompletionListener(mp -> {
            if (currentTrackNum++ < currentTrackSequence.size()) {
                if (preferences.getBoolean("isRemoteLibrary", false)) {
                    updateNextRemoteSong();
                    play(currentRemoteSongPath, currentContext);
                } else
                    play(Uri.parse(currentTrackSequence.get(currentTrackNum)), currentContext);
            } else
                player.stop();
        });
    }

    static void playSongFromList(int position) {
        // get file name and send it to new view
        currentTrackNum = position;
        currentTracksAmount = currentSongs.size();
        // Oneline command is here too long, good old one is shorter
        currentTrackSequence.clear();
        List<String> songsPaths = new ArrayList<>(currentSongs.keySet());
        for (int i = 0; i < currentTracksAmount; i++)
            currentTrackSequence.put(i, songsPaths.get(i));
        updateSavedPlaylist();
        updateRemotePlaylist();
        playCurrentSong();
    }

    static void playSongFromArtistList(int position) {
        // get file name and send it to new view
        currentTrackNum = position;
        // Oneline command is here too long, good old one is shorter
        List<String> songsPaths = new ArrayList<>(currentArtistsSongs.get(currentChosenArtist).values());
        currentTracksAmount = songsPaths.size();
        currentTrackSequence.clear();
        for (int i = 0; i < currentTracksAmount; i++)
            currentTrackSequence.put(i, songsPaths.get(i));
        updateSavedPlaylist();
        updateRemotePlaylist();
        playCurrentSong();
    }

    private static void updateRemotePlaylist() {
        boolean isRemote = preferences.getBoolean("isRemoteLibrary", false);
        if (isRemote) {
            JSONObject temp = new JSONObject();
            try {
                temp.put("type", "playlist");
                JSONArray tempArr = new JSONArray(currentTrackSequence.values());
                temp.put("content", tempArr);
                temp.put("current_track_num", currentTrackNum);
                new ServerPipeline.SendList(temp.toString()).execute().get();
            } catch (JSONException | InterruptedException | ExecutionException | IOException | CancellationException e) {
                e.printStackTrace();
                player = null;
                return;
            }
        }
    }

    private static void playCurrentSong() {
        try {
            if (preferences.getBoolean("isRemoteLibrary", false)) {
                updateCurrentRemoteSong();
                play(currentRemoteSongPath, currentContext);
                resumeRemote();
            } else
                play(Uri.parse(currentTrackSequence.get(currentTrackNum)), currentContext);
        } catch (Exception e) {
            return;
        }
    }

    static void shuffleCurrentArtistSongs() {
        currentTrackNum = 0;
        String artist = currentChosenArtist;
        List<String> songsPaths = new ArrayList<>(currentArtistsSongs.get(artist).values());
        Collections.shuffle(songsPaths);
        currentTracksAmount = songsPaths.size();
        currentTrackSequence.clear();
        for (int i = currentTrackNum; i < currentTracksAmount; i++)
            currentTrackSequence.put(i, songsPaths.get(i));
        updateSavedPlaylist();
        updateRemotePlaylist();
        playCurrentSong();
    }

    static void shuffleSongs() {
        currentTrackNum = 0;
        List<String> songsPaths = new ArrayList<>(currentSongs.keySet());
        Collections.shuffle(songsPaths);
        currentTracksAmount = currentSongs.size();
        currentTrackSequence.clear();
        for (int i = currentTrackNum; i < currentTracksAmount; i++)
            currentTrackSequence.put(i, songsPaths.get(i));
        updateSavedPlaylist();
        updateRemotePlaylist();
        playCurrentSong();
    }

    static void setSongList(SortedMap<String, Pair<String, String>> songs) {
        currentTracksAmount = songs.size();
        currentSongs = songs;
    }

    static void setArtistsSongsList(SortedMap<String, SortedMap<String, String>> artistsSongs) {
        currentArtistsSongs = artistsSongs;
    }

    static SortedMap<String, Pair<String, String>> fillSongList() {
        ContentResolver contentResolver = currentContext.getContentResolver();
        Uri songUri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;
        Cursor cursor = contentResolver.query(songUri, null, null, null, null);
        // [path <title, artist>]
        SortedMap<String, Pair<String, String>> result = new TreeMap<>();

        if (cursor != null && cursor.moveToFirst()) {
            int songTitle = cursor.getColumnIndex(MediaStore.Audio.Media.TITLE);
            int songArtist = cursor.getColumnIndex(MediaStore.Audio.Media.ARTIST);
            int songLocation = cursor.getColumnIndex(MediaStore.Audio.Media.DATA);
            Pair<String, String> tempPair;
            String tempTitle;
            String tempArtist;
            do {
                tempTitle = cursor.getString(songTitle);
                tempArtist = cursor.getString(songArtist);
                tempPair = new Pair<>(tempTitle, tempArtist);
                result.put(cursor.getString(songLocation), tempPair);
            } while (cursor.moveToNext());
            cursor.close();
        }
        return result;
    }

    static SortedMap<String, SortedMap<String, String>> fillArtistsSongsList() {
        ContentResolver contentResolver = currentContext.getContentResolver();
        Uri songUri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;
        Cursor cursor = contentResolver.query(songUri, null, null, null, null);
        // [artist [title, path]]
        SortedMap<String, SortedMap<String, String>> result = new TreeMap<>();

        if (cursor != null && cursor.moveToFirst()) {
            int songTitle = cursor.getColumnIndex(MediaStore.Audio.Media.TITLE);
            int songArtist = cursor.getColumnIndex(MediaStore.Audio.Media.ARTIST);
            int songLocation = cursor.getColumnIndex(MediaStore.Audio.Media.DATA);
            String tempTitle;
            String tempArtist;
            do {
                tempTitle = cursor.getString(songTitle);
                tempArtist = cursor.getString(songArtist);
                if (result.containsKey(tempArtist)) {
                    result.get(tempArtist).put(tempTitle, cursor.getString(songLocation));
                } else {
                    SortedMap<String, String> tempMap = new TreeMap<>();
                    tempMap.put(tempTitle, cursor.getString(songLocation));
                    result.put(tempArtist, tempMap);
                }
            } while (cursor.moveToNext());
            cursor.close();
        }
        return result;
    }

    private static void nextRemoteSong() {
        MainActivity.socketSend("play_next");
    }

    private static void previousRemoteSong() {
        MainActivity.socketSend("play_previous");
    }

    private static void pauseRemote() {
        if (isRemotePlaying) MainActivity.socketSend("play_pause");
    }

    private static void resumeRemote() {
        if (!isRemotePlaying) MainActivity.socketSend("play_pause");
    }

    static void playPrevious() {
        if (currentTrackNum-- > 0) {
            if (preferences.getBoolean("isRemoteLibrary", false)) {
                previousRemoteSong();
                play(previousRemoteSongPath, currentContext);
                updatePreviousRemoteSong();
            } else
                play(Uri.parse(currentTrackSequence.get(currentTrackNum)), currentContext);
        } else {
            pause();
        }
        updateSavedPlaylist();
    }

    static void playNext() {
        if (currentTrackNum++ < currentTrackSequence.size()) {
            if (preferences.getBoolean("isRemoteLibrary", false)) {
                nextRemoteSong();
                play(nextRemoteSongPath, currentContext);
                updateNextRemoteSong();
            } else
                play(Uri.parse(currentTrackSequence.get(currentTrackNum)), currentContext);
        } else {
            pause();
        }
        updateSavedPlaylist();
    }

    static void pause() {
        try {
            if (player.isPlaying()) {
                boolean isRemote = preferences.getBoolean("isRemoteLibrary", false);
                if (isRemote) pauseRemote();
                player.pause();
                position = player.getCurrentPosition();
            }
        } catch (IllegalStateException | NullPointerException e) {
            player = null;
            return;
        }

    }

    static void resume() {
        try {
            if (!player.isPlaying()) {
                player.seekTo(position);
                player.start();
                boolean isRemote = preferences.getBoolean("isRemoteLibrary", false);
                if (isRemote) {
                    player.setVolume(0, 0);
                    resumeRemote();
                }
            }
        } catch (IllegalStateException | NullPointerException e) {
            player = null;
            return;
        }
    }

    static void setPosition(Integer pos) {
        position = pos;
    }

    static void setIsRemotePlaying(boolean val) {
        isRemotePlaying = val;
    }
}
