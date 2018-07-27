package nsteg.threads;

import nsteg.encoders.aud.AudEndState;

public class AudThread extends nStegThread {
	protected AudEndState endState = new AudEndState();
	protected byte[] pcm;
	protected int currPCMByte = 0, currLSB = 0;

	public AudThread(byte[] pcm) {
		this.pcm = pcm;

		this.setDaemon(true);
	}
}
