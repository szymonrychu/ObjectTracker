package org.object.tracker;

import java.util.ArrayList;
import java.util.List;

import org.object.tracker.CameraDrawerPreview.BitmapC;
import org.object.tracker.CameraDrawerPreview.LineC;
import org.object.tracker.CameraDrawerPreview.ObjectC;
import org.object.tracker.CameraDrawerPreview.PointC;
import org.object.tracker.CameraDrawerPreview.TextC;
import org.opencv.calib3d.Calib3d;
import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.MatOfDouble;
import org.opencv.core.MatOfPoint2f;
import org.opencv.core.MatOfPoint3f;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.AlertDialog.Builder;
import android.app.DialogFragment;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PorterDuff.Mode;
import android.hardware.Camera;
import android.hardware.Camera.Parameters;
import android.hardware.Camera.PictureCallback;
import android.hardware.Camera.ShutterCallback;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;
import android.widget.Button;


public class Calibration extends CameraDrawerPreview{
	
	public interface OnProcessChessboardListener{
		void matrixComputed(Mat distortion,Mat camera);
	}
	private OnProcessChessboardListener listener;
	public void setOnProcessChessboardListener(OnProcessChessboardListener listener){
		this.listener = listener;
	}
    private int mFlags;
    private Size mPatternSize;
    private MatOfPoint2f mCorners;
    private boolean mPatternWasFound;
    private boolean enabled;
    private int mCornersSize;
    private Size mImageSize; // TODO
    private List<Mat> mCornersBuffer;
    public Mat cameraMatrix;
    public  Mat distortionCoefficients;
    private boolean mIsCalibrated;
    private double mRms;
    private double mSquareSize;
	private Context context;
	private int height, width;
	private ArrayList<ObjectC> objects;
    void init(){
		listener = null;
    	mPatternSize = new Size(8, 5);
        mCorners = new MatOfPoint2f();
        mPatternWasFound = false;
        enabled = false;
        mCornersBuffer = new ArrayList<Mat>();
        mCornersSize = (int)(mPatternSize.width * mPatternSize.height);
        cameraMatrix = new Mat();
        distortionCoefficients = new Mat();
        mIsCalibrated = false;
        mSquareSize = 0.0181;
        mFlags = Calib3d.CALIB_FIX_PRINCIPAL_POINT +
                Calib3d.CALIB_ZERO_TANGENT_DIST +
                Calib3d.CALIB_FIX_ASPECT_RATIO +
                Calib3d.CALIB_FIX_K4 +
                Calib3d.CALIB_FIX_K5 +
                Calib3d.CALIB_CB_ADAPTIVE_THRESH +
                Calib3d.CALIB_CB_NORMALIZE_IMAGE +
     	         Calib3d.CALIB_CB_FAST_CHECK;
		objects = new ArrayList<CameraDrawerPreview.ObjectC>();
    }
	public Calibration(Context context, AttributeSet attrs) {
		super(context, attrs);
		this.context = context;
		init();
	}
	public Calibration(Context context) {
		super(context);
		this.context = context;
		init();
	}
	public void setEnabled(){
		enabled = true;
	}
	public double getRms(){
		return mRms;
	}
	public Boolean getCalibrationState(){
		return mIsCalibrated;
	}
	@Override
	public void processImage(Mat yuvFrame) {
		Mat mRgba = new Mat();
		Imgproc.cvtColor( yuvFrame, mRgba, Imgproc.COLOR_YUV2RGBA_NV21, 4 );
		mPatternWasFound = Calib3d.findChessboardCorners(mRgba, mPatternSize, mCorners, mFlags);
		for(int cc=0;cc<mCorners.rows();cc++){
			double[] coord = mCorners.get(cc, 0);
			double x = coord[0];
			double y = coord[1];
			PointC p = new PointC();
			p.x=(float)x;
			p.y=(float)y;
			Paint paint = new Paint();
			paint.setColor(Color.GREEN);
			paint.setStrokeWidth(4.0f);
			p.paint = paint;
			objects.add(p);
		}
		if(enabled && mPatternWasFound){
			mCornersBuffer.add(mCorners.clone());
			

	        ArrayList<Mat> rvecs = new ArrayList<Mat>();
	        ArrayList<Mat> tvecs = new ArrayList<Mat>();
	        Mat reprojectionErrors = new Mat();
	        ArrayList<Mat> objectPoints = new ArrayList<Mat>();
	        objectPoints.add(Mat.zeros(mCornersSize, 1, CvType.CV_32FC3));
	        while(!mIsCalibrated){
		        calcBoardCornerPositions(objectPoints.get(0));
		        Calib3d.calibrateCamera(objectPoints, mCornersBuffer, mImageSize,
		                cameraMatrix, distortionCoefficients, rvecs, tvecs, mFlags);
		        mIsCalibrated = Core.checkRange(cameraMatrix)
		                && Core.checkRange(distortionCoefficients);
		        mRms = computeReprojectionErrors(objectPoints, rvecs, tvecs, reprojectionErrors);
	        }
	        if(listener!=null)listener.matrixComputed(distortionCoefficients, cameraMatrix);
	        enabled=false;
		}
		
	}

	@Override
	public void setupCamera(Parameters params, int w, int h) {
		height = h;
		width = w;
    	mImageSize = new Size(width, height);
		Camera.Size previewSize = params.getPreviewSize();
		float ratio=1;
        for(Camera.Size size : params.getSupportedPreviewSizes()){
        	Log.v("Size","w:"+size.width+":h:"+size.height);
        	float x = ((float)size.width/(float)w);
        	float y = ((float)size.height/(float)h);
        	if(Math.abs(x/y-1) < ratio){
        		previewSize=size;
        		ratio=Math.abs(x/y-1);
        	}
        }
        params.setFocusMode(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE);//"continuous-video");
        params.setPreviewSize(previewSize.width,previewSize.height);
		Log.v("Preview","w:"+width+":h:"+height);
		Log.v("Calibration","w:"+previewSize.width+":h:"+previewSize.height+":foc:"+Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE);
        setOnClickListener(new OnClickListener() {
			
			@Override
			public void onClick(View v) {
				setEnabled();
				
			}
		});
	}
	private void calcBoardCornerPositions(Mat corners) {
        final int cn = 3;
        float positions[] = new float[mCornersSize * cn];

        for (int i = 0; i < mPatternSize.height; i++) {
            for (int j = 0; j < mPatternSize.width * cn; j += cn) {
                positions[(int) (i * mPatternSize.width * cn + j + 0)] =
                        (2 * (j / cn) + i % 2) * (float) mSquareSize;
                positions[(int) (i * mPatternSize.width * cn + j + 1)] =
                        i * (float) mSquareSize;
                positions[(int) (i * mPatternSize.width * cn + j + 2)] = 0;
            }
        }
        corners.create(mCornersSize, 1, CvType.CV_32FC3);
        corners.put(0, 0, positions);
    }
	private double computeReprojectionErrors(List<Mat> objectPoints,
            List<Mat> rvecs, List<Mat> tvecs, Mat perViewErrors) {
        MatOfPoint2f cornersProjected = new MatOfPoint2f();
        double totalError = 0;
        double error;
        float viewErrors[] = new float[objectPoints.size()];

        MatOfDouble mDistortionCoefficients = new MatOfDouble(distortionCoefficients);
        int totalPoints = 0;
        for (int i = 0; i < objectPoints.size(); i++) {
            MatOfPoint3f points = new MatOfPoint3f(objectPoints.get(i));
            Calib3d.projectPoints(points, rvecs.get(i), tvecs.get(i),
                    cameraMatrix, mDistortionCoefficients, cornersProjected);
	        error = Core.norm(mCornersBuffer.get(i), cornersProjected, Core.NORM_L2);

            int n = objectPoints.get(i).rows();
            viewErrors[i] = (float) Math.sqrt(error * error / n);
            totalError  += error * error;
            totalPoints += n;
        }
        perViewErrors.create(objectPoints.size(), 1, CvType.CV_32FC1);
        perViewErrors.put(0, 0, viewErrors);

        return Math.sqrt(totalError / totalPoints);
    }
	

	@Override
	public void draw(int w, int h, Canvas canvas) {
		if(objects.size()>0){
			canvas.drawColor(0, Mode.CLEAR);
			for(ObjectC obj : objects){
				if(obj instanceof PointC){
					canvas.drawPoint(((PointC)obj).x, ((PointC)obj).y, ((PointC)obj).paint);
				}else if(obj instanceof LineC){
					canvas.drawLine(((LineC)obj).x1, ((LineC)obj).y1, ((LineC)obj).x2, ((LineC)obj).y2, ((LineC)obj).paint);
				}else if(obj instanceof TextC){
					canvas.drawText(((TextC)obj).value, ((TextC)obj).x, ((TextC)obj).y, ((TextC)obj).paint);
				}else if(obj instanceof BitmapC){
					canvas.drawBitmap(((BitmapC)obj).bitmap,((BitmapC)obj).left,((BitmapC)obj).top,((BitmapC)obj).paint);
				}
			}
			objects.clear();
		}
	}

}
