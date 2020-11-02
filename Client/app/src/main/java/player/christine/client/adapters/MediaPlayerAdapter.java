package player.christine.client.adapters;

import android.content.Context;
import android.os.Build;
import android.os.SystemClock;
import androidx.annotation.RequiresApi;
import android.support.v4.media.MediaMetadataCompat;
import android.support.v4.media.session.PlaybackStateCompat;

import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.ExoPlaybackException;
import com.google.android.exoplayer2.MediaItem;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.SimpleExoPlayer;
import com.google.android.exoplayer2.audio.AudioAttributes;

import player.christine.client.musicplayer.PlaybackInfoListener;
import player.christine.client.MainActivity;

/**
 * Exposes the functionality of the {@link SimpleExoPlayer} and implements the {@link PlayerAdapter}
 * so that {@link MainActivity} can control music playback.
 */
public final class MediaPlayerAdapter extends PlayerAdapter {

    private final Context mContext;
    private SimpleExoPlayer mMediaPlayer = null;
    private String mFilename = null;
    private PlaybackInfoListener mPlaybackInfoListener = null;
    private MediaMetadataCompat mCurrentMedia = null;
    private int mState;
    private boolean mCurrentMediaPlayedToCompletion;

    // Work-around for a MediaPlayer bug related to the behavior of MediaPlayer.seekTo()
    // while not playing.
    //private long mSeekWhileNotPlaying = -1;

    public MediaPlayerAdapter(Context context, PlaybackInfoListener listener) {
        super(context);
        mContext = context.getApplicationContext();
        mPlaybackInfoListener = listener;
    }

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    private void initializeMediaPlayer(Context context) {
        if (mMediaPlayer == null) {
            mMediaPlayer = new SimpleExoPlayer.Builder(context).build();
            mMediaPlayer.setHandleAudioBecomingNoisy(true);
            mMediaPlayer.setAudioAttributes(new AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.CONTENT_TYPE_MUSIC)
                    .build());
            mMediaPlayer.addListener(new Player.EventListener() {
                @Override
                public void onPlaybackStateChanged(int state) {
                    if (state == Player.STATE_ENDED) {
                        mPlaybackInfoListener.onPlaybackCompleted();

                        // Set the state to "paused" because it most closely matches the state
                        // in MediaPlayer with regards to available state transitions compared
                        // to "stop".
                        // Paused allows: seekTo(), start(), pause(), stop()
                        // Stop allows: stop()
                        setNewState(PlaybackStateCompat.STATE_STOPPED);
                    }
                }

                @Override
                public void onPlayerError(ExoPlaybackException error) {
                    throw new RuntimeException("Failed to open file: " + mFilename, error.getCause());
                }
            });
        }
    }

    // Implements PlaybackControl.
    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    @Override
    public void playFromMedia(MediaMetadataCompat metadata) {
        mCurrentMedia = metadata;
        playFile(metadata.getDescription().getMediaId());
    }

    @Override
    public MediaMetadataCompat getCurrentMedia() {
        return mCurrentMedia;
    }

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    private void playFile(String filename) {
        boolean mediaChanged = (mFilename == null || !filename.equals(mFilename));
        if (mCurrentMediaPlayedToCompletion) {
            // Last audio file was played to completion, the resourceId hasn't changed, but the
            // player was released, so force a reload of the media file for playback.
            mediaChanged = true;
            mCurrentMediaPlayedToCompletion = false;
        }
        if (!mediaChanged) {
            if (!isPlaying())
                play();
            return;
        } else
            release();

        mFilename = filename;

        initializeMediaPlayer(mContext);

        try {
            mMediaPlayer.addMediaItem(0, MediaItem.fromUri(mFilename));
            mMediaPlayer.prepare();
        } catch (Exception e) {
            throw new RuntimeException("Failed to open file: " + mFilename, e);
        }

        play();
    }

    @Override
    public void onStop() {
        // Regardless of whether or not the MediaPlayer has been created / started, the state must
        // be updated, so that MediaNotificationManager can take down the notification.
        setNewState(PlaybackStateCompat.STATE_STOPPED);
        release();
    }

    private void release() {
        if (mMediaPlayer != null) {
            mMediaPlayer.stop();
            mMediaPlayer.release();
            mMediaPlayer = null;
        }
    }

    @Override
    public boolean isPlaying() {
        return mMediaPlayer != null && mMediaPlayer.isPlaying();
    }

    @Override
    protected void onPlay() {
        if (!isPlaying()) {
            mMediaPlayer.play();
            setNewState(PlaybackStateCompat.STATE_PLAYING);
        }
    }

    @Override
    protected void onPause() {
        if (isPlaying()) {
            mMediaPlayer.pause();
            setNewState(PlaybackStateCompat.STATE_PAUSED);
        }
    }

    // This is the main reducer for the player state machine.
    private void setNewState(@PlaybackStateCompat.State int newPlayerState) {
        mState = newPlayerState;

        // Whether playback goes to completion, or whether it is stopped, the
        // mCurrentMediaPlayedToCompletion is set to true.
        if (mState == PlaybackStateCompat.STATE_STOPPED)
            mCurrentMediaPlayedToCompletion = true;

        // Work around for MediaPlayer.getCurrentPosition() when it changes while not playing.
        final long reportPosition;
//        if (mSeekWhileNotPlaying >= 0) {
//            reportPosition = mSeekWhileNotPlaying;
//            if (mState == PlaybackStateCompat.STATE_PLAYING)
//                mSeekWhileNotPlaying = -1;
//        } else
        reportPosition = mMediaPlayer == null ? 0 : mMediaPlayer.getCurrentPosition();

        final PlaybackStateCompat.Builder stateBuilder = new PlaybackStateCompat.Builder();
        stateBuilder.setActions(getAvailableActions());
        stateBuilder.setState(mState,
                              reportPosition,
                              1.0f,
                              SystemClock.elapsedRealtime());
        mPlaybackInfoListener.onPlaybackStateChange(stateBuilder.build());
    }

    /**
     * Set the current capabilities available on this session. Note: If a capability is not
     * listed in the bitmask of capabilities then the MediaSession will not handle it. For
     * example, if you don't want ACTION_STOP to be handled by the MediaSession, then don't
     * included it in the bitmask that's returned.
     */
    @PlaybackStateCompat.Actions
    private long getAvailableActions() {
        long actions = PlaybackStateCompat.ACTION_PLAY_FROM_MEDIA_ID
                       | PlaybackStateCompat.ACTION_PLAY_FROM_SEARCH
                       | PlaybackStateCompat.ACTION_SKIP_TO_NEXT
                       | PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS;
        switch (mState) {
            case PlaybackStateCompat.STATE_STOPPED:
                actions |= PlaybackStateCompat.ACTION_PLAY
                           | PlaybackStateCompat.ACTION_PAUSE;
                break;
            case PlaybackStateCompat.STATE_PLAYING:
                actions |= PlaybackStateCompat.ACTION_STOP
                           | PlaybackStateCompat.ACTION_PAUSE
                           | PlaybackStateCompat.ACTION_SEEK_TO;
                break;
            case PlaybackStateCompat.STATE_PAUSED:
                actions |= PlaybackStateCompat.ACTION_PLAY
                           | PlaybackStateCompat.ACTION_STOP;
                break;
            default:
                actions |= PlaybackStateCompat.ACTION_PLAY
                           | PlaybackStateCompat.ACTION_PLAY_PAUSE
                           | PlaybackStateCompat.ACTION_STOP
                           | PlaybackStateCompat.ACTION_PAUSE;
        }
        return actions;
    }

    @Override
    public void seekTo(long position) {
        if (mMediaPlayer != null) {
            long seekPosition = mMediaPlayer.getDuration() == C.TIME_UNSET ? 0
                    : Math.min(Math.max(0, position), mMediaPlayer.getDuration());
//            if (!isPlaying())
//                mSeekWhileNotPlaying = seekPosition;
            mMediaPlayer.seekTo(seekPosition);
            mMediaPlayer.prepare();
            onPlay();
        }
    }

    @Override
    public void setVolume(float volume) {
        if (mMediaPlayer != null)
            mMediaPlayer.setVolume(volume);
    }
}
