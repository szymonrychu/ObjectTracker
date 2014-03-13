#ifndef pattern
#define pattern
#include <jni.h>
#include <vector>
#include <opencv2/core/core.hpp>
#include <opencv2/imgproc/imgproc.hpp>
#include <opencv2/imgproc/imgproc_c.h>
#include <opencv2/highgui/highgui.hpp>
#include <opencv2/features2d/features2d.hpp>
#include <opencv2/calib3d/calib3d.hpp>


using namespace std;
using namespace cv;

class Pattern
{
	public:
		vector<Point2f> vertices;
		int id;
		int orientation;//{0,1,2,3}
		float size; //in milimeters
		double confidence;//min: -1, max: 1
		Mat rotVec, transVec, rotMat;

		Pattern(double param1=80);

		~Pattern(){};

		//solves the exterior orientation problem between patten and camera
		void getExtrinsics(int patternSize, const Mat& cameraMatrix, const Mat& distortions);

		//augments image with 3D cubes. It;s too simple augmentation jsut for checking
		void draw(Mat& frame, const Mat& camMatrix, const Mat& distMatrix);

		//computes the rotation matrix from the rotation vector using Rodrigues
		void rotationMatrix(const Mat& rotation_vector, Mat& rotation_matrix);

		//prints the properties of the patten and its transformation matrix
		void showPattern(void);

		vector<Point2f> getPoints(Mat& cameraMatrix, Mat& distortions);
};
Pattern::Pattern(double param1){
	id =-1;
	size = param1;
	orientation = -1;
	confidence = -1;

	rotVec = (Mat_<float>(3,1) << 0, 0, 0);
	transVec = (Mat_<float>(3,1) << 0, 0, 0);
	rotMat = Mat::eye(3, 3, CV_32F);
}
void Pattern::rotationMatrix(const Mat& rotation_vector, Mat& rotation_matrix)
{
	cv::Rodrigues(rotation_vector, rotation_matrix);
}
void Pattern::showPattern()
{
	cout << "Pattern ID: " << id << endl;
	cout << "Pattern Size: " << size << endl;
	cout << "Pattern Confedince Value: " << confidence << endl;
	cout << "Pattern Orientation: " << orientation << endl;
	rotationMatrix(rotVec, rotMat);
	cout << "Exterior Matrix (from pattern to camera): " << endl;
	for (int i = 0; i<3; i++){
		cout << rotMat.at<float>(i,0) << "\t" << rotMat.at<float>(i,1) << "\t" << rotMat.at<float>(i,2) << " |\t"<< transVec.at<float>(i,0) << endl;
	}
}
void Pattern::getExtrinsics(int patternSize, const Mat& cameraMatrix, const Mat& distortions)
{

	CvMat objectPts;//header for 3D points of pat3Dpts
	CvMat imagePts;//header for 2D image points of pat2Dpts
	CvMat intrinsics = cameraMatrix;
	CvMat distCoeff = distortions;
	CvMat rot = rotVec;
	CvMat tra = transVec;
//		CvMat rotationMatrix = rotMat; // projectionMatrix = [rotMat tra];

	CvPoint2D32f pat2DPts[4];
	for (int i = 0; i<4; i++){
		pat2DPts[i].x = this->vertices.at(i).x;
		pat2DPts[i].y = this->vertices.at(i).y;
	}

	//3D points in pattern coordinate system
	CvPoint3D32f pat3DPts[4];
	pat3DPts[0].x = 0.0;
	pat3DPts[0].y = 0.0;
	pat3DPts[0].z = 0.0;
	pat3DPts[1].x = patternSize;
	pat3DPts[1].y = 0.0;
	pat3DPts[1].z = 0.0;
	pat3DPts[2].x = patternSize;
	pat3DPts[2].y = patternSize;
	pat3DPts[2].z = 0.0;
	pat3DPts[3].x = 0.0;
	pat3DPts[3].y = patternSize;
	pat3DPts[3].z = 0.0;

	cvInitMatHeader(&objectPts, 4, 3, CV_32FC1, pat3DPts);
	cvInitMatHeader(&imagePts, 4, 2, CV_32FC1, pat2DPts);

	//find extrinsic parameters
	cvFindExtrinsicCameraParams2(&objectPts, &imagePts, &intrinsics, &distCoeff, &rot, &tra);
}
vector<Point2f> Pattern::getPoints(Mat& camMatrix, Mat& distMatrix){
	Mat modelPts = (Mat_<float>(8,3) << 0, 0, 0, size, 0, 0, size, size, 0, 0, size, 0,
			0, 0, -size, size, 0, -size, size, size, -size, 0, size, -size );

	std::vector<cv::Point2f> model2ImagePts;
	/* project model 3D points to the image. Points through the transformation matrix
	(defined by rotVec and transVec) "are transfered" from the pattern CS to the
	camera CS, and then, points are projected using camera parameters
	(camera matrix, distortion matrix) from the camera 3D CS to its image plane
	*/
	projectPoints(modelPts, rotVec, transVec, camMatrix, distMatrix, model2ImagePts);
	return model2ImagePts;



}
void Pattern::draw(Mat& frame, const Mat& camMatrix, const Mat& distMatrix)
{

	CvScalar color = cvScalar(255,255,255);

	switch (id){
		case 1:
			 color = cvScalar(255,0,255);
			break;
		case 2:
			 color = cvScalar(255,255,0);
			break;
		case 3:
			 color = cvScalar(0,255,255);
			break;
	}

	//model 3D points: they must be projected to the image plane
	Mat modelPts = (Mat_<float>(8,3) << 0, 0, 0, size, 0, 0, size, size, 0, 0, size, 0,
		0, 0, -size, size, 0, -size, size, size, -size, 0, size, -size );


	std::vector<cv::Point2f> model2ImagePts;
	/* project model 3D points to the image. Points through the transformation matrix
	(defined by rotVec and transVec) "are transfered" from the pattern CS to the
	camera CS, and then, points are projected using camera parameters
	(camera matrix, distortion matrix) from the camera 3D CS to its image plane
	*/
	projectPoints(modelPts, rotVec, transVec, camMatrix, distMatrix, model2ImagePts);


	//draw cube, or whatever
	int i;
	for (i =0; i<4; i++){
		cv::line(frame, model2ImagePts.at(i%4), model2ImagePts.at((i+1)%4), color, 3);
	}
	for (i =4; i<7; i++){
		cv::line(frame, model2ImagePts.at(i%8), model2ImagePts.at((i+1)%8), color, 3);
	}
	cv::line(frame, model2ImagePts.at(7), model2ImagePts.at(4), color, 3);
	for (i =0; i<4; i++){
		cv::line(frame, model2ImagePts.at(i), model2ImagePts.at(i+4), color, 3);
	}

	//draw the line that reflects the orientation. It indicates the bottom side of the pattern
	cv::line(frame, model2ImagePts.at(2), model2ImagePts.at(3), cvScalar(80,255,80), 3);

	model2ImagePts.clear();

}
#endif pattern
