package nsteg.encoders.img;

/**
 * Container for ImgEncoder and its various ImgEncoderThread instances to communicate encoding position data.
 *
 * @see ImgEncoderThread
 * @see ImgEncoder
 */
public class ImgEndState {
	public int endX, endY, endLSB, endChan;
}