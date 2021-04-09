import java.net.InetAddress;
import java.util.Scanner;

public class TCPEnd {
    public static void main(String args[]) throws Exception {
        int command = 1;
        // do {
        //     System.out.print("Enter 1 for sender, 2 for receiver: ");
        //     Scanner sysInScanner = new Scanner(System.in);
        //     command = sysInScanner.nextInt();
        //     sysInScanner.close();
        // } while (command < 1 || command > 2);

        if(command == 1) {
            TCPSend send = new TCPSend("FilesToBeSend/gdb-tutorial-handout.pdf", 1400, 30);
            send.work(2608, InetAddress.getLocalHost(), 2806); // fake input
            System.out.println( send.getStatisticsString() );
            System.exit(0);
        }
        else if (command == 2){
            System.out.println("Receiver has not been written. Abort.");
            System.exit(0);
        }
        
    }
}
