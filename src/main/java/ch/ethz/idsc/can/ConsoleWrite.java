// code by ma
package ch.ethz.idsc.can;

import java.io.PrintWriter;

class ConsoleWrite extends Thread {
  public PrintWriter comportOut = null;
  public int data0 = 0;
  public int data1 = 0;

  ConsoleWrite(PrintWriter out, int d0, int d1) {
    comportOut = out;
    data0 = d0;
    data1 = d1;
  }

  public void run() {
    System.out.println("Running thread write");
    Boolean run = true;
    while (run) {
      try {
        Thread.sleep(500);
      } catch (InterruptedException e) {
        // TODO Auto-generated catch block
        e.printStackTrace();
      }
      comportOut.println("[100] write pdo 1 2 0 2");
      // comportOut.println("[101] write pdo 1 2 " + data0 + " " + data1);
      // System.out.println("sent PDO" );
    }
  }
}
