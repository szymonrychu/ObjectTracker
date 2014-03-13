package org.object.tracker;

import java.util.ArrayList;

import org.object.tracker.Calibration.OnProcessChessboardListener;
import org.opencv.android.OpenCVLoader;
import org.opencv.core.Mat;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.AlertDialog.Builder;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
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
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		Log.i("MainActivity","onCreate");
    	if (!OpenCVLoader.initDebug()) {
	        // Handle initialization error
	    } else {
            System.loadLibrary("processing");
	    }
		setContentView(R.layout.main);

		layout = (RelativeLayout)findViewById(R.id.main_layout);
		Mat cameraMatrix = new Mat();
		Mat distortionMatrix = new Mat();
		if(!tryLoad(this, cameraMatrix, distortionMatrix)){
			preview = new Calibration(this);
			layout.addView(preview);
			((Calibration)preview).setOnProcessChessboardListener(new OnProcessChessboardListener() {
				
				@Override
				public void matrixComputed(Mat distortion, Mat camera) {
					saveResult(camera, distortion);
					layout.removeView(preview);
					preview = new Preview(context,camera, distortion);
					layout.addView(preview);
				}
			});
		}else{
			preview = new Preview(this,cameraMatrix, distortionMatrix);
			layout.addView(preview);
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
			((Calibration)preview).setOnProcessChessboardListener(new OnProcessChessboardListener() {
				
				@Override
				public void matrixComputed(Mat distortion, Mat camera) {
					saveResult(camera, distortion);
					layout.removeView(preview);
					preview = new Preview(context,camera, distortion);
					layout.addView(preview);
				}
			});
			return true;
		case R.id.threads:
			buildAlertDialog();
			return true;
		default:
			return super.onOptionsItemSelected(item);
		}
	}
	void deleteSharedMatrixes(){
		SharedPreferences preferences = context.getSharedPreferences(prefName, Context.MODE_PRIVATE);
		preferences.edit().clear().commit();
	}
	String[] threadList = {"1","2","3","4","5","6","7","8","infinite"};
	void buildAlertDialog(){
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
		builder.setSingleChoiceItems(threadList,0,new DialogInterface.OnClickListener() {
			
			@Override
			public void onClick(DialogInterface dialog, int which) {
				if(threadList[which]=="infinite"){
					threadsNum = Integer.MAX_VALUE;
				}
				threadsNum = which+1;
			}
		});
		builder.show();
	}
}
