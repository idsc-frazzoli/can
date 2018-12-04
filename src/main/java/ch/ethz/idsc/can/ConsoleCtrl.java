// code by ma
package ch.ethz.idsc.can;

import java.io.BufferedReader;
import java.io.IOException;

class ConsoleCtrl extends Thread {
  public BufferedReader comportIn = null;
  public String PDO1 = null;
  public String PDO2 = null;

  ConsoleCtrl(BufferedReader in) {
    comportIn = in;
  }

  public void run() {
    System.out.println("Running thread read");
    Boolean run = true;
    while (run) {
      String line = "";
      try {
        line = comportIn.readLine();
      } catch (IOException e) {
        // TODO Auto-generated catch block
        e.printStackTrace();
      }
      if (line.contains("ERROR")) {
        System.out.println(line);
      }
      if (line.contains("EMCY")) {
        System.out.println(line);
      }
      if (line.contains("pdo 1")) {
        if (!(line.equals(PDO1))) {
          PDO1 = line;
          char temp = line.charAt(8); // read knob state
          if ((temp == '1')) {
            // System.out.println("E-STOP");
          }
          if ((temp == '2')) {
            System.out.println("STOP");
          }
          if ((temp == '5')) {
            System.out.println("Manual operation");
          }
          if ((temp == '9')) {
            System.out.println("Autonomous operation");
          }
        }
      }
      if (line.contains("pdo 2")) {
        if (!(line.equals(PDO2))) {
          PDO2 = line;
          char temp = line.charAt(12);
          if (!(temp == '0')) {
            System.out.println("E-STOP from transmitter: " + temp);
          }
          // System.out.println(line );
        }
      }
      if (line.contains("read")) {
        System.out.println(line);
      }
    }
  }
}