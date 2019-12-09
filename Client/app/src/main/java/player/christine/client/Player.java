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

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.concurrent.ExecutionException;

final class Player {
    static MediaPlayer player;
    private static int position;
    private static int currentTrackNum;
    private static int currentTrackIndex;
    private static int currentTracksAmount;
    static String currentChosenArtist;
    private static LinkedHashMap<Integer, String> currentTrackSequence;
    static HashMap<String, Pair<String, String>> currentSongs;
    static HashMap<String, HashMap<String, String>> currentArtistsSongs;
    private static SharedPreferences preferences;
    private static Context currentContext;

    private Player() {
        position = 0;
        currentTrackNum = 1;
        currentTrackIndex = 0;
        currentTracksAmount = 1;
        currentTrackSequence = new LinkedHashMap<>();
        currentTrackSequence.put(0, "");
        currentSongs = new HashMap<>();
        currentArtistsSongs = new HashMap<>();

        player = new MediaPlayer();
        setSongList(fillSongList());
        setArtistsSongsList(fillArtistsSongsList());
        preferences  = currentContext.getSharedPreferences("Preferences", Context.MODE_PRIVATE);
    }

    static void setCurrentContext(Context context) {
        currentContext = context;
        new Player();
    }

    private static void play(Uri songPath, Context context) {
        currentContext = context;
        String title = currentSongs.get(songPath.toString()).first;
        String artist = currentSongs.get(songPath.toString()).second;
        if (player != null) {
            player.release();
        }
        boolean isRemote = preferences.getBoolean("isRemoteLibrary", false);
        if (isRemote) {
            try {
                songPath = Uri.parse(new ServerPipeline.GetSong("current").execute().get());
            } catch (InterruptedException | ExecutionException | IOException e) {
                e.printStackTrace();
            }
        }

        player = MediaPlayer.create(currentContext, songPath);
        player.setWakeMode(context, PowerManager.PARTIAL_WAKE_LOCK);
        setupPlayer();
        MainActivity.changePlayingSong(title, artist, songPath.toString());
        player.start();
    }

    private static void setupPlayer() {
        player.setOnCompletionListener(mp -> {
            if (currentTrackIndex < currentTrackSequence.size() - 1) {
                currentTrackNum = (int) currentTrackSequence.keySet().toArray()[++currentTrackIndex];
                boolean isRemote = preferences.getBoolean("isRemoteLibrary", false);
                if (isRemote) nextRemoteSong();
                play(Uri.parse(currentTrackSequence.get(currentTrackNum)), currentContext);
            } else {
                player.stop();
            }
        });
    }

    static void playSongFromList(int position) {
        // get file name and send it to new view
        currentTrackNum = position;
        // Oneline command is here too long, good old one is shorter
        currentTrackSequence.clear();
        List<String> songsPaths = new ArrayList<>(currentSongs.keySet());
        for (int i = currentTrackNum; i < currentTracksAmount; i++) {
            currentTrackSequence.put(i, songsPaths.get(i));
        }
        playCurrentSong();
    }

    static void playSongFromArtistList(int position) {
        // get file name and send it to new view
        currentTrackNum = position;
        currentTracksAmount = currentArtistsSongs.get(currentChosenArtist).size();
        // Oneline command is here too long, good old one is shorter
        List<String> songsPaths = new ArrayList<>(currentArtistsSongs.get(currentChosenArtist).values());
        currentTracksAmount = songsPaths.size();
        currentTrackSequence.clear();
        for (int i = currentTrackNum; i < currentTracksAmount; i++) {
            currentTrackSequence.put(i, songsPaths.get(i));
        }
        playCurrentSong();
    }

    private static void playCurrentSong() {
        boolean isRemote = preferences.getBoolean("isRemoteLibrary", false);
        if (isRemote) {
            JSONObject temp = new JSONObject();
            try {
                temp.put("type", "playlist");
                JSONArray tempArr = new JSONArray(currentTrackSequence.values());
                temp.put("content", tempArr);
                new ServerPipeline.SendList(temp.toString()).execute().get();
            } catch (JSONException | InterruptedException | ExecutionException | IOException e) {
                e.printStackTrace();
            }
        }
        play(Uri.parse(currentTrackSequence.get(currentTrackNum)), currentContext);
    }

    static void shuffleCurrentArtistSongs() {
        currentTrackNum = 0;
        String artist = currentChosenArtist;
        List<String> songsPaths = new ArrayList<>(currentArtistsSongs.get(artist).values());
        currentTracksAmount = songsPaths.size();
        currentTrackSequence.clear();
        List<Integer> indexes = new ArrayList<>();
        for (int i = currentTrackNum; i < currentTracksAmount; i++) {
            indexes.add(i);
        }
        Collections.shuffle(indexes);
        for (Integer i : indexes) {
            currentTrackSequence.put(i, songsPaths.get(i));
        }
        currentTrackIndex = 0;
        currentTrackNum = (int) currentTrackSequence.keySet().toArray()[Player.currentTrackIndex];
        // get file name and send it to new view
        String tempTitle = currentSongs.get(currentTrackSequence.get(currentTrackNum)).first;
        String tempArtist = currentChosenArtist;
        Uri tempPath = Uri.parse(currentArtistsSongs.get(tempArtist).get(tempTitle));
        play(tempPath, currentContext);
    }

    static void shuffleSongs() {
        currentTrackNum = 0;
        List<String> songsPaths = new ArrayList<>(currentSongs.keySet());
        currentTracksAmount = currentSongs.size();
        currentTrackSequence.clear();
        List<Integer> indexes = new ArrayList<>();
        for (int i = currentTrackNum; i < currentTracksAmount; i++) {
            indexes.add(i);
        }
        Collections.shuffle(indexes);
        for (Integer i : indexes) {
            currentTrackSequence.put(i, songsPaths.get(i));
        }
        currentTrackIndex = 0;
        currentTrackNum = (int) currentTrackSequence.keySet().toArray()[currentTrackIndex];
        play(Uri.parse(currentTrackSequence.get(currentTrackNum)), currentContext);
    }

    static void setSongList(HashMap<String, Pair<String, String>> songs) {
        currentTracksAmount = songs.size();
        currentSongs = songs;
    }

    static void setArtistsSongsList(HashMap<String, HashMap<String, String>> artistsSongs) {
        currentArtistsSongs = artistsSongs;
    }

    static HashMap<String, Pair<String, String>> fillSongList() {
        ContentResolver contentResolver = currentContext.getContentResolver();
        Uri songUri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;
        Cursor cursor = contentResolver.query(songUri, null, null, null, null);
        HashMap<String, Pair<String, String>> result = new HashMap<>();

        if (cursor != null && cursor.moveToFirst()) {
            int songTitle = cursor.getColumnIndex(MediaStore.Audio.Media.TITLE);
            int songArtist = cursor.getColumnIndex(MediaStore.Audio.Media.ARTIST);
            int songLocation = cursor.getColumnIndex(MediaStore.Audio.Media.DATA);
            Pair<String, String> tempPair;
            do {
                tempPair = new Pair<>(cursor.getString(songTitle), cursor.getString(songArtist));
                result.put(cursor.getString(songLocation), tempPair);
            } while (cursor.moveToNext());

            cursor.close();
        }
        return result;
    }

    static HashMap<String, HashMap<String, String>> fillArtistsSongsList() {
        ContentResolver contentResolver = currentContext.getContentResolver();
        Uri songUri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;
        Cursor cursor = contentResolver.query(songUri, null, null, null, null);
        HashMap<String, HashMap<String, String>> result = new HashMap<>();

        if (cursor != null && cursor.moveToFirst()) {
            int songTitle = cursor.getColumnIndex(MediaStore.Audio.Media.TITLE);
            int songArtist = cursor.getColumnIndex(MediaStore.Audio.Media.ARTIST);
            int songLocation = cursor.getColumnIndex(MediaStore.Audio.Media.DATA);
            do {
                if (result.containsKey(cursor.getString(songArtist))) {
                    result.get(cursor.getString(songArtist)).put(cursor.getString(songTitle), cursor.getString(songLocation));
                } else {
                    HashMap<String, String> tempMap = new HashMap<>();
                    tempMap.put(cursor.getString(songTitle), cursor.getString(songLocation));
                    result.put(cursor.getString(songArtist), tempMap);
                }
            } while (cursor.moveToNext());

            cursor.close();
        }
        return result;
    }

    private static void nextRemoteSong() {

    }

    private static void previousRemoteSong() {

    }

    private static void pauseRemote() {

    }

    private static void resumeRemote() {

    }

    static void playPrevious() {
        if (currentTrackIndex > 0) {
            currentTrackNum = (int) currentTrackSequence.keySet().toArray()[--currentTrackIndex];
            boolean isRemote = preferences.getBoolean("isRemoteLibrary", false);
            if (isRemote) previousRemoteSong();
            play(Uri.parse(currentTrackSequence.get(currentTrackNum)), currentContext);
        } else {
            pause();
        }
    }

    static void playNext() {
        if (currentTrackIndex < currentTrackSequence.size() - 1) {
            currentTrackNum = (int) currentTrackSequence.keySet().toArray()[++currentTrackIndex];
            boolean isRemote = preferences.getBoolean("isRemoteLibrary", false);
            if (isRemote) nextRemoteSong();
            play(Uri.parse(currentTrackSequence.get(currentTrackNum)), currentContext);
        } else {
            pause();
        }
    }

    static void pause() {
        player.pause();
        position = player.getCurrentPosition();
        boolean isRemote = preferences.getBoolean("isRemoteLibrary", false);
        if (isRemote) pauseRemote();
    }

    static void resume() {
        player.seekTo(position);
        player.start();
        boolean isRemote = preferences.getBoolean("isRemoteLibrary", false);
        if (isRemote) resumeRemote();
    }
}
