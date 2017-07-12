package com.example.dell.v_clock.fragment;


import android.content.BroadcastReceiver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.SystemClock;
import android.support.v4.app.Fragment;
import android.support.v4.widget.SwipeRefreshLayout;
import android.text.Layout;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ListView;

import com.example.dell.v_clock.MessageDBHelper;
import com.example.dell.v_clock.R;
import com.example.dell.v_clock.VClockContract;
import com.example.dell.v_clock.activity.MainActivity;
import com.example.dell.v_clock.adapter.MessageListAdapter;
import com.example.dell.v_clock.object.GuestMessage;
import com.example.dell.v_clock.util.SwipeDismissListViewTouchListener;

import java.util.ArrayList;
import java.util.List;

/**
 * The fragment displays one message of a guest when he/she arrive.
 * 当一位嘉宾到达后，该碎片显示一条到达信息。
 */
public class MessageListFragment extends Fragment {

    MessageDBHelper dbHelper;
    MessageListAdapter messageListAdapter;
    ContentValues messageValues;
    SQLiteDatabase db;
    View layoutView;
    List<GuestMessage> guestMessageList;
    SwipeRefreshLayout refreshLayout;

    public MessageListFragment() {
        // Required empty public constructor
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {

        dbHelper = new MessageDBHelper(getContext());
        messageValues = new ContentValues();
        guestMessageList = new ArrayList<>();
        layoutView = inflater.inflate(R.layout.fragment_message, container, false);

        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction("MESSAGE_ARRIVE_BROADCAST");
        MessageBroadCastReceiver broadCastReceiver = new MessageBroadCastReceiver();
        getContext().registerReceiver(broadCastReceiver, intentFilter);

        refreshLayout = layoutView.findViewById(R.id.srl_refresh_layout);
        refreshLayout.setOnRefreshListener(new SwipeRefreshLayout.OnRefreshListener() {
            @Override
            public void onRefresh() {
                guestMessageList.clear();
                GuestMessage[] guestMessages = new restoreMessageList().doInBackground();
                for (int i = 0; i < guestMessages.length; i++) {
                    guestMessageList.add(guestMessages[i]);
                }
                messageListAdapter = new MessageListAdapter(getContext(), R.layout.one_message_in_list, guestMessageList);
                ListView guestMessageView = layoutView.findViewById(R.id.lv_message_list);
                SwipeDismissListViewTouchListener touchListener =
                        new SwipeDismissListViewTouchListener(
                                guestMessageView,
                                new SwipeDismissListViewTouchListener.DismissCallbacks() {
                                    @Override
                                    public boolean canDismiss(int position) {
                                        return true;
                                    }

                                    @Override
                                    public void onDismiss(ListView listView, int[] reverseSortedPositions) {
                                        for (int position : reverseSortedPositions) {
                                            String[] visitInfo = {messageListAdapter.getItem(position).getGuestName(),
                                                    messageListAdapter.getItem(position).getArriveTime()};
                                            new removeOneMessage().execute(visitInfo);
                                            messageListAdapter.remove(messageListAdapter.getItem(position));
                                        }
                                        messageListAdapter.notifyDataSetChanged();
                                    }
                                });
                guestMessageView.setOnTouchListener(touchListener);
                guestMessageView.setAdapter(messageListAdapter);
                refreshLayout.setRefreshing(false);
            }
        });

        // Inflate the layout for this fragment;
        return layoutView;
    }

    @Override
    public void onStart() {


        //Restore unread Message
        //恢复唯独未读消息
        GuestMessage[] guestMessages = new restoreMessageList().doInBackground();
        for (int i = 0; i < guestMessages.length; i++) {
            guestMessageList.add(guestMessages[i]);
        }
        messageListAdapter = new MessageListAdapter(getContext(), R.layout.one_message_in_list, guestMessageList);
        ListView guestMessageView = layoutView.findViewById(R.id.lv_message_list);
        SwipeDismissListViewTouchListener touchListener =
                new SwipeDismissListViewTouchListener(
                        guestMessageView,
                        new SwipeDismissListViewTouchListener.DismissCallbacks() {
                            @Override
                            public boolean canDismiss(int position) {
                                return true;
                            }

                            @Override
                            public void onDismiss(ListView listView, int[] reverseSortedPositions) {
                                for (int position : reverseSortedPositions) {
                                    String[] visitInfo = {messageListAdapter.getItem(position).getGuestName(),
                                            messageListAdapter.getItem(position).getArriveTime()};
                                    new removeOneMessage().execute(visitInfo);
                                    messageListAdapter.remove(messageListAdapter.getItem(position));
                                }
                                messageListAdapter.notifyDataSetChanged();
                            }
                        });
        guestMessageView.setOnTouchListener(touchListener);
        guestMessageView.setAdapter(messageListAdapter);
        refreshLayout.setRefreshing(false);
        Log.d("TAG", "onStart");
        super.onStart();
    }

    @Override
    public void onDestroy() {
        dbHelper.close();
        super.onDestroy();
    }

    private class removeOneMessage extends AsyncTask<String, Void, Void> {
        @Override
        protected Void doInBackground(String... visitInfo) {
            db = dbHelper.getWritableDatabase();
            if (visitInfo.length < 1) {
                Log.d("TAG", "数据库删除失败！嘉宾信息不完整");
            } else {
                String selection = VClockContract.MessageInfo.COLUMN_NAME_GNAME + "=? and " +
                        VClockContract.MessageInfo.COLUMN_NAME_DATE + "=?";
                String[] selectionArgs = {visitInfo[0], visitInfo[1]};
                db.delete(VClockContract.MessageInfo.TABLE_NAME, selection, selectionArgs);
                Log.d("TAG", "删除一条消息, 嘉宾姓名：" + visitInfo[0] + "，到访时间：" + visitInfo[1]);
            }
            return null;
        }
    }

    private class restoreMessageList extends AsyncTask<Void, Void, GuestMessage[]> {
        @Override
        protected GuestMessage[] doInBackground(Void... voids) {
            db = dbHelper.getReadableDatabase();
            String[] projection = {
                    VClockContract.MessageInfo.COLUMN_NAME_GNAME,
                    VClockContract.MessageInfo.COLUMN_NAME_DATE
            };
            Cursor cursor = db.query(
                    VClockContract.MessageInfo.TABLE_NAME,
                    projection,
                    null,
                    null,
                    null,
                    null,
                    null
            );
            GuestMessage[] guestMessages = new GuestMessage[cursor.getCount()];
            while (cursor.moveToNext()) {
                String guestName = cursor.getString(0);
                String arriveTime = cursor.getString(1);
                GuestMessage guestMessage = new GuestMessage(guestName, arriveTime);
                guestMessages[cursor.getPosition()] = guestMessage;
            }
            cursor.close();
            return guestMessages;
        }
    }

    public class MessageBroadCastReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
//            messageListAdapter.notifyDataSetChanged();
        }
    }
}