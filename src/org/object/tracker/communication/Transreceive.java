package org.object.tracker.communication;

import android.util.Log;

public abstract class Transreceive extends Thread {
	public static final String TAG = "Operation Thread";
	public static void waitFor(Transreceive thread){
		if(thread==null) return;
		/*synchronized (thread) {
			try {
				thread.wait();
			} catch (InterruptedException e) {
				Log.e(TAG,"thread interrupted: "+e.getMessage());
			}
		}*/
	}
	abstract void operation();
	@Override
	public void run() {
		synchronized(this){
			operation();
			//notify();
		}
		super.run();
	}

}
