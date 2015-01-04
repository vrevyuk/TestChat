package com.revyuk.testchat;

import android.content.Context;
import android.content.pm.ActivityInfo;
import android.database.Cursor;
import android.graphics.Color;
import android.net.wifi.WifiManager;
import android.support.v7.app.ActionBarActivity;
import android.os.Bundle;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.format.Formatter;
import android.text.style.ForegroundColorSpan;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.AbsListView;
import android.widget.Button;
import android.widget.CursorAdapter;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.SimpleCursorAdapter;
import android.widget.TextView;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


public class MainActivity extends ActionBarActivity implements View.OnClickListener, Chat.OnMessageReceivedListener {
    final int CHAT_PORT = 1971;

    ListView list;
    CursorAdapter cursorAdapter;
    Button send_btn;
    EditText message;
    Chat chat;
    boolean auto_scroll_flg = true;
    boolean sender_flg = false;

    class Holder {
        ImageView imageView;
        TextView textView;
    }

    @Override
    public void onClick(View v) {
        if(chat!=null) {
            if(message.getText().length()>0) {
                chat.send(message.getText().toString());
                message.getText().clear();
            }
        }
    }

    Spannable colorizer(String instr) {
        Spannable return_str;
        return_str = new SpannableString(instr);
        Matcher matcher = Pattern.compile("#\\w+").matcher(instr);
        while(matcher.find()) {
            String s = matcher.group();
            return_str.setSpan(new ForegroundColorSpan(Color.RED), instr.indexOf(s), instr.indexOf(s)+s.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        return return_str;
    }

    class MySimpleCursorAdapter extends SimpleCursorAdapter {
        Cursor cursor;
        int layout_resource;
        String[] mFrom;
        int[] mTo;

        public MySimpleCursorAdapter(Context context, int layout, Cursor c, String[] from, int[] to, int flags) {
            super(context, layout, c, from, to, flags);
            layout_resource = layout; cursor = c; mFrom = from; mTo = to;
        }

        @Override
        public Cursor swapCursor(Cursor c) {
            cursor = c;
            return super.swapCursor(c);
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            Holder holder;
            if(convertView==null) {
                holder = new Holder();
                convertView = getLayoutInflater().inflate(layout_resource, null);
                holder.imageView = (ImageView) convertView.findViewById(R.id.chat_item_type);
                holder.textView = (TextView) convertView.findViewById(R.id.chat_item_msg);
                convertView.setTag(holder);
            } else {
                holder = (Holder) convertView.getTag();
            }
            cursor.moveToPosition(position);
            holder.imageView.setImageResource(cursor.getInt(cursor.getColumnIndex(mFrom[0])));
            holder.textView.setText(colorizer(cursor.getString(cursor.getColumnIndex(mFrom[1]))));
            return convertView;
        }
    }


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        int ip = (((WifiManager) getSystemService(WIFI_SERVICE)).getConnectionInfo()).getIpAddress();
        String ipAddress = Formatter.formatIpAddress(ip);

        chat = new Chat(MainActivity.this);
        chat.init(CHAT_PORT, ipAddress);

        list = (ListView) findViewById(R.id.list);
        list.setOnScrollListener(new AbsListView.OnScrollListener() {
            @Override
            public void onScrollStateChanged(AbsListView view, int scrollState) {        }

            @Override
            public void onScroll(AbsListView view, int firstVisibleItem, int visibleItemCount, int totalItemCount) {
                if(firstVisibleItem+visibleItemCount==totalItemCount) { auto_scroll_flg = true; } else { auto_scroll_flg = false; }
            }
        });
        cursorAdapter = new MySimpleCursorAdapter(this, R.layout.chat_item, chat.getMessages(),
                new String[] {"mtype", "message"}, new int[] {R.id.chat_item_type, R.id.chat_item_msg}, 0);
        list.setAdapter(cursorAdapter);
        message = (EditText) findViewById(R.id.message);
        message.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                boolean handled = false;
                if (actionId == EditorInfo.IME_ACTION_SEND) {
                    MainActivity.this.onClick(null);
                    if(getResources().getConfiguration().orientation == ActivityInfo.SCREEN_ORIENTATION_PORTRAIT) { handled=true; }
                }
                return handled;
            }
        });
        send_btn = (Button) findViewById(R.id.send_button);
        send_btn.setOnClickListener(this);
        sender_flg = true;
        new Thread(new RandomSendRunnable()).start();
    }

    class RandomSendRunnable implements Runnable {
        @Override
        public void run() {
            while(sender_flg) {
                long period = Math.round(Math.random()*5000);
                //Log.d("XXX", "period: " + period);
                try {
                    Thread.sleep(period);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                chat.send("Random send #message. Period: #" + period);
            }
            //Log.d("XXX", "Exit from sender");
        }
    }

    @Override
    protected void onDestroy() {
        super.onStop();
        sender_flg = false;
        if(chat!=null) chat.stop();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if(item.getItemId() == R.id.action_clear) {
            chat.clearDB();
            cursorAdapter.changeCursor(chat.getMessages());
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void message() {
        cursorAdapter.changeCursor(chat.getMessages());
        if(auto_scroll_flg) list.setSelection(list.getCount()-1);
    }

}
