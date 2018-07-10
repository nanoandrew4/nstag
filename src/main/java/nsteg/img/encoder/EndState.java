package nsteg.img.encoder;

/**
 * Container for ImgEncoder and its various EncoderThread instances to communicate encoding position data.
 *
 * @see EncoderThread
 * @see ImgEncoder
 */
public class EndState {
	int endX, endY, endLSB, endNextChanToWrite;
}