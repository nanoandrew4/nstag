package nstag;

/**
 * Simple spinning icon made out of characters. Used while program is running long computations, so the user
 * feels like the program is running something vs just being stuck. Pretty meaningless, but hey, looks good!
 */
public class Spinner extends Thread {
		private static Thread t;
		private static Spinner s;

		private char[] chars = {'|', '/', '-', '\\'};
		private boolean spinning = true;

		@Override
		public void run() {
			for (int i = 0; spinning; i++) {
				System.out.print(chars[i % chars.length]);
				try {
					sleep(500);
				} catch (InterruptedException e) {e.printStackTrace();}
				System.out.print("\b \b");
			}
		}

		private void end() {
			spinning = false;
			try {
				t.join();
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}

		public static void spin() {
			if (t == null || !s.spinning) {
				t = new Thread(s = new Spinner());
				t.setDaemon(true);
				t.start();
			} else
				s.end();
		}
}
