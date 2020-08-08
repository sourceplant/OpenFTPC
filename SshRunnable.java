package com.android.test;


import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.os.Handler;
import android.os.Message;
import android.provider.OpenableColumns;
import android.widget.Toast;

import com.jcraft.jsch.ChannelExec;
import com.jcraft.jsch.ChannelSftp;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;
import com.jcraft.jsch.SftpException;

import java.io.ByteArrayOutputStream;
import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.Properties;
import java.util.Set;
import java.util.Vector;


public class SshRunnable implements Runnable {
    /*
    This thread starts executing with run(), inside run() we call executeRemoteForward() which create a ssh session,
    do remote Port Forwarding and sleep the thread in it to keep it alive as once we are out of run(), thread is gone.
    While we are sleeping, however the internally the JSCH created SSH thread keeps running.
    Also we sleep until it is interrupted by someone or SSH Session gets terminated due to some reason ( checking this by isSessionConnected).
    On interruption, we start cleaning up and exit the executeRemoteForward() and throws the exception further.
    Next we catch the exception and log it and also send the job cleanup message to gpsJobService(JobService) that we are done.

    In Android, phone starts stopping jobs as sleeps, so here gpsJobService calls onStopJob to intimate that I am going to take
    it down, do your cleanup. So to get the message in a thread, I set put a interrupt in that function and I do cleanup.
    Basic Flow -
    gpsJobService -> Enters onStartJob() -> SshRunnable & -->  true - says SshRunnable is running in background --> Exits onStartJob()
    SshRunnable -> Enters run() -> executeRemoteForward() -> jobFinished() -> Exits run()
    Only in case when android kills the job due to sleep , this happens
    gpsJobService --> Enters onStopJob() --> I call thread.interrupt to notify SshRunnable to terminate --> -> jobFinished() -> Exits run()


     */

    public SharedPreferences sharedPref;
    private Context context;
    private Session session = null;
    private String server;
    private Intent intent;


    /**
     *
     * An interface that defines methods that myTask implements. An instance of
     * myTask passes itself to an SshRunnable instance through the
     * SshRunnable constructor, after which the two instances can access each other's
     * variables.
     */


    public SshRunnable(Context context, Intent intent) {
        this.context=context;
        this.intent=intent;
        sharedPref = context.getSharedPreferences(intent.getStringExtra("prefFile"),Context.MODE_PRIVATE);
        this.server = intent.getStringExtra("server");

    }


    @Override
    public void run() {

        /*
         * Stores the current Thread in the the SshTask instance, so that the instance
         * can interrupt the Thread.
         */

        // Moves the current Thread into the background
        //This approach reduces resource competition between the Runnable object's thread and the UI thread.
        android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_BACKGROUND);

        try {
            String addr = sharedPref.getString(server + "addr", "192.168.44.218");
            int port = sharedPref.getInt(server + "port", 1900);
            String username = sharedPref.getString(server + "username", "root");
            String password = sharedPref.getString(server + "password", "12shroot");
            boolean lpf = sharedPref.getBoolean(server + "lpf", false);
            int lpf_lport = sharedPref.getInt(server + "lpf_lport", 2022);
            String lpf_rhost = sharedPref.getString(server + "lpf_rhost", "localhost");
            int lpf_rport = sharedPref.getInt(server + "lpf_rport", 22);
            boolean rpf = sharedPref.getBoolean(server + "rpf", true);
            int rpf_rport = sharedPref.getInt(server + "rpf_rport", 2022);
            String rpf_lhost = sharedPref.getString(server + "rpf_lhost", "localhost");
            int rpf_lport = sharedPref.getInt(server + "rpf_lport", 9999);
            boolean cli = sharedPref.getBoolean(server + "cli", false);
            String cmd = sharedPref.getString(server + "cmd", "ls");
            boolean sftp = sharedPref.getBoolean(server + "sftp", false);
            // String spath = sharedPref.getString(server + "spath", "");
            // String dpath = sharedPref.getString(server + "dpath", "");
            //executeRemoteForward("192.168.44.218", 1900,"root","12shroot" );
            // executeRemoteForward("192.168.44.218", 1900,"root","12shroot" , false, 2022, "localhost",22, true, 2022, "localhost", 9999, false, "ls");

            MainActivity.serviceIntent.putExtra(server,true);
            //MainActivity.serviceIntent.putExtra(server+"completed",false);
            //MainActivity.serviceIntent.putExtra(server+"status","Connecting");
            executeSSH(addr, port, username, password, lpf, lpf_lport, lpf_rhost, lpf_rport, rpf, rpf_rport, rpf_lhost, rpf_lport, cli, cmd);
            sharedPref = null;
        } catch (Exception e) {
            //MainActivity.ipView.append("Exception: " + e.getMessage());
            MainActivity.serviceIntent.putExtra(server+"status", "Error:  "+e.getMessage());
            MainActivity.serviceIntent.putExtra(server,false);
            MainActivity.bv.setBoo(false);

            Message message = mHandler.obtainMessage(1, "Exception: " + e.getMessage());
            message.sendToTarget();
        } finally {
            Message message = mHandler.obtainMessage(1, "Finally");
            message.sendToTarget();
            /*
            MainActivity.serviceIntent.removeExtra(server + "uriList");
            MainActivity.serviceIntent.removeExtra(server + "sftp");
            //MainActivity.serviceIntent.removeExtra(server + "status");
            MainActivity.serviceIntent.removeExtra(server + "completed");
            MainActivity.serviceIntent.removeExtra(server);
            
             */
            myfgService.threadMap.remove(server);
            if(myfgService.threadMap.isEmpty()){
                context.stopService(intent);}

        }
    }
    public String queryName(ContentResolver resolver, Uri uri) {
        Cursor returnCursor = resolver.query(uri, null, null, null, null);
        assert returnCursor != null;
        returnCursor.moveToFirst();
        String name = returnCursor.getString(returnCursor.getColumnIndex(OpenableColumns.DISPLAY_NAME));
        returnCursor.close();
        return name;
    }


    public void executeSSH(String addr, int port, String username, String password, boolean lpf, int lpf_lport, String lpf_rhost, int lpf_rport, boolean rpf, int rpf_rport, String rpf_lhost, int rpf_lport, boolean cli, String cmd) throws FileNotFoundException, JSchException, SftpException, InterruptedException {

        try {
            JSch jsch = new JSch();
            session = jsch.getSession(username, addr, port);
            session.setPassword(password);
            // Avoid asking for key confirmation
            Properties prop = new Properties();
            prop.put("StrictHostKeyChecking", "no");
            session.setConfig(prop);

            session.connect(10000);
            session.setServerAliveInterval(60000);
            //myLogger.i(Tag, "Connected to SSH server..");

            if (lpf) {
                session.setPortForwardingL(lpf_lport, lpf_rhost, lpf_rport);
            }
            if (rpf) {
                session.setPortForwardingR(rpf_rport, rpf_lhost, rpf_lport);
            }

            if (cli) {
                // SSH Channel
                ChannelExec channelssh = (ChannelExec) session.openChannel("exec");
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                channelssh.setOutputStream(baos);
                // Execute command
                channelssh.setCommand(cmd);
                channelssh.connect();
                channelssh.disconnect();
                //myLogger.i(Tag, "CMD Output: " + baos.toString());
                //return
            }
            // This checks if it is a SFTP request
            if (MainActivity.serviceIntent.getBooleanExtra(server + "sftp", false)) {
                //myLogger.i(Tag, "SFTP Started");
                // Log.i("SshRunnable", "SFTp started");

                ChannelSftp sftpChannel = (ChannelSftp) session.openChannel("sftp");
                sftpChannel.connect();
                ContentResolver cr = context.getContentResolver();
                String home=sftpChannel.getHome();
                // Check if it is a download request
                String msg;
                while ((msg = MainActivity.downloadQueue.take()) != "exit"){
                    //Message message1 = mHandler.obtainMessage(1, msg);
                    //message1.sendToTarget();
                //while (msg != "next") {}


                    //MainActivity.progressBar.setIndeterminate(false);
                    //Toast.makeText(context, " SFTP Connected ", Toast.LENGTH_LONG).show();
                    // sftpChannel.put(context.getContentResolver().openInputStream(Uri.parse(spath)), dpath, new ProgressMonitor());
                    //sftpChannel.put(context.getContentResolver().openInputStream(Uri.parse(spath)), dpath)

                    if (msg.contains(server+"ls ")) {

                        sftpChannel.cd(msg.replaceFirst(server+"ls ", "")+home);

                        Vector filelist = sftpChannel.ls(".");
                        //MainActivity.directoryList.clear();
                        ArrayList<String> newList = new ArrayList<>();
                        newList.add(sftpChannel.pwd());
                        //Message message1 = mHandler.obtainMessage(1, MainActivity.serviceIntent.getStringExtra(server + "file"));
                        //message1.sendToTarget();
                        for (int i = 0; i < filelist.size(); i++) {
                            ChannelSftp.LsEntry entry = (ChannelSftp.LsEntry) filelist.get(i);
                            if (entry.getAttrs().isDir()) {
                                newList.add(entry.getFilename() + "/");
                            } else {
                                newList.add(entry.getFilename());
                            }
                        }
                        //Message message = mHandler.obtainMessage(1, "ls"+msg.replaceFirst(server+"ls ", ""));
                        //message.sendToTarget();
                        home="/";
                       MainActivity.serviceIntent.putExtra(server+"uriList", newList);
MainActivity.bv.setBoo(false);

                    }
                    // If request is for downloading a file
                    else if (msg.contains(server+"get ")) {
                        String file = msg.replaceFirst(server+"get ", "");
                        //Message message = mHandler.obtainMessage(1, "get");
                        //message.sendToTarget();
                        ArrayList<String> uriList = MainActivity.serviceIntent.getStringArrayListExtra(server + "uriList");
                        //Traversing the string uriList
                        for (String uri : uriList) {
                            MainActivity.serviceIntent.putExtra(server + "status", "Fetching... " + sftpChannel.pwd()+"/"+file);
                            MainActivity.bv.setBoo(true);
                            sftpChannel.get(file, cr.openOutputStream(Uri.parse(uri)));
                        }

                        MainActivity.serviceIntent.putExtra(server + "status", "Completed " + file);
                        MainActivity.bv.setBoo(true);

                    } else if (msg.contains(server+"put ")) {
                        home=sftpChannel.getHome();
                        ArrayList<String> uriList = MainActivity.serviceIntent.getStringArrayListExtra(server + "uriList");
                        //Message message = mHandler.obtainMessage(1, "put" + msg);
                        //message.sendToTarget();
                        int size = uriList.size();
                        //Traversing the string uriList, just another way
                        for (int i = 0; i < size; i++) {
                            String uri = uriList.get(i);
                            String fileName = queryName(cr, Uri.parse(uri));
                            MainActivity.serviceIntent.putExtra(server + "status", "Uploading... " + (i + 1) + "/" + size + " " +  home+"/"+fileName);
                            MainActivity.bv.setBoo(true);
                            sftpChannel.put(cr.openInputStream(Uri.parse(uri)), home + "/" + fileName);
                        }
                        MainActivity.serviceIntent.putExtra(server + "status", "Completed " + size + "/" + size);
                        // MainActivity.serviceIntent.putExtra(server+"status", "OK");
                        MainActivity.bv.setBoo(true);

                    }
/*
                    try {

                        Thread.sleep(1 * 60000);
                        break;
                    } catch (InterruptedException e) {
                        if (!MainActivity.serviceIntent.getBooleanExtra(server, false)) {
                            break;
                        }

                        // Message message = mHandler.obtainMessage(1, "ExceptionInterrupt" + e.getMessage());
                        //message.sendToTarget();
                    } catch (Exception em) {
                        //Message message = mHandler.obtainMessage(1, "Exception" + em.getMessage());
                        //message.sendToTarget();
                        break;
                    }

 */
                }
                if (sftpChannel.isConnected()) {
                    sftpChannel.disconnect();
                }
            }
        }


        catch (Exception e){
            //   Message message = mHandler.obtainMessage(1, "Exception :" + e.getMessage());
            // message.sendToTarget();
            throw e;
        }


        finally {
            //myLogger.i(Tag, "Cleaning up SSH Session");
            //   Message message = mHandler.obtainMessage(1, "Running Finally ");
            // message.sendToTarget();
            try {
                if (lpf) { session.delPortForwardingL(lpf_lport); }
            }
            catch (Exception e){}
            try { if (rpf){session.delPortForwardingR(rpf_rport);}}
            catch (Exception e){}
            if(session != null){ session.disconnect();}
            // Message message2 = mHandler.obtainMessage(1, "Cleaning up done");
            //message2.sendToTarget();
        }

    }
    public void readSessions(){

        //SharedPreferences sharedPref = this.getPreferences(Context.MODE_PRIVATE);
        if (sharedPref.contains("sessions")) {
            try {
                Set<String> set = sharedPref.getStringSet("sessions", null);
            } catch (Exception e) {
            }
        }

    }
    Handler mHandler = new Handler(new Handler.Callback() {

        @Override
        public boolean handleMessage(Message msg) {

            Toast.makeText(context, msg.toString(), Toast.LENGTH_LONG).show();


            //Print Toast or open dialog
            return true;
        }
    });



}
