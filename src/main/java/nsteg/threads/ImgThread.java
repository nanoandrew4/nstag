package nsteg.threads;

import nsteg.encoders.img.ImgEndState;

import javax.validation.constraints.NotNull;
import java.awt.image.BufferedImage;

public class ImgThread extends nStegThread {
	protected BufferedImage img;
	protected ImgEndState endState = new ImgEndState();

	protected int width, height;
	protected int numOfChannels;

	protected int sx, sy, currByte, endByte;

	public ImgThread(@NotNull BufferedImage img, int numOfChannels) {
		this.img = img;
		this.numOfChannels = numOfChannels;

		width = img.getWidth();
		height = img.getHeight();

		this.setDaemon(true);
	}
}
