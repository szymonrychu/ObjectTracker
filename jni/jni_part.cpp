#include <jni.h>
#include <vector>
#include <math.h>
#include <sys/time.h>
#include <opencv2/core/core.hpp>
#include <opencv2/imgproc/imgproc.hpp>
#include <opencv2/imgproc/imgproc_c.h>
#include <opencv2/highgui/highgui.hpp>
#include <opencv2/features2d/features2d.hpp>
#include <opencv2/video/tracking.hpp>

using namespace std;
using namespace cv;

#include <android/log.h>

bool debug = false;
struct Holder{
	int id;
	vector<Point2f> points;
	Mat tag;
	Mat homo;
	Point2f center;
};
class Operations{
private:
	JNIEnv*env;
	//org.opencv.core.Mat
	jclass jMatCls;
	jmethodID jMatCons;
	jmethodID jMatGetNatAddr;
	//org.object.tracker.Holder
	jclass jHolderCls;
	jmethodID jHolderCons;
public:
	Operations(){}
	Operations(JNIEnv*&env){
		this->env = env;
/*
		jMatCls = env->FindClass("org/opencv/core/Mat");
		jMatCons = env->GetMethodID(jMatCls,"<init>","()V");
		jMatGetNatAddr = env->GetMethodID(jMatCls,"getNativeObjAddr","()J");*/

	}
	void setupJHolderMethods(jclass&jHolderCls,jmethodID&jHolderCons){
		this->jHolderCls = jHolderCls;
		this->jHolderCons = jHolderCons;
	}
	void setupJMat(jclass&jMatCls,jmethodID&jMatCons,jmethodID&jMatGetAddr){
		this->jMatCls = jMatCls;
		this->jMatCons = jMatCons;
		this->jMatGetNatAddr = jMatGetAddr;
	}
	jobjectArray convertArr(vector<Holder> holders,double scaleX, double scaleY){
		unsigned int size = holders.size();
		if(size==0)return NULL;
		unsigned int cc;
		jobject object = env->NewObject(jHolderCls,jHolderCons);
		jobjectArray result = (*env).NewObjectArray(size,jHolderCls,object);
		for(cc=0;cc<size;cc++){
			Holder holder = holders.at(cc);
			jobject objectTmp = env->NewObject(jHolderCls,jHolderCons);

			jfieldID idId = env->GetFieldID(jHolderCls,"id","I");
			env->SetIntField(objectTmp,idId,holder.id);

			jfieldID cxID = env->GetFieldID(jHolderCls,"cx","F");
			jfieldID cyID = env->GetFieldID(jHolderCls,"cy","F");
			env->SetFloatField(objectTmp,cxID,holder.center.x*scaleX);
			env->SetFloatField(objectTmp,cyID,holder.center.y*scaleY);

			unsigned int size = holder.points.size();
			jfieldID xArrId = env->GetFieldID(jHolderCls,"x","[F");
			jfieldID yArrId = env->GetFieldID(jHolderCls,"y","[F");
			jfloatArray xArr = env->NewFloatArray(size);
			jfloatArray yArr = env->NewFloatArray(size);
			jfloat xSes[size];
			jfloat ySes[size];
			unsigned int i;
			for(i=0;i<size;i++){
				xSes[i] = holder.points.at(i).x*scaleX;
				ySes[i] = holder.points.at(i).y*scaleY;
			}
			env->SetFloatArrayRegion(xArr,0,size,xSes);
			env->SetFloatArrayRegion(yArr,0,size,ySes);
			jobject xData = reinterpret_cast<jobject>(xArr);
			jobject yData = reinterpret_cast<jobject>(yArr);
			env->SetObjectField(objectTmp,xArrId,xData);
			env->SetObjectField(objectTmp,yArrId,yData);

			jobject jMatT = env->NewObject(this->jMatCls,this->jMatCons);
			Mat&cMat=*(Mat*)env->CallLongMethod(jMatT,jMatGetNatAddr);
			cMat = holder.tag.clone();
			jfieldID matId = env->GetFieldID(jHolderCls,"tag","Lorg/opencv/core/Mat;");
			env->SetObjectField(objectTmp,matId,jMatT);

			jobject jMatH = env->NewObject(this->jMatCls,this->jMatCons);
			Mat&cHomo=*(Mat*)env->CallLongMethod(jMatH,jMatGetNatAddr);
			cHomo = holder.homo.clone();
			jfieldID homoId = env->GetFieldID(jHolderCls,"homo","Lorg/opencv/core/Mat;");
			env->SetObjectField(objectTmp,homoId,jMatH);

			env->SetObjectArrayElement(result,cc,objectTmp);
		}
		return result;
	}
};


class Kalman{
public:
	Kalman(float&startX,float&startY,int maxPred);
	Kalman();
	Point2f predict(float x,float y);
	Point2f predict(Point2f point);
	Point2f predict();
	void init(float&startX,float&startY,int maxPred);
	void init(Point2f center,int maxPred);
	int predictions;
	int maxPredictions;
	bool inited;
private:
	cv::KalmanFilter KF;
	Mat_<float> measurement;
};
Kalman::Kalman(){
	predictions = -1;
	KF.init(4,2,0);
	Mat_<float> state(4, 1);
	Mat processNoise(4, 1, CV_32F);
	measurement(2,1);
	measurement.setTo(Scalar(0));
	KF.statePre.at<float>(2) = 0;
	KF.statePre.at<float>(3) = 0;
	KF.transitionMatrix = *(Mat_<float>(4,4)<< 1,0,1,0,   0,1,0,1,   0,0,1,0,   0,0,0,1);

	setIdentity(KF.measurementMatrix);
	setIdentity(KF.processNoiseCov, Scalar::all(1e-4));
	setIdentity(KF.measurementNoiseCov, Scalar::all(1e-1));
	setIdentity(KF.errorCovPost, Scalar::all(.1));
}
void Kalman::init(float&startX,float&startY,int maxPred){
	predictions = 0;
	maxPredictions = maxPred;
	KF.statePre.at<float>(0) = startX;
	KF.statePre.at<float>(1) = startY;
	inited=true;
}
void Kalman::init(Point2f center,int maxPred){
	predictions = 0;
	maxPredictions = maxPred;
	KF.statePre.at<float>(0) = center.x;
	KF.statePre.at<float>(1) = center.y;
	inited=true;
}
Kalman::Kalman(float&startX,float&startY,int maxPred){
	Kalman();
	init(startX,startY,maxPred);
}
Point2f Kalman::predict(float x,float y){
	Point2f stateNowPt(x,y);
	Mat prediction = KF.predict();
	Point2f predictionPt(prediction.at<float>(0),prediction.at<float>(1));
	measurement(0)=x;
	measurement(1)=y;
	KF.correct(measurement);
	return predictionPt;
}
Point2f Kalman::predict(Point2f point){
	Point2f stateNowPt(point.x,point.y);
	Mat prediction = KF.predict();
	Point2f predictionPt(prediction.at<float>(0),prediction.at<float>(1));
	measurement(0)=point.x;
	measurement(1)=point.y;
	KF.correct(measurement);
	return predictionPt;
}
Point2f Kalman::predict(){
	predictions++;
	if(predictions>maxPredictions){
		inited=false;
	}
	Mat prediction = KF.predict();
	KF.statePre.copyTo(KF.statePost);
	KF.errorCovPre.copyTo(KF.errorCovPost);
	Point2f predictionPt(prediction.at<float>(0),prediction.at<float>(1));
	return predictionPt;
}




class PatternDetector{
public:
    //constructor
	PatternDetector(const double AdThC, const int AdTHBlSiz, const double normSiz,\
			const Mat mCamMatrix,const Mat mDistMatrix);
	//distractor
	~PatternDetector(){};

	//detect patterns in the input frame
	vector<Holder> normalizePattern(const Mat&grayFrame);
	//, const Mat&cameraMatrix, const Mat&distortions
	int identifyPattern(Mat normMat);
private:
	Point2f center(vector<Point2f> refinedVertices);
	int normSize, blockSize;
	double confThreshold, adaptThresh;
	struct patInfo{
		int index;
		int ori;
		double maxCor;
	};
	int sizePix;
	Mat cameraMatrix,distortions;
	Mat binImage, grayImage,  patMask, patMaskInt;
	Point2f norm2DPts[4];
	Kalman kalmans[32];
	bool map[32];
};
PatternDetector::PatternDetector(const double AdThC, const int AdTHBlSiz, const double normSiz,const Mat mCamMatrix,const Mat mDistMatrix){
	adaptThresh = AdThC;// for adaptive thresholding
	blockSize = AdTHBlSiz;//for adaptive image thresholding
	normSize = normSiz;// the size of normalized ROI
	//Masks for exterior(black) and interior area inside the pattern
	patMask = Mat::ones(normSize,normSize,CV_8UC1);
	Mat submat = patMask(cv::Range(normSize/4,3*normSize/4), cv::Range(normSize/4, 3*normSize/4));
	submat = Scalar(0);
	sizePix = 5;
	patMaskInt = Mat::zeros(normSize,normSize,CV_8UC1);
	submat = patMaskInt(cv::Range(normSize/4,3*normSize/4), cv::Range(normSize/4, 3*normSize/4));
	submat = Scalar(1);
	//corner of normalized area
	norm2DPts[0] = Point2f(0,0);
	norm2DPts[1] = Point2f(normSize-1,0);
	norm2DPts[2] = Point2f(normSize-1,normSize-1);
	norm2DPts[3] = Point2f(0,normSize-1);
	distortions = mDistMatrix;
	cameraMatrix = mCamMatrix;
	/*for(int cc=0;cc<32;cc++){
		map[cc]=false;
	}*/
}


vector<Holder> PatternDetector::normalizePattern(const Mat&grayFrame){
	Mat normROI = Mat(normSize,normSize,CV_8UC1);//normalized ROI
	vector<Holder> holders;
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
		if (per>(avsize/10) && per<(5*avsize)){
			vector<Point> polygon;
			approxPolyDP( contour, polygon, per*0.02, true);
			if(polygon.size()==4 && isContourConvex(Mat (polygon))){
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
				cornerSubPix(grayFrame, refinedVertices, Size(3,3), Size(-1,-1), TermCriteria(1, 3, 1));

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
				//Mat mBinSub = mBin(cv::Range(box.y, box.y+box.height), cv::Range(box.x, box.x+box.width));
				Mat subImg = grayFrame(cv::Range(box.y, box.y+box.height), cv::Range(box.x, box.x+box.width));

				//warp the input based on the homography model to get the normalized ROI

				warpPerspective( subImg, normROI, Homo, Size(normROI.cols, normROI.rows));
				//warpPerspective( mBinSub, normROI, Homo, Size(normROI.cols, normROI.rows));
				Mat tmp = normROI.clone();
				// normROI <- result! (always square)
				// points <- points on preview!
				Holder holder;
				holder.id=identifyPattern(normROI);
				holder.points = refinedVertices;
				holder.tag = tmp;
				holder.homo = Homo;
				//Kalman kal = kalmans[holder.id];
				holder.center = center(refinedVertices);
				/*if(!kal.inited){
					//first time we are using predictions
					kal.init(holder.center,50);
				}else{
					Point2f now = holder.center;
					holder.center = kal.predict(now);
				}*/

				//map[holder.id]=true;
				//__android_log_print(ANDROID_LOG_INFO, "normalizePattern", "holder");
				if(holder.id>=0){
					holders.push_back(holder);
				}
				Mat rvec = Mat(3,1,CV_32FC1);
				Mat tvec = Mat(3,1,CV_32FC1);

			}
		}

	}

	/*for(int cc=0;cc<32;cc++){
		if(!map[cc]){
			Kalman kal = kalmans[cc];
			if(kal.inited){
				Holder holder;
				holder.id=cc;
				holder.center = kal.predict();
				Mat homo(3,3,CV_32F);
				holder.homo = homo;
				vector<Point2f> emptyPoints;
				holder.points = emptyPoints;
				Mat empty(1,1,CV_8UC1);
				holder.tag = empty;
				holders.push_back(holder);
			}
		}

	}*/
	return holders;
}
Point2f PatternDetector::center(vector<Point2f> vertices){

	float xx=0;
	float yy=0;
	int size = vertices.size();
	for(int pos=0;pos<size;pos++){
		Point2f point = vertices[pos];
		xx+=point.x;
		yy+=point.y;
	}
	Point2f result(xx/size,yy/size);
	return result;
}
int PatternDetector::identifyPattern(Mat normMat){
	const int rows = normMat.rows;
	const int cols = normMat.cols;
	const int dRow = rows/sizePix;
	const int dCol = cols/sizePix;
	const int ddRow = dRow/2;
	const int ddCol = dCol/2;
	unsigned int X=0;
	unsigned int Y=0;
	int value=0;
	int power=0;
	for(X=1;X<(sizePix-1);X++){
		for(Y=1;Y<(sizePix-1);Y++){



			Mat roi = normMat(Rect(X*sizePix,Y*sizePix,sizePix,sizePix)).clone();

			Scalar m = mean(roi);
			float val = m.val[0];
			//__android_log_print(ANDROID_LOG_INFO, "X:Y:mean", "%d:%d:%f",X,Y,val);
			if(X==1 && Y==1){
				if(val<122){
					return -1;
				}
			}else if(X==sizePix-2 && Y==sizePix-2){
				if(val>122){
					return -1;
				}
			}else if(X==1 && Y==sizePix-2){
				if(val>122){
					return -1;
				}
			}else if(X==sizePix-2 && Y==1){
				if(val>122){
					return -1;
				}
			}else{
				if(val>122){
					value=value+(int)pow((float)2,(float)power);
				}
				power++;
			}

		}
	}

	return 31 - value;
}




extern "C" {
static jclass holderCls; //
static jmethodID holderCons;


static jclass jMatCls; //org.opencv.core.Mat
static jmethodID jMatCons;
static jmethodID jMatGetNatAddr;


jint JNI_OnLoad(JavaVM* vm, void* reserved){
    JNIEnv* env;
    if (vm->GetEnv(reinterpret_cast<void**>(&env), JNI_VERSION_1_6) != JNI_OK) {
        return -1;
    }
    //org/object/tracker/Holder
	jclass hldrTmp = env->FindClass("org/object/tracker/Holder");
	holderCls = (jclass)env->NewGlobalRef(hldrTmp);
	holderCons = env->GetMethodID(holderCls,"<init>","()V");
    //org/opencv/core/Mat
	jclass jMatTmp = env->FindClass("org/opencv/core/Mat");
	jMatCls = (jclass)env->NewGlobalRef(jMatTmp);
	jMatCons = env->GetMethodID(jMatCls,"<init>","()V");
	jMatGetNatAddr = env->GetMethodID(jMatCls,"getNativeObjAddr","()J");


    return JNI_VERSION_1_6;
}
JNIEXPORT jlong JNICALL Java_org_object_tracker_Preview_newPatternDetectorNtv(JNIEnv* env, jobject,\
		jint adaptThresh,jint blockSize,jint normSize,jlong mCamMatrixAddr,jlong mDistMatrixAddr ){
	Mat& mCamMatrix = *(Mat*)mCamMatrixAddr;
	Mat& mDistMatrix = *(Mat*)mDistMatrixAddr;
	PatternDetector* detector = new PatternDetector(adaptThresh,blockSize,normSize,mCamMatrix,mDistMatrix);
	return (jlong)detector;
}
JNIEXPORT void JNICALL Java_org_object_tracker_Preview_delPatternDetectorNtv(JNIEnv* env, jobject,\
		jlong pattDetectAddr){
	PatternDetector* detector = (PatternDetector*)pattDetectAddr;
	delete detector;
}
JNIEXPORT jobjectArray JNICALL Java_org_object_tracker_Preview_detectPatternDetectorArrNtv(JNIEnv* env, jobject,\
		jlong pattDetectAddr,jlong addrYuv,jdouble scaleX, jdouble scaleY){
    Mat& mYuv = *(Mat*)addrYuv;
    PatternDetector& detector = *(PatternDetector*)pattDetectAddr;
    Mat mGray(mYuv.rows,mYuv.cols,CV_8UC1);
    cvtColor(mYuv,mGray,COLOR_YUV2GRAY_NV21);
    vector<Holder> holders = detector.normalizePattern(mGray);
    Operations op(env);
    op.setupJHolderMethods(holderCls,holderCons);
    op.setupJMat(jMatCls,jMatCons,jMatGetNatAddr);
    jobjectArray result = op.convertArr(holders,scaleX,scaleY);
    return result;
}


}

