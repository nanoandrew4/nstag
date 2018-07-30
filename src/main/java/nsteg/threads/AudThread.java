package nsteg.threads;

import nsteg.encoders.aud.AudEndState;

/**
 * Superclass for AudEncoderThread class.
 */
public class AudThread extends nStegThread {
	protected AudEndState endState = new AudEndState();
	protected byte[] pcm;
	protected int currPCMByte = 0, currLSB = 0;

	/**
	 * Superclass constructor for the audio encoding threads. Assigns the PCM byte array that will be operated
	 * on.
	 *
	 * @param pcm PCM byte array that will be operated on
	 */
	public AudThread(byte[] pcm) {
		this.pcm = pcm;
		this.setDaemon(true);
	}
}
