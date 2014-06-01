package org.object.tracker.communication;

import java.util.HashMap;
import java.util.Iterator;



import android.content.Context;
import android.hardware.usb.UsbConstants;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbEndpoint;
import android.hardware.usb.UsbInterface;
import android.hardware.usb.UsbManager;
import android.util.Log;
import android.widget.Toast;

public class Transmission{
	private final static String TAG="Transmission";
	private volatile UsbEndpoint mInUsbEndpoint = null;
    private volatile UsbEndpoint mOutUsbEndpoint = null;
    private UsbDeviceConnection mUsbConnection;
    private UsbDevice usbDevice;
    UsbManager usbManager;
    public Transmission(UsbManager mUsbManager) {
		while(!findDevice(mUsbManager)){
			try {
				Thread.sleep(1000);
			} catch (InterruptedException e) {
				Log.e(TAG,"couldn't sleep loop");
			}
		}
	}
    
    private Boolean transreceive = true;
	public Boolean findDevice(UsbManager usbManager) {
        HashMap<String, UsbDevice> usbDeviceList = usbManager.getDeviceList();
        Log.d(TAG, "length: " + usbDeviceList.size());
        Iterator<UsbDevice> deviceIterator = usbDeviceList.values().iterator();
        usbDevice = null;
        if (deviceIterator.hasNext()) {
            usbDevice = deviceIterator.next();

            // Print device information. If you think your device should be able
            // to communicate with this app, add it to accepted products below.
            Log.e(TAG, "VendorId: " + usbDevice.getVendorId());
            Log.e(TAG, "ProductId: " + usbDevice.getProductId());
            Log.e(TAG, "DeviceName: " + usbDevice.getDeviceName());
            Log.e(TAG, "DeviceId: " + usbDevice.getDeviceId());
            Log.e(TAG, "DeviceClass: " + usbDevice.getDeviceClass());
            Log.e(TAG, "DeviceSubclass: " + usbDevice.getDeviceSubclass());
            Log.e(TAG, "InterfaceCount: " + usbDevice.getInterfaceCount());
            Log.e(TAG, "DeviceProtocol: " + usbDevice.getDeviceProtocol());

            
        }
        if (usbDevice == null) {
            return false;
        } else {
            mUsbConnection = usbManager.openDevice(usbDevice);
            if(mUsbConnection== null){
            	return false;
            }
            UsbInterface usbInterface = usbDevice.getInterface(1);
            if(!mUsbConnection.claimInterface(usbInterface, true)){
            	mUsbConnection.close();
            	return false;
            }

            mUsbConnection.controlTransfer(0x21, 34, 0, 0, getLineEncoding(19200), 7, 1000);
            
            for (int i = 0; i < usbInterface.getEndpointCount(); i++) {
                if (usbInterface.getEndpoint(i).getType() == UsbConstants.USB_ENDPOINT_XFER_BULK) {
                    if (usbInterface.getEndpoint(i).getDirection() == UsbConstants.USB_DIR_IN) {
                        mInUsbEndpoint = usbInterface.getEndpoint(i);
                    } else if (usbInterface.getEndpoint(i).getDirection() == UsbConstants.USB_DIR_OUT) {
                        mOutUsbEndpoint = usbInterface.getEndpoint(i);
                    }
                }
            }
            
            if (mInUsbEndpoint == null) {
                Log.e(TAG, "No in endpoint found!");
                mUsbConnection.close();
                return false;
            }

            if (mOutUsbEndpoint == null) {
                Log.e(TAG, "No out endpoint found!");
                mUsbConnection.close();
                return false;
            }
        }
        return true;
    }
    private byte[] getLineEncoding(int baudRate) {
        final byte[] lineEncodingRequest = { (byte) 0x80, 0x25, 0x00, 0x00, 0x00, 0x00, 0x08 };
        switch (baudRate) {
        case 14400:
            lineEncodingRequest[0] = 0x40;
            lineEncodingRequest[1] = 0x38;
            break;

        case 19200:
            lineEncodingRequest[0] = 0x00;
            lineEncodingRequest[1] = 0x4B;
            break;
        }

        return lineEncodingRequest;
    }
    public final static int RECEIVING = 1;
    public final static int IDLE = 0;
    public final static int SENDING = -1;
    int mode= IDLE;
    private String buffer = "";
    public void receive(){
    	new Thread(){
    		@Override
    		public void run() {
    			byte[] inBuff = new byte[4096];
    			while(mode != IDLE){
    				try {
						Thread.sleep(1);
					} catch (InterruptedException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
    			}
    			buffer="";
    			mode = RECEIVING;
    			Boolean subs=false;
    			byte[] question = {0x01};
    			mUsbConnection.bulkTransfer(mOutUsbEndpoint, question, question.length, 1000);
    			int len = mUsbConnection.bulkTransfer(mInUsbEndpoint, inBuff, inBuff.length, 5000);
    			for(int c=0;c<len;c++){
    				byte cc = inBuff[c];
    				buffer+=(char)cc;
    			}
				mode = IDLE;
    			super.run();
    		}
    	}.start();
    	
    }
    Thread sender;
    byte[] dataToSend;
    private void send(){
    	sender = new Thread(){
    		@Override
    		public void run() {
    			while(mode != IDLE){
    				try {
						Thread.sleep(1);
					} catch (InterruptedException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
    			}
    			mode = SENDING;
    			int len = mUsbConnection.bulkTransfer(mOutUsbEndpoint, dataToSend, dataToSend.length, 1000);
    			Log.e(TAG,TAG+len);
    	    	buffer="";
    	    	mode = IDLE;
    			super.run();
    		}
    	};
    	sender.start();
    }
    public void send(byte[] data){
    	this.dataToSend = data;
    	send();
    }
    public String getMessage(){
		while(mode != IDLE){
			try {
				Thread.sleep(1);
			} catch (InterruptedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
    	String str = buffer;
    	buffer = null;
    	return str;
    }
    public void setMessage(String message){
		while(mode != IDLE){
			try {
				Thread.sleep(1);
			} catch (InterruptedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
    	buffer = new String(1+message+2);
    }
    public static int byte2int(byte[] arr){
    	return (arr[0]<<24)&0xff000000|
    		       (arr[1]<<16)&0x00ff0000|
    		       (arr[2]<< 8)&0x0000ff00|
    		       (arr[3]<< 0)&0x000000ff;
    }
    public static byte[] int2byte(int value){
    	return new byte[] {
                (byte)(value >>> 24),
                (byte)(value >>> 16),
                (byte)(value >>> 8),
                (byte)value};
    	
    }
    public static byte[] drive(int A1,int A2,int B1, int B2){
    	Log.e("drive","A1="+A1+":A2="+A2+":B1="+B1+":B2="+B2);
    	byte[] arr = {((byte)A1), (byte)A2, (byte)B1, (byte)B2, (byte)59};
    	return arr;
    }
}
