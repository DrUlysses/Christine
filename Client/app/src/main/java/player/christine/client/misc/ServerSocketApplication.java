package player.christine.client.misc;

import android.app.Application;

import java.net.URISyntaxException;

import io.socket.client.IO;
import io.socket.client.Socket;

import static player.christine.client.misc.ServerPipeline.serverAdressStart;
import static player.christine.client.misc.ServerPipeline.getServerAdressIP;
import static player.christine.client.misc.ServerPipeline.statusAdress;

public class ServerSocketApplication extends Application {

    private Socket mSocket;
    {
        try {
            mSocket = IO.socket(serverAdressStart + getServerAdressIP() + statusAdress);
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }
    }

    public Socket getSocket() {
        return mSocket;
    }

    public Socket resetSocket() {
        mSocket.close();
        try {
            mSocket = IO.socket(serverAdressStart + getServerAdressIP() + statusAdress);
            return mSocket;
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }
    }
}