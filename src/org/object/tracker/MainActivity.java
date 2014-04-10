package org.object.tracker;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import org.object.tracker.Calibration.OnProcessChessboardListener;
import org.object.tracker.CameraDrawerPreview.TextC;
import org.opencv.android.OpenCVLoader;
import org.opencv.core.Mat;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.AlertDialog.Builder;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.hardware.Camera;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnSystemUiVisibilityChangeListener;
import android.view.Window;
import android.view.WindowManager;
import android.webkit.WebView.FindListener;
import android.widget.Button;
import android.widget.RelativeLayout;

public class MainActivity extends Activity {
	CameraDrawerPreview preview;
	RelativeLayout layout;
	private static final String prefName = "matrix";
	private Context context;
	private int threadsNum;
	Boolean freedable = false;
	public MainActivity() {
		this.context = this;
	}
	private View mainView;
	int vis = View.SYSTEM_UI_FLAG_LAYOUT_STABLE
            | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
            | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
            | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION // hide nav bar
            | View.SYSTEM_UI_FLAG_FULLSCREEN // hide status bar
            | View.SYSTEM_UI_FLAG_IMMERSIVE;
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		Log.i("MainActivity","onCreate");
    	if (!OpenCVLoader.initDebug()) {
	        // Handle initialization error
	    } else {
            System.loadLibrary("processing");
	    }
		setContentView(R.layout.main);
		mainView = getWindow().getDecorView();
    	Handler h = new Handler();
    	h.postDelayed(new Runnable() {
			public void run() {
				mainView.setSystemUiVisibility(vis);
			}
		}, 2000);
		mainView.setOnSystemUiVisibilityChangeListener(new OnSystemUiVisibilityChangeListener() {
            @Override
            public void onSystemUiVisibilityChange(int visibility) {
            	
                if(visibility != vis) {
                	Handler h = new Handler();
                	h.postDelayed(new Runnable() {
    					public void run() {
    						mainView.setSystemUiVisibility(vis);
    					}
    				}, 2000);
                	
                }
            }
        });
		layout = (RelativeLayout)findViewById(R.id.main_layout);
		Mat cameraMatrix = new Mat();
		Mat distortionMatrix = new Mat();
		if(!tryLoad(this, cameraMatrix, distortionMatrix)){
			preview = new Calibration(this);
			layout.addView(preview);
			//preview.setImmersive();
			((Calibration)preview).setOnProcessChessboardListener(new OnProcessChessboardListener() {
				
				@Override
				public void matrixComputed(Mat distortion, Mat camera) {
					saveResult(camera, distortion);
					layout.removeView(preview);
					preview = new Preview(context,camera, distortion);
					layout.addView(preview);
					//preview.setImmersive();
				}
			});
		}else{
			preview = new Preview(this,cameraMatrix, distortionMatrix);
			preview.setDebugDrawCallback(new org.object.tracker.CameraDrawerPreview.DebugDrawCallback() {
				
				@Override
				public void debug(List<String> text) {
					String val = "threads:"+(threadsNum!=-1 ? threadsNum : "inf");
					text.add(val);
				}
			});
			layout.addView(preview);
			//preview.setImmersive();
		}
		threadsNum = preview.getMaxThreads();
		
		super.onCreate(savedInstanceState);
	}
	public void saveResult(Mat cameraMatrix,Mat distortionCoefficients){
        SharedPreferences sharedPref = this.getSharedPreferences(prefName ,Context.MODE_PRIVATE);
		SharedPreferences.Editor editor = sharedPref.edit();
		double[] cameraMatrixArray = new double[9];
		cameraMatrix.get(0,  0, cameraMatrixArray);
		for (int i = 0; i < 3; i++) {
            for (int j = 0; j < 3; j++) {
                Integer id = i * 3 + j;
                editor.putFloat(id.toString(), (float)cameraMatrixArray[id]);
            }
        }
		double[] distortionCoefficientsArray = new double[5];
        distortionCoefficients.get(0, 0, distortionCoefficientsArray);
        int shift = 9;
        for (Integer i = shift; i < 5 + shift; i++) {
            editor.putFloat(i.toString(), (float)distortionCoefficientsArray[i-shift]);
        }
        editor.commit();
	}
	public static boolean tryLoad(Activity activity,Mat cameraMatrix, Mat distortionCoefficients) {
        SharedPreferences sharedPref = activity.getSharedPreferences(prefName ,Context.MODE_PRIVATE);
        if (sharedPref.getFloat("0", -1) == -1) {
            return false;
        }

        double[] cameraMatrixArray = new double[9];
        for (int i = 0; i < 3; i++) {
            for (int j = 0; j < 3; j++) {
                Integer id = i * 3 + j;
                cameraMatrixArray[id] = sharedPref.getFloat(id.toString(), -1);
            }
        }
        cameraMatrix.put(0, 0, cameraMatrixArray);

        double[] distortionCoefficientsArray = new double[5];
        int shift = 9;
        for (Integer i = shift; i < 5 + shift; i++) {
            distortionCoefficientsArray[i - shift] = sharedPref.getFloat(i.toString(), -1);
        }
        distortionCoefficients.put(0, 0, distortionCoefficientsArray);

        return true;
    }
	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		MenuInflater inflater = getMenuInflater();
		inflater.inflate(R.menu.main, menu);
		return true;
	}
	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch(item.getItemId()){
		case R.id.calibration:
			layout.removeView(preview);
			//deleteSharedMatrixes();
			preview = new Calibration(context);
			layout.addView(preview);
			//preview.setImmersive();
			((Calibration)preview).setOnProcessChessboardListener(new OnProcessChessboardListener() {
				
				@Override
				public void matrixComputed(Mat distortion, Mat camera) {
					saveResult(camera, distortion);
					layout.removeView(preview);
					preview = new Preview(context,camera, distortion);
					layout.addView(preview);
					//preview.setImmersive();
				}
			});
			break;
		case R.id.threads:
			buildThreadsAlertDialog();
			break;
		case R.id.resolution:
			buildResolutionAlertDialog();
			break;
		case R.id.targetfps:
			buildFpsAlertDialog();
			break;
		default:
			mainView.setSystemUiVisibility(vis);
			return super.onOptionsItemSelected(item);
		}
		mainView.setSystemUiVisibility(vis);
		return true;
	}
	void deleteSharedMatrixes(){
		SharedPreferences preferences = context.getSharedPreferences(prefName, Context.MODE_PRIVATE);
		preferences.edit().clear().commit();
	}
	private List<Camera.Size> sizes;
	private List<int[]> fpses;
	Camera.Parameters params;
	int chosen=-1;
	void buildResolutionAlertDialog(){
		params = preview.getCameraParameters();
		Camera.Size s = params.getPreviewSize();
		sizes = params.getSupportedPreviewSizes();
		String[] sizeNames = new String[sizes.size()];
		for(int num=0;num<sizes.size();num++){
			Camera.Size size=sizes.get(num);
			if(s.width == size.width && s.height == size.height){
				chosen=num;
			}
			sizeNames[num]="w:"+size.width+":h:"+size.height;
		}
		AlertDialog.Builder builder = new AlertDialog.Builder(context);
		builder.setTitle("Resolution");
		builder.setPositiveButton("Ok", new DialogInterface.OnClickListener(){
			@Override
			public void onClick(DialogInterface dialog, int which) {
				params.setPreviewSize(sizes.get(chosen).width, sizes.get(chosen).height);
				
				preview.reloadCameraSetup(params);
				chosen =-1;
				params = preview.getCameraParameters();
			}
		});
		builder.setSingleChoiceItems(sizeNames,chosen,new DialogInterface.OnClickListener() {
			
			@Override
			public void onClick(DialogInterface dialog, int which) {
				chosen=which;
			}
		});
		builder.show();
	}
	void buildFpsAlertDialog(){
		params = preview.getCameraParameters();
		int[] f= {0,0};
		params.getPreviewFpsRange(f);
		fpses = params.getSupportedPreviewFpsRange();
		String[] fpsNames = new String[fpses.size()];
		for(int num=0;num<fpses.size();num++){
			int[] fps = fpses.get(num);
			if(f[0]==fps[0] && f[1]==fps[1]){
				chosen=num;
			}
			fpsNames[num]=""+fps[0]+"-"+fps[1]+" fps";
		}
		AlertDialog.Builder builder = new AlertDialog.Builder(context);
		builder.setTitle("Fps");
		builder.setPositiveButton("Ok", new DialogInterface.OnClickListener(){
			@Override
			public void onClick(DialogInterface dialog, int which) {
				params.setPreviewFpsRange(fpses.get(chosen)[0], fpses.get(chosen)[1]);
				preview.reloadCameraSetup(params);
				chosen=-1;
				params = preview.getCameraParameters();
			}
		});
		builder.setSingleChoiceItems(fpsNames,0,new DialogInterface.OnClickListener() {
			
			@Override
			public void onClick(DialogInterface dialog, int which) {
				chosen=which;
			}
		});
		builder.show();
	}
	String[] threadList = {"1","2","3","4","5","6","7","8","infinite"};
	void buildThreadsAlertDialog(){
		AlertDialog.Builder builder = new AlertDialog.Builder(context);
		builder.setTitle("Threads");
		builder.setPositiveButton("Ok", new DialogInterface.OnClickListener() {
			
			@Override
			public void onClick(DialogInterface dialog, int which) {
				preview.setMaxThreads(threadsNum);
			}
		});
		builder.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
			
			@Override
			public void onClick(DialogInterface dialog, int which) {
				threadsNum = preview.getMaxThreads();
			}
		});
		builder.setOnCancelListener(new DialogInterface.OnCancelListener() {
			
			@Override
			public void onCancel(DialogInterface dialog) {
				threadsNum = preview.getMaxThreads();
			}
		});
		builder.setSingleChoiceItems(threadList,chosen,new DialogInterface.OnClickListener() {
			
			@Override
			public void onClick(DialogInterface dialog, int which) {
				if((which+1)==threadList.length){
					threadsNum=-1;
				}else{
					threadsNum = which+1;
				}
			}
		});
		builder.show();
	}
}
