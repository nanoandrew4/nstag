package nsteg;

/**
 * Simple spinning icon made out of characters. Used while program is running long computations, so the user
 * feels like the program is running something vs just being stuck. Pretty meaningless, but hey, looks good!
 */
public class Spinner extends Thread {
	private static Thread t;
	private static Spinner s;

	private char[] chars = {'|', '/', '-', '\\'};
	private static boolean spinning = false;

	@Override
	public void run() {
		spinning = true;
		for (int i = 0; spinning; i++) {
			System.out.print(chars[i % chars.length]);
			try {
				sleep(500);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
			System.out.print("\b \b");
		}
	}

	public static void end() {
		spinning = false;
		try {
			t.join();
		} catch (InterruptedException ignored) {
			System.err.println("Spinner is already dead");
		}
	}

	public static void spin() {
		if (t == null || !Spinner.spinning) {
			t = new Thread(s = new Spinner());
			t.setDaemon(true);
			t.start();
		} else
			Spinner.end();
	}

	/**
	 * If a spinner is running, stops it. Then prints the passed string, and starts the spinner again.
	 *
	 * @param str String to be printed before starting the spinner
	 */
	public static void printWithSpinner(String str) {
		if (spinning)
			spin();
		System.out.print(str);
		spin();
	}
}
