package nsteg.threads;

public class nStegThread extends Thread {
	protected boolean running = true, active = false;
	protected int LSBsToUse = 1;

	public void setLSBsToUse(int LSBsToUse) {
		this.LSBsToUse = LSBsToUse;
	}

	private void stopRunning() {
		running = false;
	}

	public boolean isActive() {
		return active;
	}

	public void stopThread() {
		while (isActive())
			sleepMillis(10);

		stopRunning();
		try {
			join();
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
	}

	protected static void sleepMillis(long millis) {
		try {
			sleep(millis);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
	}
}
