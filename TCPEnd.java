import java.net.InetAddress;
import java.net.UnknownHostException;

public class TCPEnd {
    private static final String usage = "usage: %n (as sender) \t\t\t\tjava TCPend -p <port> -s <remote IP> -a <remote port> –f <file name> -m <mtu> -c <sws> %n (as receiver) \t\t\t\tjava TCPend -p <port> -m <mtu> -c <sws> -f <file name> %n (debug purpose only - sender) \t\tjava TCPend -t sender %n (debug purpose only - receiver) \tjava TCPend -t receiver %n";
    public static void main(String args[]) throws Exception {
        if (args.length == 2) {
            // Only for testing. Delete this before submittal
            for (int i = 0; i < args.length; ++i) {
                if ( args[i].equals("-t") ) {
                    ++i;
                    switch (args[i]) {
                        case "sender":
                            TCPSend send = new TCPSend(2608, InetAddress.getLocalHost(), 2806, "FilesToBeSend/gdb-tutorial-handout.pdf", 1400, 30);
                            send.work(); // fake input
                            System.out.println( send.getStatisticsString() );
                            System.exit(0);
                            break;
                        case "receiver":
                            TCPRcv rcv = new TCPRcv(2806, 1400, 30, "receive.pdf");
                            rcv.work();
                            // System.out.println( rcv.getStatisticsString() );
                            System.exit(0);
                            break;
                        default:
                            Invalid("failed to parse");
                            break;
                    }
                }
                else {
                    Invalid("wrong option: " + args[i]);
                }
            }
        }
        else if (args.length == 8) {
            // Receiver
            int port = -1;
            int mtu = -1;
            int sws = -1;
            String fileName = null;

            int i = 0;
            while(i < args.length) {
                switch (args[i]) {
                    case "-p":
                        if (port != -1) Invalid("duplicate option input: " + args[i]);
                        try { port = Integer.parseInt(args[++i]); }
                        catch (NumberFormatException e) { Invalid("fail to parse");}
                        break;
                    case "-m":
                        if (mtu != -1) Invalid("duplicate option input: " + args[i]);
                        try { mtu = Integer.parseInt(args[++i]); }
                        catch (NumberFormatException e) { Invalid("fail to parse");}
                        break;
                    case "-c":
                        if (sws != -1) Invalid("duplicate option input: " + args[i]);
                        try { sws = Integer.parseInt(args[++i]); }
                        catch (NumberFormatException e) { Invalid("fail to parse");}
                        break;
                    case "-f":
                        if (fileName != null) Invalid("duplicate option input: " + args[i]);
                        fileName = args[++i];
                        break;
                    default:
                        Invalid("wrong option: " + args[i]);
                        break;
                }
                ++i;
            }

            TCPRcv rcv = new TCPRcv(port, mtu, sws, fileName);
            rcv.work();
            // System.out.println( rcv.getStatisticsString() );
            System.exit(0);
        }
        else if (args.length == 12) {
            // Sender
            int port = -1;
            InetAddress remoteIp = null;
            int remotePort = -1;
            String fileName = null;
            int mtu = -1;
            int sws = -1;

            int i = 0;
            while(i < args.length) {
                switch (args[i]) {
                    case "-p":
                        if (port != -1) Invalid("duplicate option input: " + args[i]);
                        try { port = Integer.parseInt(args[++i]); }
                        catch (NumberFormatException e) { Invalid("fail to parse");}
                        break;
                    case "-s":
                        if (remoteIp != null) Invalid("duplicate option input: " + args[i]);
                        try { remoteIp = InetAddress.getByName(args[++i]); }
                        catch (UnknownHostException e) { Invalid("unknown host");}
                        break;
                    case "-a":
                        if (remotePort != -1) Invalid("duplicate option input: " + args[i]);
                        try { remotePort = Integer.parseInt(args[++i]); }
                        catch (NumberFormatException e) { Invalid("fail to parse");}
                        break;
                    case "-f":
                        if (fileName != null) Invalid("duplicate option input: " + args[i]);
                        fileName = args[++i];
                        break;
                    case "-m":
                        if (mtu != -1) Invalid("duplicate option input: " + args[i]);
                        try { mtu = Integer.parseInt(args[++i]); }
                        catch (NumberFormatException e) { Invalid("fail to parse");}
                        break;
                    case "-c":
                        if (sws != -1) Invalid("duplicate option input: " + args[i]);
                        try { sws = Integer.parseInt(args[++i]); }
                        catch (NumberFormatException e) { Invalid("fail to parse");}
                        break;
                    default:
                        Invalid("wrong option: " + args[i]);
                        break;
                }
                ++i;
            }
            TCPSend send = new TCPSend(port, remoteIp, remotePort, fileName, mtu, sws);
            send.work();
            System.out.println( send.getStatisticsString() );
            System.exit(0);
        }
        else {
            Invalid("wrong number of argument(s)");
        }

        // if(command == 1) {
        //     TCPSend send = new TCPSend(2608, InetAddress.getLocalHost(), 2806, "FilesToBeSend/gdb-tutorial-handout.pdf", 1400, 30);
        //     send.work(); // fake input
        //     System.out.println( send.getStatisticsString() );
        //     System.exit(0);
        // }
        // else if (command == 2){
        //     TCPRcv rcv = new TCPRcv(2806, 1400, 30, "receive.pdf");
        //     rcv.work();
        //     System.out.println( rcv.getStatisticsString() );
        //     System.exit(0);
        // }
        
    }

    private static void Invalid(String message) {
        System.err.println("TCPEnd: Parsing error: " + message);
        System.out.printf(usage);
        System.exit(1);
    }
}
