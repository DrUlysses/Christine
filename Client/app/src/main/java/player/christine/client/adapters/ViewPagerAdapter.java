package player.christine.client.adapters;

import android.content.Context;
import com.google.android.material.tabs.TabLayout;
import androidx.viewpager.widget.PagerAdapter;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

import player.christine.client.musicplayer.Player;
import player.christine.client.R;

public class ViewPagerAdapter extends PagerAdapter {

    private Context mContext;

    private Button shuffleSongsButton;
    private Button shuffleArtistsButton;
    private TabLayout tabLayout;
    private ListView songsList;
    private ListView artistsList;
    private ListView artistSongsList;
    private TextView artistTextView;

    public ViewPagerAdapter(Context context) {
        mContext = context;
    }

    @Override
    public Object instantiateItem(ViewGroup collection, int position) {
        int layout = position == 0 ? R.layout.songs_tab : R.layout.artists_tab;
        LayoutInflater inflater = LayoutInflater.from(mContext);
        ViewGroup viewGroup = (ViewGroup) inflater.inflate(layout, collection, false);
        if (position == 0) {
            songsList = viewGroup.findViewById(R.id.main_songs_list);
            shuffleSongsButton = viewGroup.findViewById(R.id.main_shuffle_songs_button);

            fillSongsList();
            songsList.setOnItemClickListener(songsListOnItemClickListener());
            shuffleSongsButton.setOnClickListener(shuffleSongsButtonOnClickListener());
        } else {
            artistsList = viewGroup.findViewById(R.id.main_artists_list);
            artistSongsList = viewGroup.findViewById(R.id.main_artist_songs_list);
            shuffleArtistsButton = viewGroup.findViewById(R.id.main_shuffle_artists_button);
            artistTextView = viewGroup.findViewById(R.id.main_artist_text_view);
            tabLayout = viewGroup.findViewById(R.id.main_tab_layout);

            fillArtistsList();
            shuffleArtistsButton.setOnClickListener(shuffleArtistsButtonOnClickListener());
            artistSongsList.setOnItemClickListener(artistSongsListOnItemClickListener());
        }
        collection.addView(viewGroup);
        return viewGroup;
    }

    private void fillSongsList() {
        final SongsArrayAdapter adapterSongs = new SongsArrayAdapter(Player.currentSongs);
        songsList.setAdapter(adapterSongs);
    }

    private void fillArtistsList() {
        List<String> tempList = new ArrayList<>(Player.currentArtistsSongs.keySet());
        final ArtistsArrayAdapter adapterArtists = new ArtistsArrayAdapter(tempList);
        artistsList.setAdapter(adapterArtists);
        artistsList.setOnItemClickListener((parent, view, position, id) -> {
            // hide artists list
            artistsList.setVisibility(View.GONE);
            // fill artist songs list with artist songs
            String artist = (String) parent.getItemAtPosition(position);
            Player.currentChosenArtist = artist;
            final List<String> tempList1 = new ArrayList<>(Player.currentArtistsSongs.get(artist).keySet());
            ArtistsArrayAdapter adapterArtistSongs = new ArtistsArrayAdapter(tempList1);
            artistSongsList.setAdapter(adapterArtistSongs);
            artistSongsList.setVisibility(View.VISIBLE);
            // set text to artist and show all artists onclick; change currentSongs back
            artistTextView.setText(artist);
            artistTextView.setVisibility(View.VISIBLE);
            shuffleArtistsButton.setVisibility(View.VISIBLE);
            artistTextView.setOnClickListener(v -> {
                Player.currentChosenArtist = null;
                artistSongsList.setAdapter(null);
                artistTextView.setVisibility(View.GONE);
                artistSongsList.setVisibility(View.GONE);
                shuffleArtistsButton.setVisibility(View.GONE);
                artistsList.setVisibility(View.VISIBLE);
            });
        });
    }

    private AdapterView.OnItemClickListener songsListOnItemClickListener() {
        return (parent, view, position, id) -> {
            Player.playSongFromList(position);
        };
    }

    private AdapterView.OnItemClickListener artistSongsListOnItemClickListener() {
        return (parent, view, position, id) -> {
            Player.playSongFromArtistList(position);
        };
    }

    private View.OnClickListener shuffleSongsButtonOnClickListener() {
        return (v -> Player.shuffleSongs());
    }

    private View.OnClickListener shuffleArtistsButtonOnClickListener() {
        return (v -> Player.shuffleCurrentArtistSongs());
    }

    @Override
    public void destroyItem(ViewGroup collection, int position, Object view) {
        collection.removeView((View) view);
    }

    @Override
    public int getCount() {
        return 2;
    }

    @Override
    public boolean isViewFromObject(View view, Object object) {
        return view == object;
    }

    @Override
    public CharSequence getPageTitle(int position) {
        return position == 0 ? mContext.getString(R.string.songs) : mContext.getString(R.string.artists);
    }
}
