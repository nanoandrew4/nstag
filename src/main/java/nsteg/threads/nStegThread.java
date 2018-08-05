package nsteg.threads;

/**
 * Superclass for all encoder/decoder thread classes.
 */
public class nStegThread extends Thread {
	protected boolean running = true, active = false;
	protected int LSBsToUse = 1;

	/**
	 * Sets the number of least significant bits that this thread will use to carry out its encoding/decoding tasks.
	 * Valid range is 1-8.
	 *
	 * @param LSBsToUse Number of least significant bits to use
	 */
	public void setLSBsToUse(int LSBsToUse) {
		if (LSBsToUse > 0 && LSBsToUse < 9)
			this.LSBsToUse = LSBsToUse;
		else
			System.err.println("Number of least significant bits to use is out of range, please input a number " +
							   "between 1 and 8");
	}

	/**
	 * Ends the run() loop, as soon as it has finished any job submitted before calling this method.
	 */
	private void stopRunning() {
		running = false;
	}

	/**
	 * Returns whether the thread is currently carrying out a task, or just waiting for one to be submitted.
	 *
	 * @return True if the thread is is working, false if it is waiting for work
	 */
	public boolean isActive() {
		return active;
	}

	/**
	 * Wait for the thread to finish its current work, and shut it down. This method returs once the thread has exited
	 * successfully.
	 */
	public void stopThread() {
		while (isActive())
			sleepMillis(1);

		stopRunning();
		try {
			join();
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
	}

	/**
	 * Wrapper for Thread.sleep(int millis), with error catching.
	 *
	 * @param millis Number of milliseconds to sleep for
	 */
	protected static void sleepMillis(long millis) {
		try {
			sleep(millis);
		} catch (InterruptedException ignored) {
		}
	}
}
