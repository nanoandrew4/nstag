package nsteg.threads;

import nsteg.encoders.img.ImgEndState;

import javax.validation.constraints.NotNull;
import java.awt.image.BufferedImage;

/**
 * Superclass for ImgEncoder and ImgDecoder classes. Reduces code redundancy, and takes care of some of the setup that
 * both classes need to carry out in order to carry out their encoding and decoding tasks.
 */
public class ImgThread extends nStegThread {
	protected BufferedImage img;
	protected ImgEndState endState = new ImgEndState();

	protected int width, height;
	protected int numOfChannels;

	protected int sx, sy, currByte, endByte;

	/**
	 * Superclass constructor for the image encoding/decoding threads. Assigns and sets up the image that will be used.
	 *
	 * @param img           BufferedImage to operate on
	 * @param numOfChannels Number of channels that the image has
	 */
	public ImgThread(@NotNull BufferedImage img, int numOfChannels) {
		this.img = img;
		this.numOfChannels = numOfChannels;

		width = img.getWidth();
		height = img.getHeight();

		this.setDaemon(true);
	}
}
