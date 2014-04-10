package org.object.tracker;

import java.util.ArrayList;
import java.util.Collections;
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
	
	private long pattDetAddrNtv;
	private native long newPatternDetectorNtv(int adaptThreshC,int adaptThresBlockSize,
			int normSize, long cameraMatrixAddr,long distortionMatrixAddr);
	private native void delPatternDetectorNtv(long pattDetAddrNtv);
	private native Object[] detectPatternDetectorArrNtv(long pattDetAddrNtv,long yuvFrameAddr);
	private Mat cameraMatrix;
	private Mat distortionMatrix;
	private ArrayList<Holder> holders ;
	private LinkedBlockingQueue<List<ObjectC> > queue = new LinkedBlockingQueue<List<ObjectC>>();
	public Preview(Context context,Mat cameraMatrix, Mat distortionMatrix) {
		super(context);
		this.cameraMatrix = cameraMatrix;
		this.distortionMatrix = distortionMatrix;
		pattDetAddrNtv = newPatternDetectorNtv(5, 45, 25,
				cameraMatrix.getNativeObjAddr(), distortionMatrix.getNativeObjAddr());
		
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
	void setupMatrixes(Mat cameraMatrix, Mat distortionMatrix){
		this.cameraMatrix = cameraMatrix;
		this.distortionMatrix = distortionMatrix;
	}
	@Override
	public List<ObjectC> processImage(Mat yuvFrame) {
		List<ObjectC> objects = Collections.synchronizedList(new ArrayList<ObjectC>());//new ArrayList<CameraDrawerPreview.ObjectC>();
		Object holders[] = detectPatternDetectorArrNtv(pattDetAddrNtv, yuvFrame.getNativeObjAddr());
		if(holders!=null){
			int top=0;
			int left=0;
			int hSize=holders.length;
			for(int cc=0;cc<hSize;cc++){
				Holder holder = (Holder)holders[cc];
				int id= holder.id;
				float x[] = holder.x;
				float y[] = holder.y;
				float XX=0;
				float YY=0;
				int length = x.length;
				Paint paint = new Paint();
				paint.setStrokeWidth(2);
				float H = 360*cc/hSize;
				float hsv[] = {H,1.0f,1.0f};
				paint.setColor(Color.HSVToColor(hsv));
				for(int dd=0;dd<length;dd++){
					LineC l = new LineC();
					int scnd = (dd+1)%4;
					XX+=x[dd];
					YY+=y[dd];
					l.x1=x[dd];
					l.y1=y[dd];
					l.x2=x[scnd];
					l.y2=y[scnd];
					l.paint=paint;
					objects.add(l);
				}
				paint.setTextSize(15);
				TextC t = new TextC();
				t.x=XX/length;
				t.y=YY/length;
				t.value=""+id;
				t.paint = paint;
				objects.add(t);
				Mat mat = holder.tag;
				Bitmap bmp = matToBitrmap(mat);
				BitmapC bmpC = new BitmapC();
				bmpC.bitmap=bmp;
				bmpC.paint=paint;
				bmpC.top=top;
				bmpC.left=left;
				if(top+mat.rows()>=400){
					top=0;
					left+=mat.cols();
				}else{
					top+=mat.rows();
				}
				objects.add(bmpC);
			}
			
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
	@Override
	public void debug(ArrayList<String> text) {
		// TODO Auto-generated method stub
		
	}
}
