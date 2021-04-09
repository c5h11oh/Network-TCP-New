package Worker.Sender;

import java.io.*;
import java.nio.file.*;
import Buffer.*;
import Worker.Worker;
import Exceptions.*;

public class SendFile extends Worker {
    private SenderBuffer sendBuffer;
    private Path filePath;
    public SendFile(SenderBuffer sendBuffer, Path filePath){
        this.sendBuffer = sendBuffer;
        this.filePath = filePath;
    }
    public void run() {
        try (InputStream in = Files.newInputStream(filePath, StandardOpenOption.READ)) {
            BufferedInputStream bin = new BufferedInputStream(in);
            int bufFreeSpace, writeLength;
            while((writeLength = bin.available()) > 0){
                while ((bufFreeSpace = sendBuffer.checkFreeSpace()) == 0) { Thread.yield(); }

                writeLength = Math.min(writeLength, bufFreeSpace);
                byte[] data = new byte[writeLength];
                bin.read(data, 0, data.length);
                sendBuffer.put(data, writeLength);
            }
            sendBuffer.setSendFileFinished();
            bin.close();
        }
        catch (FileNotFoundException e){
            System.err.println("FileNotFoundException:" + e.toString());
            System.exit(1);
        }
        catch (BufferInsufficientSpaceException e){
            System.err.println("BufferInsufficientSpaceException:" + e.toString());
            System.exit(1);
        }
        catch (IOException e){
            System.err.println(e);
        }
        
    }
    
}
