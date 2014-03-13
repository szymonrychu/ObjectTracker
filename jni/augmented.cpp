#ifndef augmented
#define augmented
#include <iostream>
#include <opencv2/core/core.hpp>
#include <opencv2/highgui/highgui.hpp>
#include <memory>
#include "patterndetector.cpp"
#include "pattern.cpp"
#include "android/log.h"



class AugmentedReality{
private:
	int normSize;
	int patternCount;
	double adapt_thresh ;
	int adapt_block_size;
	double confidenceThreshold;
public:
	Mat cameraMatrix;
	Mat distortions;

	AugmentedReality(int norm_pattern_size,Mat& camMatrix,Mat& distMatrix);
	vector<Holder> normalize(Mat mGray);
};
AugmentedReality::AugmentedReality(int norm_pattern_size,Mat& camMatrix,Mat& distMatrix){
	cameraMatrix  = camMatrix;
	distortions  = distMatrix;
	normSize = norm_pattern_size;

	adapt_thresh = 5;//non-used with FIXED_THRESHOLD mode
	adapt_block_size = 45;//non-used with FIXED_THRESHOLD mode
	confidenceThreshold = 0.35;
	patternCount=0;
	__android_log_print(ANDROID_LOG_INFO, "findTagsRgbNtv - C side", "succesfully started AR");
}





extern "C" {
	JNIEXPORT jlong JNICALL Java_org_object_tracker_OcvTools_newAugmentedNtv(JNIEnv*, jobject, jint nPatSize,jlong addrCamMatrix,jlong addrDistortions){
		Mat& camMatrix  = *(Mat*)addrCamMatrix;
		Mat& distMatrix  = *(Mat*)addrDistortions;
		AugmentedReality*augmentedReality = new AugmentedReality(nPatSize,camMatrix,distMatrix);
		return (jlong)augmentedReality;
	}
	JNIEXPORT void  JNICALL Java_org_object_tracker_OcvTools_matFromPointer(long pointer, long addrMat){
		Mat& mat  = *(Mat*)addrMat;
		mat  = (*(Mat*)pointer).clone();
	}
	/*
	JNIEXPORT jlongArray  JNICALL Java_org_object_tracker_OcvTools_augmentedNormalizePatternsNtv(JNIEnv*env, jobject, jlong addrAugmented, jlong addrYuvFrame){
		AugmentedReality& ar = *(AugmentedReality*)addrAugmented;
		Mat& mYuvFrame  = *(Mat*)addrYuvFrame;
		Mat mGray(mYuvFrame.rows,mYuvFrame.cols,CV_8UC1);
		cvtColor(mYuvFrame,mGray,CV_YUV2GRAY_NV21);
		vector<Mat> library = ar.normalize(mGray);

		int size = library.size();
		jlongArray result = env->NewLongArray(size);
		jlong fill[size];
		for(unsigned int i=0;i<size;i++){
			Mat mat = library[i];
			fill[i]=(jlong)&mat;
		}
		env->SetLongArrayRegion(result,0,size,fill);
		return result;
	}
	*/
	/*JNIEXPORT void  JNICALL Java_org_object_tracker_OcvTools_augmentedNormalizePatternsNtv(JNIEnv*env, jobject, jlong addrAugmented, jlong addrYuvFrame, jlong addrMat, jlong addrPoints){
		AugmentedReality& ar = *(AugmentedReality*)addrAugmented;
		Mat& mYuvFrame  = *(Mat*)addrYuvFrame;
		Mat& mat  = *(Mat*)addrMat;
		Mat& points  = *(Mat*)addrPoints;
		Mat mGray(mYuvFrame.rows,mYuvFrame.cols,CV_8UC1);
		cvtColor(mYuvFrame,mGray,CV_YUV2GRAY_NV21);
		vector<Holder> library = ar.normalize(mGray);
		points.create(library.size(),2,CV_32FC1);
		if(library.size()>0){
			if(library.size()>0){
				Holder hldr= library[0];
				mat = hldr.mat;
				int size = hldr.points.size();
				for(int c=0;c<size;c++){
					Point2f val = hldr.points[c];
					points.at<float>(c,0) = val.x;
					points.at<float>(c,1) = val.y;
				}
			}
		}

	}*/

	JNIEXPORT void JNICALL Java_org_object_tracker_OcvTools_deleteAugmentedNtv(JNIEnv*, jobject, jlong addrAugmented){
		delete (AugmentedReality*)addrAugmented;
	}
}
#endif
