package com.android.test;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.ParcelFileDescriptor;
import android.provider.DocumentsContract;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;
import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.StringTokenizer;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

/*

How it all designed,

UI 
- A logical Layout with scrollbar 
    - A spinner which holds all SSH sessions
- A logical layout holding components to create a new session, named as create
 - Servername, username, password, ports etc.
- A logical layout holding components to download/upload the files, named as ftp
  - Progressbar, a text view for status and a button to download/upload files and switchbar for download/upload mode
- A menubar Items to Edit/Delete sessions and Exit the UI.

- When you select items in spinner that session is stored in a string variable selectedServer to uniquely identify a session
- A SSH thread is created which do a while loop on a blocking queue used as a cmd pipe between Main UI and SSH thread
  - URI of files to upload and save to is handled with scoped storage and passed as Intent items to the SSH thread
  - Eg. selectedServer+ls + /   
  - Eg. selectedServer+put filename 
  - Eg. selectedServer+get filename

- Scoped Storage is used, which is basically URI based
  - So for uploading a file, prompt user to select files to upload and send all selected files URI as list to SSH thread over Intent
  - For downloading a file, browser the file name from ListView and prompt user to save the file which allocates a URI, so further that URI is passed to SSH thread to do 
  -- actual download
  - serviceIntent.putExtra(selectedServer+"uriList", uriList)
  - serviceIntent.putExtra(selectedServer+"sftp", true)
  
*/


public class MainActivity extends Activity  {
    static final int SELECT_REQ = 23;
    static final int PICK_REQ = 20;
    static final int WRITE_REQ = 25;
// SharedPrefernces to save and load persistent sessions
    SharedPreferences sharedPref = null;
// List of sessions for spinner
    ArrayList<String> sessionList = null;
// List of directories and files from SFTP for ListView
    ArrayList<String> directoryList = null;
// A blocking queue holding for messages to SFTP session
    public static BlockingQueue<String> downloadQueue;
    Spinner spinner = null;
    ListView list = null;

    ArrayAdapter<String> arrayAdapter1;
    String cmd="";

    LinearLayout create = null;
    LinearLayout ftp = null;
    LinearLayout display = null;
    String selectedServer = null;
    public TextView ipView = null;
    public  ProgressBar progressBar = null;
    public Switch switchBar = null;
// An intent which can carry string, boolean and list 
    public static Intent serviceIntent = null;
   // public static TextView selectedFile = null;

    public static BooVariable bv;


// Creates main UI display
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        downloadQueue = new LinkedBlockingQueue<String>() ;
        sharedPref = this.getSharedPreferences("sii", this.MODE_PRIVATE);
      //  if (savedInstanceState != null) {
       /*When rotation occurs
        Example : time = savedInstanceState.getLong("time_state", 0); */
       // } else {
            //When onCreate is called for the first time

// Intializing previously declared components

        sessionList = new ArrayList<>();
        directoryList = new ArrayList<>();
        spinner = (Spinner) findViewById(R.id.spinner);
        list = (ListView) findViewById(R.id.list);
        create = (LinearLayout) findViewById(R.id.create);
        ftp = (LinearLayout) findViewById(R.id.ftp);
        display = (LinearLayout) findViewById(R.id.display);
        //selectedFile = (TextView) findViewById(R.id.selectedFile);
        ipView = (TextView) findViewById(R.id.ipview);
        progressBar = (ProgressBar) findViewById(R.id.progressBar);
        switchBar = (Switch) findViewById(R.id.switch1);



        serviceIntent = new Intent();

// Creating spinner for UI holding sessions
        createSpinner();

// On selected Item listener, let you select the sessions
        spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            public void onItemSelected(AdapterView<?> adapterView, View view, int pos, long id) {
                selectedServer = adapterView.getItemAtPosition(pos).toString();
                if (selectedServer.matches("HTTP")) {
                    progressBar.setIndeterminate(false);
                    ipView.setText(serviceIntent.getStringExtra(selectedServer + "status"));
                    if(serviceIntent.getBooleanExtra(selectedServer+"completed",false)) {
                        progressBar.setProgress(100);
                    }
                    else{
                    progressBar.setProgress(0);}
                    create.setVisibility(View.GONE);
                    ftp.setVisibility(View.VISIBLE);
                }
                else if (selectedServer.matches("New")) {
                    ftp.setVisibility(View.GONE);
                    create.setVisibility(View.VISIBLE);
                    }
                else {
                    ipView.setText(serviceIntent.getStringExtra(selectedServer + "status"));
                    progressBar.setIndeterminate(false);
                    if(serviceIntent.getBooleanExtra(selectedServer+"completed",false)) {
                        progressBar.setProgress(100);
                    }else{  progressBar.setProgress(0);}

                    if (create.getVisibility() == View.VISIBLE) {
                        create.setVisibility(View.GONE);
                    }
                    if (ftp.getVisibility() == View.GONE) {
                        ftp.setVisibility(View.VISIBLE);
                    }
                        monitorUpload();
                    }
            }

            public void onNothingSelected(AdapterView<?> adapterView) {
                return;
            }
        });
//selectedFile.addTextChangedListener(watcher);
        directoryList.add("Exit");
        arrayAdapter1 = new ArrayAdapter<String>(this, android.R.layout.simple_selectable_list_item, directoryList);
        //arrayAdapter1.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        //  arrayAdapter1.setNotifyOnChange(false);
        list.setAdapter(arrayAdapter1);

// A listener triggered by SFTP thread,so that UI can be updated with progress etc..

        bv = new BooVariable();
        bv.setListener(new BooVariable.ChangeListener() {
            @Override
            public void onChange() {

                    runOnUiThread(
                            new Runnable() {
                                @Override
                                public void run() {
                                    if(bv.isBoo()){monitorUpload();}
                                    else{ monitorDownload();}


                                }
                            });



            }
        });

// On click listener for the list, which gives a file browsing like feel

        list.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            public void onItemClick(AdapterView<?> adapterView, View view, int pos, long id) {
                String selection = directoryList.get(pos);
             
// If you click top item of ListView which is to Exit the listview while browsing SFTP server FS
                if (pos == 0 ) {
                    ftp.setVisibility(View.VISIBLE);
                    display.setVisibility(View.GONE);
return ;
                    // ipView.setText("clicked" + selection);

                }
// When you click second item, which is basically the current path, so not doing anything
                else if(pos == 1 ) {
                    return ;
                }
// When you click on a directory to browser further
                else {
                    if (selection.matches("(.*)/")) {
// Preparing input for sshServer while loop, basically accept, session and ls with space and directory path

                            cmd=selectedServer+"ls "+directoryList.get(1) + "/" + selection.replace("/","");
                        try {
// This triggers the sshServer as while is waiting there for any input
                            downloadQueue.put(cmd);
                        } catch (InterruptedException e) {

                        }
// When you choose a file to download

                    } else {
// Making cmd ready for downloadQueue which will initiate file download, not putting to downloadQueue, until we get URI as part of scoped storage in next step
                        cmd=selectedServer+"get "+selection;
                        ftp.setVisibility(View.VISIBLE);
                        display.setVisibility(View.GONE);
                        ipView.setText(selection);
// Let you create a uri to save file as in scoped storage
                        createFile(selection);

                    }


                }


            }
        });

  

            }



// Populate the spinner data

    private void createSpinner(){
        readSessions();
        ArrayAdapter<String> arrayAdapter = new ArrayAdapter<String>(this,android.R.layout.simple_spinner_item, sessionList);
        arrayAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(arrayAdapter);
    }
    

// When Share File/Download/Upload button is clicked

    public void selectFile(View view ){
        // Checks if any activity is already in Progress
            // if(!switchBar.isChecked()) {
            if (switchBar.isChecked()) {
              display.setVisibility(View.VISIBLE);
            }
                //  Toast.makeText(this, "Dont know.", Toast.LENGTH_LONG).show();
                // Try only if directory mode is not true, or we if not know we assume it is set
            else {
                Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                intent.addCategory(Intent.CATEGORY_OPENABLE);
//Let you select mulitple files
                intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
                // intent.setType("text/plain");
// Let you select all type of files
                intent.setType("*/*");
                // intent.setType("application/pdf");
                // intent.setType("image/*|application/pdf|text/plain|audio/*");
                if (selectedServer.equals("HTTP")) {
                    // if(view.getId() == R.id.circle_button){
                    startActivityForResult(intent, PICK_REQ);
// it doesn't end here, onActivityResult takes on once startActivityForResult is called, so check out onActivityResult

                }
                // else if(view.getId() == R.id.circle_button1){

                else {
                    startActivityForResult(intent, SELECT_REQ);
                }
            }

         

    }

    public void createFile(String fileName) {
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        intent.putExtra(Intent.EXTRA_TITLE, fileName);
        startActivityForResult(intent, WRITE_REQ);
    }

// Read the input data from NEW Session, store in SharedPref and populate the spinner with new session


   public void saveData (View view ){
       EditText serverText = (EditText) findViewById(R.id.server);
       EditText hostText = (EditText) findViewById(R.id.host);
       EditText  passwordText = (EditText) findViewById(R.id.password);
       EditText  lpfText = (EditText) findViewById(R.id.lpf_addr);
       EditText rpfText = (EditText) findViewById(R.id.rpf_addr);
       CheckBox lpfBool = (CheckBox) findViewById(R.id.lpf);
       CheckBox rpfBool = (CheckBox) findViewById(R.id.rpf);
       String server,addr,username, lpf_rhost, rpf_lhost,cmd,spath,dpath;
       server=addr=username=lpf_rhost=rpf_lhost=cmd=spath=dpath=null;
        int lpf_lport,lpf_rport,rpf_rport,rpf_lport,port;
       lpf_lport=lpf_rport=rpf_rport=rpf_lport=port=0;
        boolean lpf, rpf, cli,sftp;
        lpf=rpf=cli=sftp=false;

       //serverText=(EditText) findViewById(R.id.server);
      server=serverText.getText().toString().trim();
      if(server.equals("") || server.startsWith(" ")) {
          Toast.makeText(this, server + " !! Empty Name ", Toast.LENGTH_LONG).show(); return;}

      if(sessionList.contains(server) && !server.equals(selectedServer)){Toast.makeText(this, server + " !! Already Exists ", Toast.LENGTH_LONG).show(); return;}
        // hostText=(EditText) findViewById(R.id.host);
       String host=hostText.getText().toString().trim();
       StringTokenizer st = new StringTokenizer(host, "@:");
       if(st.countTokens() != 3 ){Toast.makeText(this, "Invalid Entry : username@hostname:port", Toast.LENGTH_LONG).show(); return;}
       while (st.hasMoreTokens()) {
         username = st.nextToken();
         addr = st.nextToken();
         port = Integer.parseInt(st.nextToken());
       }
     
      // passwordText=(EditText)findViewById(R.id.password);
       String password=passwordText.getText().toString().trim();


       lpf = lpfBool.isChecked();


       //lpfText=(EditText)findViewById(R.id.lpf_addr);
       String lpf_addr=lpfText.getText().toString().trim();
       StringTokenizer st1 = new StringTokenizer(lpf_addr, ":");
       if(lpf) {
           if (st1.countTokens() != 3) {
               Toast.makeText(this, "Invalid Entry", Toast.LENGTH_LONG).show();
               return;
           }
       }
       while (st1.hasMoreTokens()) {
          lpf_lport = Integer.parseInt(st1.nextToken());
          lpf_rhost = st1.nextToken();
          lpf_rport = Integer.parseInt(st1.nextToken());
       }

       rpf = rpfBool.isChecked();

     //rpfText=(EditText)findViewById(R.id.rpf_addr);
       String rpf_addr=rpfText.getText().toString().trim();
       StringTokenizer st2 = new StringTokenizer(rpf_addr, ":");
       if(rpf) {
           if (st2.countTokens() != 3) {
               Toast.makeText(this, "Invalid Entry", Toast.LENGTH_LONG).show();
               return;
           }
       }
       while (st2.hasMoreTokens()) {
          rpf_rport = Integer.parseInt(st2.nextToken());
          rpf_lhost = st2.nextToken();
          rpf_lport = Integer.parseInt(st2.nextToken());
       }
       cli=true;
       cmd="ls";
 boolean isSaved=storePref(server, addr, port, username, password, lpf, lpf_lport, lpf_rhost, lpf_rport, rpf, rpf_rport, rpf_lhost, rpf_lport, cli, cmd, sftp );

// Not calling spinner update, as observed just updating the arrayList sessionlist is enough here which is done in storePref previous function, no need to notify the adapter 
//updateSpinner(server);
       if(isSaved) {
           //Toast.makeText(this, "Saved", Toast.LENGTH_LONG).show();
           serverText.setText("");
           hostText.setText("");
           passwordText.setText("");
           lpfBool.setChecked(false);
           lpfText.setText("");
           rpfBool.setChecked(false);
           rpfText.setText("");

           spinner.setSelection(sessionList.indexOf(server));
           if (create.getVisibility() == View.VISIBLE) {
               create.setVisibility(View.GONE);
           }
           if (ftp.getVisibility() == View.GONE) {
               ftp.setVisibility(View.VISIBLE);
           }
       }
   }

// Editing a existing session, loads from SharedPref

public void loadData(String server){
    if(server.equals("HTTP") || server.equals("New")) { Toast.makeText(this, "Default", Toast.LENGTH_LONG).show();return;}
    if(ftp.getVisibility() == View.VISIBLE){ftp.setVisibility(View.GONE);}
    if(create.getVisibility() == View.GONE){create.setVisibility(View.VISIBLE);}

    EditText serverText = (EditText) findViewById(R.id.server);
    EditText hostText = (EditText) findViewById(R.id.host);
    EditText  passwordText = (EditText) findViewById(R.id.password);
    EditText  lpfText = (EditText) findViewById(R.id.lpf_addr);
    EditText rpfText = (EditText) findViewById(R.id.rpf_addr);
    CheckBox lpfBool = (CheckBox) findViewById(R.id.lpf);
    CheckBox rpfBool = (CheckBox) findViewById(R.id.rpf);
    serverText.setText(server);
    hostText.setText(sharedPref.getString(server+"username", "user")+"@"+sharedPref.getString(server+"addr", "localhost")+":"+sharedPref.getInt(server+"port", 22));
    passwordText.setText(sharedPref.getString(server+"password", ""));
    lpfBool.setChecked(sharedPref.getBoolean(server+"lpf", false));
    lpfText.setText(sharedPref.getInt(server+"lpf_lport", 0)+":"+sharedPref.getString(server+"lpf_rhost", " ")+":"+sharedPref.getInt(server+"lpf_rport", 0));
    rpfBool.setChecked(sharedPref.getBoolean(server+"rpf", false));
    rpfText.setText(sharedPref.getInt(server+"rpf_rport", 0)+":"+sharedPref.getString(server+"rpf_lhost", " ")+":"+sharedPref.getInt(server+"rpf_lport", 0));


    sharedPref.getInt(server+"job", 0);
    sharedPref.getInt(server+"freq", 1);
    sharedPref.getBoolean(server+"cli", false);
    sharedPref.getString(server+"cmd", "");

}

// Deleting a session
    public void deleteData(String server){
//Protecting NEW and HTTP sessions to delete
        if(server.equals("HTTP")|| server.equals("New")){ Toast.makeText(this, "Default", Toast.LENGTH_LONG).show();return;}
        deletePref(selectedServer);
        if(create.getVisibility() == View.VISIBLE){create.setVisibility(View.GONE);}
        spinner.setSelection(sessionList.indexOf("HTTP"));


    }


// Used by createSpinner function to load existing sessions from SharedPref
    public void readSessions(){

        //SharedPreferences sharedPref = this.getPreferences(Context.MODE_PRIVATE);
            if (sharedPref.contains("sessions")) {
                try {
                    Set<String> set = sharedPref.getStringSet("sessions", null);
                    sessionList = new ArrayList<String>(set);
                    Collections.sort(sessionList);
                } catch (Exception e) {
                }
            }
            else
            {Toast.makeText(this, "No pre-existing sessions", Toast.LENGTH_LONG).show();
               sessionList.add("HTTP");sessionList.add("New");}

    }
    public boolean deletePref(String server){
        SharedPreferences.Editor myEdit = sharedPref.edit();
        if(sessionList.contains(server)){ sessionList.remove(server);}

        HashSet<String> set = new HashSet<>(sessionList);
        set.addAll(sessionList);
        try{
            myEdit.putStringSet("sessions",set);
            myEdit.remove(server +"job");
            myEdit.remove(server+"freq");
            myEdit.remove(server+"addr");
            myEdit.remove(server+"port");
            myEdit.remove(server+"username");
            myEdit.remove(server+"password");
            myEdit.remove(server+"lpf");
            myEdit.remove(server+"lpf_lport");
            myEdit.remove(server+"lpf_rhost");
            myEdit.remove(server+"lpf_rport");
            myEdit.remove(server+"rpf");
            myEdit.remove(server+"rpf_rport");
            myEdit.remove(server+"rpf_lhost");
            myEdit.remove(server+"rpf_lport");
            myEdit.remove(server+"cli");
            myEdit.remove(server+"cmd");
            myEdit.remove(server+"sftp");

            myEdit.apply();

        }
        catch(Exception e ){ ////myLogger.e(Tag, "Got Exception in Shared Preferences" + e.getMessage());
            Toast.makeText(this, "Some issue while deleting :" + e.getMessage(), Toast.LENGTH_LONG).show();
            sessionList.add(server);
            return false;
        }
        return true;
    }


// Function which does saving for data to SharedPref
    public boolean storePref(String server, String addr, int port, String username, String password, boolean lpf, int lpf_lport, String lpf_rhost, int lpf_rport, boolean rpf, int rpf_rport, String rpf_lhost, int rpf_lport, boolean cli, String cmd, boolean sftp) {

        SharedPreferences.Editor myEdit = sharedPref.edit();

        if(!sessionList.contains(server)){ sessionList.add(server);}

        HashSet<String> set = new HashSet<>(sessionList);
        set.addAll(sessionList);
            try{
                myEdit.putStringSet("sessions",set);
                myEdit.putInt(server +"job", 3240);
                myEdit.putInt(server+"freq", 1);
                myEdit.putString(server+"addr", addr);
                myEdit.putInt(server+"port", port);
                myEdit.putString(server+"username", username);
                myEdit.putString(server+"password", password);
                myEdit.putBoolean(server+"lpf", lpf);
                myEdit.putInt(server+"lpf_lport", lpf_lport);
                myEdit.putString(server+"lpf_rhost", lpf_rhost);
                myEdit.putInt(server+"lpf_rport", lpf_rport);
                myEdit.putBoolean(server+"rpf", rpf);
                myEdit.putInt(server+"rpf_rport", rpf_rport);
                myEdit.putString(server+"rpf_lhost", rpf_lhost);
                myEdit.putInt(server+"rpf_lport", rpf_lport);
                myEdit.putBoolean(server+"cli", cli);
                myEdit.putString(server+"cmd", cmd);
                myEdit.putBoolean(server+"sftp", sftp);

                myEdit.apply();

            }
            catch(Exception e ){ ////myLogger.e(Tag, "Got Exception in Shared Preferences" + e.getMessage());
                Toast.makeText(this, "Incorrect Entry :" + e.getMessage(), Toast.LENGTH_LONG).show();
                sessionList.remove(server);
                return false;
            }
            return true;
        }

/*
// Unused here, kept for another use

// To open a directory

    public void openDirectory(View view) {

        // Choose a directory using the system's file picker.
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);

        // Provide read access to files and sub-directories in the user-selected
        // directory.
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

        // Optionally, specify a URI for the directory that should be opened in
        // the system file picker when it loads.
        //intent.putExtra(DocumentsContract.EXTRA_INITIAL_URI, uriToLoad);

        startActivityForResult(intent, 42);
    }
    public void copyDocument(View view){
       // readWrite(suri, duri);
    }

    public void createFiles(View view) {
        //stopService();
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("text/plain");
        intent.putExtra(Intent.EXTRA_TITLE, "zoftino.txt");
        startActivityForResult(intent, WRITE_REQ);
    }
*/

    public void exit(View view){
        stopHTTP();
        stopAllSSH();
        finish();
        System.exit(0);}

    @Override
    public void onActivityResult(int requestCode, int resultCode,
                                 Intent resultData) {

        super.onActivityResult(requestCode, resultCode, resultData);
        if (resultCode == Activity.RESULT_OK) {
            ArrayList<String> uriList = new ArrayList<String>();
            Uri uri = null;
            if (resultData != null) {
                if(null != resultData.getClipData()) {
                    //    uri = resultData.getData();
                    for (int i = 0; i < resultData.getClipData().getItemCount(); i++) {
                        uri = resultData.getClipData().getItemAt(i).getUri();
                        uriList.add(uri.toString());
                    }
                }
                else {uri = resultData.getData();
                    uriList.add(uri.toString());
            }

            }

            if (requestCode == SELECT_REQ) {
                // On selecting a File
                // Passing sftp put without any destination as home directory will be used
                cmd=selectedServer+"put ";
                try {
                    startSSH(uriList);
                } catch (InterruptedException e) {

                }
            } else if (requestCode == PICK_REQ) {
                // to select a File
                startHTTP(uriList);
            }
            else if (requestCode == WRITE_REQ) {
                try {
                    // cmd is already updated in onclick call but will be passed to downloadQueue in startSSH function
                    startSSH(uriList);
                } catch (InterruptedException e) {

                }

            }
        }
    }

/*
    private void editDocument(Uri uri) {

       // duri=uri;
        try {
            ParcelFileDescriptor fileDescriptor = this.getContentResolver().openFileDescriptor(uri, "w");
            FileOutputStream fileOutputStream =
                    new FileOutputStream(fileDescriptor.getFileDescriptor());
            fileOutputStream.write(("android latest updates \n").getBytes());
            fileOutputStream.write(("android latest features \n").getBytes());
            fileOutputStream.close();
            fileDescriptor.close();
        } catch (Exception e) {

        }
    }
    private void readTextFile(Uri uri) {
        //suri=uri;
        InputStream inputStream = null;
        try {
            inputStream = getContentResolver().openInputStream(uri);
            BufferedReader reader = new BufferedReader(new InputStreamReader(
                    inputStream));

            String line;
           // Log.i("","open text file - content"+"\n");
            Toast.makeText(this, reader.readLine(), Toast.LENGTH_LONG).show();
            while ((line = reader.readLine()) != null) {
                //Log.i("",line+"\n");

            }
            reader.close();
            inputStream.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void deleteFile(Uri uri) {
        Cursor cursor = this.getContentResolver()
                .query(uri, null, null, null, null);
        try {
            if (cursor != null && cursor.moveToFirst()) {
                String flags = cursor.getString(cursor.getColumnIndex(DocumentsContract.Document.COLUMN_FLAGS));
                String[] columns =  cursor.getColumnNames();
                for(String col : columns) {
                    Log.i("", "Column Flags  " + col);
                }
                Log.i("", "Delete Flags  " + flags);
                if(flags.contains(""+DocumentsContract.Document.FLAG_SUPPORTS_DELETE)){
                    DocumentsContract.deleteDocument(getContentResolver(), uri);
                }
            }
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } finally {
            cursor.close();
        }
    }
    private void readWrite(Uri suri, Uri duri){
        InputStream inputStream = null;
        try {
            inputStream = getContentResolver().openInputStream(suri);
            BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
            ParcelFileDescriptor fileDescriptor = this.getContentResolver().openFileDescriptor(duri, "w");
            FileOutputStream fileOutputStream = new FileOutputStream(fileDescriptor.getFileDescriptor());

            String line;
            // //myLogger.i("","open text file - content"+"\n");
            while ((line = reader.readLine()) != null) {
                ////myLogger.i("",line+"\n");
                fileOutputStream.write((line + "\n").getBytes());
            }
            reader.close();
            inputStream.close();
            fileOutputStream.close();
            fileDescriptor.close();
        } catch (Exception e) {
           e.printStackTrace();;
        }

    }

*/
   

// Starts SSH thread or help to make up other request once connection is up

   private void startSSH(ArrayList<String> uriList) throws InterruptedException {
       serviceIntent.putStringArrayListExtra(selectedServer+"uriList",uriList);
       serviceIntent.putExtra(selectedServer+"sftp",true);
       if (!serviceIntent.getBooleanExtra(selectedServer,false)) {

          // if(!watch){  selectedFile.addTextChangedListener(watcher);}
           Intent intent = new Intent(this, myfgService.class);
           intent.putExtra("prefFile","sii");
           intent.putExtra("server",selectedServer);
           this.startForegroundService(intent);
           downloadQueue.put(cmd);
       }
       else{
       downloadQueue.put(cmd);}
   }
   public void show(View view){
 Button b = (Button) findViewById(R.id.circle_button);

        if(switchBar.isChecked()){

            //display.setVisibility(View.VISIBLE);
            //Toast.makeText(this,"true", Toast.LENGTH_LONG).show();
            b.setText("Download");
            try {
                cmd=selectedServer+"ls ";
                startSSH(new ArrayList<String>());
            } catch (InterruptedException e) {

            }

        }
        else{

            //display.setVisibility(View.GONE);
            b.setText("Upload");
            //serviceIntent.removeExtra(selectedServer);
          //  stopSSH();


        }


   }



// Start HTTP server thread
private void startHTTP(ArrayList<String> uriList) {

    serviceIntent.putStringArrayListExtra(selectedServer+"uriList",uriList);
        if (!serviceIntent.getBooleanExtra(selectedServer,false)) {
        Intent intent = new Intent(this, myfgService.class);
        intent.putExtra("server",selectedServer);
        serviceIntent.putExtra(selectedServer,true);
        this.startForegroundService(intent);
        // stopService();

        //fileName.setText(serviceIntent.getStringExtra("url"));
        try {

            serviceIntent.putExtra(selectedServer+"status",getListOfIPsFromNIs());
            monitorUpload();
        } catch (SocketException e) {
        }
        // fileName.setText(getIp());
    }

}

// UI top corner menu items
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_menu, menu);
        return true;
    }
// When App minimizes to save state

    @Override
    public void onSaveInstanceState(Bundle outState) {
    /*Save your data to be restored here
    Example : outState.putLong("time_state", time); , time is a long variable*/
        super.onSaveInstanceState(outState);
    }

// When you select a menu time
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();
        if (id == R.id.OptEdit) {
            display.setVisibility(View.GONE);
            ftp.setVisibility(View.VISIBLE);
            loadData(selectedServer);
            return true;
        }
        if (id == R.id.OptDelete) {
            deleteData(selectedServer);
            return true;
        }
        if (id == R.id.OptExit) {
           stopSSH();
            finish();
            System.exit(0);
            return true;
        }

        return super.onOptionsItemSelected(item);
    }


    public void stopHTTP() {
        try {
            if (myfgService.threadMap.get(selectedServer).isAlive()) {
                serviceIntent.putExtra(selectedServer, false);

                if(httpServRunnable.serverSocket != null){
                    if(httpServRunnable.clientSocket != null) {
                        httpServRunnable.clientSocket.close();
                    }
                    httpServRunnable.serverSocket.close();

                }
            }
        } catch (Exception e) {
            // Toast.makeText(this,  e.getMessage() , Toast.LENGTH_LONG).show();
        }

        ipView.setText("");
       // selectedFile.setText("");
        progressBar.setProgress(0);
        // this.stopService(serviceIntent);
    }
    public void stopSSH() {
        try {
            ipView.setText("");
            progressBar.setIndeterminate(false);
            progressBar.setProgress(0);
            serviceIntent.removeExtra(selectedServer+"status");
            if (myfgService.threadMap.get(selectedServer).isAlive()) {
                serviceIntent.putExtra(selectedServer, false);
                myfgService.threadMap.get(selectedServer).interrupt();
            }
        } catch (Exception e) {
            // Toast.makeText(this,  e.getMessage() , Toast.LENGTH_LONG).show();
        }
    }

        public void stopAllSSH() {
            for(Map.Entry<String, Thread> entry: myfgService.threadMap.entrySet()) {
                try {

                    if (myfgService.threadMap.get(selectedServer).isAlive()) {
                        serviceIntent.putExtra(selectedServer, false);
                        myfgService.threadMap.get(selectedServer).interrupt();
                    }
                } catch (Exception e) {
                    // Toast.makeText(this,  e.getMessage() , Toast.LENGTH_LONG).show();
                }
            }
        }

        // this.stopService(serviceIntent);


    public void monitorUpload() {
        if (serviceIntent.getBooleanExtra(selectedServer, false)) {
            String status = serviceIntent.getStringExtra(selectedServer + "status");
            ipView.setText(status);
            if(status.contains("Completed")){
                progressBar.setIndeterminate(false);
                progressBar.setProgress(100);
            }
            else{
                progressBar.setIndeterminate(true);}
        }
    }
    public void monitorDownload(){
       // if (serviceIntent.getBooleanExtra(selectedServer, false)) {
        if (serviceIntent.getBooleanExtra(selectedServer, false)) {
            directoryList.clear();
            directoryList.add("Exit");
            directoryList.addAll(serviceIntent.getStringArrayListExtra(selectedServer + "uriList"));
            arrayAdapter1.notifyDataSetChanged();
        }
        else {ftp.setVisibility(View.VISIBLE);
        display.setVisibility(View.GONE);}
      //  display.setVisibility(View.VISIBLE);
       // list.refreshDrawableState();
       // }
       //else {
           //ftp.setVisibility(View.VISIBLE);
           //display.setVisibility(View.GONE);
            //progressBar.setIndeterminate(false);
            //ipView.setText(serviceIntent.getStringExtra(selectedServer + "status"));
            //progressBar.setProgress(100);
        //}



    }

// Returns all IPs from Network Interfaces

    public  String getListOfIPsFromNIs() throws SocketException {
    String status = "";
        ipView.setText("");
        List<InetAddress> addrList           = new ArrayList<InetAddress>();
        Enumeration<NetworkInterface> enumNI = NetworkInterface.getNetworkInterfaces();
        while ( enumNI.hasMoreElements() ){
            NetworkInterface ifc = enumNI.nextElement();
            if( ifc.isUp() ){
                Enumeration<InetAddress> enumAdds = ifc.getInetAddresses();
                while ( enumAdds.hasMoreElements() ){
                    InetAddress addr = enumAdds.nextElement();
                    if(!addr.isLoopbackAddress() && addr instanceof Inet4Address) {
                                //System.out.println(addr.getHostAddress());   //<---print IPstop
                        status=status+"http://" + addr.getHostAddress() + ":9999/ \n\n";
                       // ipView.append("http://" + addr.getHostAddress() + ":9999/ \n\n");
                    }

                    //Toast.makeText(this,  addr.getHostAddress().toString() , Toast.LENGTH_LONG).show();
                }
            }
        }
        return status;
    }
}