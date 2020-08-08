package com.android.test;


import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.provider.OpenableColumns;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.StringTokenizer;

public class httpServRunnable implements Runnable {
    /*
    Here we create a ServerSocket to listen on 9999, then it waits for client connection using ServerSocketObject.accept
    Once someone is connected we make three streams one for reading from client, other two to write header and content.
    In HTTP communication, when client connects it sends HTTP Request consists of many lines, in minimal, we care about
    first line with GET Info which method and path of file, and ignore others for now.
    GET /logs.txt HTTP/1.1
    Same while sending HTTP Response, we put details in header, the important one is
    Content-length: 2343"
    And we send the requested file in Response Payload
    Note: We use three streams for these.

     */
    public static  Socket clientSocket = null;
    public static ServerSocket serverSocket = null;
    public ContentResolver cr = null;
    private HashMap<String,Long> nameSize = null;
    private HashMap<String,Uri> nameUri = null;
    private Context context;
    String server;
    private Intent intent;
    BufferedReader in = null; PrintWriter out = null; BufferedOutputStream dout = null;
    public httpServRunnable(Context context, Intent intent) {
       this.context = context;
       this.intent = intent;
       server = intent.getStringExtra("server");
    }

    public String server( int port) throws Exception {
        int SERVER_PORT = port;


        // Creates a server socket on port 9999

          serverSocket = new ServerSocket(SERVER_PORT);
         serverSocket.setSoTimeout(2*60000);
        while (true) {
            // Thread waits here for socket connection
          //  Message message = mHandler.obtainMessage(1, "Listening");
           // message.sendToTarget();
           // Message message1 = mHandler.obtainMessage(1, "Listening");
           // message1.sendToTarget();
            clientSocket = serverSocket.accept();
            clientSocket.setSoTimeout(1*60000);
            if(nameSize == null ) {
                nameSize = new HashMap<String, Long>();
                nameUri = new HashMap<String, Uri>();
               cr = context.getContentResolver();
            }
              //  clientSocket.setKeepAlive(true);
            //clientSocket.setSoTimeout(1*60000);
            //Message message = mHandler.obtainMessage(1, "New Conn");
            //message.sendToTarget();
            try{
           //Message message = mHandler.obtainMessage(1, "Connected");
             //message.sendToTarget();


                // read characters from the client via input stream on the socket
                in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));

                // character output stream to client (for headers)
                out = new PrintWriter(clientSocket.getOutputStream());

                // binary output stream to client (for requested data)
                dout = new BufferedOutputStream(clientSocket.getOutputStream());

                String inputLine;

                // Initiate conversation with client, this class handles application functionality
                //Checking null in input BufferReader which is normally EOF when client disconnects
                // Once we see a null, streams and socket is closed and starts listening again for more requests
                // CURL uses different ports for each request so we will be seeing NULL to frequent after each request
                // Firefox uses same port until closed so work efficently.
                while ((inputLine = in.readLine()) != null ) {
               // while ((inputLine = in.readLine()) != null ) {
                    //get first line of the request having GET from the client and ignoring other lines came in
                    //as part of the single http request.
                    if (inputLine.startsWith("GET")) {
                        // Calling my class which process the request and response back over streams to client, so passing reference
                        // reference of my streams
                            processInput(inputLine);
                        //out.println(outputLine);
                    }
                }
            }catch (SocketTimeoutException se) {

            }

            catch (IOException ie) {

            }
            catch (Exception e) {
                throw e;

            }



            finally {
                //try { in.close(); } catch (Exception i){//myLogger.i(Tag, "Some issue cleaning up BufferedReader Streams" + i.getMessage());}
                try { in.close(); } catch (Exception is){
                    //myLogger.i(Tag, "Some issue cleaning up BufferedReader Streams" + is.getMessage());}
                }
                try { out.close(); } catch (Exception out1) {
                    //myLogger.i(Tag, "Some issue cleaning up PrintWriter Streams" + out1.getMessage());}
                }
                try { dout.close(); } catch (Exception dout1){
                    //myLogger.i(Tag, "Some issue cleaning up BufferedOutput Streams" + dout1.getMessage());}
                }
                try { clientSocket.close(); } catch (Exception socket){
                    //myLogger.i(Tag, "Some issue cleaning up Socket Streams" + socket.getMessage());}
                }
               // Message message1 = mHandler.obtainMessage(1, "Cleanup done");
                //message1.sendToTarget();
                if (Thread.currentThread().isInterrupted()) {
                    //myLogger.i(Tag, "Interrupt received ");
                  //  Message message1 = mHandler.obtainMessage(1, "Thread Interrupted");
                   // message1.sendToTarget();
                    break;
                }
            }
        }
        serverSocket.close();
        return "Done";
    }
   private void processInput(String inputLine) throws IOException {
       StringTokenizer parse = new StringTokenizer(inputLine);
       String method = parse.nextToken().toUpperCase(); // we get the HTTP method of the client
       // we get file requested
       String fileRequested = parse.nextToken().replaceAll("^/","").replaceAll("%20"," ");
       if (fileRequested.contentEquals("")) {
         //  MainActivity.serviceIntent.putExtra(server,true);
          // MainActivity.serviceIntent.putExtra(server+"file",true);
           //MainActivity.serviceIntent.stre
           myfgService.threadMap.get(server).interrupt();

           nameSize.clear();
           nameUri.clear();
           String html = "<html><body><h1>";
           ArrayList<String> uriList = MainActivity.serviceIntent.getStringArrayListExtra(server+"uriList");
           for (int i = 0; i < uriList.size();i++)
           {
               String fileName = queryName(cr,Uri.parse(uriList.get(i)));

               html = html + "<a href=\""+fileName+"\" style=\"text-decoration: none;\">"+ fileName + "</a>"+ "<br>\n" ;
           }
           html = html + "</h1></body></html>";
           byte[] b = html.getBytes();
           out.println("HTTP/1.1 200 OK");
           out.println("Android http server");
           out.println("Connection: keep-alive");
           out.println("Date: ");
           out.println("Content-type: text/html");
           out.println("Content-length: " + html.length());
           out.println(); // blank line between headers and content, very important !
           out.flush(); // flush character output stream buffer
               dout.write(b);
               dout.flush();


           //String content = getContentType(fileRequested);

       }
       else if (nameSize.containsKey(fileRequested)) {
           out.println("HTTP/1.1 200 OK");
           out.println("Android http server");
           out.println("Connection: keep-alive");
           out.println("Date: ");
           out.println("Content-type: " + cr.getType(nameUri.get(fileRequested)));
           out.println("Content-Disposition: attachment; filename=\"" + fileRequested + "\"");
           out.println("Content-length: " + nameSize.get(fileRequested));
           out.println(); // blank line between headers and content, very important !
           out.flush(); // flush character output stream buffer

           BufferedInputStream reader = new BufferedInputStream(cr.openInputStream(nameUri.get(fileRequested)));
           byte[] buffer = new byte[16 * 1024];
           int bytesRead;
           while ((bytesRead = reader.read(buffer)) != -1) {
               dout.write(buffer, 0, bytesRead);
               ////myLogger.d(Tag, "bytesRead : " + bytesRead);
               //fileLength = fileLength - bytesRead;
               //if ( fileLength == 0 ) {dout.flush();break;}
           }
           dout.flush();
           reader.close();

       }
       else {
           String blank = "<html><body>Not Found</body></html>";
           byte[] b = blank.getBytes();
           out.println("HTTP/1.1 200 OK");
           out.println("Android http server");
           out.println("Connection: keep-alive");
           out.println("Date: ");
           out.println("Content-type: image/webp");
           out.println("Content-length: " + blank.length());
           out.println(); // blank line between headers and content, very important !
           out.flush(); // flush character output stream buffer
           dout.write(b);
           dout.flush();}
   }

    public String queryName(ContentResolver resolver, Uri uri) {
        Cursor returnCursor =
                resolver.query(uri, null, null, null, null);
        assert returnCursor != null;
        int nameIndex = returnCursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
        int sizeIndex = returnCursor.getColumnIndex(OpenableColumns.SIZE);
        returnCursor.moveToFirst();
        String name = returnCursor.getString(nameIndex);
        returnCursor.moveToFirst();
        nameSize.put(name,returnCursor.getLong(sizeIndex));
        nameUri.put(name,uri);
        returnCursor.close();
        return name;
    }


    @Override
    public void run() {
        android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_BACKGROUND);
        try {
            server(9999);
        } catch (Exception e) {
            try {
                if(serverSocket != null){serverSocket.close();}
            } catch (Exception ex) {
            }
        }
        finally {
            //Intent serviceIntent = new Intent(context, myfgService.class);
            MainActivity.serviceIntent.removeExtra(server + "uriList");
            if(myfgService.threadMap.isEmpty()){
                context.stopService(intent);}

        }
            //myLogger.i(Tag, "Server Listening Finished" );
            ////myLogger.close();
        }

/*
    Handler mHandler = new Handler(new Handler.Callback() {

        @Override
        public boolean handleMessage(Message msg) {

            Toast.makeText(context, msg.toString(), Toast.LENGTH_LONG).show();


            //Print Toast or open dialog
            return true;
        }
    });


 */




}

