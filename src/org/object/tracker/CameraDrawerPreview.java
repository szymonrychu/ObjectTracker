package org.object.tracker;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.ArrayList;
import java.util.Currency;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Queue;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.PriorityBlockingQueue;

import org.opencv.core.CvType;
import org.opencv.core.Mat;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
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
	private int maxThreads=1;
	private Camera.Parameters params;
	public double scaleX=1;
	public double scaleY=1;
	private Boolean debug=false;
	protected int width, height;
	public Camera.Parameters getCameraParameters(){
		return params;
	}

	interface DebugDrawCallback{
		void debug(List<String> text);
	}
	public void setDebugDrawCallback(DebugDrawCallback debugDrawCallback){
		this.callback = debugDrawCallback;
	}
	public void unSetDebugDrawCallback(){
		this.callback = null;
	}
	
	private DebugDrawCallback callback;
	
	/**
	 * Method prepared for excessive image processing, called everytime,
	 * when camera refreshes it's image, called in separate thread
	 * to print results of this processing should be stored either in
	 * points, lines, texts or bitmaps, which are arrayLists with 
	 * public custom objects.
	 * @param yuvFrame
	 */
	public abstract List<ObjectC> processImage(Mat yuvFrame);
	private LinkedBlockingQueue<List<ObjectC> > queue = new LinkedBlockingQueue<List<ObjectC>>();
	
	/**
	 * Method thanks to which camera parameters could be overriden by ours.
	 * The method is called every time, when camera's surface view is
	 * changing
	 * @param params
	 * @param w
	 * @param h
	 */
	public abstract void setupCamera(Camera.Parameters params, int w, int h);
	
	
	public void reloadCameraSetup(Camera.Parameters params ){
		cameraView.reloadCameraSetup(params);
	}
	
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
		public PointC() {}
		public PointC(float x,float y){
			this.x=x;
			this.y=y;
		}
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
		private SurfaceHolder surfaceHolder;
		private CameraDrawerPreview par;
		private Boolean run = false;
		public CameraView(CameraDrawerPreview parent){
			super(context);
			this.par = parent;
			this.surfaceHolder = getHolder();
			this.surfaceHolder.addCallback(this);
			Log.v("CameraView","init");
		}
		private Thread previousThread;
		private int threads=0;
		private int currentThreads=0;
		private long delay=0;
		@Override
		public void onPreviewFrame(byte[] data, Camera camera) {
			Camera.Size size = camera.getParameters().getPreviewSize();
			yuvFrame = new Mat(size.height + size.height / 2, size.width, CvType.CV_8UC1);
			yuvFrame.put(0, 0, data);
			if(maxThreads==-1 || threads<=maxThreads){
				
				Thread thread = new Thread(){
					public void run(){
						threads++;
						List<ObjectC> list = processImage(yuvFrame);
						queue.add(list);
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
		Thread cameraPreview = new Thread(this);;
		@Override
		public void surfaceChanged(SurfaceHolder holder, int format, int w,
				int h) {
			height = h;
			width = w;
			run=false;
			camera.stopPreview();
			params = camera.getParameters();
			setupCamera(params,width, height);
			Log.d("setupCamera","CameraDrawerPreview");
			Camera.Size s = params.getPreviewSize();
			scaleY = (double)height/s.height;
			scaleX = (double)width/s.width;
	        camera.setParameters(params);
	        run = true;
	        cameraPreview = new Thread(this);
	        cameraPreview.start();
		}

		public void reloadCameraSetup(Camera.Parameters params ){
			try {
				run = false;
				camera.stopPreview();
				camera.setParameters(params);
				Camera.Size s = params.getPreviewSize();
				scaleY = (double)height/s.height;
				scaleX = (double)width/s.width;
				run = true;
				cameraPreview.join();
				cameraPreview = new Thread(this);
				cameraPreview.start();
			} catch (Exception e) {
				Log.e("reload camera parameters","failed");
			}
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
			if(run){
				camera.setPreviewCallback(this);
				camera.startPreview();
			}
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
		long lastFpsTime = 0;
		public synchronized void refresh(){
			if(draw){
				Canvas canvas=surfaceHolder.lockCanvas();
				synchronized(canvas){
					canvas.drawColor(0, Mode.CLEAR);
					synchronized(queue){
						
						List<String> text = new ArrayList<String>();
						List<ObjectC> objects = queue.poll();
						text.add("objects:"+(objects==null? 0 : objects.size()/6));
						if(objects!=null){
							Iterator<ObjectC> iterator = objects.iterator();
							while(iterator.hasNext()){
								ObjectC obj = iterator.next();
								if(obj instanceof PointC){
									int x = (int)(((PointC)obj).x);
									int y = (int)(((PointC)obj).y);
									canvas.drawPoint(x, y, ((PointC)obj).paint);
								}else if(obj instanceof LineC){
									int x1 = (int)(((LineC)obj).x1);
									int y1 = (int)(((LineC)obj).y1);
									int x2 = (int)(((LineC)obj).x2);
									int y2 = (int)(((LineC)obj).y2);
									canvas.drawLine(x1 ,y1 ,x2 ,y2 , ((LineC)obj).paint);
								}else if(obj instanceof TextC){
									int x = (int)(((TextC)obj).x);
									int y = (int)(((TextC)obj).y);
									canvas.drawText(((TextC)obj).value, x, y, ((TextC)obj).paint);
								}else if(obj instanceof BitmapC){
									int x = (int)(((BitmapC)obj).left);
									int y = (int)(((BitmapC)obj).top);
									canvas.drawBitmap(((BitmapC)obj).bitmap, x, y,((BitmapC)obj).paint);
								}
							}
							objects.clear();
						}
					}
					if(debug){
						ArrayList<String> text = new ArrayList<String>();
						if(callback!=null){
							callback.debug(text);
						}
						text.add("scale x="+scaleX);
						text.add("scale y="+scaleY);
						long now = System.currentTimeMillis();
						int delay = (int)(now - lastFpsTime);
						lastFpsTime = now;
						double FPS=(1.0d/delay)*1000;
						text.add("fps="+FPS);
						text.add("delay="+delay+"ms");
						Paint paint = new Paint();
						paint.setColor(Color.RED);
						paint.setTextSize(40);
						int cc=0;
						for(String t : text){
							canvas.drawText(t, 40, 40+40*cc, paint);
							cc++;
						}
					}
					surfaceHolder.unlockCanvasAndPost(canvas);
				}
			}
		}
		@Override
		public void run() {
			refresh();
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
		this.callback = null;
	}
	private float readUsage() {
	    try {
	        RandomAccessFile reader = new RandomAccessFile("/proc/stat", "r");
	        String load = reader.readLine();

	        String[] toks = load.split(" ");

	        long idle1 = Long.parseLong(toks[5]);
	        long cpu1 = Long.parseLong(toks[2]) + Long.parseLong(toks[3]) + Long.parseLong(toks[4])
	              + Long.parseLong(toks[6]) + Long.parseLong(toks[7]) + Long.parseLong(toks[8]);

	        try {
	            Thread.sleep(360);
	        } catch (Exception e) {}

	        reader.seek(0);
	        load = reader.readLine();
	        reader.close();

	        toks = load.split(" ");

	        long idle2 = Long.parseLong(toks[5]);
	        long cpu2 = Long.parseLong(toks[2]) + Long.parseLong(toks[3]) + Long.parseLong(toks[4])
	            + Long.parseLong(toks[6]) + Long.parseLong(toks[7]) + Long.parseLong(toks[8]);

	        return (float)(cpu2 - cpu1) / ((cpu2 + idle2) - (cpu1 + idle1));

	    } catch (IOException ex) {
	        ex.printStackTrace();
	    }

	    return 0;
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
	public int getThreadsAlive(){
		return threads;
	}
	public void setMaxThreads(int maxThreads) {
		this.maxThreads = maxThreads;
	}
	public void setDebug(Boolean enabled){
		debug=enabled;
	}
	public Boolean getDebug(){
		return debug;
	}
}
