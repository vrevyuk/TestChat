package com.revyuk.testchat;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.os.AsyncTask;
import android.os.Parcel;
import android.os.Parcelable;
import android.util.Log;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.UnknownHostException;

/**
 * Created by Notebook on 03.01.2015.
 */
public class Chat {
    Context context;
    ReceivedSocket receivedSocket;
    String local_address;
    private volatile boolean exit_task_flag;
    final int PACKET_SIZE = 1024;
    private int send_port;
    OnMessageReceivedListener callback;
    MyDB myDB;
    SQLiteDatabase db;

    public interface OnMessageReceivedListener {
        void message();
    }

    public Chat(Context context) {
        this.context = context;
        callback = (OnMessageReceivedListener) context;
    }

    private class ReceivedSocket extends AsyncTask<Integer, String, Void> {

        @Override
        protected Void doInBackground(Integer... params) {
            Log.d("XXX", "Receiver enter");
            DatagramSocket socket=null;
            DatagramPacket packet;
            byte[] buffer = new byte[PACKET_SIZE];
            try {
                socket = new DatagramSocket(params[0], InetAddress.getByName("255.255.255.255"));
                socket.setBroadcast(true);
                socket.setSoTimeout(500);
                socket.setReceiveBufferSize(PACKET_SIZE);
            } catch (SocketException e) {
                e.printStackTrace();
            } catch (UnknownHostException e) {
                e.printStackTrace();
            }
            while (!isCancelled() && !exit_task_flag) {
                if(socket!=null) {
                    try {
                        packet = new DatagramPacket(buffer, PACKET_SIZE);
                        socket.receive(packet);
                        publishProgress(new String(packet.getData(), packet.getOffset(), packet.getLength()), packet.getAddress().getHostAddress());
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                } else { break; }
            }
            if(socket!=null) socket.close();
            Log.d("XXX", "Receiver exit");
            return null;
        }

        @Override
        protected void onProgressUpdate(String... values) {
            super.onProgressUpdate(values);
            ContentValues cv = new ContentValues();
            if(!local_address.equals(values[1])) { cv.put("mtype", R.drawable.ic_action_collapse); }
                else { cv.put("mtype", R.drawable.ic_action_expand); }
            cv.put("message", values[0]);
            if(db.isOpen()) {
                db.insert("messages", null, cv);
            } else {
                db = myDB.getWritableDatabase();
                db.insert("messages", null, cv);
            }
            if(callback!=null) {
                callback.message();
            }
        }
    }

    public void init(int port, String myIP) {
        local_address = myIP;
        myDB = new MyDB(context);
        db = myDB.getWritableDatabase();
        exit_task_flag = false;
        send_port = port;

        receivedSocket = new ReceivedSocket();
        receivedSocket.execute(port);
    }

    public AsyncTask.Status getStatus() {
        return receivedSocket.getStatus();
    }

    public void stop() {
        if(receivedSocket.getStatus() == AsyncTask.Status.RUNNING) {
            receivedSocket.cancel(true);
            exit_task_flag = true;
            if(db!=null) db.close();
            if(myDB!=null) myDB.close();
        }
    }

    public void send(final String message) {
        Thread thread = new Thread(new Runnable() {
            @Override
            public void run() {
                DatagramSocket sendSocket=null;
                DatagramPacket packet;
                byte[] buff = message.getBytes();
                try {
                    sendSocket = new DatagramSocket(send_port+1);
                    sendSocket.setBroadcast(true);
                    packet = new DatagramPacket(buff, buff.length, InetAddress.getByName("255.255.255.255"), send_port);
                    sendSocket.send(packet);
                } catch (SocketException e) {
                    e.printStackTrace();
                } catch (UnknownHostException e) {
                    e.printStackTrace();
                } catch (IOException e) {
                    e.printStackTrace();
                }
                if(sendSocket!=null) sendSocket.close();
            }
        });
        thread.start();
    }

    public Cursor getMessages() {
        Cursor cursor=null;
        if(db!=null) {
            cursor = db.rawQuery("select * from messages", null);
        }
        return cursor;
    }

    public void clearDB() {
        if(db!=null) {
            db.execSQL("delete from messages");
        }
    }

    class MyDB extends SQLiteOpenHelper {

        public MyDB(Context context) {
            super(context, "chat", null, 1);
        }

        @Override
        public void onCreate(SQLiteDatabase db) {
            db.execSQL("create table messages (_id integer primary key autoincrement, mtype integer, message text);");
        }

        @Override
        public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        }
    }
}
