package org.object.tracker.communication;

import java.util.HashMap;
import java.util.Iterator;




import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbInterface;
import android.hardware.usb.UsbManager;
import android.os.IBinder;
import android.util.Log;
import android.widget.Toast;

public class Communication implements Runnable{
	//private final static String TAG = "Communnication";
	private final static String TAG = Communication.class.getSimpleName();
	private final Context context;
	private Transmission transmission;
	private final UsbManager usbManager;
	private Boolean search = false;
	Thread loop;
	int A1=0, A2=0, B1=0, B2=0;
	public Communication(Context context) {
		this.context = context;
        this.usbManager = (UsbManager) context.getSystemService(Context.USB_SERVICE);
        this.transmission = null;
        this.search = true;
		loop = new Thread(this);
        new Thread(new Runnable() {
			@Override
			public void run() {
				if(search)transmission = new Transmission(usbManager);
				loop.start();
			}
		}).start();
        
	}
	protected void finalize() throws Throwable {
		this.search = false;
		loop.join();
	};
	public void steer(float L, float R){
		
		int lVal = (int)(L*255);
		int rVal = (int)(R*255);
		A1 = lVal>0 ? lVal : 0;
		A2 = lVal<0 ? -lVal : 0;
		B1 = rVal>0 ? rVal : 0;
		B2 = rVal<0 ? -rVal : 0;
		
	}
	@Override
	public void run() {
		while(search){
			Log.v(TAG,"sending:A1="+A1+":A2="+A2+":B1="+B1+":B2="+B2);
			String message = String.format("%03d.%03d.%03d.%03d;", A1,A2,B1,B2);
			
			Log.d(TAG,TAG+message);
			byte[] toSend = Transmission.drive(A1, A2, B1, B2); // = message.getBytes();
			for(int cc=0;cc<toSend.length;cc++){
				Log.e(TAG,TAG+"data["+cc+"]="+(int)toSend[cc]+":"+(char)toSend[cc]);
			}
			this.transmission.send(toSend);
			try {
				Thread.sleep(500);
			} catch (InterruptedException e) {
				Log.e(TAG,"Couldn't sleep thread: "+e.toString());
			}
		}
	}
}
