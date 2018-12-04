// code by ma
package ch.ethz.idsc.can;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;

public class CAN {
  public static CAN create() throws IOException {
    CAN can = new CAN();
    // Check if Kvaser dongle is connected
    boolean flag = can.checkKvaserConnection();
    if (flag) {
      // set baud rate to 250k/bits
      flag = can.setBaudRate(250000);
    }
    if (flag) {
      // start CANopen gateway server with default node id 2
      flag = can.startGateway(2);
    }
    if (flag) {
      // start comport 5678
      flag = can.startComport(5678);
    }
    if (flag) {
      // ini the GrossFunk uint
      flag = can.initFGunit();
    }
    return can;
  }

  // ---
  Socket pingSocket = null;
  Socket pingSocketMon = null;
  PrintWriter comportOut = null;
  BufferedReader comportIn = null;
  ConsoleCtrl monitor = null;
  ConsoleWrite conewrite = null;

  private CAN() {
    // ---
  }

  /** TODO check bla
   * @return true if ...
   * @throws IOException */
  private boolean initFGunit() throws IOException {
    boolean flag = true;
    String line = "";
    // comportOut = new PrintWriter(pingSocket.getOutputStream(), true);
    // comportIn = new BufferedReader(new
    // InputStreamReader(pingSocket.getInputStream()));
    // set the GF (all nodes ) unit in pre-operational
    // where:
    // [1] = Sequence number
    // 0x00 = should be the node ID but when puttin '2' noting is brodcasted on the
    // bus
    // preop = command that but the node in pre operational
    comportOut.println("[1] 0x00 preop");
    line = "";
    // TODO dont use a while here
    while ((line = comportIn.readLine()) != null) {
      if (line.contains("[1] OK")) {
        System.out.println(line);
        flag = true;
        break;
      }
    }
    comportOut.println("[2] set rpdo 2 0x282 event 3 u8 u8 u8 ");
    line = "";
    // TODO dont use a while here
    while ((line = comportIn.readLine()) != null) {
      if (line.contains("[2] OK")) {
        System.out.println(line);
        flag = true;
        break;
      }
    }
    comportOut.println("[3] set rpdo 1 0x182 event 8 u8 u8 u8 u8 u8 u8 u8 u8");
    line = "";
    // TODO dont use a while here
    while ((line = comportIn.readLine()) != null) {
      if (line.contains("[3] OK")) {
        System.out.println(line);
        flag = true;
        break;
      }
    }
    // setup PDO for transmitting the heart beat to enable the GrossFunk receiver
    // where:
    // [2] = Sequence number
    // set = just syntax
    // tpdo = indicates the directions i.e Tx
    // 1 = number, each TPDO has a uniq number defined by GrossFunk see EDS file
    // 0x202 = COD-ID defined by GrossFunk see EDS file
    // sync1 = syntax
    // 2 = number of data to be transmitted
    // u8 u8 = data type
    comportOut.println("[4] set tpdo 1 0x202 sync1 2 u8 u8");
    line = "";
    // TODO dont use a while here
    while ((line = comportIn.readLine()) != null) {
      if (line.contains("[4] OK")) {
        System.out.println(line);
        flag = true;
        break;
      }
    }
    // start monitoring thread
    monitor = new ConsoleCtrl(new BufferedReader(new InputStreamReader(pingSocket.getInputStream())));
    monitor.start();
    // start GrossFunk unit
    comportOut.println("[5] 0x00 start");
    line = "";
    // TODO dont use a while here
    while ((line = comportIn.readLine()) != null) {
      if (line.contains("[5] OK")) {
        System.out.println(line);
        flag = true;
        break;
      }
    }
    // comportIn.close();
    // start sending 0x2 to RPDO_Receiver ID to enable the relay
    comportOut.println("[100] write pdo 1 2 0 2");
    conewrite = new ConsoleWrite(comportOut, 0, 2);
    conewrite.start();
    return flag;
  }

  private boolean startComport(int i) throws IOException {
    Boolean flag = true;
    // TODO add some exception handling
    pingSocket = new Socket("localhost", i);
    comportOut = new PrintWriter(pingSocket.getOutputStream(), true);
    comportIn = new BufferedReader(new InputStreamReader(pingSocket.getInputStream()));
    return flag;
  }

  private boolean startGateway(int i) throws IOException {
    Boolean flag = true;
    String number = Integer.toString(i);
    String network = "Network is down";
    String command = "./cogw_socketcan-1.1.2 -n ";
    command = command.concat(number);
    // TODO this command cases a can message to be broadcasted, could be the boot up
    // message.
    Process proc = Runtime.getRuntime().exec(command, null, new File("/home/marcus/Documents/Emtas/Tools/cogw_socketcan-1.1.2/"));
    BufferedReader reader = new BufferedReader(new InputStreamReader(proc.getInputStream()));
    String line = "";
    while ((line = reader.readLine()) != null) {
      System.out.print(line + "\n");
      if (line.contains(network)) {
        flag = false;
        System.out.println("Could not start Gateway");
        break;
      }
    }
    reader.close();
    return flag;
  }

  private boolean setBaudRate(int i) throws IOException {
    Boolean flag = true;
    String number = Integer.toString(i);
    String bit = "bitrate ";
    bit = bit.concat(number);
    String command = "sudo ip link set can0 up type can bitrate ";
    command = command.concat(number);
    Process proc = Runtime.getRuntime().exec(command);
    // command = " echo 015017 | sudo -S ifconfig can0 up";
    // command = "sudo ifconfig can0 up";
    // proc = Runtime.getRuntime().exec(command);
    // make sure baud rate is relay set to 250kbits
    command = "ip -details -statistics link show can0";
    Process proc1 = Runtime.getRuntime().exec(command);
    BufferedReader reader = new BufferedReader(new InputStreamReader(proc1.getInputStream()));
    // TODO add some fault handling here, i.e if we the command throws an exception
    // or if we get a return sting when calling the baud rate command
    String line = "";
    while ((line = reader.readLine()) != null) {
      // System.out.print(line + "\n");
      if (line.contains(bit)) {
        flag = true;
        System.out.println("Baudrate set to: " + number);
        break;
      }
      flag = false;
    }
    if (!flag) {
      System.out.println("faulty baudrate");
    }
    reader.close();
    return flag;
  }

  public boolean checkKvaserConnection() throws IOException {
    String CAN0 = "can0";
    String command = "ip link list";
    Boolean flag = false;
    Process proc = Runtime.getRuntime().exec(command);
    // Read the output
    BufferedReader reader = new BufferedReader(new InputStreamReader(proc.getInputStream()));
    String line = "";
    while ((line = reader.readLine()) != null) {
      if (line.contains(CAN0)) {
        flag = true;
        System.out.println("Kvaser dongle connected");
      }
    }
    if (!flag) {
      System.out.println("Kvaser dongle not connected");
    }
    reader.close();
    return flag;
  }
}
