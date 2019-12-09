package player.christine.client;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.AsyncTask;
import android.os.Environment;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;

class ServerPipeline {
    private static Context currentContext;
    public static final String serverAdressStart = "http://";
    public static final String statusAdress = "/status";
    private static final String addSongAdress = "/add_song";
    private static final String manageTagsAdress = "/manage_tags";
    private static final String becomeListAdress = "/become_list";
    private static final String sendListAdress = "/send_list";
    private static final String getCurrentSongAdress = "/current_song";
    private static final String getNextSongAdress = "/next_song";
    private static final String getPreviousSongAdress = "/previous_song";

    public static void setCurrentContext(Context context) {
        currentContext = context;
    }

    public static String getServerAdressIP() {
        String res = "127.0.0.1:5000";
        if (currentContext != null) {
            SharedPreferences preferences = currentContext.getSharedPreferences("Preferences", Context.MODE_PRIVATE);
            res = preferences.getString("remoteIP", "127.0.0.1:5000");
        }
        return res;
    }

    // Send file to the server
    public static class SendFileTask extends AsyncTask<Void, Void, Boolean> {

        private final String POST_URL = serverAdressStart + getServerAdressIP() + addSongAdress;
        private URL url = new URL(POST_URL);
        private HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        private String boundary = Long.toHexString(System.currentTimeMillis());
        private final String CRLF = "\r\n";
        private File uploadFile;
        private String songName;

        SendFileTask(File file, String songName) throws IOException {
            this.uploadFile = file;
            this.connection.setDoOutput(true);
            this.connection.setRequestMethod("POST");
            this.connection.setRequestProperty("Connection", "Keep-Alive");
            this.connection.setRequestProperty("Cache-Control", "no-cache");
            this.connection.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);
            this.songName = songName;
        }

        @Override
        protected Boolean doInBackground(Void... voids) {
            try (
                    DataOutputStream output = new DataOutputStream(connection.getOutputStream());
            ) {
                output.writeBytes("--" + boundary + CRLF);
                output.writeBytes("Content-Disposition: form-data; name=\"file\"; filename=\"" + songName + "\"" + CRLF);
                output.writeBytes("Content-Length: " + uploadFile.length() + CRLF);
                output.writeBytes("Content-Type: " + URLConnection.guessContentTypeFromName(uploadFile.getName()) + CRLF);
                output.writeBytes("Content-Transfer-Encoding: binary" + CRLF);
                output.writeBytes(CRLF);
                output.write(readBytesFromFile(uploadFile));
                output.writeBytes(CRLF);
                output.writeBytes("--" + boundary + "--" + CRLF);
                output.flush();
                output.close();

                int responseCode = this.connection.getResponseCode();
                System.out.println("Response code: [" + responseCode + "]");
                this.connection.disconnect();
                return responseCode == 200;
            } catch (IOException e) {
                e.printStackTrace();
                cancel(true);
                return false;
            }
        }
    }

    // Become manage song form from the server
    public static class BecomeManageSongForm extends AsyncTask<Void, Void, String> {

        private String GET_URL = serverAdressStart + getServerAdressIP() + manageTagsAdress;
        private URL url;
        private HttpURLConnection connection;
        private String boundary = Long.toHexString(System.currentTimeMillis());

        BecomeManageSongForm(String songName) throws IOException {
            this.GET_URL += "?song_name=" + songName.replaceAll(" ", "%20");
            this.url = new URL(GET_URL);
            this.connection = (HttpURLConnection) url.openConnection();
            this.connection.setDoInput(true);
            this.connection.setRequestMethod("GET");
            this.connection.setRequestProperty("Connection", "Keep-Alive");
            this.connection.setRequestProperty("Cache-Control", "no-cache");
            this.connection.setRequestProperty("Content-Type", "application/json; boundary=" + boundary);
        }

        @Override
        protected String doInBackground(Void... voids) {
            StringBuilder result = new StringBuilder();
            try (
                    BufferedReader input = new BufferedReader(new InputStreamReader(new BufferedInputStream(connection.getInputStream())))
            ) {
                this.connection.connect();
                int responseCode = this.connection.getResponseCode();
                if (responseCode == HttpURLConnection.HTTP_OK) {
                    String line = "";
                    while ((line = input.readLine()) != null) {
                        result.append(line);
                    }
                }
                return result.toString();
            } catch (IOException e) {
                e.printStackTrace();
                cancel(true);
                return null;
            }
            finally {
                if (this.connection != null) {
                    this.connection.disconnect();
                }
            }
        }
    }

    // Become list from the server
    public static class BecomeList extends AsyncTask<Void, Void, String> {

        private String GET_URL = serverAdressStart + getServerAdressIP() + becomeListAdress;
        private URL url;
        private HttpURLConnection connection;
        private String boundary = Long.toHexString(System.currentTimeMillis());

        BecomeList(String type) throws IOException {
            this.GET_URL += "?type=" + type.replaceAll(" ", "%20");
            this.url = new URL(GET_URL);
            this.connection = (HttpURLConnection) url.openConnection();
            this.connection.setDoInput(true);
            this.connection.setRequestMethod("GET");
            this.connection.setRequestProperty("Connection", "Keep-Alive");
            this.connection.setRequestProperty("Cache-Control", "no-cache");
            this.connection.setRequestProperty("Content-Type", "application/json; boundary=" + boundary);
        }

        @Override
        protected String doInBackground(Void... voids) {
            StringBuilder result = new StringBuilder();
            try (
                    BufferedReader input = new BufferedReader(new InputStreamReader(new BufferedInputStream(connection.getInputStream())))
            ) {
                this.connection.connect();
                int responseCode = this.connection.getResponseCode();
                if (responseCode == HttpURLConnection.HTTP_OK) {
                    String line;
                    while ((line = input.readLine()) != null) {
                        result.append(line);
                    }
                }
                return result.toString();
            } catch (IOException e) {
                e.printStackTrace();
                cancel(true);
                return null;
            }
            finally {
                if (this.connection != null) {
                    this.connection.disconnect();
                }
            }
        }
    }

    // Send list to the server
    public static class SendList extends AsyncTask<Void, Void, Boolean> {

        private String POST_URL = serverAdressStart + getServerAdressIP() + sendListAdress;
        private URL url = new URL(POST_URL);
        private HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        private String list;

        SendList(String list) throws IOException {
            this.list = list;
            this.connection.setDoOutput(true);
            this.connection.setDoInput(true);
            this.connection.setRequestMethod("POST");
            this.connection.setRequestProperty("Connection", "Keep-Alive");
            this.connection.setUseCaches(false);
            this.connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
        }

        @Override
        protected Boolean doInBackground(Void... voids) {
            try (
                    OutputStream output = connection.getOutputStream()
            ) {
                output.write(list.getBytes("UTF-8"));
                output.flush();
                output.close();

                int responseCode = this.connection.getResponseCode();
                System.out.println("Response code: [" + responseCode + "]");
                this.connection.disconnect();
                return responseCode == 200;
            } catch (IOException e) {
                e.printStackTrace();
                cancel(true);
                return false;
            }
        }
    }

    // Get song from the server
    public static class GetSong extends AsyncTask<Void, Void, String> {

        private String GET_URL = serverAdressStart + getServerAdressIP();
        private URL url;
        private HttpURLConnection connection;
        private String boundary = Long.toHexString(System.currentTimeMillis());
        File file;

        GetSong(String type) throws IOException {
            switch (type) {
                case "current":
                    this.GET_URL += getCurrentSongAdress;
                    this.file = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC),
                            "ulysses_music_current.mp3");
                    break;
                case "next":
                    this.GET_URL += getNextSongAdress;
                    this.file = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC),
                            "ulysses_music_next.mp3");
                    break;
                case "previous":
                    this.GET_URL += getPreviousSongAdress;
                    this.file = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC),
                            "ulysses_music_previous.mp3");
                    break;
                default:
                    throw new IOException("Wrong GetSong song type");
            }
            this.url = new URL(GET_URL);
            this.connection = (HttpURLConnection) url.openConnection();
            this.connection.setDoInput(true);
            this.connection.setRequestMethod("GET");
            this.connection.setRequestProperty("Connection", "Keep-Alive");
            this.connection.setRequestProperty("Cache-Control", "no-cache");
            this.connection.setRequestProperty("Content-Type", "audio/mpeg3; boundary=" + boundary);
        }

        @Override
        protected String doInBackground(Void... voids) {

            try (
                    FileOutputStream fileOutput = new FileOutputStream(file);
                    InputStream inputStream = connection.getInputStream();
            ) {
                this.connection.connect();
                int responseCode = this.connection.getResponseCode();
                if (responseCode == HttpURLConnection.HTTP_OK) {
                    byte[] buffer = new byte[1024];
                    int bufferLength;
                    while ((bufferLength = inputStream.read(buffer)) > 0) {
                        fileOutput.write(buffer, 0, bufferLength);
                    }
                    fileOutput.close();
                }
                return file.getAbsolutePath();
            } catch (IOException e) {
                e.printStackTrace();
                cancel(true);
                return null;
            }
            finally {
                if (this.connection != null) {
                    this.connection.disconnect();
                }
            }
        }
    }

    // Send filled file managing form to the server
    public static class SendManageSongForm extends AsyncTask<Void, Void, Boolean> {

        private String POST_URL = serverAdressStart + getServerAdressIP() + manageTagsAdress;
        private URL url = new URL(POST_URL);
        private HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        private String form;

        SendManageSongForm(String form) throws IOException {
            this.form = form;
            this.connection.setDoOutput(true);
            this.connection.setDoInput(true);
            this.connection.setRequestMethod("POST");
            this.connection.setRequestProperty("Connection", "Keep-Alive");
            this.connection.setUseCaches(false);
            this.connection.setRequestProperty("Content-Type", "application/x-www-form-encoded; charset=utf-8");
        }

        @Override
        protected Boolean doInBackground(Void... voids) {
            try (
                    DataOutputStream output = new DataOutputStream(connection.getOutputStream())
            ) {
                output.writeBytes(form);
                output.flush();
                output.close();

                int responseCode = this.connection.getResponseCode();
                System.out.println("Response code: [" + responseCode + "]");
                this.connection.disconnect();
                return responseCode == 200;
            } catch (IOException e) {
                e.printStackTrace();
                cancel(true);
                return false;
            }
        }
    }

    public static class DownloadImage extends AsyncTask<Void, Void, Bitmap> {
        String url;

        DownloadImage(String url) {
            this.url = url;
        }

        @Override
        protected Bitmap doInBackground(Void... voids) {
            Bitmap picture = null;
            try {
                InputStream in = new java.net.URL(this.url).openStream();
                picture = BitmapFactory.decodeStream(in);
            } catch (Exception e) {
                e.printStackTrace();
            }
            return picture;
        }

    }

    private static byte[] readBytesFromFile(File file) {

        FileInputStream fileInputStream = null;
        byte[] bytesArray = null;

        try {
            bytesArray = new byte[(int) file.length()];

            //read file into bytes[]
            fileInputStream = new FileInputStream(file);
            fileInputStream.read(bytesArray);

        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            if (fileInputStream != null) {
                try {
                    fileInputStream.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }

        }
        return bytesArray;
    }
}
