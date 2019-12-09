package player.christine.client;

import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.provider.OpenableColumns;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Objects;
import java.util.concurrent.ExecutionException;

public class AddSongActivity extends AppCompatActivity {

    private Button chooseFileButton;
    private Button doneChoosingFileButton;
    private Button cancelChoosingFileButton;
    private TextView chosenSongName;
    private EditText enteredSongName;
    private EditText enteredSongArtist;

    private Boolean isFileChosen;
    private File chosenFile;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_add_song);

        chooseFileButton = (Button) findViewById(R.id.choose_file_button);
        doneChoosingFileButton = (Button) findViewById(R.id.done_choosing_file_button);
        cancelChoosingFileButton = (Button) findViewById(R.id.cancel_coosing_file_button);
        chosenSongName = (TextView) findViewById(R.id.chosed_song_name);
        enteredSongName = (EditText) findViewById(R.id.song_name_field);
        enteredSongArtist = (EditText) findViewById(R.id.song_artist_field);

        isFileChosen = false;
        chosenFile = null;

        chooseFileButton.setOnClickListener(v -> {
            Intent intent = new Intent()
                    .setType("audio/*")
                    .setAction(Intent.ACTION_GET_CONTENT)
                    .addCategory(Intent.CATEGORY_OPENABLE);

            startActivityForResult(Intent.createChooser(intent, getResources().getString(R.string.select_a_file)), 10);
        });
        doneChoosingFileButton.setOnClickListener(v -> {
            if(isFileChosen && enteredSongName.getText().length() > 0 && enteredSongArtist.getText().length() > 0) {
                // Just to simplify extraction. Can be moved to new method
                String oldSongName = chosenSongName.getText().toString();
                String songExtension = oldSongName.substring(oldSongName.lastIndexOf("."));
                String songName = enteredSongName.getText().toString() + " - " + enteredSongArtist.getText().toString() + songExtension;
                // Send file and name and tell user about success
                try {
                    Toast toast = Toast.makeText(this, R.string.sending_file_please_wait, Toast.LENGTH_LONG);
                    toast.show();
                    Boolean successfullySended = new ServerPipeline.SendFileTask(chosenFile, songName).execute().get();
                    if (successfullySended == null) {
                        Toast tempToast = Toast.makeText(this, getResources().getString(R.string.some_error), Toast.LENGTH_LONG);
                        tempToast.show();
                    } else if (successfullySended){
                        chosenFile.delete();
                    }
                } catch (IOException e) {
                    Toast toast = Toast.makeText(this, e.getMessage(), Toast.LENGTH_LONG);
                    toast.show();
                } catch (ExecutionException | InterruptedException e) {
                    e.printStackTrace();
                }
                AlertDialog.Builder builder = new AlertDialog.Builder(this);
                builder.setMessage(R.string.manage_file)
                        .setPositiveButton(R.string.yes, (dialog, id) -> {
                            // Send file and start managing view
                            try {
                                Toast tempToast = Toast.makeText(this, getResources().getString(R.string.retrieving_form), Toast.LENGTH_SHORT);
                                tempToast.show();
                                String form = new ServerPipeline.BecomeManageSongForm(songName).execute().get();
                                if (form != null) {
                                    Intent intent = new Intent(this, ManageUnsortedActivity.class);
                                    intent.putExtra("form", form);
                                    startActivityForResult(intent, 20);
                                    //TODO: Start playing chosen file
                                    chosenFile.delete();
                                }

                            } catch (IOException | InterruptedException | ExecutionException e) {
                                Toast toast = Toast.makeText(this, e.getMessage(), Toast.LENGTH_LONG);
                                toast.show();
                            }
                        })
                        .setNegativeButton(R.string.no, (dialog, id) -> {
                            //TODO: Go to main screen and show some text as toast
                        });
                builder.show();
            } else {
                Toast toast = Toast.makeText(this, R.string.choose_file_and_enter_name, Toast.LENGTH_LONG);
                toast.show();
            }
        });
        cancelChoosingFileButton.setOnClickListener(v -> AddSongActivity.this.finish());
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        // On file chose
        if (requestCode == 10 && resultCode == RESULT_OK) {
            Uri selectedFilePath = data.getData(); //The uri with the location of the file
            chosenSongName.setVisibility(View.VISIBLE);
            if (selectedFilePath != null) {
                String tempName = getFileName(selectedFilePath);
                chosenSongName.setText(tempName);
                try {
                    chosenFile = new File(Objects.requireNonNull(savefile(selectedFilePath.normalizeScheme(), tempName)));
                    isFileChosen = true;
                } catch (NullPointerException e) {
                    e.printStackTrace();
                    isFileChosen = false;
                    chosenSongName.setText("");
                }
            } else {
                chosenSongName.setText(R.string.problem_getting_file_path);
                isFileChosen = false;
            }
        }
        // On manage unsorted form filled
        if (requestCode == 20) {
            finish();
        }
    }

    private String getFileName(Uri uri) {
        String result = null;
        if (Objects.requireNonNull(uri.getScheme()).equals("content")) {
            Cursor cursor = getContentResolver().query(uri, null, null, null, null);
            try {
                if (cursor != null && cursor.moveToFirst()) {
                    result = cursor.getString(cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME));
                }
            } finally {
                Objects.requireNonNull(cursor).close();
            }
        }
        if (result == null) {
            result = uri.getPath();
            int cut = Objects.requireNonNull(result).lastIndexOf('/');
            if (cut != -1) {
                result = result.substring(cut + 1);
            }
        }
        return result;
    }

    private String savefile(Uri sourceuri, String name)
    {
        String destinationFilename = getFilesDir().getAbsolutePath() + File.separatorChar + name;

        BufferedInputStream bis = null;
        BufferedOutputStream bos = null;

        try {
            bis = new BufferedInputStream(Objects.requireNonNull(getContentResolver().openInputStream(sourceuri)));
            bos = new BufferedOutputStream(new FileOutputStream(destinationFilename, false));
            byte[] buf = new byte[1024];
            bis.read(buf);
            do {
                bos.write(buf);
            } while(bis.read(buf) != -1);
            return destinationFilename;
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        } finally {
            try {
                if (bis != null) bis.close();
                if (bos != null) bos.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}
