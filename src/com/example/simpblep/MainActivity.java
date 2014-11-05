package com.example.simpblep;

import java.io.IOException;
import java.io.InputStream;
import java.security.GeneralSecurityException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import com.blemsgfw.BleMessage;
import com.blemsgfw.BleMessenger;
import com.blemsgfw.BleMessengerOptions;
import com.blemsgfw.BlePeer;
import com.blemsgfw.BleStatusCallback;
import com.google.common.io.ByteStreams;
import com.google.common.primitives.Bytes;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.os.Bundle;
import android.provider.ContactsContract;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.EditText;
import android.widget.Toast;

public class MainActivity extends Activity {

	private static final String TAG = "simpblep";

	BleMessenger bleMessenger;
	Map <String, BlePeer> bleFriends;
	String myFingerprint;
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);
		
		// because this is using BLE, we'll need to get the adapter and manager from the main context and thread 
		BluetoothManager btMgr = (BluetoothManager) this.getSystemService(Context.BLUETOOTH_SERVICE);
        BluetoothAdapter btAdptr = btMgr.getAdapter();
        
        // check to see if the bluetooth adapter is enabled
        if (!btAdptr.isEnabled()) {
        	Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
        	startActivityForResult(enableBtIntent, RESULT_OK);
        }

        // get an identifier for this installation
        String myIdentifier = Installation.id(this);
        
        // get your name (that name part isn't working)
        String userName = getUserName(this.getContentResolver());
        EditText yourNameControl = (EditText) findViewById(R.id.your_name);
        yourNameControl.setText(userName);

        BleMessengerOptions bo = new BleMessengerOptions();
       
        bo.FriendlyName = userName;
        bo.Identifier = myIdentifier;
        
        KeyStuff rsaKey = null;
        
		try {
			rsaKey = new KeyStuff(this, myIdentifier);
		} catch (GeneralSecurityException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
		
		bo.PublicKey = rsaKey.PublicKey();
		Log.v(TAG, "pubkey size in bytes:" + String.valueOf(bo.PublicKey.length));
       
		myFingerprint = bytesToHex(rsaKey.PuFingerprint());
		
		// create a messenger along with the context (for bluetooth operations)
		bleMessenger = new BleMessenger(bo, btMgr, btAdptr, this);
        
		// generate message of particular byte size
		byte[] bytesMessage = benchGenerateMessage(45);

		bleFriends = new HashMap<String, BlePeer>();
		
		// load up a generic message to be sent to everybody the first time i connect
		// there is no recipient, so the first 20 bytes are empty
		// the sender is our app, so the next 20 bytes are our fingerprint
		
		byte[] newMsg = Bytes.concat(new byte[20], rsaKey.PuFingerprint());
		BleMessage m = new BleMessage();
		m.setMessage(newMsg);
		
		// now add this message as our identifier to BleMessenger to send upon any new connection
		bleMessenger.idMessage = m;
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
	
	BleStatusCallback bleMessageStatus = new BleStatusCallback() {

		@Override
		public void handleReceivedMessage(String recipientFingerprint, String senderFingerprint, byte[] payload, String msgType) {

			// this is an identity message so handle it as such
			if (msgType.equalsIgnoreCase("identity")) {
				if (recipientFingerprint.length() == 0) {
					// there is no recipient; this is just an identifying message
				} else if (recipientFingerprint.equalsIgnoreCase(myFingerprint)) {
					// recipient is us!
				} else if (bleFriends.containsValue(recipientFingerprint)) {
					// the recipient is a friend of ours
				}
				
				if (bleFriends.containsValue(senderFingerprint)) {
					// we know the sender, check for any messages we want to send them
				} else {
					// we don't know the sender and should add them;
					// parse the public key & friendly name out of the payload, and add this as a new person
				}
				
				// the First message we send them, however, needs to be our own ID message
			}
			
		}
		
		@Override
		public void messageSent(UUID uuid) {
			
			final String sUUID = uuid.toString(); 
			
            runOnUiThread(new Runnable() {

                @Override
                public void run() {
                	showMessage("message sent:" + sUUID);
                }
            });
		}

		@Override
		public void remoteServerAdded(String serverName) {
			showMessage(serverName);
		}

		@Override
		public void foundPeer(BlePeer blePeer) {
			final String peerName = blePeer.GetName(); 
			
            runOnUiThread(new Runnable() {

                @Override
                public void run() {
                	showMessage("peer found:" + peerName);
                }
            });
			
		}
		
		
	};
	
	public void handleButtonBeAFriend(View view) {
		// time to advertise?
		bleMessenger.BeFound();
	}
	
	public void queueOutboundMessage(String destinationFingerprint, byte[] message) {
		
	}
	
	private void showMessage(String msg) {

		final String message = msg;
		final Context fctx = this;
		
		runOnUiThread(new Runnable() {
			  public void run() {
				  Toast.makeText(fctx, message, Toast.LENGTH_LONG).show();
			  }
			});
		
	}
	
	private byte[] benchGenerateMessage(int MessageSize) {
		// get the lorem text from file
		byte[] bytesLorem = null;
		byte[] bytesMessage = null;
		InputStream is = getResources().openRawResource(R.raw.lorem);
    			
		int currentMessageLength = 0;
		int maxcount = 0;
		
		while ((currentMessageLength < MessageSize) && maxcount < 1000) {
			maxcount++;
	    	try {
	    		if (currentMessageLength == 0) {
	    			bytesMessage = ByteStreams.toByteArray(is);
	    		}
	    		is.reset();
	    		bytesLorem = ByteStreams.toByteArray(is);
			} catch (IOException e) {
				e.printStackTrace();
			}
	    	
	    	try {
				is.reset();
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
	    	
	    	bytesMessage = Bytes.concat(bytesMessage, bytesLorem);
	    	
	    	currentMessageLength = bytesMessage.length;
    	
		}
		
		return Arrays.copyOf(bytesMessage, MessageSize);
	}
	
	private String getUserName(ContentResolver cr) {
        
        String displayName = "";
         
        Cursor c = cr.query(ContactsContract.Profile.CONTENT_URI, null, null, null, null); 
         
        try {
            if (c.moveToFirst()) {
                displayName = c.getString(c.getColumnIndex("display_name"));
            }  else {
            	displayName = "nexus5";
            	Log.v(TAG, "can't get user name; no error");	
            }
            
        } catch (Exception x) {
        	Log.v(TAG, "can't get user name; error");
        	
		} finally {
            c.close();
        }
        
        return displayName;
	}

    final protected static char[] hexArray = "0123456789ABCDEF".toCharArray();
    public static String bytesToHex(byte[] bytes) {
        char[] hexChars = new char[bytes.length * 2];
        for ( int j = 0; j < bytes.length; j++ ) {
            int v = bytes[j] & 0xFF;
            hexChars[j * 2] = hexArray[v >>> 4];
            hexChars[j * 2 + 1] = hexArray[v & 0x0F];
        }
        return new String(hexChars);
    }
	
}
