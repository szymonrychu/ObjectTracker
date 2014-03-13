#ifndef patterndetector
#define patterndetector
#include <jni.h>
#include <vector>
#include <opencv2/core/core.hpp>
#include <opencv2/imgproc/imgproc.hpp>
#include <opencv2/imgproc/imgproc_c.h>
#include <opencv2/highgui/highgui.hpp>
#include <opencv2/features2d/features2d.hpp>
#include <pattern.cpp>
#include "android/log.h"
using namespace std;
using namespace cv;
class Holder{
public:
	Mat mat;
	vector<Point2f> points;
};
class PatternDetector{
public:

    //constructor
	PatternDetector( const double param2, const int param3, const  double param4, const int param5);

	//distractor
	~PatternDetector(){};

enum THRES_MODE {
		FIXED_THRESHOLD,
		ADAPTIVE_THRESHOLD,
	};

//detect patterns in the input frame
	vector<Holder > normalizePattern(const Mat&grayFrame, const Mat&cameraMatrix, const Mat&distortions);
private:

	int normSize, blockSize;
	double confThreshold, adaptThresh;
	struct patInfo{
		int index;
		int ori;
		double maxCor;
	};
	Mat binImage, grayImage,  patMask, patMaskInt;
	Point2f norm2DPts[4];


};

PatternDetector::PatternDetector(const double param2, const int param3, const double param4, const int param5){
	adaptThresh = param2;// for adaptive thresholding
	blockSize = param3;//for adaptive image thresholding
	confThreshold = param4;//bound for accepted similarities between detected patterns and loaded patterns
	normSize = param5;// the size of normalized ROI


	//Masks for exterior(black) and interior area inside the pattern
	patMask = Mat::ones(normSize,normSize,CV_8UC1);
	Mat submat = patMask(cv::Range(normSize/4,3*normSize/4), cv::Range(normSize/4, 3*normSize/4));
	submat = Scalar(0);

	patMaskInt = Mat::zeros(normSize,normSize,CV_8UC1);
	submat = patMaskInt(cv::Range(normSize/4,3*normSize/4), cv::Range(normSize/4, 3*normSize/4));
	submat = Scalar(1);


	//corner of normalized area
	norm2DPts[0] = Point2f(0,0);
	norm2DPts[1] = Point2f(normSize-1,0);
	norm2DPts[2] = Point2f(normSize-1,normSize-1);
	norm2DPts[3] = Point2f(0,normSize-1);

}
vector<Holder > PatternDetector::normalizePattern(const Mat&grayFrame, const Mat&cameraMatrix, const Mat&distortions){
	Mat normROI = Mat(normSize,normSize,CV_8UC1);//normalized ROI
	vector<Holder > result;
	Mat mBin;
	adaptiveThreshold(grayFrame,mBin,255, CV_ADAPTIVE_THRESH_GAUSSIAN_C, CV_THRESH_BINARY_INV, blockSize, adaptThresh);
	dilate(mBin, mBin, Mat());
	int avsize = (mBin.rows+mBin.cols)/2;
	vector<vector<Point> > contours;
	findContours(mBin.clone(), contours, CV_RETR_LIST, CV_CHAIN_APPROX_SIMPLE);
	unsigned int count;
	for(count=0;count<contours.size();count++){
		Mat contour = Mat(contours[count]);
		const double per = arcLength( contour, true);
		if (per>(avsize/4) && per<(4*avsize)){
			vector<Point> polygon;
			approxPolyDP( contour, polygon, per*0.02, true);
			if(polygon.size()==4 && isContourConvex(Mat (polygon))){
				__android_log_print(ANDROID_LOG_INFO, "normalizePatternLength", "pattern l=%f/%d", per,avsize);

				//locate the 2D box of contour,
				Point p;
				int pMinY, pMaxY, pMinX, pMaxX;
				p = polygon.at(0);
				pMinX = pMaxX = p.x;
				pMinY = pMaxY = p.y;
				int j;
				for(j=1; j<4; j++){
					p = polygon.at(j);
					if (p.x<pMinX){
						pMinX = p.x;
					}
					if (p.x>pMaxX){
						pMaxX = p.x;
					}
					if (p.y<pMinY){
						pMinY = p.y;
					}
					if (p.y>pMaxY){
						pMaxY = p.y;
					}
				}
				Rect box(pMinX, pMinY, pMaxX-pMinX+1, pMaxY-pMinY+1);
				//find the upper left vertex
				double d;
				double dmin=(4*avsize*avsize);
				int v1=-1;
				for (j=0; j<4; j++){
					d = norm(polygon.at(j));
					if (d<dmin) {
					dmin=d;
					v1=j;
					}
				}

				vector<Point2f> refinedVertices;
				for(j=0; j<4; j++){
					refinedVertices.push_back(polygon.at(j));
				}
				//refine corners
				__android_log_print(ANDROID_LOG_INFO, "normalizePattern", "debug");
				cornerSubPix(grayFrame, refinedVertices, Size(3,3), Size(-1,-1), TermCriteria(1, 3, 1));
				__android_log_print(ANDROID_LOG_INFO, "normalizePattern", "debug");

				vector<Point2f > points;
				for(j=0; j<4; j++){
					Point2f point2f = refinedVertices[j];
					points.push_back(point2f);
					__android_log_print(ANDROID_LOG_INFO, "normalizePatternCorners","corner%d:%d:%f:%f",count,j,point2f.x,point2f.y);
				}
				//rotate vertices based on upper left vertex; this gives you the most trivial homogrpahy
				Point2f roi2DPts[4];
				for(j=0; j<4;j++){
					roi2DPts[j] = Point2f(refinedVertices.at((4+v1-j)%4).x - pMinX, refinedVertices.at((4+v1-j)%4).y - pMinY);
				}

				//compute the homography
				//normalizePattern(const Mat& src, const Point2f roiPoints[], Rect& rec, Mat& dst)
				//normalizePattern(grayImage, roi2DPts, box, normROI);
				Mat Homo(3,3,CV_32F);
				Homo = getPerspectiveTransform( roi2DPts, norm2DPts);

				Mat subImg = grayFrame(cv::Range(box.y, box.y+box.height), cv::Range(box.x, box.x+box.width));

				//warp the input based on the homography model to get the normalized ROI
				warpPerspective( subImg, normROI, Homo, Size(normROI.cols, normROI.rows));

				if(normROI.rows == normROI.cols){


					Holder hldr;
					hldr.mat = normROI;
					hldr.points = points;

					result.push_back(hldr);
				}

			}
		}


	}

	return result;


}


#endif patterndetector
