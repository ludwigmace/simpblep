package com.blemsgfw;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothProfile;
import android.content.Context;
import android.os.Handler;
import android.util.Log;

public class MyCentral {
	
	private static final String TAG = "BLECC";
	
	private boolean bFound;
	
	private Context ctx;
	private Activity myActivity;

    public final static String ACTION_GATT_CONNECTED =
            "com.example.bluetooth.le.ACTION_GATT_CONNECTED";
    public final static String ACTION_GATT_DISCONNECTED =
            "com.example.bluetooth.le.ACTION_GATT_DISCONNECTED";
    public final static String ACTION_GATT_SERVICES_DISCOVERED =
            "com.example.bluetooth.le.ACTION_GATT_SERVICES_DISCOVERED";
    public final static String ACTION_DATA_AVAILABLE =
            "com.example.bluetooth.le.ACTION_DATA_AVAILABLE";
    public final static String EXTRA_DATA =
            "com.example.bluetooth.le.EXTRA_DATA";

    private static String strSvcUuidBase = "73A20000-2C47-11E4-8C21-0800200C9A66";
    
    
    private static final int STATE_DISCONNECTED = 0;
    private static final int STATE_CONNECTING = 1;
    private static final int STATE_CONNECTED = 2;

    private BluetoothGattCharacteristic clientReadCharacteristic;
    
    private boolean bScan;
    
    private int mConnectionState = STATE_DISCONNECTED;
    
    
	// scanning happens asynchronously, so get a link to a Handler
    private Handler mHandler;
    
    private boolean mScanning;
    private ArrayList<BluetoothDevice> foundDevices;
    
    private BluetoothAdapter centralBTA;
    //private BluetoothGatt mBluetoothGatt;
    
    private MyGattClientHandler gattClientHandler;
    
    private List<BleCharacteristic> serviceDef;
    private Map<String, BluetoothGatt> gattS;
    
    // scan for 2 1/2 seconds at a time
    private static final long SCAN_PERIOD = 2500;
    
    MyCentral(BluetoothAdapter btA, Context ctx, MyGattClientHandler myHandler) {
    	centralBTA = btA;
    	
    	this.ctx = ctx;
    	//myActivity = act;
    	
        // to be used for scanning for LE devices
        mHandler = new Handler();
        
        foundDevices = new ArrayList<BluetoothDevice>(); 
        
        mScanning = false;
        
        gattClientHandler = myHandler;
        
        serviceDef = new ArrayList<BleCharacteristic>();
        
        gattS = new HashMap<String, BluetoothGatt>();
        
    }

    public void initConnect(BluetoothDevice b) {
    	b.connectGatt(ctx, false, mGattCallback);
    }
    
    public void connectAddress(String btAddress){
    	BluetoothDevice b = centralBTA.getRemoteDevice(btAddress);
    	
    	// instead of using this global variable, try something else
    	//mBluetoothGatt = b.connectGatt(ctx, false, mGattCallback);
    	b.connectGatt(ctx, false, mGattCallback);
    }
    
    
    public void setRequiredServiceDef(List<BleCharacteristic> bleChars) {
    	
    	serviceDef = bleChars;
    	
    }
    
    public boolean submitSubscription(String remoteAddr, UUID uuidChar) {
    	boolean result = false;
    	
    	BluetoothGatt gatt = gattS.get(remoteAddr);
    	BluetoothGattCharacteristic indicifyChar = gatt.getService(UUID.fromString(strSvcUuidBase)).getCharacteristic(uuidChar);
   	
    	if (indicifyChar != null) {
        	int cProps = indicifyChar.getProperties();
        	
	   	     if ((cProps & (BluetoothGattCharacteristic.PROPERTY_NOTIFY | BluetoothGattCharacteristic.PROPERTY_INDICATE)) != 0) {
		    	 Log.v(TAG, "sub for notifications from " + indicifyChar.getUuid().toString().substring(0,8));
			
				// enable notifications for this guy
		    	gatt.setCharacteristicNotification(indicifyChar, true);
				
				// tell the other guy that we want characteristics enabled
				BluetoothGattDescriptor descriptor = indicifyChar.getDescriptor(UUID.fromString(SampleGattAttributes.CLIENT_CHARACTERISTIC_CONFIG));
				
				// if it's a notification value, subscribe by setting the descriptor to ENABLE_NOTIFICATION_VALUE
				if ((cProps & BluetoothGattCharacteristic.PROPERTY_NOTIFY) != 0) {
					descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
				}

				// if it's an INDICATION value, subscribe by setting the descriptor to ENABLE_INDICATION_VALUE				
				if ((cProps & BluetoothGattCharacteristic.PROPERTY_INDICATE) != 0) {
					descriptor.setValue(BluetoothGattDescriptor.ENABLE_INDICATION_VALUE);
				}
				
				gatt.writeDescriptor(descriptor);
				
				result = true;
			
			}
    	} else {
    		Log.v(TAG, "can't pull characteristic so i can't sub it");
    	}
    	    	
		return result;
    }
    

    
    public boolean isScanning() {
    	return mScanning;
    }
    
    public boolean submitCharacteristicReadRequest(String remoteAddr, UUID uuidChar) {
    	
    	boolean charFound = false;
    	
    	BluetoothGatt gatt = gattS.get(remoteAddr);
    	BluetoothGattCharacteristic readChar = gatt.getService(UUID.fromString(strSvcUuidBase)).getCharacteristic(uuidChar);

    	if (readChar != null) {
    		Log.v(TAG, "issuing read request:" + readChar.getUuid().toString());
    		gatt.readCharacteristic(readChar);
    		charFound = true;
    	}
    	
    	return charFound;
    	
    }
    
    public boolean submitCharacteristicWriteRequest(String remoteAddr, UUID uuidChar, final byte[] val) {
		
    	boolean charWrote = false;

    	BluetoothGatt gatt = gattS.get(remoteAddr);
    	BluetoothGattCharacteristic writeChar = gatt.getService(UUID.fromString(strSvcUuidBase)).getCharacteristic(uuidChar);
    	
    	writeChar.setValue(val);
    	writeChar.setWriteType(BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE);
    		
    	try {
    		gatt.writeCharacteristic(writeChar);
    		charWrote = true;
    	} catch (Exception e) {
    		Log.v(TAG, "cannot write char ");
    		Log.v(TAG, e.getMessage());
    	}
		
		return charWrote;
    }
    
 // when a device is found from the ScanLeDevice method, call this
    private BluetoothAdapter.LeScanCallback mLeScanCallback = new BluetoothAdapter.LeScanCallback() {
        
    	@Override
        public void onLeScan(final BluetoothDevice device, int rssi, byte[] scanRecord) {
    		//Log.v(TAG, "LeScanCallback sez:" + device.getAddress());
    		if (!foundDevices.contains(device)) {
    			foundDevices.add(device);
        	}
    		
       }
    };
    
    public ArrayList<BluetoothDevice> getAdvertisers() {
    
    	return foundDevices;
    }
    
    private void connectDevices() {
    	
    	// if we've found devices, loop through and do what we gotta do
    	if (foundDevices.size() > 0) {

    		for (BluetoothDevice b: foundDevices) {
	    		b.connectGatt(ctx, false, mGattCallback);
	    		Log.v(TAG, "connect to: " + b.getAddress());
			}
    		
    	} else {
    		Log.v(TAG, "no devices found to connect!");
    	}
    }
    

    
    public void scanLeDevice(final boolean enable) {
        if (enable) {

        	// call STOP after SCAN_PERIOD ms, which will spawn a thread to stop the scan
            mHandler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    mScanning = false;
                    centralBTA.stopLeScan(mLeScanCallback);

        			gattClientHandler.intakeFoundDevices(foundDevices);
        			Log.v(TAG, "scan stopped, found " + String.valueOf(foundDevices.size()) + " devices");
                }
            }, SCAN_PERIOD);

            // start scanning!
            mScanning = true;
            
            Log.v(TAG, "scan started");
            centralBTA.startLeScan(mLeScanCallback);
            // will only scan for "normal" UUIDs
            //centralBTA.startLeScan(serviceUuids, mLeScanCallback);

        } else {
        	
        	// the "enable" variable passed wa False, so turn scanning off
            mScanning = false;
            centralBTA.stopLeScan(mLeScanCallback);
            
        }

    }

    private final BluetoothGattCallback mGattCallback = new BluetoothGattCallback() {
    	@Override
    	// Characteristic notification
    	public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {

    		// probably need to pass the GATT object, or server, etc
    		//gattClientHandler.getNotifyUpdate(characteristic.getUuid().toString(), characteristic.getValue());
    		//Log.v(TAG, "characteristic changed val is " + bytesToHex(characteristic.getValue()));
    		gattClientHandler.incomingMissive(gatt.getDevice().getAddress(), characteristic.getUuid(), characteristic.getValue());
    		
    	}
    	
    	public void onReadRemoteRssi(BluetoothGatt gatt, int rssi, int status) {
    		//defaultHandler.DoStuff(gatt.getDevice().getAddress() + " - " + String.valueOf(rssi));
    	}
    	
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            String intentAction;
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                intentAction = ACTION_GATT_CONNECTED;
                mConnectionState = STATE_CONNECTED;
                
                Log.v(TAG, "Connected to GATT server " + gatt.getDevice().getAddress());

                // save a reference to this gatt server!
                gattS.put(gatt.getDevice().getAddress(), gatt);
                gatt.discoverServices();
                //Log.i(TAG, "Attempting to start service discovery:" + mBluetoothGatt.discoverServices());

            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                intentAction = ACTION_GATT_DISCONNECTED;
                mConnectionState = STATE_DISCONNECTED;
                
                // since we're disconnected, remove this guy
                gattS.remove(gatt.getDevice().getAddress());
                
                gattClientHandler.reportDisconnect();
                Log.i(TAG, "Disconnected from GATT server " + gatt.getDevice().getAddress());
                
            }
        }
        
        @Override
        public void onCharacteristicWrite(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
        	// the passed in characteristic's value is always 00, so it must be a different characteristic
        	Log.v(TAG, "write submitted, BtGattChar.getValue=" + bytesToHex(characteristic.getValue()));
        	
        	
        	if (status == BluetoothGatt.GATT_SUCCESS) {
        		Log.v(TAG, "successful!");
        		//gattClientHandler.handleWriteResult(gatt, characteristic, status);
        	} else {
        		Log.v(TAG, "not successful!");
        	}
        	
           
          
        }

        @Override
        // New services discovered
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                //broadcastUpdate(ACTION_GATT_SERVICES_DISCOVERED);
            	Log.v("SERVICES", "services discovered on " + gatt.getDevice().getAddress());
            	
            	// now that services have been discovered, let's pull them down
            	// we shouldn't actually need to do this
            	/*
            	List<BluetoothGattService> foundServices = gatt.getServices();
            	
            	Log.v(TAG, "found " + String.valueOf(foundServices.size()) + " service(s)");
            	*/
            	/*
            	for (BluetoothGattService s : foundServices) {
            		Log.v("SERVICES", "services found:" + s.getUuid().toString());
            	}
            	*/
            	// we're pulling a specific service
            	BluetoothGattService s = gatt.getService(UUID.fromString(strSvcUuidBase));

            	boolean bServiceGood = false;
            	
            	// if we've found a service
            	if (s != null) {
            		// we need to determine which phase we're in, or what function we want, to decide what to do here...
            		// should we decide what to do here or pass that decision on somewhere else?
            		
            		bServiceGood = true;
            		
            		// check to make sure every characteristic we want is advertised in this service
                	for (BleCharacteristic b: serviceDef) {
                		if (s.getCharacteristic(b.uuid) == null) {
                			bServiceGood = false;
                			Log.v(TAG, "characteristic " + b.uuid.toString() + " not found");
                			break;
                		}
                	}
            		           
            	} else {
            		Log.v(TAG, "can't find service " + strSvcUuidBase);
            	}

            	// if this service is good, we can proceed to parlay with our remote party
            	// OR, you can actually go ahead and issue your READ for the id characteristic
        		if (bServiceGood) {
        			Log.v(TAG, "service definition found; stay connected");
        			//gattClientHandler.getFoundCharacteristics(gatt, s.getCharacteristics());
        			gattClientHandler.parlayWithRemote(gatt.getDevice().getAddress());
        			// initiate identification phase, and then data transfer phase!
        			
        		} else {
        			Log.v(TAG, "service definition not found, disconnect");
        			gatt.disconnect();
        		}

		        
            } else {
                Log.w(TAG, "onServicesDiscovered received: " + status);
            }
        }

        @Override
        // Result of a characteristic read operation
        public void onCharacteristicRead(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
            	
            	//gattClientHandler.readCharacteristicReturned(gatt, characteristic, characteristic.getValue(), status);
            	//gattClientHandler.getReadCharacteristic(gatt, characteristic, characteristic.getValue(), status);
            	gattClientHandler.incomingMissive(gatt.getDevice().getAddress(), characteristic.getUuid(), characteristic.getValue());
            	
                Log.v(TAG, "+read " + characteristic.getUuid().toString() + ": " + new String((characteristic.getValue())));
            } else {
            	Log.v(TAG, "-fail read " + characteristic.getUuid().toString());
            }
        }
    };

    final protected static char[] hexArray = "0123456789ABCDEF".toCharArray();
    
    private static String bytesToHex(byte[] bytes) {
        char[] hexChars = new char[bytes.length * 2];
        for ( int j = 0; j < bytes.length; j++ ) {
            int v = bytes[j] & 0xFF;
            hexChars[j * 2] = hexArray[v >>> 4];
            hexChars[j * 2 + 1] = hexArray[v & 0x0F];
        }
        return new String(hexChars);
    }
    
}
