package fr.unistra.pelican.algorithms.detection;


import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;

import fr.unistra.pelican.Algorithm;
import fr.unistra.pelican.AlgorithmException;
import fr.unistra.pelican.Image;
import fr.unistra.pelican.algorithms.conversion.AverageChannels;
import fr.unistra.pelican.algorithms.io.ImageLoader;
import fr.unistra.pelican.algorithms.visualisation.Viewer2D;
import fr.unistra.pelican.util.Keypoint;
import fr.unistra.pelican.util.NumericValuedPoint;


/* Old imports
import java.util.Collections;
import fr.unistra.pelican.BooleanImage;
import fr.unistra.pelican.algorithms.conversion.AverageChannels;
import fr.unistra.pelican.algorithms.io.ImageLoader;
import fr.unistra.pelican.algorithms.morphology.gray.GrayInternGradient;
import fr.unistra.pelican.algorithms.visualisation.Viewer2D;
import fr.unistra.pelican.util.Keypoint;
import fr.unistra.pelican.util.NumericValuedPoint;
import fr.unistra.pelican.util.Point4D;
import fr.unistra.pelican.util.Tools;
import fr.unistra.pelican.util.data.DoubleArrayData;
import fr.unistra.pelican.util.morphology.FlatStructuringElement2D;
 */

/**
 * Harris Corner Detector
 * 		
 *  k = det(A) - k * trace(A)^2
 * 
 *  Where A is the second-moment matrix 
 * 
 *            | Lx²(x+dx,y+dy)    Lx.Ly(x+dx,y+dy) |
 *  A =  Sum  |                                    | * Gaussian(dx,dy)
 *      dx,dy | Lx.Ly(x+dx,y+dy)  Ly²(x+dx,y+dy)   |
 * 
 *  and k = a/(1+a)^2, 
 *  
 *  where "a" is the minimum ratio between the two eigenvalues
 *  for a point to be considered as a corner.
 *  
 * @author Xavier Philippeau (source : http://www.developpez.net/forums/d325133/general-developpement/algorithme-mathematiques/contribuez/image-detecteur-harris-imagej/#post3363731)
 * @author Julien Bidolet (adaptation for pelican)	
 */
public class Harris extends Algorithm {
	/** Input image */
	public Image image;

	/** Maximal number of points */
	public int maxNumber=0;

	/** Gaussian filter parameter*/
	public double sigma=1.2;

	/**Measure formula parameter*/
	public double k=0.06;

	/** Minimal distance between 2 corners*/
	public int spacing=8;

	/** Output corners list */
	public ArrayList<Keypoint> keypoints = new ArrayList<Keypoint>();


	/** precomputed values of the derivatives */
	private float[][] Lx2,Ly2,Lxy;

	/**
	 *  Constructor
	 */
	public Harris() {
		super.help="Performs Harris corner detection";
		super.inputs="image";
		super.outputs="keypoints";
		super.options="maxNumber,sigma,k,spacing";
	}

	/**
	 * Gaussian function
	 */
	private double gaussian(double x, double y, double sigma) {
		double sigma2 = sigma*sigma;
		double t = (x*x+y*y)/(2*sigma2);
		double u = 1.0/(2*Math.PI*sigma2);
		double e = u*Math.exp( -t );
		return e;
	}

	/**
	 * Sobel gradient 3x3
	 */
	private float[] sobel(int x, int y) {
		int v00=0,v01=0,v02=0,v10=0,v12=0,v20=0,v21=0,v22=0;

		int x0 = x-1, x1 = x, x2 = x+1;
		int y0 = y-1, y1 = y, y2 = y+1;
		if (x0<0) x0=0;
		if (y0<0) y0=0;
		if (x2>=image.xdim) x2=image.xdim-1;
		if (y2>=image.ydim) y2=image.ydim-1;

		v00=image.getPixelXYByte(x0, y0); v10=image.getPixelXYByte(x1, y0); v20=image.getPixelXYByte(x2, y0);
		v01=image.getPixelXYByte(x0, y1);                    				v21=image.getPixelXYByte(x2, y1);
		v02=image.getPixelXYByte(x0, y2); v12=image.getPixelXYByte(x1, y2); v22=image.getPixelXYByte(x2, y2);

		float sx = ((v20+2*v21+v22)-(v00+2*v01+v02))/(4*255f);
		float sy = ((v02+2*v12+v22)-(v00+2*v10+v20))/(4*255f);
		return new float[] {sx,sy};
	}


	/**
	 * Compute the 3 arrays Ix, Iy and Ixy
	 */
	private void computeDerivatives(double sigma){
		this.Lx2 = new float[image.xdim][image.ydim];
		this.Ly2 = new float[image.xdim][image.ydim];
		this.Lxy = new float[image.xdim][image.ydim];

		// gradient values: Gx,Gy
		float[][][] grad = new float[image.xdim][image.ydim][];
		for (int y=0; y<image.ydim; y++)
			for (int x=0; x<image.xdim; x++)
				grad[x][y] = sobel(x,y);

		// precompute the coefficients of the gaussian filter
		int radius = (int)(2*sigma);
		int window = 1+2*radius;
		float[][] gaussian = new float[window][window];
		for(int j=-radius;j<=radius;j++)
			for(int i=-radius;i<=radius;i++)
				gaussian[i+radius][j+radius]=(float)gaussian(i,j,sigma);

		// Convolve gradient with gaussian filter:
		//
		// Ix2 = (F) * (Gx^2)
		// Iy2 = (F) * (Gy^2)
		// Ixy = (F) * (Gx.Gy)
		//
		for (int y=0; y<image.ydim; y++) {
			for (int x=0; x<image.xdim; x++) {

				for(int dy=-radius;dy<=radius;dy++) {
					for(int dx=-radius;dx<=radius;dx++) {
						int xk = x + dx;
						int yk = y + dy;
						if (xk<0 || xk>=image.xdim) continue;
						if (yk<0 || yk>=image.ydim) continue;

						// gaussian weight
						double gw = gaussian[dx+radius][dy+radius];

						// convolution
						this.Lx2[x][y]+=gw*grad[xk][yk][0]*grad[xk][yk][0];
						this.Ly2[x][y]+=gw*grad[xk][yk][1]*grad[xk][yk][1];
						this.Lxy[x][y]+=gw*grad[xk][yk][0]*grad[xk][yk][1];
					}
				}
			}
		}
	}

	/**
	 * compute harris measure for a pixel
	 */
	private float harrisMeasure(int x, int y, float k) {
		// matrix elements (normalized)
		float m00 = this.Lx2[x][y]; 
		float m01 = this.Lxy[x][y];
		float m10 = this.Lxy[x][y];
		float m11 = this.Ly2[x][y];

		// Harris corner measure = det(M)-k.trace(M)^2
		return m00*m11 - m01*m10 - k*(m00+m11)*(m00+m11);
	}

	/**
	 * return true if the measure at pixel (x,y) is a local spatial Maxima
	 */
	private boolean isSpatialMaxima(float[][] hmap, int x, int y) {
		int n=8;
		int[] dx = new int[] {-1,0,1,1,1,0,-1,-1};
		int[] dy = new int[] {-1,-1,-1,0,1,1,1,0};
		double w =  hmap[x][y];
		for(int i=0;i<n;i++) {
			double wk = hmap[x+dx[i]][y+dy[i]];
			if (wk>=w) return false;
		}
		return true;
	}

	/**
	 * compute the Harris measure for each pixel of the image
	 */
	private float[][] computeHarrisMap(double k) {

		// Harris measure map
		float[][] harrismap = new float[image.xdim][image.ydim];

		// for each pixel in the image
		for (int y=0; y<image.ydim; y++) {
			for (int x=0; x<image.xdim; x++) {
				// compute the harris measure
				double h =  harrisMeasure(x,y,(float)k);
				if (h<=0) continue;
				// log scale
				h = 255 * Math.log(1+h) / Math.log(1+255);
				// store
				harrismap[x][y]=(float)h;
			}
		}

		return harrismap;
	}

	/**
	 * Perfom the Harris Corner Detection
	 * 
	 * @param sigma gaussian filter parameter 
	 * @param k parameter of the harris measure formula
	 * @param minDistance minimum distance between corners
	 * @return the orginal image marked with cross sign at each corner
	 */
	public void filter(double sigma, double k, int minDistance) {

		// precompute derivatives
		computeDerivatives(sigma);

		// Harris measure map
		float[][] harrismap = computeHarrisMap(k);
		ArrayList<NumericValuedPoint> corners = new ArrayList<NumericValuedPoint>();
		// for each pixel in the harrismap 
		for (int y=1; y<image.ydim-1; y++) {
			for (int x=1; x<image.xdim-1; x++) {
				// thresholding : harris measure > epsilon
				float h = harrismap[x][y];
				if (h<=1E-3) continue;
				// keep only a local maxima
				if (!isSpatialMaxima(harrismap, x, y)) continue;
				// add the corner to the list
				corners.add( new NumericValuedPoint(x,y,h) );
			}
		}

		// remove corners to close to each other (keep the highest measure)
		Iterator<NumericValuedPoint> iter = corners.iterator();
		while(iter.hasNext()) {
			NumericValuedPoint p = iter.next();
			for(NumericValuedPoint n:corners) {
				if (n==p) continue;
				int dist = (int)Math.sqrt( (p.getX()-n.getX())*(p.getX()-n.getX())+(p.getY()-n.getY())*(p.getY()-n.getY()) );
				if(dist>minDistance) continue;
				if (n.getValue().floatValue()<p.getValue().floatValue()) continue;
				iter.remove();
				break;
			}
		}
		keypoints = new ArrayList<Keypoint>();
		if(maxNumber == 0 || maxNumber >=corners.size()){
			for (NumericValuedPoint p:corners) {
				keypoints.add(new Keypoint(p.getX(),p.getY()));
			}
		}
		else{
			Collections.sort(corners);
			for(int i=0;i<maxNumber;i++){
				NumericValuedPoint p = corners.get(i);
				keypoints.add(new Keypoint(p.getX(),p.getY()));
			}
		}

	}

	@Override
	public void launch() throws AlgorithmException {
		filter(sigma, k, spacing);
	}

//TODO : Check the different exec parameters order (seems to be some problem there ...)	
	
	/**
	 * Perform Harris corner detection
	 * @param image Input image
	 * @return List of detected corners
	 */
	public static ArrayList<Keypoint> exec(Image image) {
		return (ArrayList<Keypoint>) new Harris().process(image);

	}

	/**
	 * Perform Harris corner detection
	 * @param image Input image
	 * @param gaussian Gaussian filter parameter
	 * @param k parameter of the harris measure formula
	 * @param maxNumber Maximum number of keypoints
	 * @param spacing Minimal space between two corners
	 * @return List of detected corners
	 */
	public static ArrayList<Keypoint> exec(Image image,int maxNumber,double gaussian,double k,int spacing) {
		return (ArrayList<Keypoint>) new Harris().process(image,maxNumber,gaussian,k,spacing);

	}

	/**
	 * Perform Harris corner detection
	 * @param image Input image
	 * @param gaussian Gaussian filter parameter
	 * @param k parameter of the harris measure formula
	 * @param maxNumber Maximum number of keypoints
	 * @return List of detected corners
	 */
	public static ArrayList<Keypoint> exec(Image image,int maxNumber,double gaussian,double k) {
		return (ArrayList<Keypoint>) new Harris().process(image,maxNumber,gaussian,k);

	}

	/**
	 * Perform Harris corner detection
	 * @param image Input image
	 * @param gaussian Gaussian filter parameter
	 * @param maxNumber Maximum number of keypoints
	 * @return List of detected corners
	 */
	public static ArrayList<Keypoint> exec(Image image,int maxNumber,double gaussian) {
		return (ArrayList<Keypoint>) new Harris().process(image,maxNumber,gaussian);

	}

	/**
	 * Perform Harris corner detection
	 * @param image Input image
	 * @param maxNumber Maximum number of keypoints
	 * @return List of detected corners
	 */
	public static ArrayList<Keypoint> exec(Image image,int maxNumber) {
		return (ArrayList<Keypoint>) new Harris().process(image,maxNumber);

	}

	public static void main(String[] args) {
		Image i = ImageLoader.exec("samples/lenna.png");
		i = AverageChannels.exec(i);

		ArrayList<Keypoint> keypoints = Harris.exec(i,50);
		for (Keypoint k : keypoints) {
			// add the cross sign over the image
			for (int dt=-3; dt<=3; dt++) {
				if (k.x+dt>=0 && k.x+dt<i.xdim ) i.setPixelXYByte((int) k.x+dt,(int) k.y, 255);
				if (k.y+dt>=0 && k.y+dt<i.ydim) i.setPixelXYByte((int) k.x,(int) k.y+dt, 255);
			}
			//System.out.println("corner found at: "+p.x+","+p.y+" ("+p.h+")");
		}	
		Viewer2D.exec(i);


	}
}


