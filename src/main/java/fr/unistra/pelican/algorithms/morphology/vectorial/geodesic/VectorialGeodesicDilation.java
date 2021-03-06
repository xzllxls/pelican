package fr.unistra.pelican.algorithms.morphology.vectorial.geodesic;

import fr.unistra.pelican.Algorithm;
import fr.unistra.pelican.AlgorithmException;
import fr.unistra.pelican.BooleanImage;
import fr.unistra.pelican.Image;
import fr.unistra.pelican.PelicanException;
import fr.unistra.pelican.algorithms.arithmetic.VectorialMinimum;
import fr.unistra.pelican.algorithms.morphology.vectorial.VectorialDilation;
import fr.unistra.pelican.util.vectorial.orders.VectorialOrdering;

/**
 * This class performs a vectorial gedodesic dilation with the given structuring element
 * and ordering.
 * 
 * @author Abdullah
 */
public class VectorialGeodesicDilation extends Algorithm
{
	/**
	 * The input image
	 */
	public Image inputImage;

	/**
	 * The mask
	 */
	public Image mask;

	/**
	 * the structuring element
	 */
	public BooleanImage se;

	/**
	 * the vectorial ordering
	 */
	public VectorialOrdering vo;

	/**
	 * the output image
	 */
	public Image outputImage;
	
	/**
	 * This class performs a vectorial gedodesic dilation with the given structuring element and ordering.
	 * 
	 * @param image the input image
	 * @param mask the mask
	 * @param se the structuring element
	 * @param vo the vectorial ordering
	 * @return the output image
	 */
	public static Image exec(Image image, Image mask,BooleanImage se,VectorialOrdering vo) {
		return (Image) new VectorialGeodesicDilation().process(image,mask,se,vo);
	}

	/**
	 * Constructor
	 * 
	 */
	public VectorialGeodesicDilation() {

		super();
		super.inputs = "inputImage,mask,se,vo";
		super.outputs = "outputImage";
		
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see fr.unistra.pelican.Algorithm#launch()
	 */
	public void launch() throws AlgorithmException {
		outputImage = inputImage.copyImage(false);

		try {
			outputImage = (Image) new VectorialDilation().process(inputImage, se, vo);
			outputImage = (Image) new VectorialMinimum().process(outputImage, mask, vo);
		} catch (PelicanException e) {
			e.printStackTrace();
		}
	}

}
