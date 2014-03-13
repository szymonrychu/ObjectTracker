package org.object.tracker;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Queue;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.PriorityBlockingQueue;

import org.opencv.core.CvType;
import org.opencv.core.Mat;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.PorterDuff.Mode;
import android.hardware.Camera;
import android.hardware.Camera.AutoFocusCallback;
import android.hardware.Camera.Parameters;
import android.hardware.Camera.PreviewCallback;
import android.hardware.Sensor;
import android.hardware.SensorEventListener;
import android.hardware.SensorListener;
import android.hardware.SensorManager;
import android.os.Handler;
import android.util.AttributeSet;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceHolder.Callback;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup;
import android.widget.RelativeLayout;

abstract class CameraDrawerPreview extends ViewGroup{
	private int threads=0;
	private int maxThreads=4;
	private Camera.Parameters params;
	

	
	/**
	 * Method prepared for excessive image processing, called everytime,
	 * when camera refreshes it's image, called in separate thread
	 * to print results of this processing should be stored either in
	 * points, lines, texts or bitmaps, which are arrayLists with 
	 * public custom objects.
	 * @param yuvFrame
	 */
	public abstract void processImage(Mat yuvFrame);
	/**
	 * Method thanks to which camera parameters could be overriden by ours.
	 * The method is called every time, when camera's surface view is
	 * changing
	 * @param params
	 * @param w
	 * @param h
	 */
	public abstract void setupCamera(Camera.Parameters params, int w, int h);
	public abstract void draw(int w, int h,Canvas canvas);
	
	private Context context;
	private Camera camera;
	public Camera getCamera(){
		return this.camera;
	}
	/**
	 * ObjectC - inner class which contains all drawing data
	 * it can contain :
	 *  - multiple points,
	 *  - multiple lines,
	 *  - text as String,
	 *  - bitmap
	 *
	 */
	public abstract class ObjectC{
	}
	
	public class PointC extends ObjectC{
		public Paint paint;
		public float x,y;
	}
	public class LineC extends ObjectC{
		public Paint paint;
		public float x1,x2,y1,y2;
	}
	public class TextC extends ObjectC{
		public float x,y;
		String value;
		public Paint paint;
	}
	public class BitmapC extends ObjectC{
		public float left,top;
		public Bitmap bitmap;
		public Paint paint;
	}
	private class CameraView extends SurfaceView implements Callback, PreviewCallback, Runnable{
		
		protected Mat yuvFrame;
		private int width, height;
		private SurfaceHolder surfaceHolder;
		private CameraDrawerPreview par;
		public CameraView(CameraDrawerPreview parent){
			super(context);
			this.par = parent;
			this.surfaceHolder = getHolder();
			this.surfaceHolder.addCallback(this);
			Log.v("CameraView","init");
		}
		private Thread previousThread;
		private int threads=0;
		@Override
		public void onPreviewFrame(byte[] data, Camera camera) {
			yuvFrame = new Mat(height + height / 2, width, CvType.CV_8UC1);
			yuvFrame.put(0, 0, data);
			if(threads<maxThreads){
				
				Thread thread = new Thread(){
					public void run(){
						threads++;
						processImage(yuvFrame);
						if(previousThread!=null){
							while(previousThread.isAlive()){
								try {
									previousThread.join();
								}catch(Exception e){}
							}
						}
						previousThread = this;
						drawerView.refresh();
						threads--;
					}
				};
				thread.setPriority(Thread.MAX_PRIORITY);
				thread.start();
			}
		
		}
		@Override
		public void surfaceCreated(SurfaceHolder holder) {
			if(camera!=null){
				camera.release();
				camera=null;
			}
			camera = Camera.open();
	        try {
				camera.setPreviewDisplay(this.surfaceHolder);
			} catch (Exception e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
		@Override
		public void surfaceChanged(SurfaceHolder holder, int format, int width,
				int height) {
			this.height = height;
			this.width = width;
			params = camera.getParameters();
			setupCamera(params,width, height);
			Log.d("setupCamera","CameraDrawerPreview");
	        camera.setParameters(params);
	        Thread cameraPreview = new Thread(this);
	        cameraPreview.start();
		}
		@Override
		public void surfaceDestroyed(SurfaceHolder holder) {
			if(camera!=null){
				camera.setPreviewCallback(null);
				camera.stopPreview();
				camera.release();
				camera=null;
			}
		}
		@Override
		public void run() {
			camera.setPreviewCallback(this);
			camera.startPreview();
		}
	}
	private class DrawerView extends SurfaceView implements Callback, Runnable{
		private SurfaceHolder surfaceHolder;
		private Boolean draw = false;
		private int width, height;
		private CameraDrawerPreview par;
		public DrawerView(CameraDrawerPreview parent){
			super(context);
			this.par = parent;
			Log.v("DrawerView","init");
			this.surfaceHolder = getHolder();
			this.surfaceHolder.addCallback(this);
			this.surfaceHolder.setFormat(PixelFormat.TRANSPARENT);
		}
		@Override
		public void surfaceCreated(SurfaceHolder holder) {
		}
		@Override
		public void surfaceChanged(SurfaceHolder holder, int format, int width,
				int height) {
			this.height = height;
			this.width = width;
			draw = true;
		}
		@Override
		public void surfaceDestroyed(SurfaceHolder holder) {
			draw = false;
		}
		public synchronized void refresh(){
			if(draw){
				Canvas canvas=surfaceHolder.lockCanvas();
				synchronized(canvas){
					par.draw(width,height,canvas);
					surfaceHolder.unlockCanvasAndPost(canvas);
				}
			}
		}
		@Override
		public void run() {
			if(draw){
				Canvas canvas=surfaceHolder.lockCanvas();
				synchronized(canvas){
					par.draw(width,height,canvas);
					surfaceHolder.unlockCanvasAndPost(canvas);
				}
			}
		}
		
	}
	private CameraView cameraView;
	private DrawerView drawerView;
	private void setup(){
		this.cameraView = new CameraView(this);
		this.drawerView = new DrawerView(this);
		LayoutParams params = new LayoutParams(LayoutParams.MATCH_PARENT,LayoutParams.MATCH_PARENT );
		addView(drawerView,params);
		addView(cameraView,params);
	}
	public CameraDrawerPreview(Context context, AttributeSet attrs) {
		super(context, attrs);
		this.context = context;
		setup();
	}
	public CameraDrawerPreview(Context context){
		super(context);
		this.context = context;
		setup();
	}
	@Override
	protected void onLayout(boolean changed, int l, int t, int r, int b) {
		final int count = getChildCount();
		for(int i=0;i<count;i++){
			View child = getChildAt(i);
			child.layout(l, t, r, b);
		}
	}
	@Override
	protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
		int w = MeasureSpec.makeMeasureSpec(widthMeasureSpec, MeasureSpec.EXACTLY);
		int h = MeasureSpec.makeMeasureSpec(heightMeasureSpec, MeasureSpec.EXACTLY);
		for(int i=0; i<getChildCount(); i++){
			View v = getChildAt(i);
			v.measure(w, h);
		}
		super.onMeasure(widthMeasureSpec, heightMeasureSpec);
	}
	public int getMaxThreads() {
		return maxThreads;
	}
	public void setMaxThreads(int maxThreads) {
		this.maxThreads = maxThreads;
	}
}
