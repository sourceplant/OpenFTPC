package com.android.test;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.IBinder;
import android.service.notification.StatusBarNotification;
import java.util.HashMap;


public class myfgService extends Service {

    //public  Thread t2;
    public static final String CHANNEL_ID = "AppChannel";
    public int count = 0;
    public static HashMap<String, Thread> threadMap;
    public Context context1;
    //public static  boolean active = false;
    @Override
    public void onCreate() {
        super.onCreate();
        threadMap = new HashMap<String, Thread>();
    }
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        this.context1 = this;
       // String input = intent.getStringExtra("inputExtra");
        if(!checkNotification()) {
            createNotification();
        }
            if(intent.getStringExtra("server").equals("HTTP")){
                Thread t = new Thread( new httpServRunnable(this, intent));
                t.start();
                threadMap.put(intent.getStringExtra("server"), t);
            }
            else {
                Thread t = new Thread(new SshRunnable(this, intent));
                t.start();
                threadMap.put(intent.getStringExtra("server"), t);

            }

            // if(intent.getBooleanExtra("http",false)){  t2 = new Thread(new httpServRunnable(this));;}
            //else { t2 = new Thread(new SshRunnable(this, intent));}


       // Toast.makeText(this, "Starting ssh thread ", Toast.LENGTH_LONG).show();
       // t2 = new Thread(new SshRunnable(this, intent));
     //   t2.start();
        //Toast.makeText(this, "Finsihed on Create ", Toast.LENGTH_LONG).show();
        //do heavy work on a background thread
        //stopSelf();

        return START_NOT_STICKY;
    }
    @Override
    public void onDestroy() {
       //Toast.makeText(this, "Destroying thread ", Toast.LENGTH_LONG).show();
       //Toast.makeText(this, "Thread State " + t2.getState() + "is Alive" + t2.isAlive(), Toast.LENGTH_LONG).show();
if(threadMap.isEmpty()) {
   // Message message = mHandler.obtainMessage(1, "Empty");
    //message.sendToTarget();
    deleteNotificationChannel();
    // if(t2.isAlive()){ t2.interrupt();
    //}
    super.onDestroy();
}
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void createNotificationChannel() {

            NotificationChannel serviceChannel = new NotificationChannel(CHANNEL_ID, "ShareLight", NotificationManager.IMPORTANCE_DEFAULT
            );
            serviceChannel.setSound(null,null);
            serviceChannel.enableLights(false);
            serviceChannel.enableVibration(false);
            NotificationManager manager = getSystemService(NotificationManager.class);
            manager.createNotificationChannel(serviceChannel);

    }
    private void deleteNotificationChannel(){
        NotificationManager manager= (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
       manager.deleteNotificationChannel(CHANNEL_ID);

    }
    private void createNotification(){
        if(!checkNotificationChannel()) {
            createNotificationChannel();
        }
        Intent notificationIntent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, notificationIntent, 0);
        Notification notification = new Notification.Builder(this, CHANNEL_ID)
                //   .setContentTitle("Foreground Service")
                // .setContentText(input)
                // .setSmallIcon(R.drawable.)
                .setContentIntent(pendingIntent)
                .build();
        startForeground(1, notification);
    }
    private boolean checkNotificationChannel(){
        NotificationManager manager= (NotificationManager) getSystemService(NotificationManager.class);
        NotificationChannel serviceChannel = manager.getNotificationChannel(CHANNEL_ID);
       if (serviceChannel != null){
        return true;}
       else { return false;}
        //return serviceChannel.getId().equals(CHANNEL_ID);
    }
    private boolean checkNotification() {
        NotificationManager mNotificationManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        StatusBarNotification[] notifications = mNotificationManager.getActiveNotifications();
        for (StatusBarNotification notification : notifications) {
            if (notification.getId() == 1 && notification.isOngoing()) {
               // Message message = mHandler.obtainMessage(1, "notification Exists");
                //message.sendToTarget();
                return true;
                // Do something.
            }
        }
        return false;
    }
    /*
    Handler mHandler = new Handler(new Handler.Callback() {

        @Override
        public boolean handleMessage(Message msg) {

            Toast.makeText(context1, msg.toString(), Toast.LENGTH_LONG).show();


            //Print Toast or open dialog
            return true;
        }
    });


     */
}