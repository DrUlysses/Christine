package player.christine.client.musicplayer;

import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.AsyncTask;
import android.provider.MediaStore;
import android.support.v4.media.MediaBrowserCompat;
import android.support.v4.media.MediaDescriptionCompat;
import android.support.v4.media.MediaMetadataCompat;
import android.support.v4.media.session.MediaControllerCompat;
import android.support.v4.media.session.MediaSessionCompat;
import android.util.Pair;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import player.christine.client.MainActivity;
import player.christine.client.R;
import player.christine.client.misc.ServerPipeline;

public final class Player {
    private static int currentTrackNum;
    private static int currentTracksAmount;
    public static String currentChosenArtist;
    public static LinkedHashMap<Integer, String> currentTrackSequence;
    public static SortedMap<String, Pair<String, String>> currentSongs; // [path <title, artist>]
    public static SortedMap<String, SortedMap<String, String>> currentArtistsSongs;
    private static final HashMap<String, String> albumRes = new HashMap<>(); // [id (path), album]
    private static SharedPreferences preferences;
    private static SharedPreferences.Editor preferencesEditor;
    private static Context currentContext;
    private static Uri currentRemoteSongPath;
    private static Uri nextRemoteSongPath;
    private static Uri previousRemoteSongPath;
    private static boolean isRemotePlaying;
    private static boolean isPlaying;
    public static MediaBrowserHelper mMediaBrowserHelper;

    private Player() {
        preferences  = currentContext.getSharedPreferences("Preferences", Context.MODE_PRIVATE);
        currentSongs = new TreeMap<>();
        currentArtistsSongs = new TreeMap<>();
        setSongList(fillSongList());
        setArtistsSongsList(fillArtistsSongsList());
        isPlaying = false;
        currentTrackNum = preferences.getInt("currentTrackNum", 0);
        currentTracksAmount = preferences.getInt("currentTracksAmount", 1);
        currentTrackSequence = new LinkedHashMap<>();
        Set<String> tempTrackSequence = preferences.getStringSet("currentTrackSequence", null);
        // TODO: can be beautified
        if (tempTrackSequence != null) {
            Iterator<String> it = tempTrackSequence.iterator();
            for (int i = 0; i < tempTrackSequence.size(); i++)
                currentTrackSequence.put(i, it.next());
        } else
            currentTrackSequence.put(0, "");
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
        //mMediaBrowserHelper.getMediaController().addQueueItem();
    }

    public static void setCurrentContext(Context context) {
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
            }
        }
    }

    private static void play(Uri songPath, Context context) {
        currentContext = context;
        String title = currentSongs.get(currentTrackSequence.get(currentTrackNum)).first;
        String artist = currentSongs.get(currentTrackSequence.get(currentTrackNum)).second;
        MainActivity.changePlayingSong(title, artist, extractAlbumArt(songPath.toString(), context));
        isPlaying = true;
        if (preferences.getBoolean("isRemoteLibrary", false))
            mMediaBrowserHelper.getMediaController().setVolumeTo(0, (int) mMediaBrowserHelper.getMediaController().getFlags());
    }

    public static void setMuted(boolean state) {
        if (state)
            mMediaBrowserHelper.getMediaController().setVolumeTo(0, (int) mMediaBrowserHelper.getMediaController().getFlags());
        else
            mMediaBrowserHelper.getMediaController().setVolumeTo(100, (int) mMediaBrowserHelper.getMediaController().getFlags());
    }

    public static void playSongFromList(int position) {
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
        mMediaBrowserHelper.getTransportControls().play();
    }

    public static void playSongFromArtistList(int position) {
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
        mMediaBrowserHelper.getTransportControls().play();
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
            }
        }
    }

    public static void playCurrentSong() {
        try {
            if (preferences.getBoolean("isRemoteLibrary", false)) {
                updateCurrentRemoteSong();
                play(currentRemoteSongPath, currentContext);
                resumeRemote();
            } else
                play(Uri.parse(currentTrackSequence.get(currentTrackNum)), currentContext);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void shuffleCurrentArtistSongs() {
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
        mMediaBrowserHelper.getTransportControls().play();
    }

    public static void shuffleSongs() {
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
        mMediaBrowserHelper.getTransportControls().play();
    }

    public static void setSongList(SortedMap<String, Pair<String, String>> songs) {
        currentTracksAmount = songs.size();
        currentSongs = songs;
        updateCurrentRemoteSong();
        // get file name and send it to new view
        currentTrackNum = 0;
        // Oneline command is here too long, good old one is shorter
        List<String> songsPaths = new ArrayList<>(currentSongs.keySet());
        Collections.shuffle(songsPaths);
        currentTracksAmount = currentSongs.size();
        if (currentTrackSequence == null)
            currentTrackSequence = new LinkedHashMap<>();
        currentTrackSequence.clear();
        for (int i = currentTrackNum; i < currentTracksAmount; i++)
            currentTrackSequence.put(i, songsPaths.get(i));
    }

    public static void setArtistsSongsList(SortedMap<String, SortedMap<String, String>> artistsSongs) {
        currentArtistsSongs = artistsSongs;
    }

    public static SortedMap<String, Pair<String, String>> fillSongList() {
        ContentResolver contentResolver = currentContext.getContentResolver();
        Uri songsUri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;
        Cursor cursor = contentResolver.query(songsUri, null, null, null, null);
        // [path [title, artist]]
        SortedMap<String, Pair<String, String>> result = new TreeMap<>();

        if (cursor != null && cursor.moveToFirst()) {
            int songTitle = cursor.getColumnIndex(MediaStore.Audio.Media.TITLE);
            int songAlbum = cursor.getColumnIndex(MediaStore.Audio.Media.ALBUM);
            int songArtist = cursor.getColumnIndex(MediaStore.Audio.Media.ARTIST);
            int songLocation = cursor.getColumnIndex(MediaStore.Audio.Media.DATA);
            String tempPath;
            String tempTitle;
            String tempAlbum;
            String tempArtist;
            Pair<String, String> tempPair;
            do {
                tempTitle = cursor.getString(songTitle);
                tempAlbum = cursor.getString(songAlbum);
                tempArtist = cursor.getString(songArtist);
                tempPath = cursor.getString(songLocation);
                albumRes.put(tempPath, tempAlbum);
                tempPair = new Pair<>(tempTitle, tempArtist);
                result.put(cursor.getString(songLocation), tempPair);
            } while (cursor.moveToNext());
            cursor.close();
        }
        return result;
    }

    public static SortedMap<String, SortedMap<String, String>> fillArtistsSongsList() {
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

    public static void playPrevious() {
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

    public static void playNext() {
        if (currentTrackNum++ < currentTrackSequence.size()) {
            if (preferences.getBoolean("isRemoteLibrary", false)) {
                nextRemoteSong();
                play(nextRemoteSongPath, currentContext);
                updateNextRemoteSong();
            } else
                play(Uri.parse(currentTrackSequence.get(currentTrackNum)), currentContext);
        } else
            pause();
        updateSavedPlaylist();
    }

    public static void pause() {
        try {
            if (preferences.getBoolean("isRemoteLibrary", false))
                pauseRemote();
            isPlaying = false;
        } catch (IllegalStateException | NullPointerException e) {
            e.printStackTrace();
        }

    }

    public static void resume() {
        try {
            if (!isPlaying) {
                isPlaying = true;
                boolean isRemote = preferences.getBoolean("isRemoteLibrary", false);
                if (isRemote) {
                    mMediaBrowserHelper.getMediaController().setVolumeTo(0, (int) mMediaBrowserHelper.getMediaController().getFlags());
                    resumeRemote();
                }
            }
        } catch (IllegalStateException | NullPointerException e) {
            e.printStackTrace();
        }
    }

    public static void setIsRemotePlaying(boolean val) {
        isRemotePlaying = val;
    }

    public static boolean isPlaying() {
        return isPlaying;
    }

    public static String getCurrentSongName() {
        String title = currentSongs.get(currentTrackSequence.get(currentTrackNum)).first;
        String artist = currentSongs.get(currentTrackSequence.get(currentTrackNum)).second;
        return title + " - " + artist;
    }

    public static Bitmap extractAlbumArt(String path, Context context) {
        android.media.MediaMetadataRetriever mmr = new MediaMetadataRetriever();
        try {
            mmr.setDataSource(path);
            InputStream is = new ByteArrayInputStream(mmr.getEmbeddedPicture());
            return BitmapFactory.decodeStream(is);
        } catch (Exception e) {
            return BitmapFactory.decodeResource(context.getResources(), R.mipmap.ic_launcher);
        }
    }

    public static String getRoot() {
        return "root";
    }

    public static List<MediaBrowserCompat.MediaItem> getMediaItems() {
        List<MediaBrowserCompat.MediaItem> result = new ArrayList<>();
        for (String songPath : currentTrackSequence.values()) {
            AsyncTask.execute(() -> {
                try {
                    result.add(new MediaBrowserCompat.MediaItem(getMetadataWithoutBitmap(songPath)
                            .getDescription(), MediaBrowserCompat.MediaItem.FLAG_PLAYABLE));
                } catch (Exception e) {
                    System.out.print("Failed to load " + songPath);
                }
            });
        }
        return result;
    }

    public static MediaMetadataCompat getMetadata(Context context) {
        String mediaId;
        MediaMetadataCompat metadataWithoutBitmap;
        boolean isRemote = preferences.getBoolean("isRemoteLibrary", false);
        if (isRemote)
            if (currentRemoteSongPath == null)
                return null;
            else
                mediaId = currentRemoteSongPath.toString();
        else
            mediaId = currentTrackSequence.get(currentTrackNum);

        MediaMetadataRetriever retriever = new MediaMetadataRetriever();
        try {
            retriever.setDataSource(mediaId);
        } catch (Exception e) {
            return null;
        }
        metadataWithoutBitmap = new MediaMetadataCompat.Builder()
                .putString(MediaMetadataCompat.METADATA_KEY_MEDIA_ID, mediaId)
                .putString(MediaMetadataCompat.METADATA_KEY_ALBUM,
                        retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM))
                .putString(MediaMetadataCompat.METADATA_KEY_ARTIST,
                        retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST))
                .putLong(MediaMetadataCompat.METADATA_KEY_DURATION,
                        TimeUnit.MILLISECONDS.convert(
                                Integer.parseInt(
                                        retriever.extractMetadata(
                                                MediaMetadataRetriever.METADATA_KEY_DURATION)),
                                TimeUnit.MILLISECONDS))
                .putBitmap(
                        MediaMetadataCompat.METADATA_KEY_ALBUM_ART,
                        //extractAlbumArt(tempPath, context)
                        null
                )
                .putString(MediaMetadataCompat.METADATA_KEY_TITLE,
                        retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE))
                .build();
        if (metadataWithoutBitmap == null)
            return null;
        Bitmap albumArt = extractAlbumArt(mediaId, context);

        // Since MediaMetadataCompat is immutable, we need to create a copy to set the album art.
        // We don't set it initially on all items so that they don't take unnecessary memory.
        MediaMetadataCompat.Builder builder = new MediaMetadataCompat.Builder();
        for (String key :
                new String[]{
                        MediaMetadataCompat.METADATA_KEY_MEDIA_ID,
                        MediaMetadataCompat.METADATA_KEY_ALBUM,
                        MediaMetadataCompat.METADATA_KEY_ARTIST,
                        MediaMetadataCompat.METADATA_KEY_GENRE,
                        MediaMetadataCompat.METADATA_KEY_TITLE
                }) {
            builder.putString(key, metadataWithoutBitmap.getString(key));
        }
        builder.putLong(
                MediaMetadataCompat.METADATA_KEY_DURATION,
                metadataWithoutBitmap.getLong(MediaMetadataCompat.METADATA_KEY_DURATION));
        builder.putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, albumArt);
        return builder.build();
    }

    private static MediaMetadataCompat getMetadataWithoutBitmap(String path) {
        MediaMetadataCompat metadataWithoutBitmap;
        MediaMetadataRetriever retriever = new MediaMetadataRetriever();
        retriever.setDataSource(path);
        metadataWithoutBitmap = new MediaMetadataCompat.Builder()
                .putString(MediaMetadataCompat.METADATA_KEY_MEDIA_ID, path)
                .putString(MediaMetadataCompat.METADATA_KEY_ALBUM,
                        retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM))
                .putString(MediaMetadataCompat.METADATA_KEY_ARTIST,
                        retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST))
                .putLong(MediaMetadataCompat.METADATA_KEY_DURATION,
                        TimeUnit.MILLISECONDS.convert(
                                Integer.parseInt(
                                        retriever.extractMetadata(
                                                MediaMetadataRetriever.METADATA_KEY_DURATION)),
                                TimeUnit.MILLISECONDS))
                .putBitmap(
                        MediaMetadataCompat.METADATA_KEY_ALBUM_ART,
                        null
                )
                .putString(MediaMetadataCompat.METADATA_KEY_TITLE,
                        retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE))
                .build();
        return metadataWithoutBitmap;
    }
}
