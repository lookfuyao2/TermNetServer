package com.example.termserver;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.v7.app.ActionBarActivity;
import android.text.TextUtils;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import com.esotericsoftware.kryonet.Connection;
import com.esotericsoftware.kryonet.Listener;
import com.esotericsoftware.kryonet.Server;
import com.example.termserver.Network.ChatMessage;
import com.example.termserver.Network.RegisterName;

public class TermServerActivity extends ActionBarActivity implements OnClickListener {

    private static final String TAG = "fuyao-" + TermServerActivity.class.getSimpleName();

    private Server mTermServer = null;

    private EditText mCommandEditText = null;
    private TextView mOutTextView = null;
    private Button mSendButton = null;
    private TextView mIpTextView = null;

    private StringBuilder sBuilder = new StringBuilder();

    List<String> mClients = new ArrayList<String>();

    @Override
    protected void onResume() {
        String ip = IPv4v6Utils.getLocalIPAddress();
        if (TextUtils.isEmpty(ip)) {
            ip = "Not connect wifi!";
        } else {

        }
        mIpTextView.setText(ip);
        super.onResume();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        mCommandEditText = (EditText) findViewById(R.id.command);
        mOutTextView = (TextView) findViewById(R.id.outtext);
        mSendButton = (Button) findViewById(R.id.send);
        mSendButton.setOnClickListener(this);
        mIpTextView = (TextView) findViewById(R.id.ip);
        initTermmTermServer();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();
        if (id == R.id.action_settings) {
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void initTermmTermServer() {
        mTermServer = new Server() {
            protected Connection newConnection() {
                // By providing our own connection implementation, we can store
                // per
                // connection state without a connection ID to state look up.
                return new ChatConnection();
            }
        };

        // For consistency, the classes to be sent over the network are
        // registered by the same method for both the client and mTermServer.
        Network.register(mTermServer);

        mTermServer.addListener(new Listener() {
            public void received(Connection c, Object object) {

                LogExt.d(TAG, "received " + c + " object " + object);
                // We know all connections for this mTermServer are actually
                // ChatConnections.
                ChatConnection connection = (ChatConnection) c;

                if (object instanceof RegisterName) {
                    // Ignore the object if a client has already registered a
                    // name. This is
                    // impossible with our client, but a hacker could send
                    // messages at any time.
                    if (connection.name != null)
                        return;
                    // Ignore the object if the name is invalid.
                    String name = ((RegisterName) object).name;
                    if (name == null)
                        return;
                    name = name.trim();
                    if (name.length() == 0)
                        return;
                    // Store the name on the connection.
                    connection.name = name;
                    // Send a "connected" message to everyone except the new
                    // client.
                    // ChatMessage chatMessage = new ChatMessage();
                    // chatMessage.text = name + " connected.";
                    // mTermServer.sendToAllExceptTCP(connection.getID(),
                    // chatMessage);
                    // Send everyone a new list of connection names.
                    updateNames();
                    return;
                }

                if (object instanceof ChatMessage) {
                    // Ignore the object if a client tries to chat before
                    // registering a name.
                    if (connection.name == null)
                        return;
                    ChatMessage chatMessage = (ChatMessage) object;
                    // Ignore the object if the chat message is invalid.
                    String message = chatMessage.text;
                    if (message == null)
                        return;
                    message = message.trim();
                    if (message.length() == 0)
                        return;
                    // Prepend the connection's name and send to everyone.
                    chatMessage.text = connection.name + ": " + message;
                    // mTermServer.sendToAllTCP(chatMessage);

                    sBuilder.append(chatMessage.text);
                    sBuilder.append("\n");
                    mHandler.sendEmptyMessage(MSG_UPDATE_TEXT);
                    return;
                }
            }

            public void disconnected(Connection c) {
                ChatConnection connection = (ChatConnection) c;
                if (connection.name != null) {
                    // Announce to everyone that someone (with a registered
                    // name) has left.
                    ChatMessage chatMessage = new ChatMessage();
                    chatMessage.text = connection.name + " disconnected.";
                    mTermServer.sendToAllTCP(chatMessage);
                    updateNames();
                }
            }
        });
        try {
            mTermServer.bind(Network.port);
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        mTermServer.start();
    }

    void updateNames() {
        // Collect the names for each connection.
        Connection[] connections = mTermServer.getConnections();

        // int length = mClients.size();
        // String name = "";
        // for(int i = 0; i < length; i++){
        // name =
        // if(){
        //
        // }
        // }
        // mClients = new ArrayList(connections.length);
        // for (int i = connections.length - 1; i >= 0; i--) {
        // ChatConnection connection = (ChatConnection) connections[i];
        // names.add(connection.name);
        // }
        // // Send the names to everyone.
        // UpdateNames updateNames = new UpdateNames();
        // updateNames.names = (String[]) names.toArray(new
        // String[names.size()]);
        // mTermServer.sendToAllTCP(updateNames);
    }

    // This holds per connection state.
    static class ChatConnection extends Connection {
        public String name;
    }

    private static final int MSG_UPDATE_TEXT = 1;

    private Handler mHandler = new Handler() {

        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case MSG_UPDATE_TEXT:
                    mOutTextView.setText(sBuilder.toString());
                    break;

                default:
                    break;
            }
        }

    };

    class SendThread extends Thread {
        String command;

        public SendThread(String cmd) {
            command = cmd;
        }

        @Override
        public void run() {
            ChatMessage chatMessage = new ChatMessage();
            chatMessage.text = command + "\n";
            mTermServer.sendToAllTCP(chatMessage);
            sBuilder.append(command);
            sBuilder.append("\n");
            mHandler.sendEmptyMessage(MSG_UPDATE_TEXT);
            mSendThread = null;
        }
    }

    SendThread mSendThread = null;

    @Override
    public void onClick(View v) {
        String cmd = mCommandEditText.getText().toString();
        if (!TextUtils.isEmpty(cmd) && mTermServer.getConnections().length > 0) {
            if (null == mSendThread) {
                mSendThread = new SendThread(cmd);
                mSendThread.start();
                mCommandEditText.setText("");
            }else{
                Toast.makeText(this, "Send Thread working !", Toast.LENGTH_LONG).show();
            }
        } else {
            Toast.makeText(this, "Command is empty or No client connected !", Toast.LENGTH_LONG).show();
        }
    }
}
