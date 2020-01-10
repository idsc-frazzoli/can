// code by ma
// when running in a bash file use 'java -jar filename.jar; exit' to exit the program properly
package ch.ethz.idsc.can;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;

public class CAN {
  
	  Socket pingSocket = null;
	  PrintWriter comportOut = null;
	  BufferedReader comportIn = null;
	  ConsoleCtrl monitor = null;
	  
	  private int Baudrate = 250000; // 250k/bits
	  private int NodeID = 2;
	  private int SocketNr = 5678;
	  private String CANchannel = "can1";
	  
	  public static CAN create() throws IOException {
		CAN can = new CAN();
		
		boolean flag = can.checkCANmodule(); // make sure the dongle or PCI is connected
		
		if (flag) {
		  flag = can.setBaudRate(); // set baud rate and turn on the can module
		}
		if (flag) {
		  flag = can.CANselfcheck(); // make sure we can send and receive messages 
		}
		if (flag) {
		  flag = can.startGateway();// start Emotas CANopen gateway server
		}
		if (flag) {
		  flag = can.startComport(); // start socket 5678
		}
		if (flag) {
		  flag = can.initFGunit();// ini the GrossFunk uint and start heart beat signal
		}
		
		return can;
	}

  ConsoleWrite conewrite = null;

  private CAN() {
    // ---
  }

  private boolean initFGunit() throws IOException {
    boolean flag = true;
    String line = null;
    
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
        break;
      }
    }
    comportOut.println("[2] set rpdo 2 0x282 event 3 u8 u8 u8 ");
    line = "";
    // TODO dont use a while here
    while ((line = comportIn.readLine()) != null) {
      if (line.contains("[2] OK")) {
        System.out.println(line);
        break;
      }
    }
    comportOut.println("[3] set rpdo 1 0x182 event 8 u8 u8 u8 u8 u8 u8 u8 u8");
    line = "";
    // TODO dont use a while here
    while ((line = comportIn.readLine()) != null) {
      if (line.contains("[3] OK")) {
        System.out.println(line);
        break;
      }
    }
    // setup PDO for transmitting the heart beat to enable the GrossFunk receiver
    // where:
    // [4] = Sequence number
    // set = just syntax
    // tpdo = indicates the directions i.e Tx
    // 1 = number, each TPDO has a uniq number defined by GrossFunk see EDS file
    // 0x202 = COD-ID defined by GrossFunk see EDS file
    // sync1 = syntax
    // 2 = number of data to be transmitted
    // u8 nr1 = RPDO_StatusRegister
    // u8 nr2 = RPDO_ReceiverID
    comportOut.println("[4] set tpdo 1 0x202 sync1 2 u8 u8");
    line = "";
    // TODO dont use a while here
    while ((line = comportIn.readLine()) != null) {
      if (line.contains("[4] OK")) {
        System.out.println(line);
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
        break;
      }
    }
    // comportIn.close();
    // start sending 0x2 to RPDO_Receiver ID to enable the relay
    comportOut.println("[100] write pdo 1 2 0 2");
    conewrite = new ConsoleWrite(comportOut , 0, 2);
    conewrite.start();
    return flag;
  }

  private boolean startComport() throws IOException {
    boolean flag = true;
    // TODO add some exception handling
    pingSocket = new Socket("localhost", SocketNr);
    comportOut = new PrintWriter(pingSocket.getOutputStream(), true);
    comportIn = new BufferedReader(new InputStreamReader(pingSocket.getInputStream()));
    return flag;
  }

  private boolean startGateway() throws IOException {
    boolean flag = false;
    String line = null;
    // -b 250
    String baud = " -b " + Integer.toString(Baudrate/1000);
    // -p 5678
    String port = " -p " + Integer.toString(SocketNr);
    // -D can0
    String canx = " -D " + CANchannel;
    String licensefile = " -l /home/bumblebee/Documents/Emotas/Licences/emtas_01021_IDSC_ETH_Zurich_gw309_1-x-xII.ldat";
    // this is the basic command 
    String command = "./cogw_socketcan-1.4.0 -n " + Integer.toString(NodeID);
    command = command.concat(licensefile);
    command = command.concat(baud);
    command = command.concat(port);
    command = command.concat(canx);
    // ./cogw_socketcan-1.4.0 -n 2 -l /home/bumblebee/Documents/Emotas/Licences/emtas_01021_IDSC_ETH_Zurich_gw309_1-x-xII.ldat -b 250 -p 5678 -D can1
    
    // TODO this command cases a can message to be broadcasted, could be the boot up
    // message.
    Process proc = Runtime.getRuntime().exec(command, null, new File("/home/bumblebee/Documents/Emotas/Tools/cogw_socketcan-1_4_0/"));
    BufferedReader reader = new BufferedReader(new InputStreamReader(proc.getInputStream()));
    
    // TODO when running the program the first time we get stuck in the while loop
    while ( (line = reader.readLine()) != null) {
      System.out.print(line + "\n");
      flag = true;
      if (line.contains("Network is down") || line.contains("ERROR on bind") ) {
        flag = false;
        System.out.println("Could not start Gateway");
        System.out.println(line);
        proc.destroy();
        break;
      }
    }
    reader.close();
    return flag;
  }

  private boolean setBaudRate() throws IOException {
    boolean flag = false;
    String line = null;
    // sudo ip link set canX up type can bitrate 250000 
    String command = "sudo ip link set  " + CANchannel + " up type can bitrate " + Integer.toString(Baudrate);
    Process proc = Runtime.getRuntime().exec(command);
    
    // make sure baud rate is relay set to 250kbits
    command = "ip -details -statistics link show ";
    command = command.concat(CANchannel);
    proc = Runtime.getRuntime().exec(command);
    BufferedReader reader = new BufferedReader(new InputStreamReader(proc.getInputStream()));
    // TODO add some fault handling here, i.e if we the command throws an exception
    // or if we get a return sting when calling the baud rate command
   
    while ((line = reader.readLine()) != null) {
      //System.out.print(line + "\n");
      if (line.contains(Integer.toString(Baudrate))) {
        flag = true;
        System.out.println("Baudrate set to: " + Integer.toString(Baudrate));
        break;
      }
    }
    if (!flag) {
      System.out.println("faulty baudrate");
    }
    proc.destroy();
    reader.close();
    return flag;
  }

  public boolean checkCANmodule() throws IOException {
	boolean flag = false;
	String line = null;
	String command = "ip link list";
    Process proc = Runtime.getRuntime().exec(command);
    // Read the output
    BufferedReader reader = new BufferedReader(new InputStreamReader(proc.getInputStream()));
    
    while ((line = reader.readLine()) != null) {
      if (line.contains(CANchannel)) {
        flag = true;
        System.out.println("CAN module detected: " + CANchannel);
      }
    }
    if (!flag) {
    	System.out.println("CAN module not detected");
    }
    proc.destroy();
    reader.close();
    return flag;
  }
  
  // Loopback not possible i.e can not do a self check on the received messages 
  public boolean CANselfcheck() throws IOException{
	  boolean flag = false;
	  String line = null;
	  String sub = null;
	  int pretxCount = 0;
	  int posttxCount = 0;
	  boolean txDetect = false;
	  
	  // make sure the channel is ON and record the Tx
	  String command = "ip -details -statistics link show " + CANchannel;
	  Process proc = Runtime.getRuntime().exec(command);
	  BufferedReader reader = new BufferedReader(new InputStreamReader(proc.getInputStream()));
	
	  while ((line = reader.readLine()) != null) {
	      
		  if (line.contains("STOPPED") || (line.contains("BUS-OFF")) ) {
	        System.out.println(CANchannel + " is not activated or have a CAN bus error");
	        command = " sudo ip link set " + CANchannel + " up type can restart";
	        System.out.println("Attempt a restart");
	        proc = Runtime.getRuntime().exec(command);
	        command = "sudo ip link set  " + CANchannel + " up type can bitrate " + Integer.toString(Baudrate);
	        proc = Runtime.getRuntime().exec(command);
	      }		  
	    }
	  
	  command = "ip -details -statistics link show " + CANchannel;
	  proc = Runtime.getRuntime().exec(command);
	  reader = new BufferedReader(new InputStreamReader(proc.getInputStream()));
	
	  while ((line = reader.readLine()) != null) {
	      
		  if (line.contains("STOPPED") || (line.contains("BUS-OFF")) ) {
	        System.out.println("Faild to restart:" + CANchannel);
	        return false;
	      }
		  if (txDetect) { 
			// the count in stored like this:
		    //        TX: bytes  packets  errors  dropped overrun mcast   
			// line = 0          0        0       0       0       0  
		    txDetect = false;
		    sub = line.substring(0, 15);
		    pretxCount = Integer.parseInt(sub.trim());
		  }
		  if (line.contains("TX:")) { // next line will contain the Tx count 
			txDetect = true;
		  }
	    }
	  
	  // send a new CAN message and read the Tx
	  command = "cansend " + CANchannel + " 123#1122334455667788";
	  proc = Runtime.getRuntime().exec(command);
	  
	  command = "ip -details -statistics link show " + CANchannel;
	  proc = Runtime.getRuntime().exec(command);
	  
	  reader = new BufferedReader(new InputStreamReader(proc.getInputStream()));
	  
	  while ((line = reader.readLine()) != null) {
		  if (txDetect) { 
		    txDetect = false;
		    sub = line.substring(0, 15);
		    posttxCount = Integer.parseInt(sub.trim());
		  }
		  if (line.contains("TX:")) { // next line will contain the Tx count 
			txDetect = true;
		  }
	    }
	  
	  if(pretxCount != posttxCount){
		  System.out.println("Selfcheck done on: " + CANchannel );
		  flag = true;
	  }
	  else {
		  System.out.println("Selfcheck faild on: " + CANchannel );
		  flag = false;
	  }
	  
	  return flag;
  }
}
