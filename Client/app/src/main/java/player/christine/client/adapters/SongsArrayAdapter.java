package player.christine.client.adapters;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.MediaMetadataRetriever;
import android.util.Pair;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import player.christine.client.R;

public class SongsArrayAdapter extends BaseAdapter {
    private final ArrayList mData;
    private final HashMap<String, Integer> idList;

    public SongsArrayAdapter(Map<String, Pair<String, String>> map) {
        mData = new ArrayList();
        idList = new HashMap<>();
        mData.addAll(map.entrySet());
        int i = 0;
        for (String s : map.keySet()) {
            idList.put(s, i++);
        }
    }

    @Override
    public int getCount() {
        return mData.size();
    }

    @Override
    public Map.Entry<String, Pair<String, String>> getItem(int position) {
        return (Map.Entry) mData.get(position);
    }

    @Override
    public long getItemId(int position) {
        return position;
    }


    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        final View result;

        if (convertView == null)
            result = LayoutInflater.from(parent.getContext()).inflate(R.layout.main_songs_list_item, parent, false);
        else
            result = convertView;


        Map.Entry<String, Pair<String, String>> item = getItem(position);
        ((TextView) result.findViewById(R.id.main_songs_list_item_path)).setText(item.getKey());
        ((TextView) result.findViewById(R.id.main_songs_list_item_title)).setText(item.getValue().first);
        ((TextView) result.findViewById(R.id.main_songs_list_item_artist)).setText(item.getValue().second);

        return result;
    }

}
