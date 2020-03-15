package player.christine.client;

import android.content.Intent;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;

import player.christine.client.misc.ServerPipeline;

public class ManageUnsortedActivity extends AppCompatActivity {

    private ListView unsortedList = null;
    private JSONObject JSONStorage = null;
    private Button nextButton = null;
    private Button previousButton = null;
    private ImageView coverView = null;
    private EditText titleEdit = null;
    private EditText artistEdit = null;
    private EditText albumEdit = null;
    private EditText coverUrlEdit = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Check if entered from menu or activated from other activity
        String tempForm = getIntent().getStringExtra("form");

        // If from menu
        if (tempForm == null) {
            ShowList();
        // If from another activity
        } else {
            try {
                JSONStorage = new JSONObject(tempForm);
            } catch (JSONException e) {
                e.printStackTrace();
            }
            ShowItem();
        }
    }

    private void ShowList() {
        setContentView(R.layout.activity_manage_unsorted_list);
        unsortedList = (ListView) findViewById(R.id.unsorted_list);
        // Ask server and become list of unsorted
        // fill the list with the filenames TODO - add status if started managing file so user can recognize where he stopped
        try {
            JSONStorage = new JSONObject(new ServerPipeline.BecomeList("unmanaged").execute().get());
        } catch (JSONException | InterruptedException | ExecutionException | IOException | CancellationException e) {
            e.printStackTrace();
        }
        HashMap<String, String> items = new HashMap<>();
        for (Iterator<String> it = JSONStorage.keys(); it.hasNext(); ) {
            String name = it.next();
            try {
                items.put(name, JSONStorage.getString(name));
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }
        final CustomArrayAdapter adapter = new CustomArrayAdapter(items);
        unsortedList.setAdapter(adapter);
        unsortedList.setOnItemClickListener((parent, view, position, id) -> {
            // get file name and send it to new view
            Map.Entry<String, String> tempEntry = (Map.Entry<String, String>) parent.getItemAtPosition(position);
            String songName = tempEntry.getKey();
            String form = null;
            try {
                form = new ServerPipeline.BecomeManageSongForm(songName).execute().get();
            } catch (InterruptedException | ExecutionException | IOException e) {
                Toast toast = Toast.makeText(this, e.getMessage(), Toast.LENGTH_LONG);
                toast.show();
                e.printStackTrace();
            }
            if (form != null) {
                try {
                    JSONStorage = new JSONObject(form);
                } catch (JSONException e) {
                    Toast toast = Toast.makeText(this, e.getMessage(), Toast.LENGTH_LONG);
                    toast.show();
                    e.printStackTrace();
                }
                ShowItem();
            }
        });
    }

    private void ShowItem() {
        setContentView(R.layout.activity_manage_unsorted);
        nextButton = (Button) findViewById(R.id.next_unsorted_button);
        previousButton = (Button) findViewById(R.id.previous_unsorted_button);
        coverView = (ImageView) findViewById(R.id.unsorted_song_cover_view);
        titleEdit = (EditText) findViewById(R.id.unsorted_song_title_field);
        artistEdit = (EditText) findViewById(R.id.unsorted_song_artist_field);
        albumEdit = (EditText) findViewById(R.id.unsorted_song_album_field);
        coverUrlEdit = (EditText) findViewById(R.id.unsorted_song_cover_url_edit);

        try {
            coverUrlEdit.setText(this.JSONStorage.getString("cover"));
            coverView.setImageBitmap(new ServerPipeline.DownloadImage(this.JSONStorage.getString("cover")).execute().get());
        } catch (JSONException e) {
            e.printStackTrace();
            coverView.setImageResource(R.drawable.ic_launcher_background);
        } catch (InterruptedException | ExecutionException e) {
            e.printStackTrace();
        }

        try {
            titleEdit.setText(this.JSONStorage.getString("title"));
        } catch (JSONException e) {
            e.printStackTrace();
            try {
                titleEdit.setText(this.JSONStorage.getString("spotify_title"));
            } catch (JSONException e1) {
                e1.printStackTrace();
            }
        }

        try {
            artistEdit.setText(this.JSONStorage.getString("artist"));
        } catch (JSONException e) {
            e.printStackTrace();
            try {
                artistEdit.setText(this.JSONStorage.getString("spotify_artist"));
            } catch (JSONException e1) {
                e1.printStackTrace();
            }
        }

        try {
            albumEdit.setText(this.JSONStorage.getString("album"));
        } catch (JSONException e) {
            e.printStackTrace();
        }

        nextButton.setText(R.string.done);
        nextButton.setOnClickListener(v -> {
            Intent intent = new Intent();
            try {
                this.JSONStorage.put("title", titleEdit.getText().toString());
                this.JSONStorage.put("artist", artistEdit.getText().toString());
                this.JSONStorage.put("album", albumEdit.getText().toString());
                this.JSONStorage.put("cover", coverUrlEdit.getText().toString());
            } catch (JSONException e) {
                e.printStackTrace();
            }
            try {
                JSONStorage.getString("song_name");
            } catch (JSONException e) {
                e.printStackTrace();
                return;
            }
            Boolean successfullyManaged = false;
            try {
                successfullyManaged = new ServerPipeline.SendManageSongForm(JSONStorage.toString()).execute().get();
            } catch (InterruptedException | ExecutionException | IOException e) {
                e.printStackTrace();
            }
            if (successfullyManaged) {
                setResult(RESULT_OK, intent);
            } else {
                setResult(RESULT_CANCELED);
                Toast toast = Toast.makeText(this, "Trouble with sending filled form to server", Toast.LENGTH_LONG);
                toast.show();
            }
            finish();
        });

        previousButton.setText(R.string.cancel);
        previousButton.setOnClickListener(v -> {
            setResult(RESULT_CANCELED);
            finish();
        });
    }

    private class CustomArrayAdapter extends BaseAdapter {
        private final ArrayList mData;
        private final HashMap<String, Integer> idList;

        public CustomArrayAdapter(Map<String, String> map) {
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
        public Map.Entry<String, String> getItem(int position) {
            return (Map.Entry) mData.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }


        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            final View result;

            if (convertView == null) {
                result = LayoutInflater.from(parent.getContext()).inflate(R.layout.manage_unsorted_list_item, parent, false);
            } else {
                result = convertView;
            }

            Map.Entry<String, String> item = getItem(position);
            ((TextView) result.findViewById(R.id.manage_unsorted_list_item_name)).setText(item.getKey());
            ((TextView) result.findViewById(R.id.manage_unsorted_list_item_status)).setText(item.getValue());

            return result;
        }
    }
}
