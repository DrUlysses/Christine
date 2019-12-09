package player.christine.client;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ArtistsArrayAdapter extends BaseAdapter {
    private final List<String> idList = new ArrayList<>();

    public ArtistsArrayAdapter(List<String> map) {
        Collections.sort(map);
        idList.addAll(map);
    }

    @Override
    public int getCount() {
        return idList.size();
    }

    @Override
    public Object getItem(int position) {
        return idList.get(position);
    }

    @Override
    public long getItemId(int position) {
        return position;
    }


    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        final View result;

        if (convertView == null) {
            result = LayoutInflater.from(parent.getContext()).inflate(R.layout.main_artists_list_item, parent, false);
        } else {
            result = convertView;
        }

        String item = (String) getItem(position);
        ((TextView) result.findViewById(R.id.main_artists_list_item_name)).setText(item);

        return result;
    }
}
