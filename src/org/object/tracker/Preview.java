package org.object.tracker;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.PriorityQueue;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.LinkedBlockingQueue;

import org.opencv.android.Utils;
import org.opencv.core.CvException;
import org.opencv.core.Mat;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PorterDuff.Mode;
import android.hardware.Camera;
import android.hardware.Camera.AutoFocusCallback;
import android.hardware.Camera.Parameters;
import android.hardware.Camera.Size;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.util.AttributeSet;
import android.util.Log;

public class Preview extends CameraDrawerPreview{
	public interface CenterPositions{
		void pointWithCoords(int width, int height, HashMap<Integer, PointC> map);
	}
	private long pattDetAddrNtv;
	private native long newPatternDetectorNtv(int adaptThreshC,int adaptThresBlockSize,
			int normSize, long cameraMatrixAddr,long distortionMatrixAddr);
	private native void delPatternDetectorNtv(long pattDetAddrNtv);
	private native Object[] detectPatternDetectorArrNtv(long pattDetAddrNtv,long yuvFrameAddr,double scaleX, double scaleY);
	private Mat cameraMatrix;
	private Mat distortionMatrix;
	private ArrayList<Holder> holders ;
	private LinkedBlockingQueue<List<ObjectC> > queue = new LinkedBlockingQueue<List<ObjectC>>();
	private CenterPositions positions;
	public Preview(Context context,Mat cameraMatrix, Mat distortionMatrix) {
		super(context);
		this.cameraMatrix = cameraMatrix;
		this.distortionMatrix = distortionMatrix;
		pattDetAddrNtv = newPatternDetectorNtv(5, 45, 25,
				cameraMatrix.getNativeObjAddr(), distortionMatrix.getNativeObjAddr());
		this.positions = null;
		
	}
	public Preview(Context context) {
		super(context);
		pattDetAddrNtv = newPatternDetectorNtv(5, 45, 25,
				cameraMatrix.getNativeObjAddr(), distortionMatrix.getNativeObjAddr());
		
	}
	
	public Preview(Context context,AttributeSet attr) {
		super(context,attr);
		pattDetAddrNtv = newPatternDetectorNtv(5, 45, 25,
				cameraMatrix.getNativeObjAddr(), distortionMatrix.getNativeObjAddr());
		
	}
	public void addCenterPositionsCallback(CenterPositions callback){
		this.positions = callback;
	}
	void setupMatrixes(Mat cameraMatrix, Mat distortionMatrix){
		this.cameraMatrix = cameraMatrix;
		this.distortionMatrix = distortionMatrix;
	}
	private final int textSize=15;
	HashMap<Integer, PointC> map;
	@Override
	public List<ObjectC> processImage(Mat yuvFrame) {
		map = new HashMap<Integer, CameraDrawerPreview.PointC>();
		List<ObjectC> objects = Collections.synchronizedList(new ArrayList<ObjectC>());//new ArrayList<CameraDrawerPreview.ObjectC>();
		Object holders[] = detectPatternDetectorArrNtv(pattDetAddrNtv, yuvFrame.getNativeObjAddr(),scaleX,scaleY);
		if(holders!=null){
			int hSize=holders.length;
			for(int cc=0;cc<hSize;cc++){
				final Holder holder = (Holder)holders[cc];
				final Mat homo = holder.homo;
				final int id= holder.id;
				final float x[] = holder.x;
				final float y[] = holder.y;
				final int length = x.length;
				final float H = 360*cc/hSize;
				final float hsv[] = {H,1.0f,1.0f};
				final float cx = holder.cx;
				final float cy = holder.cy;
				PointC ppp = new PointC(cx,cy);
				map.put(holder.id, ppp);
				Paint paint = new Paint();
				paint.setStrokeWidth(2);
				paint.setColor(Color.HSVToColor(hsv));
				paint.setTextSize(textSize);
				
				for(int dd=0;dd<length;dd++){
					LineC l = new LineC();
					int scnd = (dd+1)%4;
					l.x1=x[dd];
					l.y1=y[dd];
					l.x2=x[scnd];
					l.y2=y[scnd];
					l.paint=paint;
					objects.add(l);
				}
				TextC tID = new TextC();
				tID.x=cx-(2*textSize)*3;
				tID.y=cy-2*textSize;
				tID.value=""+id;
				tID.paint = paint;
				objects.add(tID);
				for(int row=0;row<homo.rows();row++){
					for(int col=0;col<homo.cols();col++){
						double[] data = new double[homo.channels()];
						homo.get(row, col, data);
						for(int ch=0;ch<homo.channels();ch++){
							double d = data[ch];
							TextC t = new TextC();
							t.x=cx+(col*textSize-1*textSize)*3;
							t.y=cy+row*textSize-1*textSize;
							t.value=String.format("%+.2f",d);
							t.paint = paint;
							objects.add(t);
						}
					}
				}
			}
			
		}
		if(positions!=null){
			positions.pointWithCoords(width, height, map);
		}
		return objects;
		
	}
	@Override
	public void setupCamera(Parameters params, int w, int h) {
		for(int[] tab : params.getSupportedPreviewFpsRange()){
			String fps="";
			for(int i : tab){
				fps+=":"+i;
			}
			Log.e("fpsRange","fps"+fps);
		}
		for(int tab : params.getSupportedPreviewFrameRates()){
			
			Log.e("fps","fps:"+tab);
		}
		//params.setPreviewFrameRate(Collections.min(params.getSupportedPreviewFrameRates()));
		Camera.Size previewSize = params.getPreviewSize();
		float ratio=1;
		Log.v("Preview","SetupCamera");
        for(Camera.Size size :params.getSupportedPictureSizes()){
        	float x = ((float)size.width/(float)w);
        	float y = ((float)size.height/(float)h);
        	if(Math.abs(x/y-1) < ratio){
        		previewSize=size;
        		ratio=Math.abs(x/y-1);
        	}
        }
        params.setFocusMode(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE);//"continuous-video");
        params.setPreviewSize(previewSize.width,previewSize.height);
	}
	@Override
	protected void finalize() throws Throwable {
		delPatternDetectorNtv(pattDetAddrNtv);
		super.finalize();
	}
	public static Bitmap matToBitrmap(Mat mat){
        Bitmap bmp = null;
        try {
            bmp = Bitmap.createBitmap(mat.cols(), mat.rows(), Bitmap.Config.ARGB_8888);
            Utils.matToBitmap(mat, bmp);
        }
        catch (CvException e){Log.d("Exception",e.getMessage());}
        return bmp;
    }
}
